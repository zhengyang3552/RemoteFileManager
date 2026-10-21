package com.filemanager.manager.impl

import com.filemanager.manager.ErrorMapper
import com.filemanager.manager.FileSystemManager
import com.filemanager.manager.RemoteException
import com.filemanager.model.FileInfo
import com.filemanager.model.RemoteConnection
import com.filemanager.util.RemotePath
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import jcifs.smb.SmbFileInputStream
import jcifs.smb.SmbFileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Properties

/**
 * SMB / CIFS 实现（jcifs-ng，支持 SMB2/3）。
 *
 * 路径语义：
 * - `/` 表示「主机根」，列出可访问的共享（share）；
 * - `/share/dir` 表示某个共享下的目录。
 *
 * 边界处理：
 * - 用户名支持 `DOMAIN\\user`、`user@domain` 与纯用户名三种写法；
 * - 超时通过 jcifs 配置注入，避免默认 35s 的长时间等待；
 * - 递归删除先列子项，非空目录不会静默失败。
 */
class SmbFileSystemManager : FileSystemManager {

    private var context: CIFSContext? = null
    private var rootUrl: String = ""
    private var connection: RemoteConnection? = null

    override val isConnected: Boolean get() = context != null

    override fun connect(connection: RemoteConnection) {
        disconnect()
        try {
            val properties = Properties().apply {
                setProperty("jcifs.smb.client.responseTimeout", connection.timeoutMs.toString())
                setProperty("jcifs.smb.client.soTimeout", connection.timeoutMs.toString())
                setProperty("jcifs.smb.client.connTimeout", connection.timeoutMs.toString())
                setProperty("jcifs.smb.client.enableSMB2", "true")
                setProperty("jcifs.smb.client.dfs.disabled", "true")
                setProperty("jcifs.resolveOrder", "DNS")
            }
            val base = BaseContext(PropertyConfiguration(properties))
            val (domain, user) = splitUsername(connection.username)
            val auth = NtlmPasswordAuthenticator(domain, user, connection.password)
            val ctx = base.withCredentials(auth)

            val portPart = if (connection.port != 0 && connection.port != 445) ":${connection.port}" else ""
            rootUrl = "smb://${connection.host}$portPart/"

            // 连接校验：根共享可枚举，或指定的初始路径存在
            val probe = SmbFile(rootUrl, ctx)
            val exists = runCatching { probe.exists() }.getOrDefault(false)
            if (!exists) {
                throw RemoteException(RemoteException.Reason.HOST_UNREACHABLE, "smb host not reachable: $rootUrl")
            }

            this.context = ctx
            this.connection = connection
        } catch (e: RemoteException) {
            throw e
        } catch (e: Exception) {
            disconnect()
            throw ErrorMapper.toRemote(e)
        }
    }

    override fun disconnect() {
        runCatching { context?.close() }
        context = null
        connection = null
        rootUrl = ""
    }

    private fun requireContext(): CIFSContext = context
        ?: throw RemoteException(RemoteException.Reason.NOT_CONNECTED, "smb context closed")

    private fun splitUsername(raw: String): Pair<String, String> {
        val username = raw.trim()
        return when {
            username.contains('\\') -> username.substringBefore('\\') to username.substringAfter('\\')
            username.contains('@') -> username.substringAfter('@') to username.substringBefore('@')
            else -> "" to username
        }
    }

    /** 远程路径 → SMB URL；目录统一补尾斜杠，便于 jcifs 识别 */
    private fun toUrl(path: String, directory: Boolean): String {
        val normalized = RemotePath.normalize(path)
        val suffix = normalized.removePrefix("/")
        val trailing = if (directory && !suffix.endsWith("/")) "/" else ""
        return rootUrl + suffix + trailing
    }

    override fun list(path: String): List<FileInfo> {
        val ctx = requireContext()
        val normalized = RemotePath.normalize(path)
        try {
            val target = SmbFile(toUrl(normalized, true), ctx)
            if (!target.exists()) {
                throw RemoteException(RemoteException.Reason.NOT_FOUND, "no such path: $normalized")
            }
            if (!target.isDirectory) {
                throw RemoteException(RemoteException.Reason.INVALID_INPUT, "not a directory: $normalized")
            }
            return target.listFiles().mapNotNull { child ->
                val name = child.name.trimEnd('/')
                if (name.isEmpty() || name == "." || name == "..") return@mapNotNull null
                val isDirectory = runCatching { child.isDirectory }.getOrDefault(false)
                FileInfo(
                    name = name,
                    isDirectory = isDirectory,
                    size = if (isDirectory) 0L else runCatching { child.length() }.getOrDefault(0L),
                    lastModified = runCatching { child.lastModified() }.getOrDefault(0L)
                )
            }
        } catch (e: RemoteException) {
            throw e
        } catch (e: Exception) {
            throw ErrorMapper.toRemote(e)
        }
    }

    override fun changeDirectory(path: String) {
        val ctx = requireContext()
        val normalized = RemotePath.normalize(path)
        try {
            val target = SmbFile(toUrl(normalized, true), ctx)
            if (!target.exists()) throw RemoteException(RemoteException.Reason.NOT_FOUND, "no such path: $normalized")
            if (!target.isDirectory) throw RemoteException(RemoteException.Reason.INVALID_INPUT, "not a directory")
        } catch (e: RemoteException) {
            throw e
        } catch (e: Exception) {
            throw ErrorMapper.toRemote(e)
        }
    }

    override fun createDirectory(path: String) {
        val ctx = requireContext()
        try {
            val target = SmbFile(toUrl(path, true), ctx)
            if (target.exists()) throw RemoteException(RemoteException.Reason.ALREADY_EXISTS, "already exists")
            target.mkdir()
        } catch (e: RemoteException) {
            throw e
        } catch (e: Exception) {
            throw ErrorMapper.toRemote(e)
        }
    }

    override fun delete(path: String, recursive: Boolean) {
        val ctx = requireContext()
        val normalized = RemotePath.normalize(path)
        try {
            val target = SmbFile(toUrl(normalized, true), ctx)
            if (!target.exists()) throw RemoteException(RemoteException.Reason.NOT_FOUND, "no such path: $normalized")
            if (target.isDirectory && recursive) {
                target.listFiles().forEach { child ->
                    delete(RemotePath.child(normalized, child.name.trimEnd('/')), true)
                }
            }
            target.delete()
        } catch (e: RemoteException) {
            throw e
        } catch (e: Exception) {
            throw ErrorMapper.toRemote(e)
        }
    }

    override fun move(sourcePath: String, targetPath: String) {
        val ctx = requireContext()
        try {
            val from = RemotePath.normalize(sourcePath)
            val to = RemotePath.normalize(targetPath)
            if (from == to) throw RemoteException(RemoteException.Reason.INVALID_INPUT, "same path")
            val source = SmbFile(toUrl(from, true), ctx)
            val targetDir = RemotePath.parent(to)
            val targetName = RemotePath.name(to)
            val destination = SmbFile(toUrl(targetDir, true) + targetName, ctx)
            source.renameTo(destination, true)
        } catch (e: RemoteException) {
            throw e
        } catch (e: Exception) {
            throw ErrorMapper.toRemote(e)
        }
    }

    override fun download(path: String, target: OutputStream) {
        val ctx = requireContext()
        try {
            SmbFileInputStream(SmbFile(toUrl(path, false), ctx)).use { input ->
                input.copyTo(target, DEFAULT_BUFFER_SIZE)
            }
        } catch (e: Exception) {
            throw ErrorMapper.toRemote(e)
        }
    }

    override fun upload(path: String, source: InputStream) {
        val ctx = requireContext()
        try {
            SmbFileOutputStream(SmbFile(toUrl(path, false), ctx)).use { output ->
                source.copyTo(output, DEFAULT_BUFFER_SIZE)
            }
        } catch (e: Exception) {
            throw ErrorMapper.toRemote(e)
        }
    }
}
