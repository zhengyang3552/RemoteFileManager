package com.filemanager.manager

import com.filemanager.model.FileInfo
import com.filemanager.model.RemoteConnection
import org.apache.commons.net.ftp.*
import java.io.FileOutputStream
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * FTP/FTPS 文件系统管理器
 * 支持主动/被动模式
 */
class FtpFileSystemManager : FileSystemManager {
    private var ftpClient: FTPClient? = null
    private var connection: RemoteConnection? = null
    private var currentDir: String = "/"

    override fun connect(conn: RemoteConnection): Boolean {
        try {
            ftpClient = FTPClient()
            
            // 配置连接参数
            val ftp = ftpClient ?: return false
            ftp.connect(conn.host, conn.port)
            ftp.setConnectTimeout(conn.timeout)

            when (conn.type) {
                com.filemanager.model.ProtocolType.FTP -> {
                    ftp.setFileType(FTP.BINARY_FILE_TYPE)
                    // 配置数据连接模式
                    if (conn.activeMode) {
                        ftp.enterLocalActiveMode()
                    } else {
                        ftp.enterLocalPassiveMode()
                    }
                }
                com.filemanager.model.ProtocolType.FTPS -> {
                    ftp.setFileType(FTP.BINARY_FILE_TYPE)
                    ftp.enterLocalPassiveMode()
                }
                else -> {
                    throw IllegalArgumentException("不支持的协议类型: ${conn.type}")
                }
            }

            // 登录
            val isLoggedIn = ftp.login(conn.username, conn.password)
            if (!isLoggedIn) {
                ftp.disconnect()
                return false
            }

            // 设置控制编码
            ftp.controlEncoding = "UTF-8"

            // 如果登录成功，初始化FTP对象
            when (conn.type) {
                com.filemanager.model.ProtocolType.FTPS -> {
                    // FTPS 需要特殊的配置
                    // 这里使用标准方式连接，实际项目中可能需要使用SSLSocketFactory
                }
                else -> {}
            }

            // 切换到初始目录
            if (conn.path != "/") {
                changeDirectory(conn.path)
            }

            this.connection = conn
            conn.connectionState = com.filemanager.model.ConnectionState.CONNECTED
            conn.errorMessage = null
            return true

        } catch (e: Exception) {
            connection?.connectionState = com.filemanager.model.ConnectionState.ERROR
            connection?.errorMessage = e.message
            disconnect()
            return false
        }
    }

    override fun disconnect() {
        try {
            ftpClient?.logout()
            ftpClient?.disconnect()
        } catch (e: Exception) {
            // ignore
        }
        ftpClient = null
        connection?.connectionState = com.filemanager.model.ConnectionState.DISCONNECTED
    }

    override fun isConnected(): Boolean {
        return ftpClient?.isConnected() == true
    }

    override fun listFiles(path: String): List<FileInfo> {
        val ftp = ftpClient ?: return emptyList()
        
        try {
            // 切换到目标目录
            if (path != currentDir) {
                if (!ftp.changeWorkingDirectory(normalizePath(path))) {
                    return emptyList()
                }
                currentDir = path
            }

            val files = mutableListOf<FileInfo>()
            
            // 使用 MLSD 命令获取标准化的文件列表
            val entries = ftp.mlistFiles(".")
            
            // 如果 MLSD 不可用，使用传统方法
            if (entries.isNullOrEmpty()) {
                val list = ftp.listFiles(".")
                for (entry in list) {
                    val isDir = entry.isDirectory
                    val name = entry.name
                    val size = entry.size
                    val time = entry.timestamp?.time?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime() }
                    
                    files.add(FileInfo(
                        name = name,
                        isDirectory = isDir,
                        size = size,
                        lastModified = time
                    ))
                }
            } else {
                for (entryStr in entries) {
                    val parts = entryStr.split("\t")
                    if (parts.size >= 2) {
                        val isDir = parts[0].uppercase().contains("DIR")
                        val name = parts[1]
                        val size = if (!isDir) {
                            parts.getOrNull(2)?.toLongOrNull() ?: 0L
                        } else 0L
                        val time = if (parts.size >= 4) {
                            parseMlsdTime(parts[2], parts[3])
                        } else null
                        files.add(FileInfo(
                            name = name,
                            isDirectory = isDir,
                            size = size,
                            lastModified = time
                        ))
                    }
                }
            }

            return files.sortedBy { it.name }

        } catch (e: Exception) {
            println("列出文件失败: ${e.message}")
            return emptyList()
        }
    }

    override fun getCurrentDirectory(): String = currentDir

    override fun changeDirectory(path: String): Boolean {
        val ftp = ftpClient ?: return false
        try {
            val normalizedPath = normalizePath(path)
            if (ftp.changeWorkingDirectory(normalizedPath)) {
                currentDir = path
                return true
            }
        } catch (e: Exception) {
            println("切换目录失败: ${e.message}")
        }
        return false
    }

    override fun createDirectory(path: String): Boolean {
        val ftp = ftpClient ?: return false
        try {
            val normalizedPath = normalizePath(path)
            return ftp.makeDirectory(normalizedPath)
        } catch (e: Exception) {
            println("创建目录失败: ${e.message}")
            return false
        }
    }

    override fun delete(path: String): Boolean {
        val ftp = ftpClient ?: return false
        try {
            val normalizedPath = normalizePath(path)
            return ftp.deleteFile(normalizedPath) || ftp.removeDirectory(normalizedPath)
        } catch (e: Exception) {
            println("删除失败: ${e.message}")
            return false
        }
    }

    override fun rename(oldPath: String, newName: String): Boolean {
        val ftp = ftpClient ?: return false
        try {
            val oldNormalized = normalizePath(oldPath)
            val parentDir = oldNormalized.substringBeforeLast("/")
            val newFullPath = "$parentDir/$newName"
            return ftp.rename(oldNormalized, newFullPath)
        } catch (e: Exception) {
            println("重命名失败: ${e.message}")
            return false
        }
    }

    override fun downloadFile(remotePath: String, localPath: String): Boolean {
        val ftp = ftpClient ?: return false
        try {
            val normalizedPath = normalizePath(remotePath)
            Files.createDirectories(Path.of(localPath).parent)
            val outputStream = FileOutputStream(localPath)
            val success = ftp.retrieveFile(normalizedPath, outputStream)
            outputStream.close()
            return success
        } catch (e: Exception) {
            println("下载文件失败: ${e.message}")
            return false
        }
    }

    override fun uploadFile(localPath: String, remotePath: String): Boolean {
        val ftp = ftpClient ?: return false
        try {
            val normalizedPath = normalizePath(remotePath)
            val inputStream = java.io.FileInputStream(localPath)
            val success = ftp.storeFile(normalizedPath, inputStream)
            inputStream.close()
            return success
        } catch (e: Exception) {
            println("上传文件失败: ${e.message}")
            return false
        }
    }

    override fun copyRemote(sourcePath: String, destinationPath: String): Boolean {
        println("FTP不支持服务器端复制，请先下载再上传")
        return false
    }

    override fun moveRemote(sourcePath: String, destinationPath: String): Boolean {
        val ftp = ftpClient ?: return false
        try {
            val oldNormalized = normalizePath(sourcePath)
            val parentDir = destinationPath.substringBeforeLast("/")
            val newFullPath = "$parentDir/${sourcePath.substringAfterLast("/")}"
            return ftp.rename(oldNormalized, newFullPath)
        } catch (e: Exception) {
            println("移动文件失败: ${e.message}")
            return false
        }
    }

    override fun getFileSize(remotePath: String): Long {
        val ftp = ftpClient ?: return -1L
        try {
            val normalizedPath = normalizePath(remotePath)
            val size = ftp.getSize(normalizedPath)
            return if (size > 0) size else -1L
        } catch (e: Exception) {
            return -1L
        }
    }

    override fun close() {
        disconnect()
    }

    private fun normalizePath(path: String): String {
        return path.replace("\\", "/")
            .trim('/')
            .ifEmpty { "." }
    }

    private fun parseMlsdTime(dateStr: String, timeStr: String): java.time.LocalDateTime? {
        return try {
            val datePart = dateStr.replace("-", "")
            val timePart = timeStr.replace(":", "")
            val dt = "${datePart}${timePart}"
            java.time.LocalDateTime.parse(dt, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
        } catch (e: Exception) {
            null
        }
    }
}
