package com.filemanager.model

/**
 * 统一的远程文件/目录模型。
 *
 * 说明：模型层不依赖 Android，也不做格式化，UI 文案一律由 [com.filemanager.util.Formatters]
 * 在展示时生成，避免把「显示逻辑」散落到协议实现里。
 */
data class FileInfo(
    /** 文件名（不含路径） */
    val name: String,
    /** 是否为目录 */
    val isDirectory: Boolean,
    /** 字节数；目录固定为 0，未知大小用 [SIZE_UNKNOWN] */
    val size: Long = 0L,
    /** 最后修改时间（epoch millis），未知为 0 */
    val lastModified: Long = 0L,
    /** 权限字符串（部分协议可提供），未知为 null */
    val permissions: String? = null,
    /** 是否为「返回上级」的虚拟条目 */
    val isParentLink: Boolean = false
) {
    val hasKnownSize: Boolean get() = !isDirectory && size >= 0
    val hasKnownModified: Boolean get() = lastModified > 0L

    companion object {
        const val SIZE_UNKNOWN: Long = -1L

        /** 生成「返回上级」虚拟条目 */
        fun parentLink(): FileInfo = FileInfo(
            name = "..",
            isDirectory = true,
            size = 0L,
            lastModified = 0L,
            isParentLink = true
        )
    }
}
