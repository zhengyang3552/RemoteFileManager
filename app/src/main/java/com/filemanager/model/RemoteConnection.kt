package com.filemanager.model

import java.util.UUID

/**
 * 一条远程连接配置。
 *
 * - [id] 稳定的标识，用于持久化与「当前选中连接」的恢复；
 * - 连接运行时状态（[connectionState]/[lastError]）不参与相等性比较，
 *   也不落盘，避免数据类 copy 时把瞬时状态带进持久化逻辑。
 */
data class RemoteConnection(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: ProtocolType,
    val host: String,
    val port: Int = type.defaultPort,
    val username: String = "",
    val password: String = "",
    val path: String = "/",
    val useSsl: Boolean = type == ProtocolType.FTPS,
    val passiveMode: Boolean = true,
    val timeoutMs: Int = DEFAULT_TIMEOUT_MS
) {
    val endpoint: String get() = "$host:$port"

    /** 运行态：连接状态 */
    var connectionState: ConnectionState = ConnectionState.DISCONNECTED

    /** 运行态：最近的错误描述（已本地化），无错误时为 null */
    var lastError: String? = null

    val isConnected: Boolean get() = connectionState == ConnectionState.CONNECTED

    fun withState(state: ConnectionState, error: String? = null): RemoteConnection = apply {
        connectionState = state
        lastError = error
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 30_000
        const val MIN_TIMEOUT_MS = 5_000
        const val MAX_TIMEOUT_MS = 120_000
    }
}
