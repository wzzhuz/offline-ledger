package com.zwz.offlineledger.data.local

/**
 * 第 30 批：预置分类清单来自随包 assets/default_categories.json，
 * 换清单只改 JSON，不改 Kotlin。解析用固定 schema 的极简正则，
 * 纯 JVM 可测（不依赖 android.jar 的 org.json）。
 */
object SeedCatalog {

    data class Row(
        val id: Long, val name: String, val type: String,
        val parentId: Long?, val level: Int,
        val enabled: Boolean, val sortOrder: Int
    )

    private val OBJECT = Regex("\\{([^{}]*)\\}")
    private val FIELD = Regex("\"([A-Za-z]+)\"\\s*:\\s*(\"[^\"]*\"|null|true|false|-?\\d+)")

    fun parse(json: String): List<Row> = OBJECT.findAll(json).mapNotNull { m ->
        val fields = FIELD.findAll(m.groupValues[1])
            .associate { it.groupValues[1] to it.groupValues[2] }
        val id = fields["id"]?.toLongOrNull() ?: return@mapNotNull null
        val name = fields["name"]?.removeSurrounding("\"") ?: return@mapNotNull null
        val type = (fields["type"] ?: "\"EXPENSE\"").removeSurrounding("\"")
        val parent = fields["parentId"]?.takeIf { it != "null" }?.toLongOrNull()
        Row(
            id = id,
            name = name,
            type = type,
            parentId = parent,
            level = fields["level"]?.toIntOrNull() ?: 0,
            enabled = fields["enabled"] != "false",
            sortOrder = fields["sortOrder"]?.toIntOrNull() ?: 0
        )
    }.toList()

    /** 与 SeedData.allInsertStatements 同一列集合；只给 SeedCallback.onCreate 用。 */
    fun insertStatements(json: String): List<String> = parse(json).map { r ->
        val parent = r.parentId?.toString() ?: "NULL"
        val name = r.name.replace("'", "''")
        "INSERT INTO categories (id, name, type, parentId, level, iconName, colorHex, sortOrder, isArchived, enabled) " +
            "VALUES (${r.id}, '$name', '${r.type}', $parent, ${r.level}, '', '#888888', ${r.sortOrder}, 0, ${if (r.enabled) 1 else 0})"
    }
}
