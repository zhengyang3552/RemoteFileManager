package com.filemanager.manager

import android.content.Context
import com.filemanager.model.ConnectionState
import com.filemanager.model.FileInfo
import com.filemanager.model.RemoteConnection
import com.filemanager.util.NetworkState
import com.filemanager.util.RemotePath
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * 连接会话与远程操作的统一入口。
 *
 * 设计要点：
 * - **串行化**：所有方法加锁，调用方（MainActivity）用单线程执行器调度，
 *   保证同一时刻只有一个协议操作在跑，避免 FTP 控制通道/WebDAV 会话状态互相干扰；
 * - **路径托管**：维护当前目录，业务层只需传文件名，绝对路径拼接集中在 [RemotePath]；
 * - **能力降级**：服务器不支持服务端复制时，自动用「下载到缓存→上传」兜底；
 * - **异常归一**：所有失败都抛 [RemoteException]，UI 只处理 reason。
 */
class ConnectionManager(private val context: Context) {

    private var manager: FileSystemManager? = null
    private var connection: RemoteConnection? = null
    private var currentPath: String = RemotePath.ROOT

    val activeConnection: RemoteConnection? get() = connection
    val currentDirectory: String get() = currentPath
    val isConnected: Boolean get() = manager?.isConnected == true

    /**
     * 建立连接并解析初始目录。
     * 若配置的初始目录不存在，会回退到根目录而不是直接失败（配置写错的常见情形）。
     *
     * @return 实际生效的目录
     */
    @Synchronized
    fun connect(target: RemoteConnection): String {
        ensureNetworkAvailable()
        disconnectInternal()

        target.connectionState = ConnectionState.CONNECTING
        target.lastError = null
        val fs = FileSystemManagerFactory.create(target)
        try {
            fs.connect(target)
            manager = fs
            connection = target
            target.connectionState = ConnectionState.CONNECTED

            val requested = RemotePath.normalize(target.path)
            val resolved = try {
                fs.changeDirectory(requested)
                requested
            } catch (e: RemoteException) {
                // 初始路径不可用时回退根目录；若根目录也不可用，如实向上抛出
                fs.changeDirectory(RemotePath.ROOT)
                RemotePath.ROOT
            }
            currentPath = resolved
            return resolved
        } catch (e: Throwable) {
            val remote = ErrorMapper.toRemote(e)
            runCatching { fs.disconnect() }
            manager = null
            connection = null
            currentPath = RemotePath.ROOT
            target.connectionState = ConnectionState.ERROR
            target.lastError = remote.detail
            throw remote
        }
    }

    @Synchronized
    fun disconnect() {
        val active = connection
        disconnectInternal()
        active?.connectionState = ConnectionState.DISCONNECTED
    }

    private fun disconnectInternal() {
        runCatching { manager?.disconnect() }
        manager = null
        connection = null
        currentPath = RemotePath.ROOT
    }

    /** 列出目录内容（已过滤 `.`/`..`），并更新当前目录 */
    @Synchronized
    fun list(path: String): List<FileInfo> {
        val fs = requireManager()
        val normalized = RemotePath.normalize(path)
        val items = try {
            fs.list(normalized)
        } catch (e: Throwable) {
            throw ErrorMapper.toRemote(e)
        }.filter { it.name != "." && it.name != ".." }
        currentPath = normalized
        return items
    }

    /** 进入子目录，返回新的当前目录 */
    @Synchronized
    fun enter(name: String): String {
        val fs = requireManager()
        val target = RemotePath.child(currentPath, name)
        try {
            fs.changeDirectory(target)
        } catch (e: Throwable) {
            throw ErrorMapper.toRemote(e)
        }
        currentPath = target
        return target
    }

    /** 上跳一级，根目录保持不变 */
    @Synchronized
    fun goUp(): String {
        val parent = RemotePath.parent(currentPath)
        if (parent == currentPath) return currentPath
        val fs = requireManager()
        try {
            fs.changeDirectory(parent)
        } catch (e: Throwable) {
            throw ErrorMapper.toRemote(e)
        }
        currentPath = parent
        return parent
    }

    @Synchronized
    fun createFolder(name: String) {
        val fs = requireManager()
        validateName(name)
        val path = RemotePath.child(currentPath, name.trim())
        if (exists(path)) throw RemoteException(RemoteException.Reason.ALREADY_EXISTS, "exists: $path")
        try {
            fs.createDirectory(path)
        } catch (e: Throwable) {
            throw ErrorMapper.toRemote(e)
        }
    }

    /** 重命名当前目录下的条目 */
    @Synchronized
    fun rename(file: FileInfo, newName: String, baseDir: String = currentPath) {
        val fs = requireManager()
        validateName(newName)
        val trimmed = newName.trim()
        if (trimmed == file.name) throw RemoteException(RemoteException.Reason.INVALID_INPUT, "same name")
        val source = RemotePath.child(baseDir, file.name)
        val target = RemotePath.child(baseDir, trimmed)
        if (exists(target)) throw RemoteException(RemoteException.Reason.ALREADY_EXISTS, "exists: $target")
        try {
            fs.move(source, target)
        } catch (e: Throwable) {
            throw ErrorMapper.toRemote(e)
        }
    }

    /** 删除当前目录下的条目（目录递归删除） */
    @Synchronized
    fun delete(file: FileInfo, baseDir: String = currentPath) {
        val fs = requireManager()
        val path = RemotePath.child(baseDir, file.name)
        try {
            fs.delete(path, recursive = true)
        } catch (e: Throwable) {
            throw ErrorMapper.toRemote(e)
        }
    }

    /**
     * 复制到指定目录。
     * 服务端支持 COPY 时直接复制；否则走「下载到缓存→上传」中转，
     * 并在文件过大时给出「不支持」提示，避免把设备存储写满。
     */
    @Synchronized
    fun copy(file: FileInfo, targetDir: String, overwrite: Boolean, baseDir: String = currentPath) {
        val fs = requireManager()
        val from = RemotePath.child(baseDir, file.name)
        val to = RemotePath.child(targetDir, file.name)
        if (from == to) throw RemoteException(RemoteException.Reason.INVALID_INPUT, "same path")
        if (!overwrite && exists(to)) throw RemoteException(RemoteException.Reason.ALREADY_EXISTS, "exists: $to")

        try {
            if (fs.supportsCopy) {
                fs.copy(from, to)
                return
            }
            if (file.isDirectory) {
                throw RemoteException(RemoteException.Reason.UNSUPPORTED, "server-side directory copy unsupported")
            }
            if (file.size > COPY_FALLBACK_LIMIT) {
                throw RemoteException(RemoteException.Reason.UNSUPPORTED, "file too large for fallback copy")
            }
            val temp = File(context.cacheDir, "rfm_copy_${System.nanoTime()}")
            try {
                temp.outputStream().buffered().use { out -> fs.download(from, out) }
                temp.inputStream().buffered().use { input -> fs.upload(to, input) }
            } finally {
                runCatching { temp.delete() }
            }
        } catch (e: RemoteException) {
            throw e
        } catch (e: Throwable) {
            throw ErrorMapper.toRemote(e)
        }
    }

    /** 移动到指定目录 */
    @Synchronized
    fun move(file: FileInfo, targetDir: String, overwrite: Boolean, baseDir: String = currentPath) {
        val fs = requireManager()
        val from = RemotePath.child(baseDir, file.name)
        val to = RemotePath.child(targetDir, file.name)
        if (from == to) throw RemoteException(RemoteException.Reason.INVALID_INPUT, "same path")
        if (!overwrite && exists(to)) throw RemoteException(RemoteException.Reason.ALREADY_EXISTS, "exists: $to")
        try {
            fs.move(from, to)
        } catch (e: Throwable) {
            throw ErrorMapper.toRemote(e)
        }
    }

    @Synchronized
    fun download(file: FileInfo, target: OutputStream, baseDir: String = currentPath) {
        val fs = requireManager()
        if (file.isDirectory) throw RemoteException(RemoteException.Reason.INVALID_INPUT, "cannot download a directory")
        try {
            fs.download(RemotePath.child(baseDir, file.name), target)
        } catch (e: Throwable) {
            throw ErrorMapper.toRemote(e)
        }
    }

    @Synchronized
    fun upload(fileName: String, source: InputStream, baseDir: String = currentPath) {
        val fs = requireManager()
        val safeName = RemotePath.sanitizeFileName(fileName)
        val target = RemotePath.child(baseDir, safeName)
        try {
            fs.upload(target, source)
        } catch (e: Throwable) {
            throw ErrorMapper.toRemote(e)
        }
    }

    /** 当前位置是否已存在同名条目 */
    @Synchronized
    fun exists(path: String): Boolean {
        val fs = requireManager()
        val normalized = RemotePath.normalize(path)
        val parent = RemotePath.parent(normalized)
        val name = RemotePath.name(normalized)
        return try {
            fs.list(parent).any { it.name == name }
        } catch (e: Throwable) {
            // 无法列举父目录时按「不存在」处理，让后续操作自己报出真实错误
            false
        }
    }

    /** 测试连接：连接成功后立刻断开，不影响当前会话 */
    @Synchronized
    fun test(target: RemoteConnection) {
        ensureNetworkAvailable()
        val fs = FileSystemManagerFactory.create(target)
        try {
            fs.connect(target)
        } catch (e: Throwable) {
            throw ErrorMapper.toRemote(e)
        } finally {
            runCatching { fs.disconnect() }
        }
    }

    private fun requireManager(): FileSystemManager = manager?.takeIf { it.isConnected }
        ?: throw RemoteException(RemoteException.Reason.NOT_CONNECTED, "no active connection")

    private fun ensureNetworkAvailable() {
        if (!NetworkState.isOnline(context)) {
            throw RemoteException(RemoteException.Reason.NETWORK_UNAVAILABLE, "no network")
        }
    }

    private fun validateName(name: String) {
        when (RemotePath.validateName(name)) {
            RemotePath.NameError.EMPTY -> throw RemoteException(RemoteException.Reason.INVALID_INPUT, "empty name")
            RemotePath.NameError.TOO_LONG -> throw RemoteException(RemoteException.Reason.INVALID_INPUT, "name too long")
            RemotePath.NameError.INVALID_CHARS -> throw RemoteException(RemoteException.Reason.INVALID_INPUT, "illegal chars")
            RemotePath.NameError.RESERVED -> throw RemoteException(RemoteException.Reason.INVALID_INPUT, "reserved name")
            null -> Unit
        }
    }

    private companion object {
        /** 服务端不支持复制时的中转上限（512 MB），避免耗尽设备缓存空间 */
        const val COPY_FALLBACK_LIMIT = 512L * 1024 * 1024
    }
}
