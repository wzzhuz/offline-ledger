package com.zwz.offlineledger.core.prefs

/**
 * 第 91 批：虚拟账本定义。
 *
 * 「虚拟账本」不是账本维度，只是**一条带前缀的标签 + 一个独立视图**。
 * 不进 Room、不进备份 DTO、不触发 Migration；流水本身仍躺在 records 表里，
 * 靠 BackupRecord.tagNames 往返。定义丢了只是视图列表丢，流水与标签都还在。
 *
 * 刻意不用 org.json / kotlinx.serialization：本类是纯 JVM，JVM 单测可直接跑，
 * 且零新增依赖。字段分隔符用控制字符，normalize() 会剔除含分隔符的脏输入。
 */
data class VirtualBook(
    val id: String,
    val name: String,
    val tagName: String,
    val archived: Boolean = false,
    val sortOrder: Int = 0
)

object VirtualBookCodec {

    /** 统一前缀：主账本明细据此排除台账记录，也避免撞用户自建的普通标签。 */
    const val TAG_PREFIX: String = "台账:"

    private const val SEP = '\u0001'
    private const val LINE = '\u0002'

    fun tagFor(name: String): String = TAG_PREFIX + name.trim()

    /** 纯 JVM 解码：任何一段脏数据都跳过，不让一条坏记录毁掉整个列表。 */
    fun decode(raw: String): List<VirtualBook> {
        if (raw.isBlank()) return emptyList()
        val out = mutableListOf<VirtualBook>()
        for (line in raw.split(LINE)) {
            if (line.isBlank()) continue
            val p = line.split(SEP)
            if (p.size < 5) continue
            val id = p[0].trim()
            val name = p[1].trim()
            if (id.isEmpty() || name.isEmpty()) continue
            val archived = p[3].trim() == "1"
            val order = p[4].toIntOrNull() ?: out.size
            out += VirtualBook(id, name, p[2].trim(), archived, order)
        }
        return out
    }

    fun encode(list: List<VirtualBook>): String = list.joinToString(LINE.toString()) { b ->
        listOf(
            b.id, b.name, b.tagName,
            if (b.archived) "1" else "0", b.sortOrder.toString()
        ).joinToString(SEP.toString())
    }

    /**
     * 唯一规范化出口：名称去空白、标签强制统一前缀、id 去重、按 order 稳定排序。
     *
     * 第 91 批修订：tagName 创建后 MUST 保持稳定，**改名不改标签**，
     * 否则历史流水会因旧标签失联。只有缺省/非法时才由 name 派生；
     * 重建同名视图时也定成 tagFor(name)，与已落库标签一致。
     */
    fun normalize(list: List<VirtualBook>): List<VirtualBook> {
        val seen = HashSet<String>()
        val cleaned = ArrayList<VirtualBook>()
        for (b in list) {
            val name = b.name.trim()
                .replace(SEP.toString(), "").replace(LINE.toString(), "")
            if (name.isEmpty() || b.id.isBlank() || !seen.add(b.id)) continue
            val rawTag = b.tagName.trim()
                .replace(SEP.toString(), "").replace(LINE.toString(), "")
            val tag = if (rawTag.startsWith(TAG_PREFIX) &&
                rawTag.removePrefix(TAG_PREFIX).isNotBlank()
            ) rawTag else tagFor(name)
            cleaned += b.copy(name = name, tagName = tag)
        }
        val sorted = cleaned.sortedWith(compareBy<VirtualBook> { it.sortOrder }.thenBy { it.id })
        return sorted.mapIndexed { i, b -> b.copy(sortOrder = i) }
    }

    fun newId(): String = "vb-" + System.currentTimeMillis().toString(36) +
        "-" + (1000..9999).random().toString()
}
