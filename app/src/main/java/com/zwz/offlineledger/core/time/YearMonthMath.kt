package com.zwz.offlineledger.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/**
 * 第 41 批：YearMonthPicker 的纯 JVM 数学层。
 * Compose Material 3 没有独立的年月选择器，但官方 DatePickerState 的
 * displayedMonthMillis 可写；这里只负责「跳到哪一个月」的换算，
 * 日视图、闰年、无障碍全部继续由官方 DatePicker 负责。
 * 零第三方依赖、零自造动画。
 */
object YearMonthMath {

    /** 一次可见 12 格，不靠左右箭头翻页。 */
    fun monthsOf(year: Int): List<Int> = (1..12).map { year * 100 + it }

    /** 1 月 31 日跳到 2 月时取 min(day, 该月天数)，不静默丢日期。 */
    fun clampDay(yearMonth: Int, day: Int): Int {
        val ym = YearMonth.of(yearMonth / 100, yearMonth % 100)
        return day.coerceIn(1, ym.lengthOfMonth())
    }

    /** 保留原「日」，把 YYYYMMDD 搬到目标年月。 */
    fun clampDateTo(yearMonth: Int, localDate: Int): Int =
        yearMonth * 100 + clampDay(yearMonth, localDate % 100)

    /** YYYYMM -> 该月 1 日 UTC 午夜毫秒，供 displayedMonthMillis 直接跳转。 */
    fun firstDayUtcMillis(yearMonth: Int): Long =
        LocalDate.of(yearMonth / 100, yearMonth % 100, 1)
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun utcMillisOf(localDate: Int): Long =
        LocalDate.of(localDate / 10000, (localDate / 100) % 100, localDate % 100)
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun dateIntOfUtcMillis(millis: Long): Int {
        val d = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
        return d.year * 10000 + d.monthValue * 100 + d.dayOfMonth
    }

    /** 年份直达列表：以今年为中心前后各 span 年，纯静态列表无动画。 */
    fun yearsAround(center: Int, span: Int = 12): List<Int> =
        (center - span..center + span).toList()

    fun label(yearMonth: Int): String = MonthRange.label(yearMonth)
}
