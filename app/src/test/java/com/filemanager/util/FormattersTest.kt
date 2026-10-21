package com.filemanager.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** 文件大小与时间的展示格式。 */
class FormattersTest {

    @Test
    fun `formatSize handles boundary values`() {
        assertEquals("-", Formatters.formatSize(-1))
        assertEquals("0 B", Formatters.formatSize(0))
        assertEquals("1023 B", Formatters.formatSize(1023))
        assertEquals("1.0 KB", Formatters.formatSize(1024))
        assertEquals("1.5 KB", Formatters.formatSize(1536))
        assertEquals("24 MB", Formatters.formatSize(25_165_824))
        assertEquals("1.0 GB", Formatters.formatSize(1024L * 1024 * 1024))
    }

    @Test
    fun `formatDateTime returns dash for unknown timestamp`() {
        assertEquals("-", Formatters.formatDateTime(0))
        assertEquals("-", Formatters.formatDateTime(-5))
    }

    @Test
    fun `formatMeta skips missing parts`() {
        val meta = Formatters.formatMeta(
            isDirectory = false,
            size = 2048,
            lastModified = 0,
            directoryLabel = "文件夹",
            fileLabel = "文件"
        )
        assertEquals("文件 · 2.0 KB", meta)

        val dirMeta = Formatters.formatMeta(
            isDirectory = true,
            size = 0,
            lastModified = 0,
            directoryLabel = "文件夹",
            fileLabel = "文件"
        )
        assertEquals("文件夹", dirMeta)
    }
}
