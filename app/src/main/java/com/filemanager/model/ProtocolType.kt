package com.filemanager.model

import com.filemanager.R

/**
 * 支持的远程协议。
 *
 * 每个协议自带默认端口、显示名称与能力标记，避免在过去那种
 * `when (protocol) { "FTP" -> 21 ... }` 的字符串分支里重复维护映射关系。
 */
enum class ProtocolType(
    val labelRes: Int,
    val defaultPort: Int,
    /** 是否支持在服务器侧直接复制（无需本地中转） */
    val supportsServerSideCopy: Boolean,
    /** 是否支持配置 SSL/TLS 开关 */
    val supportsSslToggle: Boolean,
    /** 是否支持配置主动/被动模式 */
    val supportsPassiveMode: Boolean
) {
    FTP(R.string.protocol_ftp, 21, false, false, true),
    FTPS(R.string.protocol_ftps, 990, false, true, true),
    SFTP(R.string.protocol_sftp, 22, false, false, false),
    SMB(R.string.protocol_smb, 445, false, false, false),
    WEBDAV(R.string.protocol_webdav, 80, true, true, false);

    companion object {
        fun fromNameOrDefault(name: String?): ProtocolType =
            values().firstOrNull { it.name.equals(name, ignoreCase = true) } ?: FTP
    }
}
