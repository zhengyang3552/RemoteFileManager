package com.filemanager.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** 网络可用性检查：在发起远程连接前先做一次快速判断，避免用户等待超时。 */
object NetworkState {

    fun isOnline(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
