package com.zwz.offlineledger.engine

import kotlin.math.abs

/**
 * 对账与资产展示的唯一计算出口（纯 JVM，可单测）。
 *
 * 刻意不依赖 Room / Android SDK：差额、阈值、展示聚合值、金额反查区间
 * 全在这里，UI 与 DAO 都不各自心算。
 */
object Reconciliation {

    /** 默认尾差阈值：1.00 元 = 100 分，比较含等号。
     * 阶段 5 已落地可配置：UI / ViewModel / Repository 三层读同一个偏好值，
     * 本常量只作为未设置偏好时的默认值。 */
    const val TAIL_DIFFERENCE_THRESHOLD_CENTS = 100L

    /** 近似金额反查容差：±1.00 元。 */
    const val APPROX_TOLERANCE_CENTS = 100L

    /** 疑似「异常大额」阈值：1000.00 元。 */
    const val LARGE_AMOUNT_THRESHOLD_CENTS = 100_000L

    /** 疑似「短时重复」窗口：24 小时。 */
    const val DUPLICATE_WINDOW_MILLIS = 86_400_000L

    /** 差额 = 实际 − 账面，可正可负，单位分。 */
    fun difference(bookCents: Long, actualCents: Long): Long = actualCents - bookCents

    fun isTailDifference(
        deltaCents: Long,
        thresholdCents: Long = TAIL_DIFFERENCE_THRESHOLD_CENTS
    ): Boolean = abs(deltaCents) <= thresholdCents

    /**
     * 可用额度 = 额度 + 余额。余额欠款时为负，MUST NOT 写成 额度 − 余额。
     * 无额度返回 null，UI 不展示该项。
     */
    fun availableCredit(creditLimitCents: Long?, balanceCents: Long): Long? =
        creditLimitCents?.let { it + balanceCents }

    // ---------------------------------------------------------------
    /** 展示聚合值的输入：刻意不依赖 Room Entity，纯 JVM 可测。 */
    data class AccountTotalsInput(
        val balanceCents: Long,
        val isLiability: Boolean,
        val includeInTotal: Boolean
    )

    data class DisplayTotals(
        val netWorthCents: Long,
        val totalAssetsCents: Long,
        val totalLiabilitiesCents: Long
    )

    /**
     * 三个数各自独立聚合：净资产是 SUM(includeInTotal)，
     * 绝不由 totalAssets − totalLiabilities 倒推（带符号余额下会凭空虚高）。
     */
    fun displayTotals(accounts: List<AccountTotalsInput>): DisplayTotals {
        val included = accounts.filter { it.includeInTotal }
        val liabilitySum = included.filter { it.isLiability }.sumOf { it.balanceCents }
        return DisplayTotals(
            netWorthCents = included.sumOf { it.balanceCents },
            totalAssetsCents = included.filter { !it.isLiability }.sumOf { it.balanceCents },
            totalLiabilitiesCents = abs(liabilitySum)
        )
    }

    // ---------------------------------------------------------------
    enum class LookupMode { EXACT, RANGE, APPROX }

    /**
     * 三种反查模式统一折算成一对绑定参数，交给同一条 SQL，不拼字符串。
     * 下界 clamp 到 0（金额恒正）。
     */
    fun amountLookupBounds(
        amountCents: Long,
        mode: LookupMode,
        toleranceCents: Long = APPROX_TOLERANCE_CENTS,
        rangeMinCents: Long? = null,
        rangeMaxCents: Long? = null
    ): Pair<Long?, Long?> = when (mode) {
        LookupMode.EXACT -> amountCents to amountCents
        LookupMode.APPROX ->
            (amountCents - toleranceCents).coerceAtLeast(0L) to amountCents + toleranceCents
        LookupMode.RANGE -> rangeMinCents to rangeMaxCents
    }

    // ---------------------------------------------------------------
    /** 疑似标记位掩码：前三位由 SQL CASE 算出，第四位在已限定结果集上补算。 */
    object SuspectFlags {
        const val ZERO_AMOUNT = 1
        const val UNCATEGORIZED = 2
        const val LARGE_AMOUNT = 4
        const val NEAR_DUPLICATE = 8
    }

    fun hasFlag(flags: Int, bit: Int): Boolean = flags and bit != 0

    fun labels(flags: Int): List<String> = buildList {
        if (hasFlag(flags, SuspectFlags.ZERO_AMOUNT)) add("金额为 0")
        if (hasFlag(flags, SuspectFlags.UNCATEGORIZED)) add("未分类")
        if (hasFlag(flags, SuspectFlags.LARGE_AMOUNT)) add("异常大额")
        if (hasFlag(flags, SuspectFlags.NEAR_DUPLICATE)) add("疑似重复")
    }

    /**
     * 在**已由 SQL 限定**（单账户 + 锚点后 + LIMIT）的结果集上补算「24h 内同额重复」。
     * 返回 id -> 是否重复。不是全表加载：入参最多几百行。
     */
    fun markNearDuplicates(rows: List<ReviewItem>): Map<Long, Boolean> {
        val groups = rows.groupBy { it.amountCents }
        val duplicated = mutableSetOf<Long>()
        for ((_, group) in groups) {
            if (group.size < 2) continue
            val sorted = group.sortedBy { it.timestamp }
            for (i in 1 until sorted.size) {
                if (sorted[i].timestamp - sorted[i - 1].timestamp <= DUPLICATE_WINDOW_MILLIS) {
                    duplicated.add(sorted[i - 1].id)
                    duplicated.add(sorted[i].id)
                }
            }
        }
        return rows.associate { it.id to (it.id in duplicated) }
    }

    /** markNearDuplicates 只需要这四个字段，与 UI 的 ReviewRow 解耦。 */
    data class ReviewItem(
        val id: Long,
        val amountCents: Long,
        val timestamp: Long
    )
}
