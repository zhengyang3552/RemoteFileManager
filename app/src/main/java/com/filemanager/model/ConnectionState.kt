package com.filemanager.model

/** 连接生命周期状态，UI 上的状态点与状态栏文案均由它驱动。 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}
