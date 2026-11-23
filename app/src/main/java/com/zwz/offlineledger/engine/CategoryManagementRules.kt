package com.zwz.offlineledger.engine

/**
 * 第 55 批：分类管理三页共用的纯 JVM 规则出口。
 *
 * 不依赖 Android / Compose / Room，便于单测锁死三件容易改错的事：
 * 等宽网格分行、排序作用域（type / level / parentId 三元组）、同父级重名校验。
 * UI 与 ViewModel MUST 都从这里取值，MUST NOT 各自再写一份换算。
 */
object CategoryManagementRules {

    data class Item(
        val id: Long,
        val type: String,
        val level: Int,
        val parentId: Long?,
        val sortOrder: Int,
        val archived: Boolean = false
    )

    /** 等宽网格：返回每个格位对应的 item 下标，null = 末行补空位。 */
    fun gridCells(itemCount: Int, columns: Int): List<Int?> {
        require(columns > 0) { "columns must be positive" }
        val rows = rowCount(itemCount, columns)
        return (0 until rows * columns).map { i -> i.takeIf { it < itemCount } }
    }

    fun rowCount(itemCount: Int, columns: Int): Int {
        require(columns > 0) { "columns must be positive" }
        return (itemCount + columns - 1) / columns
    }

    /** 同一父级下是否已有同名（去首尾空白、忽略大小写）。 */
    fun isDuplicate(existing: List<String>, candidate: String): Boolean {
        val c = candidate.trim()
        if (c.isEmpty()) return false
        return existing.any { it.trim().equals(c, ignoreCase = true) }
    }

    /**
     * 排序页「保存」：按 (type, level, parentId) 三元组重建全量 id 顺序。
     *
     * - 一级只在目标 type 的一级之间换位；
     * - 二级只在所属父级内部换位；
     * - 另一收支类型、归档项、以及 draft 漏掉的项全部按原 sortOrder 补齐，
     *   MUST NOT 丢项、MUST NOT 把不同组混成一个全局数组。
     */
    fun rebuildOrder(
        all: List<Item>,
        type: String,
        draftRoots: List<Long>,
        draftChildren: Map<Long, List<Long>>
    ): List<Long> {
        val active = all.filter { !it.archived }
        val archivedIds = all.filter { it.archived }
            .sortedBy { it.sortOrder }
            .map { it.id }
        val byId = active.associateBy { it.id }

        val targetRoots = mutableListOf<Item>()
        val rootUsed = mutableSetOf<Long>()
        draftRoots.forEach { id ->
            val item = byId[id]
            if (item != null && item.level == 0 && item.type == type && rootUsed.add(item.id)) {
                targetRoots += item
            }
        }
        active.filter { it.level == 0 && it.type == type && it.id !in rootUsed }
            .sortedBy { it.sortOrder }
            .forEach { targetRoots += it; rootUsed += it.id }

        val ordered = mutableListOf<Long>()
        targetRoots.forEach { root ->
            ordered += root.id
            val kids = mutableListOf<Item>()
            val kidUsed = mutableSetOf<Long>()
            draftChildren[root.id].orEmpty().forEach { id ->
                val item = byId[id]
                if (item != null && item.level == 1 && item.parentId == root.id && kidUsed.add(item.id)) {
                    kids += item
                }
            }
            active.filter { it.level == 1 && it.parentId == root.id && it.id !in kidUsed }
                .sortedBy { it.sortOrder }
                .forEach { kids += it; kidUsed += it.id }
            ordered += kids.map { it.id }
        }

        // 另一收支类型（含其一级与二级）保持原相对次序；已在 ordered 里的不重复加入。
        val otherType = active.filter { it.type != type }
            .sortedBy { it.sortOrder }
            .map { it.id }
            .filter { it !in ordered }

        return ordered + otherType + archivedIds
    }
}
