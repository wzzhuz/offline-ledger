package com.zwz.offlineledger.core

/** 第 44 批：账户快捷行的纯数据项，UI 层从 AccountEntity 映射而来。 */
data class AccountQuickItem(
    val id: Long,
    val name: String,
    val isArchived: Boolean,
    /** 第 60 批：卡种小标志；带默认值，既有单测构造零改动。 */
    val cardKind: String? = null
)

/**
 * 第 44 批：编辑转账时，转出 / 转入若是不常用或已归档账户，
 * 原先被 accounts.filter { !isArchived }.take(4) 挡在可见候选之外，
 * 使用者看到一排常用账户、没有一个选中，误以为账户被换掉。
 *
 * 规则：常用前 N 个保持原顺序；当前选中项 MUST 追加进来（去重）；
 * 归档账户不隐藏、只加标注；账户已删除时由 UI 显式报错，不静默兜底。
 */
object AccountQuickCandidates {
    fun select(
        accounts: List<AccountQuickItem>,
        selectedId: Long?,
        limit: Int
    ): List<AccountQuickItem> {
        val shown = accounts.take(limit).toMutableList()
        val chosen = accounts.firstOrNull { it.id == selectedId }
        if (chosen != null && shown.none { it.id == chosen.id }) shown.add(chosen)
        return shown
    }

    fun labelOf(a: AccountQuickItem): String =
        if (a.isArchived) a.name + "（已归档）" else a.name

    fun currentOrNull(accounts: List<AccountQuickItem>, selectedId: Long?): AccountQuickItem? =
        accounts.firstOrNull { it.id == selectedId }

    /**
     * 第 45 批：编辑态把本记录引用的账户（转出 / 转入）提到列表最前。
     * 只改展示顺序，按 id 精确回填与「已删除显式拦截」仍由第 44 批保证。
     */
    fun <T> promoteById(
        items: List<T>,
        selectedIds: List<Long>,
        idOf: (T) -> Long
    ): List<T> {
        if (selectedIds.isEmpty()) return items
        val head = selectedIds.distinct().mapNotNull { id -> items.firstOrNull { idOf(it) == id } }
        val headIds = head.map(idOf).toSet()
        return head + items.filter { idOf(it) !in headIds }
    }

    fun promoteIds(
        accounts: List<AccountQuickItem>,
        selectedIds: List<Long>
    ): List<AccountQuickItem> = promoteById(accounts, selectedIds) { it.id }

    /** 第 45 批：交易对方当前值置顶，联想 / 快捷行不再把「虚拟-不记录」埋在后面。 */
    fun promoteText(items: List<String>, current: String?): List<String> {
        val v = current?.trim().orEmpty()
        if (v.isEmpty()) return items
        return listOf(v) + items.filter { it != v }
    }
}
