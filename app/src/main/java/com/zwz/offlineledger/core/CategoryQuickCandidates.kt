package com.zwz.offlineledger.core

/**
 * 第 52 批：记一笔分类候选的纯 JVM 出口（零 Android / Room 依赖）。
 *
 * 停用语义从「录入侧不可见」收窄为「默认快捷栏不展示、全量选择器仍可选」：
 * - 快捷栏一级 / 二级只列启用项，但**当前选中项所属一级与该二级 MUST 追加**，
 *   否则编辑一条引用了停用分类的历史流水时，一排候选里没有一个选中；
 * - 全量选择器（分组 chips + 搜索）列出未归档的全部一级与二级，含停用项；
 * - 归档分类仍不进选择器（归档 = 不再新选），只由快捷栏的「当前选中」兜底显示。
 *
 * 树关系只看 parentId，与 id 数值无关（第 51 批规则）。
 */
data class CategoryQuickItem(
    val id: Long,
    val name: String,
    val type: String,
    val parentId: Long?,
    val level: Int,
    val enabled: Boolean,
    val isArchived: Boolean,
    val sortOrder: Int,
    val colorHex: String = ""
)

data class CategoryPickerGroup(
    val root: CategoryQuickItem,
    val children: List<CategoryQuickItem>
)

/** 搜索命中：id 为实际要记的分类，rootId 为其一级，label 带父级全名。 */
data class CategoryPickerHit(
    val id: Long,
    val rootId: Long,
    val label: String,
    val enabled: Boolean
)

object CategoryQuickCandidates {

    /** 选中项所属一级 id：一级指向自身，二级取 parentId；找不到返回 null。 */
    fun rootIdOf(items: List<CategoryQuickItem>, selectedId: Long?): Long? {
        if (selectedId == null) return null
        val c = items.firstOrNull { it.id == selectedId } ?: return null
        return if (c.level == 0) c.id else c.parentId
    }

    /** 默认快捷栏一级：启用且未归档 + 当前选中项所属一级（含停用 / 归档）。 */
    fun visibleRoots(
        items: List<CategoryQuickItem>,
        type: String,
        selectedId: Long?
    ): List<CategoryQuickItem> {
        val roots = items.filter { it.level == 0 && it.type == type }
            .sortedBy { it.sortOrder }
        val shown = roots.filter { it.enabled && !it.isArchived }.toMutableList()
        val chosen = roots.firstOrNull { it.id == rootIdOf(items, selectedId) }
        if (chosen != null && shown.none { it.id == chosen.id }) shown.add(chosen)
        return shown.sortedBy { it.sortOrder }
    }

    /** 展开的二级 chips：该父下启用且未归档 + 当前选中项（含停用 / 归档）。 */
    fun visibleChildren(
        items: List<CategoryQuickItem>,
        parentId: Long,
        selectedId: Long?
    ): List<CategoryQuickItem> {
        val children = items.filter { it.level == 1 && it.parentId == parentId }
            .sortedBy { it.sortOrder }
        val shown = children.filter { it.enabled && !it.isArchived }.toMutableList()
        val chosen = children.firstOrNull { it.id == selectedId }
        if (chosen != null && shown.none { it.id == chosen.id }) shown.add(chosen)
        return shown.sortedBy { it.sortOrder }
    }

    /** 全量选择器分组：未归档一级（含停用）按 sortOrder，组内为该父下未归档二级。 */
    fun pickerGroups(
        items: List<CategoryQuickItem>,
        type: String
    ): List<CategoryPickerGroup> {
        val all = items.filter { it.type == type && !it.isArchived }
        return all.filter { it.level == 0 }.sortedBy { it.sortOrder }.map { root ->
            CategoryPickerGroup(
                root,
                all.filter { it.level == 1 && it.parentId == root.id }
                    .sortedBy { it.sortOrder }
            )
        }
    }

    /** 搜索：命中一级名或其下二级名；二级 label 一律带「一级 / 二级」。 */
    fun searchHits(
        items: List<CategoryQuickItem>,
        type: String,
        query: String
    ): List<CategoryPickerHit> {
        val q = query.trim()
        val all = items.filter { it.type == type && !it.isArchived }
        val out = mutableListOf<CategoryPickerHit>()
        all.filter { it.level == 0 }.sortedBy { it.sortOrder }.forEach { r ->
            val rootMatch = q.isBlank() || r.name.contains(q, ignoreCase = true)
            if (rootMatch) out += CategoryPickerHit(r.id, r.id, labelOf(r), r.enabled)
            all.filter { it.level == 1 && it.parentId == r.id }
                .sortedBy { it.sortOrder }.forEach { c ->
                    if (rootMatch || q.isBlank() || c.name.contains(q, ignoreCase = true)) {
                        out += CategoryPickerHit(
                            c.id, r.id, r.name + " / " + labelOf(c), c.enabled
                        )
                    }
                }
        }
        return out
    }

    fun labelOf(c: CategoryQuickItem): String = when {
        c.isArchived -> c.name + "（已归档）"
        !c.enabled -> c.name + "（停用）"
        else -> c.name
    }
}
