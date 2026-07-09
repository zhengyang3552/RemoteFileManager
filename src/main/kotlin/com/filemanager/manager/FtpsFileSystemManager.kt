package com.filemanager.manager

import com.filemanager.model.FileInfo
import com.filemanager.model.ProtocolType
import com.filemanager.model.RemoteConnection
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient
import java.io.File
import java.time.Instant
import java.time.ZoneId

/**
 * FTPS (FTP over SSL) 文件系统管理器
 */
class FtpsFileSystemManager : FileSystemManager {

    private var ftpsClient: FTPSClient? = null
    private var currentDir: String = "/"

    override fun connect(connection: RemoteConnection): Boolean {
        return try {
            ftpsClient = FTPSClient(true) // 显式 SSL
            ftpsClient?.connect(connection.host, connection.port)

            val reply = ftpsClient?.replyCode
            if (!FTPReply.isPositiveCompletion(reply)) {
                ftpsClient?.disconnect()
                return false
            }

            val loggedIn = ftpsClient?.login(connection.username, connection.password) ?: false
            if (!loggedIn) {
                ftpsClient?.disconnect()
                return false
            }

            ftpsClient?.enterLocalPassiveMode()
            ftpsClient?.fileType = FTP.BINARY_FILE_TYPE

            if (connection.path != "/") {
                ftpsClient?.changeWorkingDirectory(connection.path)
                currentDir = connection.path
            }

            true
        } catch (e: Exception) {
            println("FTPS 连接失败: ${e.message}")
            false
        }
    }

    override fun disconnect() {
        try {
            ftpsClient?.logout()
        } catch (e: Exception) {
            // ignore
        }
        ftpsClient?.disconnect()
        ftpsClient = null
        currentDir = "/"
    }

    override fun isConnected(): Boolean = ftpsClient?.isConnected() ?: false

    override fun listFiles(path: String): List<FileInfo> {
        return try {
            val changed = changeDirectory(path)
            val files = ftpsClient?.listFiles()?.mapNotNull { ftpFile ->
                toFileInfo(ftpFile, path)
            } ?: emptyList()

            if (changed) {
                changeDirectory(currentDir)
            }

            files
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun getCurrentDirectory(): String = currentDir

    override fun changeDirectory(path: String): Boolean {
        return try {
            ftpsClient?.changeWorkingDirectory(normalizePath(path))?.also { success ->
                if (success) {
                    currentDir = normalizePath(path)
                }
            } ?: false
        } catch (e: Exception) {
            println("切换目录失败: ${e.message}")
            false
        }
    }

    override fun createDirectory(path: String): Boolean {
        return try {
            val normalizedPath = normalizePath(path)
            val parts = normalizedPath.removePrefix("/").split("/")
            var currentPath = "/"

            for (part in parts) {
                if (part.isBlank()) continue
                currentPath = "$currentPath/$part"
                ftpsClient?.makeDirectory(currentPath)
                ftpsClient?.changeWorkingDirectory(currentPath)
            }
            true
        } catch (e: Exception) {
            println("创建目录失败: ${e.message}")
            false
        }
    }

    override fun delete(path: String): Boolean {
        return try {
            val normalizedPath = normalizePath(path)
            ftpsClient?.deleteFile(normalizedPath) || ftpsClient?.removeDirectory(normalizedPath) ?: false
        } catch (e: Exception) {
            println("删除失败: ${e.message}")
            false
        }
    }

    override fun rename(oldPath: String, newName: String): Boolean {
        return try {
            val normalizedOld = normalizePath(oldPath)
            val parentPath = normalizedOld.substringBeforeLast("/") + "/"
            val newFullPath = if (parentPath != "/") "$parentPath$newName" else "/$newName"
            ftpsClient?.rename(normalizedOld, newFullPath) ?: false
        } catch (e: Exception) {
            println("重命名失败: ${e.message}")
            false
        }
    }

    override fun downloadFile(remotePath: String, localPath: String): Boolean {
        return try {
            val normalizedPath = normalizePath(remotePath)
            File(localPath).also { file ->
                file.parentFile?.mkdirs()
            }

            val parentDir = normalizedPath.substringBeforeLast("/")
            val fileName = normalizedPath.substringAfterLast("/")

            ftpsClient?.changeWorkingDirectory(parentDir)
            val success = ftpsClient?.retrieveFile(fileName, File(localPath).outputStream()) ?: false
            ftpsClient?.changeWorkingDirectory(currentDir)
            success
        } catch (e: Exception) {
            println("下载失败: ${e.message}")
            false
        }
    }

    override fun uploadFile(localPath: String, remotePath: String): Boolean {
        return try {
            val localFile = File(localPath)
            if (!localFile.exists()) return false

            val remoteDir = remotePath.substringBeforeLast("/")
            val fileName = remotePath.substringAfterLast("/")

            ftpsClient?.changeWorkingDirectory(remoteDir)
            ftpsClient?.setFileType(FTP.BINARY_FILE_TYPE)
            ftpsClient?.storeFile(fileName, localFile.inputStream()) ?: false
        } catch (e: Exception) {
            println("上传失败: ${e.message}")
            false
        }
    }

    override fun copyRemote(sourcePath: String, destinationPath: String): Boolean {
        println("FTPS 暂不支持远程复制")
        return false
    }

    override fun moveRemote(sourcePath: String, destinationPath: String): Boolean {
        return try {
            val parentPath = destinationPath.substringBeforeLast("/")
            ftpsClient?.changeWorkingDirectory(parentPath)
            ftpsClient?.rename(
                normalizePath(sourcePath),
                destinationPath.substringAfterLast("/")
            ) ?: false
        } catch (e: Exception) {
            println("移动失败: ${e.message}")
            false
        }
    }

    override fun getFileSize(remotePath: String): Long {
        return try {
            val normalizedPath = normalizePath(remotePath)
            val current = getCurrentDirectory()
            changeDirectory(normalizedPath.substringBeforeLast("/"))
            val fileName = normalizedPath.substringAfterLast("/")
            val files = ftpsClient?.listFiles() ?: emptyArray()
            changeDirectory(current)

            files.find { it.name == fileName }?.size ?: -1
        } catch (e: Exception) {
            -1L
        }
    }

    override fun close() {
        disconnect()
    }

    private fun toFileInfo(ftpFile: FTPFile, baseDir: String): FileInfo {
        val dateTime = ftpFile.timestamp?.time?.let { timestamp ->
            Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime()
        }

        return FileInfo(
            name = ftpFile.name,
            isDirectory = ftpFile.isDirectory,
            size = if (ftpFile.isFile) ftpFile.size else 0L,
            lastModified = dateTime,
            permissions = ftpFile.permissions.toString(),
            owner = ftpFile.user
        )
    }

    private fun normalizePath(path: String): String {
        return if (path.startsWith("/")) {
            path
        } else {
            val current = currentDir
            if (current == "/") "/$path"
            else "$current/$path"
        }.also { result ->
            if (result.length > 1 && result.endsWith("/")) {
                result.substring(0, result.length - 1)
            } else {
                result
            }
        }
    }
}
