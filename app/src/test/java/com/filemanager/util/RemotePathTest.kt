package com.filemanager.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 路径规范化的边界用例：多余斜杠、相对段、根目录、非法名称。 */
class RemotePathTest {

    @Test
    fun `normalize keeps root`() {
        assertEquals("/", RemotePath.normalize("/"))
        assertEquals("/", RemotePath.normalize(""))
        assertEquals("/", RemotePath.normalize("   "))
        assertEquals("/", RemotePath.normalize("///"))
    }

    @Test
    fun `normalize collapses duplicate and trailing slashes`() {
        assertEquals("/a/b", RemotePath.normalize("//a//b//"))
        assertEquals("/a/b", RemotePath.normalize("a/b"))
        assertEquals("/a/b", RemotePath.normalize("\\a\\b\\"))
    }

    @Test
    fun `normalize resolves dot segments`() {
        assertEquals("/a/c", RemotePath.normalize("/a/./c"))
        assertEquals("/c", RemotePath.normalize("/a/../c"))
        assertEquals("/", RemotePath.normalize("/../.."))
        assertEquals("/a", RemotePath.normalize("/a/b/.."))
    }

    @Test
    fun `child joins without double slash`() {
        assertEquals("/a/b", RemotePath.child("/a", "b"))
        assertEquals("/b", RemotePath.child("/", "b"))
        assertEquals("/a/b", RemotePath.child("/a/", "/b"))
        assertEquals("/a/b/c", RemotePath.child("/a", "b/c"))
    }

    @Test
    fun `parent stops at root`() {
        assertEquals("/", RemotePath.parent("/"))
        assertEquals("/", RemotePath.parent("/a"))
        assertEquals("/a", RemotePath.parent("/a/b"))
        assertEquals("/a/b", RemotePath.parent("/a/b/c/"))
    }

    @Test
    fun `name extracts last segment`() {
        assertEquals("/", RemotePath.name("/"))
        assertEquals("b", RemotePath.name("/a/b"))
        assertEquals("b", RemotePath.name("/a/b/"))
    }

    @Test
    fun `isUnder handles root and siblings`() {
        assertTrue(RemotePath.isUnder("/a/b", "/"))
        assertTrue(RemotePath.isUnder("/a/b", "/a"))
        assertTrue(RemotePath.isUnder("/a", "/a"))
        assertFalse(RemotePath.isUnder("/ab", "/a"))
    }

    @Test
    fun `validateName rejects illegal values`() {
        assertNull(RemotePath.validateName("report.pdf"))
        assertNull(RemotePath.validateName("中文 名称.txt"))

        assertEquals(RemotePath.NameError.EMPTY, RemotePath.validateName(""))
        assertEquals(RemotePath.NameError.EMPTY, RemotePath.validateName("   "))
        assertEquals(RemotePath.NameError.RESERVED, RemotePath.validateName(".."))
        assertEquals(RemotePath.NameError.INVALID_CHARS, RemotePath.validateName("a/b"))
        assertEquals(RemotePath.NameError.INVALID_CHARS, RemotePath.validateName("a:b"))
        assertEquals(RemotePath.NameError.INVALID_CHARS, RemotePath.validateName("a\\b"))
        assertEquals(RemotePath.NameError.TOO_LONG, RemotePath.validateName("a".repeat(300)))
    }

    @Test
    fun `sanitizeFileName strips path and control characters`() {
        assertEquals("file.txt", RemotePath.sanitizeFileName("content://x/file.txt"))
        assertEquals("file.txt", RemotePath.sanitizeFileName("a\\b\\file.txt"))
        assertEquals("untitled", RemotePath.sanitizeFileName(null))
        assertEquals("untitled", RemotePath.sanitizeFileName("   "))
        assertEquals("ac", RemotePath.sanitizeFileName("a\u0000b\u001Fc".replace("b", "")))
    }
}
