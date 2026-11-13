package com.zwz.offlineledger.engine

import com.zwz.offlineledger.core.time.LedgerClock
import com.zwz.offlineledger.data.model.Cadence
import java.time.LocalDate

/**
 * 周期到期日计算，纯 JVM（不依赖 Android / Room），单测可直接跑。
 *
 * 用 java.time.LocalDate（API 26 原生支持），避免自写日期运算出错。
 * 60 天 catch-up 上限是刻意的：用户三年没打开 App 时，
 * 不能一次生成上千条把冷启动卡死。
 */
object RecurringSchedule {

    const val MAX_CATCH_UP_DAYS = 60

    fun dueDates(
        cadence: Cadence,
        dayOfMonth: Int,
        dayOfWeek: Int,
        anchorLocalDate: Int,
        lastGeneratedLocalDate: Int?,
        todayLocalDate: Int,
        maxCatchUpDays: Int = MAX_CATCH_UP_DAYS
    ): List<Int> {
        val today = parse(todayLocalDate)
        val anchor = parse(anchorLocalDate)
        if (today.isBefore(anchor)) return emptyList()

        var cursor = if (lastGeneratedLocalDate != null) {
            parse(lastGeneratedLocalDate).plusDays(1)
        } else {
            anchor
        }
        // 含 today 在内的窗口恰好是 maxCatchUpDays 天，
        // 否则 minusDays(N) 会生成 N+1 条（含两端），与「60 天上限」语义不符。
        val floor = today.minusDays((maxCatchUpDays.coerceAtLeast(1) - 1).toLong())
        if (cursor.isBefore(floor)) cursor = floor

        val out = ArrayList<Int>()
        while (!cursor.isAfter(today)) {
            if (matches(cadence, dayOfMonth, dayOfWeek, cursor)) {
                out.add(toLocalDateInt(cursor))
            }
            cursor = cursor.plusDays(1)
        }
        return out
    }

    private fun matches(cadence: Cadence, dayOfMonth: Int, dayOfWeek: Int, date: LocalDate): Boolean =
        when (cadence) {
            Cadence.DAILY -> true
            Cadence.WEEKLY -> date.dayOfWeek.value == dayOfWeek.coerceIn(1, 7)
            Cadence.MONTHLY -> {
                val wanted = dayOfMonth.coerceAtLeast(1)
                date.dayOfMonth == minOf(wanted, date.lengthOfMonth())
            }
        }

    /** YYYYMMDD -> LocalDate。非法输入交给调用方的事务回滚，不静默吞掉。 */
    fun parse(v: Int): LocalDate = LocalDate.of(v / 10000, (v / 100) % 100, v % 100)

    fun toLocalDateInt(date: LocalDate): Int =
        date.year * 10000 + date.monthValue * 100 + date.dayOfMonth

    fun yearMonthOf(localDate: Int): Int = localDate / 100

    /** 正午 12:00，避开 DST / 零点边界导致的串日；按 Asia/Hong_Kong 口径。 */
    fun toEpochMilli(localDate: Int): Long =
        parse(localDate).atTime(12, 0).atZone(LedgerClock.zoneId)
            .toInstant().toEpochMilli()
}
