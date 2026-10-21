package com.filemanager.manager.impl

import com.filemanager.manager.ErrorMapper
import com.filemanager.manager.FileSystemManager
import com.filemanager.manager.RemoteException
import com.filemanager.model.FileInfo
import com.filemanager.model.ProtocolType
import com.filemanager.model.RemoteConnection
import com.filemanager.util.RemotePath
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient
import org.apache.commons.net.util.TrustManagerUtils
import java.io.InputStream
import java.io.OutputStream
import javax.net.ssl.HostnameVerifier

/**
 * FTP / FTPS 实现（Apache Commons Net）。
 *
 * 边界处理：
 * - 连接被服务器拒绝（非 2xx welcome）与登录失败（530）分开上报，便于引导用户排查；
 * - 目录列举统一先 `CWD` 再 `LIST .`，既校验目录存在性，也兼容不支持绝对路径列举的服务器；
 * - 删除时先尝试作为目录递归处理，失败再按文件删除；
 * - FTPS 使用显式/隐式 TLS 两种模式（端口 990 视为隐式）。
 */
class FtpFileSystemManager(private val implicitFtps: Boolean = false) : FileSystemManager {

    private var client: FTPClient? = null

    override val isConnected: Boolean
        get() = client?.isConnected == true

    override fun connect(connection: RemoteConnection) {
        disconnect()
        val ftp = if (connection.type == ProtocolType.FTPS) createFtpsClient() else FTPClient()
        try {
            ftp.connectTimeout = connection.timeoutMs
            ftp.defaultTimeout = connection.timeoutMs
            ftp.connect(connection.host, connection.port)
            if (!FTPReply.isPositiveCompletion(ftp.replyCode)) {
                throw RemoteException(
                    RemoteException.Reason.PROTOCOL,
                    "server refused connection: ${ftp.replyCode} ${ftp.replyString.orEmpty()}"
                )
            }
            ftp.controlEncoding = "UTF-8"
            if (connection.passiveMode) ftp.enterLocalPassiveMode() else ftp.enterLocalActiveMode()
            if (!ftp.login(connection.username, connection.password)) {
                throw RemoteException(RemoteException.Reason.AUTH_FAILED, ftp.replyString.orEmpty())
            }
            ftp.setFileType(FTP.BINARY_FILE_TYPE)
            if (ftp is FTPSClient) {
                // 数据通道同样加密（PROT P）；PBSZ 0 是 TLS 要求
                ftp.execPBSZ(0)
                ftp.execPROT("P")
            }
            client = ftp
        } catch (e: RemoteException) {
            safeQuit(ftp)
            throw e
        } catch (e: Exception) {
            safeQuit(ftp)
            throw ErrorMapper.toRemote(e)
        }
    }

    private fun createFtpsClient(): FTPSClient {
        val ftps = FTPSClient("TLS", implicitFtps)
        // 远程文件服务器常用自签名证书，这里放宽校验以便「测试连接」可用；
        // 如需严格校验，应替换为带 CA 的 TrustManager 并保留 HostnameVerifier。
        ftps.trustManager = TrustManagerUtils.getAcceptAllTrustManager()
        ftps.hostnameVerifier = HostnameVerifier { _, _ -> true }
        return ftps
    }

    override fun disconnect() {
        client?.let { safeQuit(it) }
        client = null
    }

    private fun safeQuit(ftp: FTPClient) {
        try {
            if (ftp.isConnected) {
                ftp.logout()
                ftp.disconnect()
            }
        } catch (ignored: Exception) {
            // 断开连接阶段的异常不影响用户可见结果
        }
    }

    private fun requireClient(): FTPClient =
        client?.takeIf { it.isConnected } ?: throw RemoteException(RemoteException.Reason.NOT_CONNECTED, "ftp client closed")

    override fun list(path: String): List<FileInfo> {
        val ftp = requireClient()
        val normalized = RemotePath.normalize(path)
        try {
            if (!ftp.changeWorkingDirectory(normalized)) {
                throw RemoteException(
                    RemoteException.Reason.NOT_FOUND,
                    "CWD failed: ${ftp.replyCode} ${ftp.replyString.orEmpty()}"
                )
            }
            val entries = ftp.listFiles(".") ?: emptyArray()
            return entries.mapNotNull { entry ->
                val name = entry.name?.trim().orEmpty()
                if (name.isEmpty() || name == "." || name == "..") return@mapNotNull null
                FileInfo(
                    name = name,
                    isDirectory = entry.isDirectory,
                    size = if (entry.isDirectory) 0L else entry.size.coerceAtLeast(0L),
                    lastModified = entry.timestamp?.timeInMillis ?: 0L
                )
            }
        } catch (e: RemoteException) {
            throw e
        } catch (e: Exception) {
            throw ErrorMapper.toRemote(e, RemoteException.Reason.PROTOCOL)
        }
    }

    override fun changeDirectory(path: String) {
        val ftp = requireClient()
        val normalized = RemotePath.normalize(path)
        try {
            if (!ftp.changeWorkingDirectory(normalized)) {
                throw RemoteException(RemoteException.Reason.NOT_FOUND, "CWD failed: ${ftp.replyString.orEmpty()}")
            }
        } catch (e: RemoteException) {
            throw e
        } catch (e: Exception) {
            throw ErrorMapper.toRemote(e)
        }
    }

    override fun createDirectory(path: String) {
        val ftp = requireClient()
        try {
            if (!ftp.makeDirectory(RemotePath.normalize(path))) {
                throw failure("MKD", ftp)
            }
        } catch (e: RemoteException) {
            throw e
        } catch (e: Exception) {
            throw ErrorMapper.toRemote(e)
        }
    }

    override fun delete(path: String, recursive: Boolean) {
        val ftp = requireClient()
        val normalized = RemotePath.normalize(path)
        try {
            if (recursive) {
                // 能列举说明是目录，先递归清空；列举失败则按文件处理
                val children = runCatching { list(normalized) }.getOrNull()
                if (children != null) {
                    children.forEach { child ->
                        delete(RemotePath.child(normalized, child.name), true)
                    }
                }
            }
            val removed = ftp.deleteFile(normalized) || ftp.removeDirectory(normalized)
            if (!removed) throw failure("DELE/RMD", ftp)
        } catch (e: RemoteException) {
            throw e
        } catch (e: Exception) {
            throw ErrorMapper.toRemote(e)
        }
    }

    override fun move(sourcePath: String, targetPath: String) {
        val ftp = requireClient()
        try {
            val from = RemotePath.normalize(sourcePath)
            val to = RemotePath.normalize(targetPath)
            if (from == to) throw RemoteException(RemoteException.Reason.INVALID_INPUT, "same path")
            if (!ftp.rename(from, to)) throw failure("RNFR/RNTO", ftp)
        } catch (e: RemoteException) {
            throw e
        } catch (e: Exception) {
            throw ErrorMapper.toRemote(e)
        }
    }

    override fun download(path: String, target: OutputStream) {
        val ftp = requireClient()
        try {
            ftp.setFileType(FTP.BINARY_FILE_TYPE)
            if (!ftp.retrieveFile(RemotePath.normalize(path), target)) throw failure("RETR", ftp)
        } catch (e: RemoteException) {
            throw e
        } catch (e: Exception) {
            throw ErrorMapper.toRemote(e)
        }
    }

    override fun upload(path: String, source: InputStream) {
        val ftp = requireClient()
        try {
            ftp.setFileType(FTP.BINARY_FILE_TYPE)
            if (!ftp.storeFile(RemotePath.normalize(path), source)) throw failure("STOR", ftp)
        } catch (e: RemoteException) {
            throw e
        } catch (e: Exception) {
            throw ErrorMapper.toRemote(e)
        }
    }

    private fun failure(operation: String, ftp: FTPClient): RemoteException {
        val message = "$operation ${ftp.replyCode} ${ftp.replyString.orEmpty()}".trim()
        val reason = when (ftp.replyCode) {
            530 -> RemoteException.Reason.AUTH_FAILED
            550 -> RemoteException.Reason.PERMISSION_DENIED
            552, 452, 425, 426 -> RemoteException.Reason.PROTOCOL
            502, 504 -> RemoteException.Reason.UNSUPPORTED
            else -> RemoteException.Reason.PROTOCOL
        }
        return RemoteException(reason, message)
    }
}
