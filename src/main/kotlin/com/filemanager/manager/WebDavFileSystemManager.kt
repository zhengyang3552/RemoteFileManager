package com.filemanager.manager

import com.filemanager.model.FileInfo
import com.filemanager.model.RemoteConnection
import java.io.*
import java.net.*
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneId
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.NodeList

/**
 * WebDAV 文件系统管理器
 * 基于 HttpURLConnection 实现，支持标准 WebDAV 操作
 */
class WebDavFileSystemManager : FileSystemManager {
    private var baseUri: String? = null
    private var authHeader: String? = null
    private var connection: RemoteConnection? = null
    private var currentDir: String = "/"

    override fun connect(conn: RemoteConnection): Boolean {
        try {
            val scheme = if (conn.useSsl) "https" else "http"
            val port = if (conn.port == 0) {
                if (conn.useSsl) 443 else 80
            } else conn.port
            
            val baseUrl = if (port == 80 || port == 443) {
                "$scheme://$conn.host"
            } else {
                "$scheme://$conn.host:$port"
            }
            
            val path = conn.path.trim('/')
            baseUri = if (path.isNotEmpty()) "$baseUrl/$path" else baseUrl
            
            // 生成 Basic Auth header
            val credentials = "${conn.username}:${conn.password}"
            authHeader = "Basic " + Base64.getEncoder().encodeToString(credentials.toByteArray(Charsets.UTF_8))
            
            // 测试连接
            val url = URL("$baseUri/")
            val conn2 = url.openConnection() as HttpURLConnection
            conn2.requestMethod = "PROPFIND"
            conn2.setRequestProperty("Authorization", authHeader!!)
            conn2.setRequestProperty("Depth", "0")
            conn2.setRequestProperty("Content-Type", "text/xml; charset=utf-8")
            conn2.doOutput = true
            
            val propXml = """<?xml version="1.0" encoding="utf-8"?><D:propfind xmlns:D="DAV:"><D:prop><D:resourcetype/></D:prop></D:propfind>"""
            conn2.outputStream.write(propXml.toByteArray(Charsets.UTF_8))
            
            val responseCode = conn2.responseCode
            conn2.disconnect()
            
            if (responseCode in listOf(200, 201, 204, 207)) {
                this.connection = conn
                conn.connectionState = com.filemanager.model.ConnectionState.CONNECTED
                conn.errorMessage = null
                return true
            }
            throw RuntimeException("连接失败: HTTP $responseCode")
            
        } catch (e: Exception) {
            connection?.connectionState = com.filemanager.model.ConnectionState.ERROR
            connection?.errorMessage = e.message
            close()
            return false
        }
    }

    override fun disconnect() {
        baseUri = null
        authHeader = null
        connection?.connectionState = com.filemanager.model.ConnectionState.DISCONNECTED
    }

    override fun isConnected(): Boolean = baseUri != null

    override fun listFiles(path: String): List<FileInfo> {
        return try {
            val targetPath = normalizePath(path)
            val url = URL(targetPath)
            val conn = url.openConnection() as HttpURLConnection
            
            conn.requestMethod = "PROPFIND"
            conn.setRequestProperty("Authorization", authHeader!!)
            conn.setRequestProperty("Depth", "1")
            conn.setRequestProperty("Content-Type", "text/xml; charset=utf-8")
            conn.doOutput = true
            
            val xml = """<?xml version="1.0" encoding="utf-8"?>
<D:propfind xmlns:D="DAV:">
  <D:prop>
    <D:resourcetype/>
    <D:getcontentlength/>
    <D:getlastmodified/>
    <D:displayname/>
  </D:prop>
</D:propfind>"""
            conn.outputStream.write(xml.toByteArray(Charsets.UTF_8))
            
            val responseCode = conn.responseCode
            val inputStream = if (responseCode == 200 || responseCode == 207) {
                conn.inputStream
            } else {
                conn.errorStream
            }
            
            val response = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()
            
            if (responseCode != 200 && responseCode != 207) {
                println("WebDAV列表失败: HTTP $responseCode")
                return emptyList()
            }
            
            parsePropFindResponse(response, path)
        } catch (e: Exception) {
            println("列出WebDAV文件异常: ${e.message}")
            emptyList()
        }
    }

    override fun getCurrentDirectory(): String = currentDir

    override fun changeDirectory(path: String): Boolean {
        return try {
            val targetPath = normalizePath(path)
            val url = URL(targetPath)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "PROPFIND"
            conn.setRequestProperty("Authorization", authHeader!!)
            conn.setRequestProperty("Depth", "0")
            conn.setRequestProperty("Content-Type", "text/xml; charset=utf-8")
            conn.doOutput = true
            
            val propXml = """<?xml version="1.0" encoding="utf-8"?><D:propfind xmlns:D="DAV:"><D:prop><D:resourcetype/></D:prop></D:propfind>"""
            conn.outputStream.write(propXml.toByteArray(Charsets.UTF_8))
            
            val exists = conn.responseCode == 200 || conn.responseCode == 207
            conn.disconnect()
            
            if (exists) {
                currentDir = path
                true
            } else {
                false
            }
        } catch (e: Exception) {
            println("切换WebDAV目录失败: ${e.message}")
            false
        }
    }

    override fun createDirectory(path: String): Boolean {
        return try {
            val targetPath = normalizePath(path)
            val url = URL(targetPath)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "MKCOL"
            conn.setRequestProperty("Authorization", authHeader!!)
            val responseCode = conn.responseCode
            conn.disconnect()
            responseCode == 201 || responseCode == 204
        } catch (e: Exception) {
            println("创建WebDAV目录失败: ${e.message}")
            false
        }
    }

    override fun delete(path: String): Boolean {
        return try {
            val targetPath = normalizePath(path)
            val url = URL(targetPath)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "DELETE"
            conn.setRequestProperty("Authorization", authHeader!!)
            val responseCode = conn.responseCode
            conn.disconnect()
            responseCode == 200 || responseCode == 204 || responseCode == 201
        } catch (e: Exception) {
            println("删除WebDAV文件失败: ${e.message}")
            false
        }
    }

    override fun rename(oldPath: String, newName: String): Boolean {
        return try {
            val oldTarget = normalizePath(oldPath)
            val parentPath = oldTarget.substringBeforeLast("/")
            val newTarget = "$parentPath/$newName"
            
            val url = URL(oldTarget)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "MOVE"
            conn.setRequestProperty("Authorization", authHeader!!)
            conn.setRequestProperty("Destination", newTarget)
            conn.setRequestProperty("Overwrite", "T")
            val responseCode = conn.responseCode
            conn.disconnect()
            responseCode == 201 || responseCode == 204 || responseCode == 200
        } catch (e: Exception) {
            println("重命名WebDAV文件失败: ${e.message}")
            false
        }
    }

    override fun downloadFile(remotePath: String, localPath: String): Boolean {
        return try {
            val targetPath = normalizePath(remotePath)
            val url = URL(targetPath)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", authHeader!!)
            
            val responseCode = conn.responseCode
            if (responseCode == 200) {
                File(localPath).parentFile?.mkdirs()
                conn.inputStream.buffered().use { input ->
                    File(localPath).outputStream().buffered().use { output ->
                        input.copyTo(output)
                    }
                }
                conn.disconnect()
                true
            } else {
                conn.disconnect()
                println("下载WebDAV文件失败: HTTP $responseCode")
                false
            }
        } catch (e: Exception) {
            println("下载WebDAV文件异常: ${e.message}")
            false
        }
    }

    override fun uploadFile(localPath: String, remotePath: String): Boolean {
        return try {
            val targetPath = normalizePath(remotePath)
            val url = URL(targetPath)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "PUT"
            conn.setRequestProperty("Authorization", authHeader!!)
            conn.doOutput = true
            
            val file = File(localPath)
            if (!file.exists()) return false
            
            file.inputStream().buffered().use { input ->
                conn.outputStream.buffered().use { output ->
                    input.copyTo(output)
                }
            }
            
            val responseCode = conn.responseCode
            conn.disconnect()
            responseCode == 200 || responseCode == 201 || responseCode == 204
        } catch (e: Exception) {
            println("上传WebDAV文件失败: ${e.message}")
            false
        }
    }

    override fun copyRemote(sourcePath: String, destinationPath: String): Boolean {
        return try {
            val sourceTarget = normalizePath(sourcePath)
            val destTarget = normalizePath(destinationPath)
            
            val url = URL(sourceTarget)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "COPY"
            conn.setRequestProperty("Authorization", authHeader!!)
            conn.setRequestProperty("Destination", destTarget)
            conn.setRequestProperty("Overwrite", "T")
            val responseCode = conn.responseCode
            conn.disconnect()
            responseCode == 201 || responseCode == 204 || responseCode == 200
        } catch (e: Exception) {
            println("复制WebDAV文件失败: ${e.message}")
            false
        }
    }

    override fun moveRemote(sourcePath: String, destinationPath: String): Boolean {
        return try {
            val sourceTarget = normalizePath(sourcePath)
            val destTarget = normalizePath(destinationPath)
            
            val url = URL(sourceTarget)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "MOVE"
            conn.setRequestProperty("Authorization", authHeader!!)
            conn.setRequestProperty("Destination", destTarget)
            conn.setRequestProperty("Overwrite", "T")
            val responseCode = conn.responseCode
            conn.disconnect()
            responseCode == 201 || responseCode == 204 || responseCode == 200
        } catch (e: Exception) {
            println("移动WebDAV文件失败: ${e.message}")
            false
        }
    }

    override fun getFileSize(remotePath: String): Long {
        return try {
            val targetPath = normalizePath(remotePath)
            val url = URL(targetPath)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "HEAD"
            conn.setRequestProperty("Authorization", authHeader!!)
            val responseCode = conn.responseCode
            val contentLength = if (responseCode == 200) {
                conn.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
            } else {
                -1L
            }
            conn.disconnect()
            contentLength
        } catch (e: Exception) {
            -1L
        }
    }

    override fun close() {
        disconnect()
    }

    // ========== Helper Methods ==========

    private fun normalizePath(path: String): String {
        val baseUrl = baseUri ?: return "/"
        val pathStr = if (path == "/") "" else "/$path"
        return "$baseUrl$pathStr"
    }

    private fun parsePropFindResponse(xml: String, basePath: String): List<FileInfo> {
        val files = mutableListOf<FileInfo>()
        
        try {
            val dbFactory = DocumentBuilderFactory.newInstance()
            dbFactory.isNamespaceAware = true
            val dbBuilder = dbFactory.newDocumentBuilder()
            val doc = dbBuilder.parse(InputSource(StringReader(xml)))
            
            val namespaceUri = "DAV:"
            val responses = doc.getElementsByTagNameNS(namespaceUri, "response")
            
            for (i in 0 until responses.length) {
                val response = responses.item(i) as Element
                val href = response.getElementsByTagNameNS(namespaceUri, "href").item(0).textContent
                
                // 从href中提取相对于basePath的路径
                val normalizedHref = href.substringAfterLast("/")
                val isDir = response.getElementsByTagNameNS(namespaceUri, "collection").length > 0
                
                val contentLengthNode = response.getElementsByTagNameNS(namespaceUri, "getcontentlength").item(0)
                val size = contentLengthNode?.textContent?.toLongOrNull() ?: 0L
                val finalSize = if (isDir) 0L else size
                
                val lastModifiedNode = response.getElementsByTagNameNS(namespaceUri, "getlastmodified").item(0)
                val lastModified = lastModifiedNode?.textContent?.let { parseDate(it) }
                
                val displayName = response.getElementsByTagNameNS(namespaceUri, "displayname").item(0)?.textContent ?: normalizedHref
                
                files.add(FileInfo(
                    name = displayName,
                    isDirectory = isDir,
                    size = finalSize,
                    lastModified = lastModified
                ))
            }
        } catch (e: Exception) {
            println("解析WebDAV响应失败: ${e.message}")
            // 如果XML解析失败，使用简单的字符串解析作为备用
            parsePropFindResponseSimple(xml, basePath, files)
        }
        
        return files.sortedBy { it.name }
    }

    private fun parsePropFindResponseSimple(xml: String, basePath: String, files: MutableList<FileInfo>) {
        val lines = xml.lines()
        var currentName = ""
        var isDir = false
        var size = 0L
        
        for (line in lines) {
            if ("<D:href>".toRegex().containsMatchIn(line)) {
                val href = line.substringAfter("<D:href>").substringBefore("</D:href>")
                currentName = href.substringAfterLast("/")
            }
            if ("<D:resourcetype><D:collection/></D:resourcetype>".toRegex().containsMatchIn(line) ||
                "<D:resourcetype/>".toRegex().containsMatchIn(line) && line.contains("collection")) {
                isDir = true
            }
            if ("<D:getcontentlength>".toRegex().containsMatchIn(line)) {
                size = line.substringAfter("<D:getcontentlength>").substringBefore("</D:getcontentlength>").toLongOrNull() ?: 0L
            }
            if (line.contains("</D:response>")) {
                if (currentName.isNotEmpty()) {
                    files.add(FileInfo(
                        name = currentName,
                        isDirectory = isDir,
                        size = if (isDir) 0L else size
                    ))
                }
                currentName = ""
                isDir = false
                size = 0L
            }
        }
    }

    private fun parseDate(dateStr: String): java.time.LocalDateTime? {
        return try {
            val sdf = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US)
            sdf.parse(dateStr)?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDateTime()
        } catch (e: Exception) {
            null
        }
    }
}
