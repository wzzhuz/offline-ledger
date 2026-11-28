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


/**
 * 第 92 批：标签选择器「新建」的唯一判定出口（纯 JVM，可单测）。
 *
 * 之前 TagPickerDialog 只把新名字加进 picked，但 chip 区只渲染数据库已有的
 * tags，点 + 后**没有任何可见反馈**，也无从反选，使用者会判定「新增不生效」。
 * 本函数把校验 / 判重 / 上限收口，UI 再用「已有名 + picked」合并渲染。
 * 返回 (新的 picked, 错误文案)；错误时 picked 原样返回。
 */
fun addPickedTag(
    picked: List<String>,
    existingNames: List<String>,
    input: String
): Pair<List<String>, String?> {
    val n = input.trim()
    val bad = TagNameRules.validate(n)
    if (bad != null) return picked to bad
    return when {
        n in picked -> picked to "该标签已选择"
        n in existingNames -> (picked + n).take(TagNameRules.MAX_PER_RECORD) to null
        picked.size >= TagNameRules.MAX_PER_RECORD ->
            picked to "最多 ${TagNameRules.MAX_PER_RECORD} 个标签"
        else -> (picked + n) to null
    }
}

/** chip 区渲染名单：数据库已有名 + 本次新建名，去重后保证新建项立刻可见。 */
fun visibleTagNames(existingNames: List<String>, picked: List<String>): List<String> =
    (existingNames + picked).distinct()
