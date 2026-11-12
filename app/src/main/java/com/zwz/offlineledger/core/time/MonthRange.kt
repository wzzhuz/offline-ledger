package com.zwz.offlineledger.core.time

import java.time.YearMonth

/** YYYYMM 与 YYYYMMDD 的换算，按自然月，不依赖设备当前时区。 */
object MonthRange {

    fun of(yearMonth: Int): Pair<Int, Int> {
        val y = yearMonth / 100
        val m = yearMonth % 100
        require(m in 1..12) { "非法年月：$yearMonth" }
        val lastDay = YearMonth.of(y, m).lengthOfMonth()
        return (yearMonth * 100 + 1) to (yearMonth * 100 + lastDay)
    }

    fun shift(yearMonth: Int, deltaMonths: Int): Int {
        val ym = YearMonth.of(yearMonth / 100, yearMonth % 100)
            .plusMonths(deltaMonths.toLong())
        return ym.year * 100 + ym.monthValue
    }

    fun label(yearMonth: Int): String =
        "%04d-%02d".format(yearMonth / 100, yearMonth % 100)

    /** 20261112 -> 2026-11-12 */
    fun dateLabel(localDate: Int): String =
        "%04d-%02d-%02d".format(localDate / 10000, (localDate / 100) % 100, localDate % 100)

    fun currentYearMonth(): Int =
        LedgerClock.fromEpochMilli(System.currentTimeMillis()).yearMonth
}
