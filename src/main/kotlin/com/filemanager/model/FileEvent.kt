package com.filemanager.model

/**
 * 文件事件，用于 UI 更新通知
 */
sealed class FileEvent {
    data class ConnectionStateChanged(val connection: RemoteConnection) : FileEvent()
    data class FilesChanged(val connection: RemoteConnection, val files: List<FileInfo>) : FileEvent()
    data class OperationResult(
        val connection: RemoteConnection,
        val success: Boolean,
        val message: String
    ) : FileEvent()
    data class DirectoryChanged(
        val connection: RemoteConnection,
        val currentPath: String
    ) : FileEvent()
}
