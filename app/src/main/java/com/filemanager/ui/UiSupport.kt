package com.filemanager.ui

import android.content.Context
import android.view.View
import androidx.core.view.isVisible
import com.filemanager.R
import com.filemanager.manager.ErrorMapper
import com.filemanager.manager.RemoteException
import com.google.android.material.snackbar.Snackbar

/** 把异常翻译成用户能理解的文案，并统一 Snackbar 的呈现方式。 */
object ErrorTexts {

    fun message(context: Context, throwable: Throwable): String {
        val remote = ErrorMapper.toRemote(throwable)
        return when (remote.reason) {
            RemoteException.Reason.AUTH_FAILED -> context.getString(R.string.error_auth_failed)
            RemoteException.Reason.HOST_UNREACHABLE -> context.getString(R.string.error_host_unreachable)
            RemoteException.Reason.TIMEOUT -> context.getString(R.string.error_timeout)
            RemoteException.Reason.NOT_FOUND -> context.getString(R.string.error_not_found)
            RemoteException.Reason.PERMISSION_DENIED -> context.getString(R.string.error_permission_denied)
            RemoteException.Reason.ALREADY_EXISTS -> context.getString(R.string.error_already_exists)
            RemoteException.Reason.UNSUPPORTED -> context.getString(R.string.error_unsupported)
            RemoteException.Reason.NETWORK_UNAVAILABLE -> context.getString(R.string.error_network_unavailable)
            RemoteException.Reason.INVALID_INPUT -> context.getString(R.string.error_invalid_input)
            RemoteException.Reason.NOT_CONNECTED -> context.getString(R.string.msg_pick_connection_first)
            RemoteException.Reason.PROTOCOL -> context.getString(
                R.string.error_protocol,
                remote.detail ?: remote.reason.name
            )
            RemoteException.Reason.UNKNOWN -> context.getString(
                R.string.error_unknown,
                remote.detail ?: remote.cause?.javaClass?.simpleName ?: "-"
            )
        }
    }

}

/** 在根视图上显示 Snackbar；可选带一个操作按钮 */
fun View.showSnack(message: CharSequence, actionText: CharSequence? = null, action: (() -> Unit)? = null) {
    val snackbar = Snackbar.make(this, message, Snackbar.LENGTH_LONG)
    if (actionText != null && action != null) {
        snackbar.setAction(actionText) { action() }
        snackbar.setActionTextColor(
            com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimaryInverse)
        )
    }
    snackbar.show()
}

/** 避免在 XML 里散落 visibility 判断，语义化包装 */
fun View.showIf(condition: Boolean) {
    isVisible = condition
}
