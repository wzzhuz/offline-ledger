package com.zwz.offlineledger.engine

import com.zwz.offlineledger.core.time.LedgerClock
import com.zwz.offlineledger.data.model.Cadence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringScheduleTest {

    private fun due(
        cadence: Cadence, anchor: Int, last: Int?, today: Int,
        dom: Int = 1, dow: Int = 1, max: Int = 400
    ) = RecurringSchedule.dueDates(cadence, dom, dow, anchor, last, today, max)

    @Test
    fun monthly_first_day_generates_each_month() {
        val d = due(Cadence.MONTHLY, 20260101, null, 20260401)
        assertEquals(listOf(20260101, 20260201, 20260301, 20260401), d)
    }

    @Test
    fun monthly_is_idempotent_after_last_generated() {
        val d = due(Cadence.MONTHLY, 20260101, 20260401, 20260401)
        assertTrue(d.isEmpty())
    }

    @Test
    fun monthly_31st_falls_back_to_last_day_of_february() {
        val d = due(Cadence.MONTHLY, 20260131, null, 20260228, dom = 31)
        assertEquals(listOf(20260131, 20260228), d)
    }

    @Test
    fun weekly_matches_iso_day_of_week() {
        // 2026-01-05 是周一
        val d = due(Cadence.WEEKLY, 20260105, null, 20260119, dow = 1)
        assertEquals(listOf(20260105, 20260112, 20260119), d)
    }

    @Test
    fun daily_generates_every_day() {
        val d = due(Cadence.DAILY, 20260101, null, 20260103)
        assertEquals(listOf(20260101, 20260102, 20260103), d)
    }

    @Test
    fun future_anchor_produces_nothing() {
        assertTrue(due(Cadence.MONTHLY, 20270101, null, 20260101).isEmpty())
    }

    @Test
    fun catch_up_is_capped() {
        // 默认窗口放宽到 400 天是为了让其它用例不被截断；
        // 本用例显式传 60，验证「严格 60 天、含 today」。
        val d = due(Cadence.DAILY, 20200101, null, 20261113, max = 60)
        assertEquals(60, d.size)
        assertEquals(20261113, d.last())
    }

    @Test
    fun epoch_roundtrip_uses_hong_kong_noon() {
        val ts = RecurringSchedule.toEpochMilli(20260615)
        assertEquals(20260615, LedgerClock.fromEpochMilli(ts).localDate)
        assertEquals(202606, RecurringSchedule.yearMonthOf(20260615))
    }
}
