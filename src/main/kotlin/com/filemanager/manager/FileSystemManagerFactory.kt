package com.filemanager.manager

import com.filemanager.model.ProtocolType

/**
 * 文件系统管理器工厂
 * 根据协议类型创建对应的管理器实例
 */
object FileSystemManagerFactory {
    
    /**
     * 根据协议类型获取对应的管理器类
     */
    fun getManager(protocolType: ProtocolType): FileSystemManager {
        return when (protocolType) {
            ProtocolType.FTP -> FtpFileSystemManager()
            ProtocolType.FTPS -> FtpFileSystemManager()  // FTPS 也使用 FTP 管理器
            ProtocolType.SFTP -> SftpFileSystemManager()
            ProtocolType.SMB -> SmbFileSystemManager()
            ProtocolType.WEBDAV -> WebDavFileSystemManager()
        }
    }
    
    /**
     * 获取协议显示名称
     */
    fun getProtocolDisplayName(protocolType: ProtocolType): String {
        return when (protocolType) {
            ProtocolType.FTP -> "FTP"
            ProtocolType.FTPS -> "FTPS (SSL)"
            ProtocolType.SFTP -> "SFTP"
            ProtocolType.SMB -> "SMB/CIFS"
            ProtocolType.WEBDAV -> "WebDAV"
        }
    }
    
    /**
     * 获取协议默认端口
     */
    fun getDefaultPort(protocolType: ProtocolType): Int {
        return when (protocolType) {
            ProtocolType.FTP -> 21
            ProtocolType.FTPS -> 990
            ProtocolType.SFTP -> 22
            ProtocolType.SMB -> 445
            ProtocolType.WEBDAV -> 80
        }
    }
    
    /**
     * 获取所有支持的协议类型
     */
    fun getAllProtocols(): List<ProtocolType> {
        return listOf(
            ProtocolType.FTP,
            ProtocolType.FTPS,
            ProtocolType.SFTP,
            ProtocolType.SMB,
            ProtocolType.WEBDAV
        )
    }
}
