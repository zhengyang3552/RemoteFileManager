package com.filemanager.manager

import com.filemanager.model.FileInfo
import com.filemanager.model.RemoteConnection
import jcifs.smb.SmbFile
import jcifs.smb.SmbFileInputStream
import jcifs.smb.SmbFileOutputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId

/**
 * SMB/CIFS 文件系统管理器
 * 基于 JCIFS NG 库实现
 */
class SmbFileSystemManager : FileSystemManager {
    private var smbRoot: SmbFile? = null
    private var connection: RemoteConnection? = null
    private var currentDir: String = "/"

    override fun connect(conn: RemoteConnection): Boolean {
        try {
            // 构建 SMB 连接字符串
            val host = conn.host
            val port = if (conn.port == 0) 445 else conn.port
            val sharePath = conn.path.ifBlank { "/" }
            
            val smbUrl = if (conn.port == 0) {
                "smb://${conn.username}:${conn.password.replace(":", "%3A")}@$host$sharePath"
            } else {
                "smb://${conn.username}:${conn.password.replace(":", "%3A")}@$host:$port$sharePath"
            }
            
            smbRoot = SmbFile(smbUrl)
            
            // 测试连接
            smbRoot?.exists()
            
            this.connection = conn
            conn.connectionState = com.filemanager.model.ConnectionState.CONNECTED
            conn.errorMessage = null
            return true
            
        } catch (e: Exception) {
            connection?.connectionState = com.filemanager.model.ConnectionState.ERROR
            connection?.errorMessage = e.message
            smbRoot = null
            return false
        }
    }

    override fun disconnect() {
        smbRoot = null
        connection?.connectionState = com.filemanager.model.ConnectionState.DISCONNECTED
    }

    override fun isConnected(): Boolean {
        return smbRoot != null
    }

    override fun listFiles(path: String): List<FileInfo> {
        val smbRoot = smbRoot ?: return emptyList()
        
        try {
            val targetPath = normalizePath(path, smbRoot)
            val smbFile = SmbFile(targetPath)
            
            if (!smbFile.exists()) {
                return emptyList()
            }
            
            val children = smbFile.listFiles()
            val files = mutableListOf<FileInfo>()
            
            for (child in children) {
                val isDir = child.isDirectory
                val name = child.name
                val size = if (!isDir) child.length else 0L
                val lastModified = child.lastModifiedTime?.let { 
                    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime() 
                }
                val permissions = if (child.isReadOnly) "-r--" else "-rwx"
                
                files.add(FileInfo(
                    name = name,
                    isDirectory = isDir,
                    size = size,
                    lastModified = lastModified,
                    permissions = permissions
                ))
            }
            
            return files.sortedBy { it.name }
            
        } catch (e: Exception) {
            println("列出SMB文件失败: ${e.message}")
            return emptyList()
        }
    }

    override fun getCurrentDirectory(): String = currentDir

    override fun changeDirectory(path: String): Boolean {
        val smbRoot = smbRoot ?: return false
        try {
            val targetPath = normalizePath(path, smbRoot)
            val smbFile = SmbFile(targetPath)
            
            if (smbFile.exists() && smbFile.isDirectory) {
                currentDir = path
                return true
            }
        } catch (e: Exception) {
            println("切换SMB目录失败: ${e.message}")
        }
        return false
    }

    override fun createDirectory(path: String): Boolean {
        val smbRoot = smbRoot ?: return false
        return try {
            val targetPath = normalizePath(path, smbRoot)
            SmbFile(targetPath).mkdirs()
            true
        } catch (e: Exception) {
            println("创建SMB目录失败: ${e.message}")
            false
        }
    }

    override fun delete(path: String): Boolean {
        val smbRoot = smbRoot ?: return false
        return try {
            val targetPath = normalizePath(path, smbRoot)
            val smbFile = SmbFile(targetPath)
            
            if (smbFile.isDirectory) {
                // 递归删除目录
                smbFile.listFiles()?.forEach { child ->
                    child.delete()
                }
            }
            
            smbFile.delete()
        } catch (e: Exception) {
            println("删除SMB文件失败: ${e.message}")
            false
        }
    }

    override fun rename(oldPath: String, newName: String): Boolean {
        val smbRoot = smbRoot ?: return false
        return try {
            val oldTargetPath = normalizePath(oldPath, smbRoot)
            val oldFile = SmbFile(oldTargetPath)
            val parentDir = oldTargetPath.substringBeforeLast("/")
            val newTargetPath = "$parentDir/$newName"
            oldFile.renameTo(SmbFile(newTargetPath))
        } catch (e: Exception) {
            println("重命名SMB文件失败: ${e.message}")
            false
        }
    }

    override fun downloadFile(remotePath: String, localPath: String): Boolean {
        val smbRoot = smbRoot ?: return false
        return try {
            val targetPath = normalizePath(remotePath, smbRoot)
            Files.createDirectories(Path.of(localPath).parent)
            
            val inputStream = SmbFileInputStream(SmbFile(targetPath))
            val outputStream = FileOutputStream(localPath)
            
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            
            inputStream.close()
            outputStream.close()
            true
            
        } catch (e: Exception) {
            println("下载SMB文件失败: ${e.message}")
            false
        }
    }

    override fun uploadFile(localPath: String, remotePath: String): Boolean {
        val smbRoot = smbRoot ?: return false
        return try {
            val targetPath = normalizePath(remotePath, smbRoot)
            
            // 确保目标目录存在
            val parentDir = targetPath.substringBeforeLast("/")
            try {
                SmbFile(parentDir).mkdirs()
            } catch (e: Exception) {
                // 目录可能已存在
            }
            
            val inputStream = java.io.FileInputStream(localPath)
            val outputStream = SmbFileOutputStream(SmbFile(targetPath))
            
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            
            inputStream.close()
            outputStream.close()
            true
            
        } catch (e: Exception) {
            println("上传SMB文件失败: ${e.message}")
            false
        }
    }

    override fun copyRemote(sourcePath: String, destinationPath: String): Boolean {
        val smbRoot = smbRoot ?: return false
        return try {
            val source = SmbFile(normalizePath(sourcePath, smbRoot))
            val dest = SmbFile(normalizePath(destinationPath, smbRoot))
            source.copyTo(dest, 0)
        } catch (e: Exception) {
            println("复制SMB文件失败: ${e.message}")
            false
        }
    }

    override fun moveRemote(sourcePath: String, destinationPath: String): Boolean {
        val smbRoot = smbRoot ?: return false
        return try {
            val source = SmbFile(normalizePath(sourcePath, smbRoot))
            val dest = SmbFile(normalizePath(destinationPath, smbRoot))
            source.renameTo(dest)
        } catch (e: Exception) {
            println("移动SMB文件失败: ${e.message}")
            false
        }
    }

    override fun getFileSize(remotePath: String): Long {
        val smbRoot = smbRoot ?: return -1L
        return try {
            val targetPath = normalizePath(remotePath, smbRoot)
            SmbFile(targetPath).length()
        } catch (e: Exception) {
            -1L
        }
    }

    override fun close() {
        disconnect()
    }

    private fun normalizePath(path: String, smbRoot: SmbFile): String {
        val rootUrl = smbRoot.url.toString()
        
        return when {
            path.startsWith("/") -> {
                // 绝对路径
                val share = rootUrl.substringAfter("smb://").substringBefore("/")
                "$rootUrl/$path"
            }
            path.startsWith("..") -> {
                // 父目录
                currentDir.substringBeforeLast("/")
            }
            else -> {
                // 相对路径
                val currentPath = when {
                    currentDir == "/" -> ""
                    else -> currentDir
                }
                if (currentPath.isBlank()) path else "$currentPath/$path"
            }
        }
    }
}
