package com.filemanager.model

import com.filemanager.R

/** 文件列表排序字段 */
enum class SortField(val labelRes: Int) {
    NAME(R.string.sort_by_name),
    SIZE(R.string.sort_by_size),
    DATE(R.string.sort_by_date);

    companion object {
        fun fromNameOrDefault(name: String?): SortField =
            values().firstOrNull { it.name.equals(name, ignoreCase = true) } ?: NAME
    }
}
