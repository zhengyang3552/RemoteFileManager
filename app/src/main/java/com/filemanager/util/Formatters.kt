package com.filemanager.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 展示层格式化工具：文件大小、时间、协议描述。
 * 纯 Kotlin 实现，便于单元测试覆盖边界情况（0 字节、未知大小、超大文件等）。
 */
object Formatters {

    private val SIZE_UNITS = arrayOf("B", "KB", "MB", "GB", "TB", "PB")

    /**
     * 人类可读的文件大小。
     * - 未知大小（负数）→ "-"
     * - 1024 进制，小于 10 时保留一位小数（1.5 MB），大于等于 10 时取整（24 MB）
     */
    fun formatSize(bytes: Long): String {
        if (bytes < 0) return "-"
        if (bytes < 1024) return "$bytes ${SIZE_UNITS[0]}"

        var value = bytes.toDouble()
        var unitIndex = 0
        while (value >= 1024 && unitIndex < SIZE_UNITS.lastIndex) {
            value /= 1024
            unitIndex++
        }

        val text = if (value < 10) {
            String.format(Locale.US, "%.1f", value)
        } else {
            value.toLong().toString()
        }
        return "$text ${SIZE_UNITS[unitIndex]}"
    }

    /** 时间戳（epoch millis）→ `yyyy-MM-dd HH:mm`；未知时间返回 "-" */
    fun formatDateTime(millis: Long): String {
        if (millis <= 0L) return "-"
        return try {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
        } catch (e: IllegalArgumentException) {
            "-"
        }
    }

    /** 列表项副标题：目录只显示「文件夹 · 修改于 …」，文件显示「大小 · 修改于 …」 */
    fun formatMeta(isDirectory: Boolean, size: Long, lastModified: Long, directoryLabel: String, fileLabel: String): String {
        val typeLabel = if (isDirectory) directoryLabel else fileLabel
        val sizeLabel = if (isDirectory) null else formatSize(size)
        val timeLabel = if (lastModified > 0L) formatDateTime(lastModified) else null
        return listOfNotNull(typeLabel, sizeLabel, timeLabel).joinToString(" · ")
    }
}
