package com.filemanager.manager.impl

import android.net.Uri
import android.util.Base64
import com.filemanager.manager.ErrorMapper
import com.filemanager.manager.FileSystemManager
import com.filemanager.manager.RemoteException
import com.filemanager.model.FileInfo
import com.filemanager.model.RemoteConnection
import com.filemanager.util.RemotePath
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * WebDAV 实现（HttpURLConnection + PROPFIND 解析）。
 *
 * 边界处理：
 * - 路径按段做百分号编码，避免中文/空格文件名导致 400/404；
 * - PROPFIND 结果中会包含被查询目录自身，需要按 href 过滤掉；
 * - 认证失败(401)、无权限(403)、不存在(404)、冲突(409)、不支持(405/501) 分别映射；
 * - 服务端复制/移动使用 COPY / MOVE，天然支持目录递归。
 */
class WebDavFileSystemManager : FileSystemManager {

    private var baseUrl: String = ""
    private var authHeader: String = ""
    private var timeoutMs: Int = RemoteConnection.DEFAULT_TIMEOUT_MS
    private var connected: Boolean = false

    override val isConnected: Boolean get() = connected
    override val supportsCopy: Boolean get() = true

    override fun connect(connection: RemoteConnection) {
        disconnect()
        try {
            val scheme = if (connection.useSsl || connection.port == 443) "https" else "http"
            val defaultPort = scheme == "https"
            val portPart = when {
                connection.port <= 0 -> ""
                connection.port == 80 && !defaultPort -> ""
                connection.port == 443 && defaultPort -> ""
                else -> ":${connection.port}"
            }
            baseUrl = "$scheme://${connection.host}$portPart"
            timeoutMs = connection.timeoutMs
            val credentials = "${connection.username}:${connection.password}"
            authHeader = "Basic " + Base64.encodeToString(credentials.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

            // 用 Depth:0 的 PROPFIND 校验凭据与路径可用性
            propfind(RemotePath.normalize(connection.path), depth = 0)
            connected = true
        } catch (e: RemoteException) {
            disconnect()
            throw e
        } catch (e: Exception) {
            disconnect()
            throw ErrorMapper.toRemote(e)
        }
    }

    override fun disconnect() {
        connected = false
        baseUrl = ""
        authHeader = ""
    }

    private fun requireBase(): String {
        if (!connected || baseUrl.isEmpty()) {
            throw RemoteException(RemoteException.Reason.NOT_CONNECTED, "webdav not connected")
        }
        return baseUrl
    }

    private fun buildUrl(path: String, directory: Boolean): String {
        val normalized = RemotePath.normalize(path)
        val encoded = normalized.split('/').joinToString("/") { Uri.encode(it) }
        val trailing = if (directory && !encoded.endsWith("/")) "/" else ""
        return requireBase() + encoded + trailing
    }

    private fun open(method: String, url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.instanceFollowRedirects = true
        conn.useCaches = false
        conn.setRequestProperty("Authorization", authHeader)
        return conn
    }

    private fun ensureSuccess(conn: HttpURLConnection, vararg allowed: Int) {
        val code = conn.responseCode
        if (code in allowed) return
        val message = "HTTP $code ${conn.responseMessage.orEmpty()}".trim()
        val reason = when (code) {
            401 -> RemoteException.Reason.AUTH_FAILED
            403 -> RemoteException.Reason.PERMISSION_DENIED
            404 -> RemoteException.Reason.NOT_FOUND
            409 -> RemoteException.Reason.ALREADY_EXISTS
            405, 501 -> RemoteException.Reason.UNSUPPORTED
            507 -> RemoteException.Reason.PROTOCOL
            else -> RemoteException.Reason.PROTOCOL
        }
        throw RemoteException(reason, message)
    }

    private val propfindBody = """<?xml version="1.0" encoding="utf-8"?>
<D:propfind xmlns:D="DAV:">
  <D:prop>
    <D:resourcetype/>
    <D:getcontentlength/>
    <D:getlastmodified/>
    <D:displayname/>
  </D:prop>
</D:propfind>""".toByteArray(Charsets.UTF_8)

    private fun propfind(path: String, depth: Int): Document {
        val conn = open("PROPFIND", buildUrl(path, directory = true))
        try {
            conn.setRequestProperty("Depth", depth.toString())
            conn.setRequestProperty("Content-Type", "application/xml; charset=utf-8")
            conn.doOutput = true
            conn.setFixedLengthStreamingMode(propfindBody.size)
            conn.outputStream.use { it.write(propfindBody) }
            ensureSuccess(conn, 200, 207)
            val stream = conn.inputStream
            return parseXml(stream)
        } finally {
            conn.disconnect()
        }
    }

    private fun parseXml(stream: InputStream): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // 关闭外部实体，避免 XXE
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            isExpandEntityReferences = false
        }
        return factory.newDocumentBuilder().parse(stream)
    }

    override fun list(path: String): List<FileInfo> {
        val normalized = RemotePath.normalize(path)
        val document = propfind(normalized, depth = 1)
        val responses = document.getElementsByTagNameNS(NS_DAV, "response")
        val selfKey = normalized.trimEnd('/')
        val result = ArrayList<FileInfo>()

        for (i in 0 until responses.length) {
            val response = responses.item(i) as? Element ?: continue
            val href = response.textOf("href") ?: continue
            val hrefPath = hrefToPath(href)
            if (hrefPath.trimEnd('/').equals(selfKey, ignoreCase = true)) continue

            val name = hrefPath.trimEnd('/').substringAfterLast('/')
            if (name.isEmpty()) continue

            val isDirectory = response.getElementsByTagNameNS(NS_DAV, "collection").length > 0
            val size = response.textOf("getcontentlength")?.trim()?.toLongOrNull() ?: 0L
            val modified = response.textOf("getlastmodified")?.let { parseHttpDate(it.trim()) } ?: 0L

            result.add(
                FileInfo(
                    name = Uri.decode(name),
                    isDirectory = isDirectory,
                    size = if (isDirectory) 0L else size,
                    lastModified = modified
                )
            )
        }
        return result
    }

    override fun changeDirectory(path: String) {
        propfind(RemotePath.normalize(path), depth = 0)
    }

    override fun createDirectory(path: String) {
        val conn = open("MKCOL", buildUrl(path, directory = true))
        try {
            ensureSuccess(conn, 200, 201, 204)
        } finally {
            conn.disconnect()
        }
    }

    override fun delete(path: String, recursive: Boolean) {
        val conn = open("DELETE", buildUrl(path, directory = true))
        try {
            ensureSuccess(conn, 200, 202, 204)
        } finally {
            conn.disconnect()
        }
    }

    override fun move(sourcePath: String, targetPath: String) {
        val destination = buildUrl(targetPath, directory = true)
        val conn = open("MOVE", buildUrl(sourcePath, directory = true))
        try {
            conn.setRequestProperty("Destination", destination)
            conn.setRequestProperty("Overwrite", "T")
            ensureSuccess(conn, 200, 201, 204)
        } finally {
            conn.disconnect()
        }
    }

    override fun copy(sourcePath: String, targetPath: String) {
        val destination = buildUrl(targetPath, directory = true)
        val conn = open("COPY", buildUrl(sourcePath, directory = true))
        try {
            conn.setRequestProperty("Destination", destination)
            conn.setRequestProperty("Overwrite", "T")
            conn.setRequestProperty("Depth", "infinity")
            ensureSuccess(conn, 200, 201, 204)
        } finally {
            conn.disconnect()
        }
    }

    override fun download(path: String, target: OutputStream) {
        val conn = open("GET", buildUrl(path, directory = false))
        try {
            ensureSuccess(conn, 200)
            conn.inputStream.use { input -> input.copyTo(target, DEFAULT_BUFFER_SIZE) }
        } finally {
            conn.disconnect()
        }
    }

    override fun upload(path: String, source: InputStream) {
        val conn = open("PUT", buildUrl(path, directory = false))
        try {
            conn.doOutput = true
            conn.setChunkedStreamingMode(64 * 1024)
            conn.setRequestProperty("Content-Type", "application/octet-stream")
            conn.outputStream.use { output -> source.copyTo(output, DEFAULT_BUFFER_SIZE) }
            ensureSuccess(conn, 200, 201, 204)
        } finally {
            conn.disconnect()
        }
    }

    // ---------- 解析辅助 ----------

    private fun Document.textOf(tag: String): String? {
        val nodes = getElementsByTagNameNS(NS_DAV, tag)
        return nodes.item(0)?.textContent
    }

    private fun Element.textOf(tag: String): String? {
        val nodes = getElementsByTagNameNS(NS_DAV, tag)
        return nodes.item(0)?.textContent
    }

    private fun hrefToPath(href: String): String {
        val raw = try {
            val uri = Uri.parse(href)
            uri.path ?: href
        } catch (e: Exception) {
            href
        }
        return RemotePath.normalize(raw)
    }

    private fun parseHttpDate(value: String): Long = try {
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).parse(value)?.time ?: 0L
    } catch (e: Exception) {
        0L
    }

    private companion object {
        const val NS_DAV = "DAV:"
    }
}
