package com.filemanager.model

import java.time.LocalDateTime

/**
 * 统一文件信息模型
 */
data class FileInfo(
    val name: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val lastModified: LocalDateTime? = null,
    val permissions: String? = null,
    val owner: String? = null
) {
    val displayName: String
        get() = if (isDirectory) "$name/" else name

    val formattedSize: String
        get() = if (isDirectory) "<DIR>" else formatSize(size)

    companion object {
        fun formatSize(bytes: Long): String {
            return when {
                bytes < 1024L -> "$bytes B"
                bytes < 1024L * 1024 -> "${bytes / 1024} KB"
                bytes < 1024L * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
                else -> "${bytes / (1024 * 1024 * 1024)} GB"
            }
        }
    }
}

/**
 * 远程连接配置
 */
data class RemoteConnection(
    val name: String,                      // 连接显示名称
    val type: ProtocolType,                // 协议类型
    val host: String,                      // 主机地址
    val port: Int,                         // 端口
    val username: String,                  // 用户名
    val password: String,                  // 密码
    val path: String = "/",                // 初始路径
    val useSsl: Boolean = false,           // 是否使用SSL (FTPS)
    val timeout: Int = 30000,              // 超时时间(ms)
    val activeMode: Boolean = false        // 是否使用主动模式 (FTP)
) {
    val isConnected: Boolean get() = connectionState == ConnectionState.CONNECTED

    var connectionState: ConnectionState = ConnectionState.DISCONNECTED
    var errorMessage: String? = null
}

/**
 * 协议类型枚举
 */
enum class ProtocolType {
    FTP, FTPS, SFTP, SMB, WEBDAV
}

/**
 * 连接状态枚举
 */
enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR
}
