package com.zwz.offlineledger.core

/**
 * 第 75 批：标签名快照的唯一分隔符契约。
 * CSV / Firefly / 回收站三处都以英文逗号分隔，标签名 MUST NOT 含逗号。
 */
fun splitTagNames(raw: String): List<String> =
    raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        .distinct().take(TagNameRules.MAX_PER_RECORD)

fun joinTagNames(names: List<String>): String =
    names.map { it.trim() }.filter { it.isNotEmpty() }
        .distinct().take(TagNameRules.MAX_PER_RECORD).joinToString(",")

object TagNameRules {
    const val MAX_PER_RECORD = 8
    fun validate(name: String): String? = when {
        name.isBlank() -> "标签名不能为空"
        name.contains(',') -> "标签名不能包含逗号（逗号用于分隔多个标签）"
        name.length > 20 -> "标签名不能超过 20 个字"
        else -> null
    }
}
