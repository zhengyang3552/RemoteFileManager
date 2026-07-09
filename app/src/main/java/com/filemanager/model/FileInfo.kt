package com.filemanager.model

data class FileInfo(
    val name: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val formattedSize: String = "",
    val lastModified: String? = null,
    val path: String? = null
)
