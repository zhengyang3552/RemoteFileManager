package com.filemanager.manager

import com.filemanager.model.FileInfo
import com.filemanager.model.RemoteConnection

/**
 * 文件系统管理器接口
 * 所有协议管理器必须实现此接口
 */
interface FileSystemManager {
    /**
     * 连接到远程服务器
     */
    fun connect(connection: RemoteConnection): Boolean

    /**
     * 断开连接
     */
    fun disconnect()

    /**
     * 检查是否已连接
     */
    fun isConnected(): Boolean

    /**
     * 列出目录内容
     */
    fun listFiles(path: String): List<FileInfo>

    /**
     * 获取当前目录
     */
    fun getCurrentDirectory(): String

    /**
     * 更改当前目录
     */
    fun changeDirectory(path: String): Boolean

    /**
     * 创建目录
     */
    fun createDirectory(path: String): Boolean

    /**
     * 删除文件或目录
     */
    fun delete(path: String): Boolean

    /**
     * 重命名文件或目录
     */
    fun rename(oldPath: String, newName: String): Boolean

    /**
     * 下载文件（下载到本地目录）
     */
    fun downloadFile(remotePath: String, localPath: String): Boolean

    /**
     * 上传文件（从本地到远程）
     */
    fun uploadFile(localPath: String, remotePath: String): Boolean

    /**
     * 在远程服务器上复制文件
     */
    fun copyRemote(sourcePath: String, destinationPath: String): Boolean

    /**
     * 在远程服务器上移动文件/目录
     */
    fun moveRemote(sourcePath: String, destinationPath: String): Boolean

    /**
     * 获取文件下载大小
     */
    fun getFileSize(remotePath: String): Long

    /**
     * 清理资源
     */
    fun close()
}
