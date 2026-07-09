package com.filemanager.manager

import com.filemanager.model.FileInfo
import com.filemanager.model.RemoteConnection
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.IOUtils
import net.schmizz.sshj.sftp.RemoteDirectory
import net.schmizz.sshj.sftp.RemoteResourceType
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import java.io.FileOutputStream
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneId

/**
 * SFTP 文件系统管理器
 * 基于 SSHJ 库实现
 */
class SftpFileSystemManager : FileSystemManager {
    private var sshClient: SSHClient? = null
    private var sftpClient: SFTPClient? = null
    private var connection: RemoteConnection? = null
    private var currentDir: String = "/"

    override fun connect(conn: RemoteConnection): Boolean {
        try {
            sshClient = SSHClient()
            sshClient?.apply {
                // 添加所有主机密钥验证器（生产环境应该使用密钥验证）
                addHostKeyVerifier(PromiscuousVerifier())
                
                // 连接
                connect(conn.host, conn.port)
                
                // 认证
                when {
                    conn.password.isNotEmpty() -> {
                        authenticatePassword(conn.username, conn.password)
                    }
                    else -> {
                        // 尝试公钥认证
                        throw IllegalArgumentException("需要提供密码或配置私钥")
                    }
                }

                // 打开 SFTP 会话
                sftpClient = startSftpClient()
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
            close()
            return false
        }
    }

    override fun disconnect() {
        try {
            sftpClient?.close()
        } catch (e: Exception) {
            // ignore
        }
        try {
            sshClient?.close()
        } catch (e: Exception) {
            // ignore
        }
        sftpClient = null
        sshClient = null
        connection?.connectionState = com.filemanager.model.ConnectionState.DISCONNECTED
    }

    override fun isConnected(): Boolean {
        return sshClient?.isConnected() == true && sftpClient != null
    }

    override fun listFiles(path: String): List<FileInfo> {
        val sftp = sftpClient ?: return emptyList()
        
        try {
            val normalizedPath = normalizePath(path)
            
            // 切换到目标目录
            if (path != currentDir) {
                sftp.use { client ->
                    client.lcd(normalizedPath)
                    currentDir = path
                }
            }

            val files = mutableListOf<FileInfo>()
            
            sftp.use { client ->
                val resources: RemoteDirectory = client.readDir(normalizedPath)
                for (resource in resources) {
                    if (resource.name == "." || resource.name == "..") continue
                    
                    val isDir = resource.type == RemoteResourceType.DIRECTORY
                    val size = if (!isDir) resource.attributes.size else 0L
                    val lastModified = resource.attributes.mtime?.let { 
                        Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()).toLocalDateTime() 
                    }
                    
                    files.add(FileInfo(
                        name = resource.name,
                        isDirectory = isDir,
                        size = size,
                        lastModified = lastModified,
                        permissions = resource.attributes.permissions
                    ))
                }
            }

            return files.sortedBy { it.name }

        } catch (e: Exception) {
            println("列出SFTP文件失败: ${e.message}")
            return emptyList()
        }
    }

    override fun getCurrentDirectory(): String = currentDir

    override fun changeDirectory(path: String): Boolean {
        val sftp = sftpClient ?: return false
        val normalizedPath = normalizePath(path)
        return try {
            sftp.use { it.cd(normalizedPath) }
            currentDir = path
            true
        } catch (e: Exception) {
            println("切换SFTP目录失败: ${e.message}")
            false
        }
    }

    override fun createDirectory(path: String): Boolean {
        val sftp = sftpClient ?: return false
        return try {
            sftp.use { it.mkdir(normalizePath(path)) }
            true
        } catch (e: Exception) {
            println("创建SFTP目录失败: ${e.message}")
            false
        }
    }

    override fun delete(path: String): Boolean {
        val sftp = sftpClient ?: return false
        val normalizedPath = normalizePath(path)
        return try {
            sftp.use { client ->
                val attrs = client.lstat(normalizedPath)
                if (attrs.isDir) {
                    // 递归删除目录
                    deleteDirectoryRecursively(client, normalizedPath)
                } else {
                    client.rm(normalizedPath)
                }
            }
            true
        } catch (e: Exception) {
            println("删除SFTP文件失败: ${e.message}")
            false
        }
    }

    private fun deleteDirectoryRecursively(client: SFTPClient, path: String) {
        client.use {
            val resources = client.readDir(path)
            for (resource in resources) {
                if (resource.name == "." || resource.name == "..") continue
                val fullPath = "$path/${resource.name}"
                if (resource.type == RemoteResourceType.DIRECTORY) {
                    deleteDirectoryRecursively(client, fullPath)
                } else {
                    client.rm(fullPath)
                }
            }
            client.rmdir(path)
        }
    }

    override fun rename(oldPath: String, newName: String): Boolean {
        val sftp = sftpClient ?: return false
        return try {
            val normalizedPath = normalizePath(oldPath)
            val parentDir = normalizedPath.substringBeforeLast("/")
            val newFullPath = "$parentDir/$newName"
            sftp.use { it.rename(normalizedPath, newFullPath) }
            true
        } catch (e: Exception) {
            println("重命名SFTP文件失败: ${e.message}")
            false
        }
    }

    override fun downloadFile(remotePath: String, localPath: String): Boolean {
        val sftp = sftpClient ?: return false
        try {
            Files.createDirectories(java.nio.file.Path.of(localPath).parent)
            sftp.use { client ->
                client.fastFetch(remotePath).to(localPath)
            }
            return true
        } catch (e: Exception) {
            println("下载SFTP文件失败: ${e.message}")
            return false
        }
    }

    override fun uploadFile(localPath: String, remotePath: String): Boolean {
        val sftp = sftpClient ?: return false
        try {
            val normalizedPath = normalizePath(remotePath)
            sftp.use { client ->
                client.fastPut(localPath, normalizedPath)
            }
            return true
        } catch (e: Exception) {
            println("上传SFTP文件失败: ${e.message}")
            return false
        }
    }

    override fun copyRemote(sourcePath: String, destinationPath: String): Boolean {
        val sftp = sftpClient ?: return false
        try {
            sftp.use { client ->
                // 使用SFTP的硬链接方式复制
                client.ln(sourcePath, destinationPath)
            }
            return true
        } catch (e: Exception) {
            println("复制SFTP文件失败: ${e.message}")
            return false
        }
    }

    override fun moveRemote(sourcePath: String, destinationPath: String): Boolean {
        val sftp = sftpClient ?: return false
        return try {
            val normalizedPath = normalizePath(sourcePath)
            sftp.use { it.rename(normalizedPath, normalizePath(destinationPath)) }
            true
        } catch (e: Exception) {
            println("移动SFTP文件失败: ${e.message}")
            false
        }
    }

    override fun getFileSize(remotePath: String): Long {
        val sftp = sftpClient ?: return -1L
        return try {
            sftp.use { client ->
                val attrs = client.lstat(normalizePath(remotePath))
                attrs.size
            }
        } catch (e: Exception) {
            -1L
        }
    }

    override fun close() {
        disconnect()
    }

    private fun normalizePath(path: String): String {
        return path.replace("\\", "/")
            .trim('/')
            .ifEmpty { "/" }
    }
}
