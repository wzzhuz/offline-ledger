package com.zwz.offlineledger.engine

/**
 * 第 13 批：洞察数据准备层（纯 JVM，全部可单测）。
 *
 * ⚠️ 金额边界与 ChartData 一致：金额的求和 / 比较 / 差额全是 Long 分，
 * 唯一用 Float 的地方是「占比 → 进度条宽度」的几何换算，MUST NOT 回写金额。
 */
object InsightData {

    /** 均值分母是自然日 / 自然月数，不是「有流水的天数」。 */
    fun averageCents(totalCents: Long, unitCount: Int): Long =
        if (unitCount <= 0) 0L else totalCents / unitCount

    /** 占比只用于绘制进度条；总量 <= 0 时一律 0。 */
    fun ratio(cents: Long, totalCents: Long): Float =
        if (totalCents <= 0L || cents <= 0L) 0f
        else (cents.toFloat() / totalCents.toFloat()).coerceIn(0f, 1f)

    data class RankItem(val name: String, val cents: Long, val count: Int, val id: Long? = null)
    data class RankRow(
        val name: String,
        val cents: Long,
        val count: Int,
        val ratio: Float,
        val id: Long? = null
    )

    /** 按金额降序取 Top N，并算出占比；金额 <= 0 的项不展示。 */
    fun topRows(items: List<RankItem>, totalCents: Long, limit: Int): List<RankRow> =
        items.filter { it.cents > 0L }
            .sortedByDescending { it.cents }
            .take(limit.coerceAtLeast(0))
            .map { RankRow(it.name, it.cents, it.count, ratio(it.cents, totalCents), it.id) }

    /**
     * 一级分类笔数 = 自身直接流水 + 所有二级流水。
     * parentOf: categoryId -> parentId（一级为 null）。
     */
    fun rollupCounts(
        direct: Map<Long, Int>,
        parentOf: Map<Long, Long?>
    ): Map<Long, Int> {
        val out = direct.toMutableMap()
        direct.forEach { (id, cnt) ->
            val parent = parentOf[id]
            if (parent != null) out[parent] = (out[parent] ?: 0) + cnt
        }
        return out
    }
}
