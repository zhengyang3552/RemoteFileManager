package com.filemanager.manager

import java.io.FileNotFoundException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * 把各协议底层抛出的五花八门的异常，归一化成 [RemoteException.Reason]。
 *
 * 由于 FTP / SFTP / SMB / WebDAV 四类库的异常体系互不兼容，这里采用
 * 「类名 + 消息特征」的判断策略，并对中英文常见错误消息做兜底匹配。
 */
object ErrorMapper {

    fun toRemote(e: Throwable, fallback: RemoteException.Reason = RemoteException.Reason.UNKNOWN): RemoteException {
        if (e is RemoteException) return e
        return RemoteException(classify(e, fallback), e.message, e)
    }

    fun classify(e: Throwable, fallback: RemoteException.Reason = RemoteException.Reason.UNKNOWN): RemoteException.Reason {
        when (e) {
            is RemoteException -> return e.reason
            is UnknownHostException, is NoRouteToHostException -> return RemoteException.Reason.HOST_UNREACHABLE
            is SocketTimeoutException, is InterruptedIOException -> return RemoteException.Reason.TIMEOUT
            is ConnectException, is PortUnreachableException -> return RemoteException.Reason.HOST_UNREACHABLE
            is SSLException -> return RemoteException.Reason.PROTOCOL
            is SocketException -> {
                val message = e.message.orEmpty().lowercase()
                return when {
                    message.contains("timeout") || message.contains("timed out") -> RemoteException.Reason.TIMEOUT
                    message.contains("reset") || message.contains("broken pipe") -> RemoteException.Reason.PROTOCOL
                    else -> RemoteException.Reason.HOST_UNREACHABLE
                }
            }
            is FileNotFoundException -> return RemoteException.Reason.NOT_FOUND
        }

        val type = e.javaClass.simpleName.lowercase()
        val message = e.message.orEmpty()
        val lower = message.lowercase()

        // 认证类：SmbAuthException / AuthenticationException / AuthFailException …
        if (type.contains("auth") || lower.contains("authentication fail") || lower.contains("login incorrect") ||
            lower.contains("530 ") || lower.contains("invalid credentials") || lower.contains("access denied")
        ) {
            return if (lower.contains("access denied") || lower.contains("550")) {
                RemoteException.Reason.PERMISSION_DENIED
            } else {
                RemoteException.Reason.AUTH_FAILED
            }
        }
        if (lower.contains("permission denied") || lower.contains("550 permission") ||
            lower.contains("operation not permitted") || lower.contains("403 forbidden")
        ) {
            return RemoteException.Reason.PERMISSION_DENIED
        }
        if (lower.contains("no such file") || lower.contains("not found") || lower.contains("550 ") ||
            lower.contains("404") || lower.contains("does not exist")
        ) {
            return RemoteException.Reason.NOT_FOUND
        }
        if (lower.contains("already exists") || lower.contains("file exists") || lower.contains("409")) {
            return RemoteException.Reason.ALREADY_EXISTS
        }
        if (lower.contains("not supported") || lower.contains("unsupported") || lower.contains("501") ||
            lower.contains("405 ") || e is UnsupportedOperationException
        ) {
            return RemoteException.Reason.UNSUPPORTED
        }
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return RemoteException.Reason.TIMEOUT
        }
        if (lower.contains("failed to connect") || lower.contains("connection refused") ||
            lower.contains("no route to host") || lower.contains("unreachable")
        ) {
            return RemoteException.Reason.HOST_UNREACHABLE
        }
        if (e is IOException) return RemoteException.Reason.PROTOCOL
        return fallback
    }
}
