package com.filemanager.manager

import com.filemanager.model.ProtocolType
import com.filemanager.model.RemoteConnection
import com.filemanager.manager.impl.FtpFileSystemManager
import com.filemanager.manager.impl.SftpFileSystemManager
import com.filemanager.manager.impl.SmbFileSystemManager
import com.filemanager.manager.impl.WebDavFileSystemManager

/** 根据协议类型创建对应的 [FileSystemManager] 实例。 */
object FileSystemManagerFactory {

    fun create(connection: RemoteConnection): FileSystemManager = when (connection.type) {
        ProtocolType.FTP -> FtpFileSystemManager(implicitFtps = false)
        // 端口 990 为隐式 FTPS（整条连接先 TLS 握手），其余按显式 AUTH TLS 处理
        ProtocolType.FTPS -> FtpFileSystemManager(implicitFtps = connection.port == 990)
        ProtocolType.SFTP -> SftpFileSystemManager()
        ProtocolType.SMB -> SmbFileSystemManager()
        ProtocolType.WEBDAV -> WebDavFileSystemManager()
    }
}
