package com.filemanager.util

import com.filemanager.model.FileInfo
import com.filemanager.model.SortField

/**
 * 文件列表排序策略：
 * 「返回上级」永远置顶，目录始终排在文件之前，其余按用户选择的字段排序。
 */
object FileSorter {

    private val GROUP_COMPARATOR = Comparator<FileInfo> { a, b ->
        when {
            a.isParentLink != b.isParentLink -> if (a.isParentLink) -1 else 1
            a.isDirectory != b.isDirectory -> if (a.isDirectory) -1 else 1
            else -> 0
        }
    }

    fun sort(items: List<FileInfo>, field: SortField, ascending: Boolean): List<FileInfo> {
        if (items.size <= 1) return items

        val byField: Comparator<FileInfo> = when (field) {
            SortField.NAME -> Comparator { a, b ->
                val byName = String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name)
                if (byName != 0) byName else a.name.compareTo(b.name)
            }
            SortField.SIZE -> Comparator { a, b -> a.size.compareTo(b.size) }
            SortField.DATE -> Comparator { a, b -> a.lastModified.compareTo(b.lastModified) }
        }

        val effective = if (ascending) byField else byField.reversed()
        return items.sortedWith(GROUP_COMPARATOR.thenComparing(effective))
    }
}
