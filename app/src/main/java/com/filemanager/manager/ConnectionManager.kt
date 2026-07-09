package com.filemanager.manager

import com.filemanager.model.ConnectionState
import com.filemanager.model.FileInfo
import com.filemanager.model.RemoteConnection
import java.io.InputStream
import java.io.OutputStream

class ConnectionManager {

    private val connections = mutableListOf<RemoteConnection>()

    fun addConnection(connection: RemoteConnection): Boolean {
        connections.add(connection)
        return true
    }

    fun getConnections(): List<RemoteConnection> = connections.toList()

    fun connect(connection: RemoteConnection): Boolean {
        connection.connectionState = ConnectionState.CONNECTED
        return true
    }

    fun disconnect(connection: RemoteConnection) {
        connection.connectionState = ConnectionState.DISCONNECTED
    }

    fun changeDirectory(connection: RemoteConnection, path: String): Boolean {
        return true
    }

    fun getCurrentDirectory(connection: RemoteConnection): String {
        return connection.path
    }

    fun listFiles(connection: RemoteConnection, path: String): List<FileInfo> {
        // Stub implementation - return empty list
        return emptyList()
    }

    fun downloadFile(
        connection: RemoteConnection,
        remotePath: String,
        outputStream: OutputStream
    ): Boolean {
        // Stub implementation
        return true
    }

    fun uploadFile(
        connection: RemoteConnection,
        inputStream: InputStream,
        remotePath: String
    ): Boolean {
        // Stub implementation
        return true
    }

    fun delete(connection: RemoteConnection, path: String): Boolean {
        // Stub implementation
        return true
    }

    fun rename(
        connection: RemoteConnection,
        oldPath: String,
        newName: String
    ): Boolean {
        // Stub implementation
        return true
    }

    fun copyRemote(
        connection: RemoteConnection,
        sourcePath: String,
        destPath: String
    ): Boolean {
        // Stub implementation
        return true
    }
}