package com.zwz.offlineledger.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetEngineTest {

    @Test
    fun under_budget() {
        val p = BudgetEngine.progress(100000L, 80000L)
        assertEquals(100000L, p.limitCents)
        assertEquals(80000L, p.spentCents)
        assertEquals(20000L, p.remainingCents)
        assertEquals(0L, p.overCents)
        assertFalse(p.isOver)
        assertEquals(0.8f, p.usedRatio, 0.0001f)
    }

    @Test
    fun exactly_at_limit_is_not_over() {
        val p = BudgetEngine.progress(100000L, 100000L)
        assertFalse(p.isOver)
        assertEquals(0L, p.remainingCents)
        assertEquals(0L, p.overCents)
    }

    @Test
    fun over_budget() {
        val p = BudgetEngine.progress(100000L, 120000L)
        assertTrue(p.isOver)
        assertEquals(20000L, p.overCents)
        assertEquals(0L, p.remainingCents)
    }

    @Test
    fun zero_limit_does_not_divide_by_zero() {
        val p = BudgetEngine.progress(0L, 500L)
        assertEquals(0f, p.usedRatio, 0.0001f)
        assertTrue(p.isOver)
    }

    @Test
    fun negative_inputs_are_clamped() {
        val p = BudgetEngine.progress(-1L, -5L)
        assertEquals(0L, p.limitCents)
        assertEquals(0L, p.spentCents)
        assertFalse(p.isOver)
    }
}
