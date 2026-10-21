package com.filemanager.data

import android.content.Context
import com.filemanager.model.ProtocolType
import com.filemanager.model.RemoteConnection
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 连接配置的本地持久化（SharedPreferences + JSON）。
 *
 * 选择 SharedPreferences 而非数据库：连接数量通常在个位数，
 * 一次性读写全量 JSON 更简单，也避免为一张表引入 Room。
 * 读取时对损坏数据采取「跳过单条」策略，不让一条坏记录导致整个列表丢失。
 */
class ConnectionStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun load(): List<RemoteConnection> {
        val raw = prefs.getString(KEY_CONNECTIONS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let { parse(it) }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveAll(connections: List<RemoteConnection>) {
        val array = JSONArray()
        connections.forEach { array.put(serialize(it)) }
        prefs.edit().putString(KEY_CONNECTIONS, array.toString()).apply()
    }

    private fun serialize(connection: RemoteConnection): JSONObject = JSONObject().apply {
        put("id", connection.id)
        put("name", connection.name)
        put("type", connection.type.name)
        put("host", connection.host)
        put("port", connection.port)
        put("username", connection.username)
        put("password", SecretCipher.encrypt(connection.password))
        put("path", connection.path)
        put("useSsl", connection.useSsl)
        put("passiveMode", connection.passiveMode)
        put("timeoutMs", connection.timeoutMs)
    }

    private fun parse(json: JSONObject): RemoteConnection? = try {
        val host = json.optString("host").trim()
        if (host.isEmpty()) {
            null
        } else {
            val type = ProtocolType.fromNameOrDefault(json.optString("type"))
            RemoteConnection(
                id = json.optString("id").ifEmpty { UUID.randomUUID().toString() },
                name = json.optString("name").ifEmpty { host },
                type = type,
                host = host,
                port = json.optInt("port", type.defaultPort).takeIf { it in 1..65535 } ?: type.defaultPort,
                username = json.optString("username"),
                password = SecretCipher.decrypt(json.optString("password")),
                path = json.optString("path").ifEmpty { "/" },
                useSsl = json.optBoolean("useSsl", type == ProtocolType.FTPS),
                passiveMode = json.optBoolean("passiveMode", true),
                timeoutMs = json.optInt("timeoutMs", RemoteConnection.DEFAULT_TIMEOUT_MS)
                    .takeIf { it in RemoteConnection.MIN_TIMEOUT_MS..RemoteConnection.MAX_TIMEOUT_MS }
                    ?: RemoteConnection.DEFAULT_TIMEOUT_MS
            )
        }
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val PREF_NAME = "rfm_connections"
        const val KEY_CONNECTIONS = "connections_v1"
    }
}
