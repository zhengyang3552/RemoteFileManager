package com.filemanager.util

import com.filemanager.model.FileInfo
import com.filemanager.model.SortField
import org.junit.Assert.assertEquals
import org.junit.Test

/** 排序策略：返回上级置顶、目录优先、按字段升降序。 */
class FileSorterTest {

    private val files = listOf(
        FileInfo(name = "b.txt", isDirectory = false, size = 200, lastModified = 2000),
        FileInfo(name = "A.txt", isDirectory = false, size = 100, lastModified = 1000),
        FileInfo(name = "docs", isDirectory = true, size = 0, lastModified = 3000),
        FileInfo(name = "media", isDirectory = true, size = 0, lastModified = 500)
    )

    @Test
    fun `directories always come before files`() {
        val sorted = FileSorter.sort(files, SortField.SIZE, ascending = true)
        assertEquals(listOf("docs", "media", "A.txt", "b.txt"), sorted.map { it.name })
    }

    @Test
    fun `parent link is pinned to the top`() {
        val withParent = files + FileInfo.parentLink()
        val sorted = FileSorter.sort(withParent, SortField.NAME, ascending = false)
        assertEquals("..", sorted.first().name)
    }

    @Test
    fun `name sorting is case insensitive`() {
        val sorted = FileSorter.sort(files, SortField.NAME, ascending = true)
        assertEquals(listOf("docs", "media", "A.txt", "b.txt"), sorted.map { it.name })
    }

    @Test
    fun `size sorting respects direction`() {
        val ascending = FileSorter.sort(files, SortField.SIZE, ascending = true).filter { !it.isDirectory }
        assertEquals(listOf("A.txt", "b.txt"), ascending.map { it.name })

        val descending = FileSorter.sort(files, SortField.SIZE, ascending = false).filter { !it.isDirectory }
        assertEquals(listOf("b.txt", "A.txt"), descending.map { it.name })
    }

    @Test
    fun `date sorting respects direction`() {
        val ascending = FileSorter.sort(files, SortField.DATE, ascending = true).filter { it.isDirectory }
        assertEquals(listOf("media", "docs"), ascending.map { it.name })
    }

    @Test
    fun `empty and single item lists are returned as is`() {
        assertEquals(emptyList<FileInfo>(), FileSorter.sort(emptyList(), SortField.NAME, true))
        val single = listOf(files.first())
        assertEquals(single, FileSorter.sort(single, SortField.NAME, true))
    }
}
