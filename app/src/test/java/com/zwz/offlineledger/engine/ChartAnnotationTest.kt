package com.zwz.offlineledger.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartAnnotationTest {

    @Test
    fun emptySeriesHasNoLabels() {
        assertTrue(ChartData.annotationIndices(emptyList(), null, false).isEmpty())
    }

    @Test
    fun verticalScreenMarksPeakLastAndSelected() {
        val values = listOf(0.1f, 0.9f, 0.4f, 0.2f)
        val marked = ChartData.annotationIndices(values, 2, false)
        assertTrue(marked.contains(1))
        assertTrue(marked.contains(3))
        assertTrue(marked.contains(2))
        assertFalse(marked.contains(0))
    }

    @Test
    fun allZeroSeriesOnlyMarksLast() {
        val values = listOf(0f, 0f, 0f)
        val marked = ChartData.annotationIndices(values, null, false)
        assertEquals(setOf(2), marked)
    }

    @Test
    fun fullscreenMarksEveryPoint() {
        val values = listOf(0.1f, 0.5f, 0.3f, 0.7f, 0.2f)
        val marked = ChartData.annotationIndices(values, null, true)
        assertEquals(setOf(0, 1, 2, 3, 4), marked)
    }

    @Test
    fun outOfRangeSelectedIsIgnored() {
        val values = listOf(0.2f, 0.8f)
        val marked = ChartData.annotationIndices(values, 99, false)
        assertFalse(marked.contains(99))
        assertTrue(marked.contains(1))
    }
}
