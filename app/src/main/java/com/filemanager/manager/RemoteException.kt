package com.filemanager.manager

import java.io.IOException

/**
 * 统一对外抛出的远程操作异常。
 *
 * 协议实现层只负责把底层异常归一化成 [Reason]，用户可读的文案与重试策略
 * 交给 UI 层决定，这样同一个错误在不同入口（吐司 / 空状态 / 对话框）都能表达。
 */
class RemoteException(
    val reason: Reason,
    /** 技术细节，用于日志与「协议通信失败：xxx」这类兜底文案 */
    val detail: String? = null,
    cause: Throwable? = null
) : IOException(detail ?: reason.name, cause) {

    enum class Reason {
        AUTH_FAILED,
        HOST_UNREACHABLE,
        TIMEOUT,
        NOT_FOUND,
        PERMISSION_DENIED,
        ALREADY_EXISTS,
        UNSUPPORTED,
        NETWORK_UNAVAILABLE,
        INVALID_INPUT,
        NOT_CONNECTED,
        PROTOCOL,
        UNKNOWN
    }

    companion object {
        fun of(reason: Reason, detail: String? = null): RemoteException = RemoteException(reason, detail)
    }
}
