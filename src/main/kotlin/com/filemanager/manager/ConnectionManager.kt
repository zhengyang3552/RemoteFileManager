package com.filemanager.manager

import com.filemanager.model.*
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport

/**
 * 连接管理器
 * 管理所有连接并通知UI更新
 */
class ConnectionManager {
    private val connections = mutableListOf<RemoteConnection>()
    private val managers = mutableMapOf<RemoteConnection, FileSystemManager>()
    private val propertyChangeSupport = PropertyChangeSupport(this)

    /**
     * 添加连接
     */
    fun addConnection(connection: RemoteConnection): Boolean {
        if (connections.any { it.name == connection.name }) {
            return false
        }
        connections.add(connection)
        propertyChangeSupport.firePropertyChange("connectionAdded", null, connection)
        return true
    }

    /**
     * 删除连接
     */
    fun removeConnection(name: String): Boolean {
        val index = connections.indexOfFirst { it.name == name }
        if (index >= 0) {
            val connection = connections.removeAt(index)
            managers.remove(connection)?.close()
            propertyChangeSupport.firePropertyChange("connectionRemoved", null, connection)
            return true
        }
        return false
    }

    /**
     * 获取所有连接
     */
    fun getConnections(): List<RemoteConnection> = connections.toList()

    /**
     * 获取连接
     */
    fun getConnection(name: String): RemoteConnection? = connections.find { it.name == name }

    /**
     * 连接到服务器
     */
    fun connect(connection: RemoteConnection): Boolean {
        try {
            connection.connectionState = ConnectionState.CONNECTING
            propertyChangeSupport.firePropertyChange("connectionChanged", null, connection)

            // 获取对应的管理器
            val manager = FileSystemManagerFactory.getManager(connection.type)
            managers[connection] = manager

            // 连接
            val success = manager.connect(connection)

            if (!success) {
                connection.connectionState = ConnectionState.ERROR
                connection.errorMessage = "连接失败: 无法连接到服务器"
            }

            propertyChangeSupport.firePropertyChange("connectionChanged", null, connection)
            return success

        } catch (e: Exception) {
            connection.connectionState = ConnectionState.ERROR
            connection.errorMessage = "连接失败: ${e.message}"
            propertyChangeSupport.firePropertyChange("connectionChanged", null, connection)
            return false
        }
    }

    /**
     * 断开连接
     */
    fun disconnect(connection: RemoteConnection) {
        managers[connection]?.disconnect()
        connection.connectionState = ConnectionState.DISCONNECTED
        propertyChangeSupport.firePropertyChange("connectionChanged", null, connection)
    }

    /**
     * 列出目录内容
     */
    fun listFiles(connection: RemoteConnection, path: String): List<FileInfo> {
        val manager = managers[connection] ?: return emptyList()
        return try {
            manager.listFiles(path)
        } catch (e: Exception) {
            println("列出文件失败: ${e.message}")
            emptyList()
        }
    }

    /**
     * 获取当前目录
     */
    fun getCurrentDirectory(connection: RemoteConnection): String {
        val manager = managers[connection] ?: return "/"
        return manager.getCurrentDirectory()
    }

    /**
     * 切换目录
     */
    fun changeDirectory(connection: RemoteConnection, path: String): Boolean {
        val manager = managers[connection] ?: return false
        return manager.changeDirectory(path)
    }

    /**
     * 创建目录
     */
    fun createDirectory(connection: RemoteConnection, path: String): Boolean {
        val manager = managers[connection] ?: return false
        return manager.createDirectory(path)
    }

    /**
     * 删除文件/目录
     */
    fun delete(connection: RemoteConnection, path: String): Boolean {
        val manager = managers[connection] ?: return false
        return manager.delete(path)
    }

    /**
     * 重命名文件/目录
     */
    fun rename(connection: RemoteConnection, oldPath: String, newName: String): Boolean {
        val manager = managers[connection] ?: return false
        return manager.rename(oldPath, newName)
    }

    /**
     * 下载文件
     */
    fun downloadFile(connection: RemoteConnection, remotePath: String, localPath: String): Boolean {
        val manager = managers[connection] ?: return false
        return manager.downloadFile(remotePath, localPath)
    }

    /**
     * 上传文件
     */
    fun uploadFile(connection: RemoteConnection, localPath: String, remotePath: String): Boolean {
        val manager = managers[connection] ?: return false
        return manager.uploadFile(localPath, remotePath)
    }

    /**
     * 复制文件
     */
    fun copyRemote(connection: RemoteConnection, sourcePath: String, destinationPath: String): Boolean {
        val manager = managers[connection] ?: return false
        return manager.copyRemote(sourcePath, destinationPath)
    }

    /**
     * 移动文件
     */
    fun moveRemote(connection: RemoteConnection, sourcePath: String, destinationPath: String): Boolean {
        val manager = managers[connection] ?: return false
        return manager.moveRemote(sourcePath, destinationPath)
    }

    /**
     * 获取文件大小
     */
    fun getFileSize(connection: RemoteConnection, path: String): Long {
        val manager = managers[connection] ?: return -1L
        return manager.getFileSize(path)
    }

    /**
     * 关闭所有连接
     */
    fun closeAll() {
        connections.forEach { disconnect(it) }
        managers.values.forEach { it.close() }
        connections.clear()
        managers.clear()
    }

    /**
     * 添加属性ChangeListener
     */
    fun addPropertyChangeListener(listener: PropertyChangeListener) {
        propertyChangeSupport.addPropertyChangeListener(listener)
    }

    /**
     * 移除属性ChangeListener
     */
    fun removePropertyChangeListener(listener: PropertyChangeListener) {
        propertyChangeSupport.removePropertyChangeListener(listener)
    }
}
