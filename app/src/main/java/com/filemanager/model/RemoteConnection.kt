package com.filemanager.model

data class RemoteConnection(
    val name: String,
    val type: ProtocolType,
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val path: String = "/",
    val useSsl: Boolean = false
) {
    val errorMessage: String = ""
    var connectionState: ConnectionState = ConnectionState.DISCONNECTED
}