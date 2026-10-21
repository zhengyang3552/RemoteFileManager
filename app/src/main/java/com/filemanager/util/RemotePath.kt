package com.filemanager.util

/**
 * 远程路径的规范化与校验。
 *
 * 所有协议统一使用 POSIX 风格路径（以 `/` 分隔、以 `/` 开头），
 * 这里集中处理重复斜杠、`.`/`..`、尾部斜杠等边界情况，
 * 避免每个协议实现各自拼接路径导致「/a//b」「/a/../b」这类脏路径。
 */
object RemotePath {

    const val ROOT = "/"

    private val ILLEGAL_NAME_CHARS = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
    private const val MAX_NAME_LENGTH = 255

    /** 名称校验结果 */
    enum class NameError { EMPTY, TOO_LONG, INVALID_CHARS, RESERVED }

    /** 规范化路径：去重斜杠、解析 `.` 与 `..`、补齐前导 `/` */
    fun normalize(raw: String?): String {
        if (raw.isNullOrBlank()) return ROOT
        val segments = ArrayList<String>()
        for (segment in raw.trim().replace('\\', '/').split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex)
                else -> segments.add(segment)
            }
        }
        return if (segments.isEmpty()) ROOT else segments.joinToString(separator = "/", prefix = "/")
    }

    /** 拼接子路径，[name] 中的 `/` 会被视为分隔符并参与规范化 */
    fun child(base: String, name: String): String = normalize("${normalize(base)}/$name")

    /** 父目录；根目录的父目录仍是根目录 */
    fun parent(path: String): String {
        val normalized = normalize(path)
        if (normalized == ROOT) return ROOT
        val index = normalized.lastIndexOf('/')
        return if (index <= 0) ROOT else normalized.substring(0, index)
    }

    /** 路径最后一段的名称 */
    fun name(path: String): String {
        val normalized = normalize(path)
        return if (normalized == ROOT) ROOT else normalized.substringAfterLast('/')
    }

    fun isRoot(path: String?): Boolean = normalize(path) == ROOT

    /** 判断 [path] 是否位于 [ancestor] 之下（含自身） */
    fun isUnder(path: String, ancestor: String): Boolean {
        val p = normalize(path)
        val a = normalize(ancestor)
        return a == ROOT || p == a || p.startsWith("$a/")
    }

    /** 校验文件名/文件夹名，返回 null 表示合法 */
    fun validateName(name: String): NameError? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> NameError.EMPTY
            trimmed.length > MAX_NAME_LENGTH -> NameError.TOO_LONG
            trimmed == "." || trimmed == ".." -> NameError.RESERVED
            trimmed.any { it in ILLEGAL_NAME_CHARS } -> NameError.INVALID_CHARS
            // 控制字符会导致多数协议解析异常
            trimmed.any { it.code in 0..31 } -> NameError.INVALID_CHARS
            else -> null
        }
    }

    /**
     * 清洗外部传入的文件名（来自 SAF 的显示名）：
     * 去掉路径分隔符与控制字符，空名回退为 `untitled`。
     */
    fun sanitizeFileName(raw: String?): String {
        val cleaned = (raw ?: "")
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .filter { it !in ILLEGAL_NAME_CHARS && it.code !in 0..31 }
            .trim()
        return cleaned.ifEmpty { "untitled" }
    }
}
