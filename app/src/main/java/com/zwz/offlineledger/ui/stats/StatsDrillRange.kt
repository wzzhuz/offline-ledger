package com.zwz.offlineledger.ui.stats

import com.zwz.offlineledger.core.time.MonthRange

/**
 * 第 44 批：统计页下钻带进明细的周期快照，单一出口。
 *
 * 第 42 批把「不带时间 = 全部记录」设成全局默认时，三个跨 Tab 下钻工厂
 * 也跟着写成 allMonths = true，把统计页正在查看的周期抹掉了。
 * 这里在下钻瞬间把周期固化成 fromDate/toDate（YYYYMMDD），
 * 交给既有 (:fromDate IS NULL OR ...) 参数化短路，零新增 SQL 出口。
 */
data class StatsDrillRange(
    val yearMonth: Int,
    val fromDate: Int,
    val toDate: Int,
    val label: String
) {
    companion object {
        fun of(mode: StatsMode, yearMonth: Int, year: Int): StatsDrillRange {
            val (fromYm, toYm) = when (mode) {
                StatsMode.MONTH -> yearMonth to yearMonth
                StatsMode.YEAR -> (year * 100 + 1) to (year * 100 + 12)
                StatsMode.MULTI_YEAR -> ((year - 4) * 100 + 1) to (year * 100 + 12)
            }
            val label = when (mode) {
                StatsMode.MONTH -> MonthRange.label(yearMonth)
                StatsMode.YEAR -> year.toString() + " 年"
                StatsMode.MULTI_YEAR -> (year - 4).toString() + "–" + year + " 年"
            }
            return StatsDrillRange(
                yearMonth = yearMonth,
                fromDate = MonthRange.of(fromYm).first,
                toDate = MonthRange.of(toYm).second,
                label = label
            )
        }
    }
}
