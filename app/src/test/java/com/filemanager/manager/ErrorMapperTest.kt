package com.filemanager.manager

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** 异常归一化：不同协议库的异常应映射到同一套原因。 */
class ErrorMapperTest {

    @Test
    fun `unknown host maps to unreachable`() {
        assertEquals(
            RemoteException.Reason.HOST_UNREACHABLE,
            ErrorMapper.classify(UnknownHostException("no.such.host"))
        )
        assertEquals(
            RemoteException.Reason.HOST_UNREACHABLE,
            ErrorMapper.classify(ConnectException("Connection refused"))
        )
    }

    @Test
    fun `timeout maps to timeout`() {
        assertEquals(
            RemoteException.Reason.TIMEOUT,
            ErrorMapper.classify(SocketTimeoutException("Read timed out"))
        )
    }

    @Test
    fun `ftp login failure maps to auth`() {
        assertEquals(
            RemoteException.Reason.AUTH_FAILED,
            ErrorMapper.classify(IOException("530 Login incorrect."))
        )
    }

    @Test
    fun `missing path maps to not found`() {
        assertEquals(
            RemoteException.Reason.NOT_FOUND,
            ErrorMapper.classify(IOException("550 No such file or directory"))
        )
    }

    @Test
    fun `permission errors map to permission denied`() {
        assertEquals(
            RemoteException.Reason.PERMISSION_DENIED,
            ErrorMapper.classify(IOException("Permission denied"))
        )
    }

    @Test
    fun `already exists maps to conflict`() {
        assertEquals(
            RemoteException.Reason.ALREADY_EXISTS,
            ErrorMapper.classify(IOException("File already exists"))
        )
    }

    @Test
    fun `remote exception is passed through`() {
        val original = RemoteException(RemoteException.Reason.UNSUPPORTED, "nope")
        assertEquals(original, ErrorMapper.toRemote(original))
    }

    @Test
    fun `generic io exception maps to protocol`() {
        assertEquals(
            RemoteException.Reason.PROTOCOL,
            ErrorMapper.classify(IOException("Unexpected end of stream"))
        )
    }
}
