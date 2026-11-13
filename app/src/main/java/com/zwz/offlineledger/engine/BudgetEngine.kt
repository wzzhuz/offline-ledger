package com.zwz.offlineledger.engine

/** 预算进度，纯 JVM 可单测。usedRatio 仅用于绘制进度条，不回写任何金额。 */
data class BudgetProgress(
    val limitCents: Long,
    val spentCents: Long,
    val remainingCents: Long,
    val overCents: Long,
    val usedRatio: Float,
    val isOver: Boolean
)

object BudgetEngine {

    fun progress(limitCents: Long, spentCents: Long): BudgetProgress {
        val limit = limitCents.coerceAtLeast(0L)
        val spent = spentCents.coerceAtLeast(0L)
        val over = (spent - limit).coerceAtLeast(0L)
        val remaining = (limit - spent).coerceAtLeast(0L)
        // 唯一用 Float 的地方是「金额 -> 绘图比例」，与 ChartData.sectors 同理。
        val ratio = if (limit <= 0L) 0f
        else (spent.toFloat() / limit.toFloat()).coerceAtLeast(0f)
        return BudgetProgress(limit, spent, remaining, over, ratio, over > 0L)
    }
}
