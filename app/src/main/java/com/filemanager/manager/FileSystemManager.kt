package com.filemanager.manager

import com.filemanager.model.FileInfo
import com.filemanager.model.RemoteConnection
import java.io.InputStream
import java.io.OutputStream

/**
 * 协议无关的远程文件系统抽象。
 *
 * 约定：
 * - 所有路径均为已规范化的 POSIX 绝对路径（见 [com.filemanager.util.RemotePath]）；
 * - 所有失败都以 [RemoteException] 抛出，由调用方决定如何提示；
 * - 实现类不是线程安全的，调用方负责串行化（见 [ConnectionManager]）。
 */
interface FileSystemManager {

    /** 建立连接；失败抛出 [RemoteException] */
    fun connect(connection: RemoteConnection)

    fun disconnect()

    val isConnected: Boolean

    /** 列出目录内容（不含 `.`/`..`，不排序） */
    fun list(path: String): List<FileInfo>

    /** 确认目标目录存在，不存在抛 [RemoteException.Reason.NOT_FOUND] */
    fun changeDirectory(path: String)

    fun createDirectory(path: String)

    /** 删除文件或目录；[recursive] 为 true 时递归删除目录内容 */
    fun delete(path: String, recursive: Boolean)

    /** 移动到新路径（同 rename，但接受完整目标路径） */
    fun move(sourcePath: String, targetPath: String)

    /** 下载到 [target]，由调用方负责关闭流 */
    fun download(path: String, target: OutputStream)

    /** 从 [source] 上传，由调用方负责关闭流 */
    fun upload(path: String, source: InputStream)

    /** 是否支持服务器侧复制（否则由 [ConnectionManager] 走「下载→上传」中转） */
    val supportsCopy: Boolean get() = false

    /** 服务器侧复制，默认不支持 */
    fun copy(sourcePath: String, targetPath: String) {
        throw RemoteException(RemoteException.Reason.UNSUPPORTED, "copy not supported")
    }
}
