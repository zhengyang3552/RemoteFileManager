package com.filemanager.manager.impl

import com.filemanager.manager.ErrorMapper
import com.filemanager.manager.FileSystemManager
import com.filemanager.manager.RemoteException
import com.filemanager.model.FileInfo
import com.filemanager.model.RemoteConnection
import com.filemanager.util.RemotePath
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import java.io.InputStream
import java.io.OutputStream
import java.util.EnumSet

/**
 * SFTP 实现（sshj）。
 *
 * 边界处理：
 * - 连接超时与读写超时分别设置，避免「能连上但列目录卡死」；
 * - 主机密钥采用 PromiscuousVerifier（自建服务器常见），首次连接不做 known_hosts 校验；
 * - 上传使用 WRITE|CREAT|TRUNC，覆盖语义明确；
 * - 递归删除先列子项再删自身，遇到空目录直接 rmdir。
 */
class SftpFileSystemManager : FileSystemManager {

    private var sshClient: SSHClient? = null
    private var sftpClient: SFTPClient? = null

    override val isConnected: Boolean
        get() = sshClient?.isConnected == true && sftpClient != null

    override fun connect(connection: RemoteConnection) {
        disconnect()
        val ssh = SSHClient()
        try {
            ssh.addHostKeyVerifier(PromiscuousVerifier())
            ssh.connectTimeout = connection.timeoutMs
            ssh.timeout = connection.timeoutMs
            ssh.connect(connection.host, connection.port)
            ssh.authPassword(connection.username, connection.password.toCharArray())
            sftpClient = ssh.newSFTPClient()
            sshClient = ssh
        } catch (e: Exception) {
            runCatching { ssh.disconnect() }
            throw ErrorMapper.toRemote(e)
        }
    }

    override fun disconnect() {
        runCatching { sftpClient?.close() }
        runCatching { sshClient?.disconnect() }
        sftpClient = null
        sshClient = null
    }

    private fun requireSftp(): SFTPClient =
        sftpClient?.takeIf { sshClient?.isConnected == true }
            ?: throw RemoteException(RemoteException.Reason.NOT_CONNECTED, "sftp session closed")

    override fun list(path: String): List<FileInfo> {
        val sftp = requireSftp()
        val normalized = RemotePath.normalize(path)
        try {
            return sftp.ls(normalized).mapNotNull { info ->
                val name = info.name
                if (name.isNullOrEmpty() || name == "." || name == "..") return@mapNotNull null
                val attributes = info.attributes
                FileInfo(
                    name = name,
                    isDirectory = info.isDirectory,
                    size = if (info.isDirectory) 0L else attributes.size.coerceAtLeast(0L),
                    lastModified = attributes.mtime * 1000L,
                    permissions = null
                )
            }
        } catch (e: Exception) {
            throw ErrorMapper.toRemote(mapNotFound(e, normalized), RemoteException.Reason.PROTOCOL)
        }
    }

    override fun changeDirectory(path: String) {
        val sftp = requireSftp()
        val normalized = RemotePath.normalize(path)
        try {
            val attributes = sftp.statExistence(normalized)
                ?: throw RemoteException(RemoteException.Reason.NOT_FOUND, "no such directory: $normalized")
            if (attributes.type != net.schmizz.sshj.sftp.FileMode.Type.DIRECTORY) {
                throw RemoteException(RemoteException.Reason.INVALID_INPUT, "not a directory: $normalized")
            }
        } catch (e: RemoteException) {
            throw e
        } catch (e: Exception) {
            throw ErrorMapper.toRemote(e)
        }
    }

    override fun createDirectory(path: String) {
        val sftp = requireSftp()
        try {
            sftp.mkdir(RemotePath.normalize(path))
        } catch (e: Exception) {
            throw ErrorMapper.toRemote(e)
        }
    }

    override fun delete(path: String, recursive: Boolean) {
        val sftp = requireSftp()
        val normalized = RemotePath.normalize(path)
        try {
            val attributes = sftp.statExistence(normalized)
                ?: throw RemoteException(RemoteException.Reason.NOT_FOUND, "no such path: $normalized")
            if (attributes.type == net.schmizz.sshj.sftp.FileMode.Type.DIRECTORY) {
                if (recursive) {
                    sftp.ls(normalized).forEach { child ->
                        if (child.name != "." && child.name != "..") {
                            delete(RemotePath.child(normalized, child.name), true)
                        }
                    }
                }
                sftp.rmdir(normalized)
            } else {
                sftp.rm(normalized)
            }
        } catch (e: RemoteException) {
            throw e
        } catch (e: Exception) {
            throw ErrorMapper.toRemote(e)
        }
    }

    override fun move(sourcePath: String, targetPath: String) {
        val sftp = requireSftp()
        try {
            val from = RemotePath.normalize(sourcePath)
            val to = RemotePath.normalize(targetPath)
            if (from == to) throw RemoteException(RemoteException.Reason.INVALID_INPUT, "same path")
            sftp.rename(from, to)
        } catch (e: RemoteException) {
            throw e
        } catch (e: Exception) {
            throw ErrorMapper.toRemote(e)
        }
    }

    override fun download(path: String, target: OutputStream) {
        val sftp = requireSftp()
        try {
            sftp.open(RemotePath.normalize(path)).use { remoteFile ->
                remoteFile.RemoteFileInputStream().use { input ->
                    input.copyTo(target, DEFAULT_BUFFER_SIZE)
                }
            }
        } catch (e: Exception) {
            throw ErrorMapper.toRemote(e)
        }
    }

    override fun upload(path: String, source: InputStream) {
        val sftp = requireSftp()
        try {
            val modes = EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC)
            sftp.open(RemotePath.normalize(path), modes).use { remoteFile ->
                remoteFile.RemoteFileOutputStream().use { output ->
                    source.copyTo(output, DEFAULT_BUFFER_SIZE)
                }
            }
        } catch (e: Exception) {
            throw ErrorMapper.toRemote(e)
        }
    }

    /** sshj 对「路径不存在」通常抛 SFTPException(NoSuchFile)，这里补充路径信息便于定位 */
    private fun mapNotFound(e: Throwable, path: String): Throwable {
        val message = e.message.orEmpty()
        return if (message.contains("No such file", ignoreCase = true)) {
            RemoteException(RemoteException.Reason.NOT_FOUND, "no such path: $path")
        } else {
            e
        }
    }
}
