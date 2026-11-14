package com.zwz.offlineledger.engine

import com.zwz.offlineledger.core.time.MonthRange
import kotlin.math.abs

/**
 * 图表数据准备层（纯 JVM，全部可单测）。
 *
 * ⚠️ 金额边界：金额的**存储、求和、比较、差额**全是 Long 分。
 * 唯一用 Float 的地方是「金额比例 → Canvas 绘制角度 / 归一化坐标」的
 * 几何换算，结果只进 DrawScope，MUST NOT 回写任何金额或参与统计。
 */
object ChartData {

    data class ChartSlice(
        val id: Long?,
        val name: String,
        val colorHex: String?,
        val cents: Long
    )

    /** startAngle 从 -90°（12 点方向）起，顺时针；与 DrawScope.drawArc 一致。 */
    data class ChartSector(
        val slice: ChartSlice,
        val startAngle: Float,
        val sweepAngle: Float
    )

    data class DailyPoint(val day: Int, val expenseCents: Long, val incomeCents: Long)

    data class MonthPoint(val month: Int, val incomeCents: Long, val expenseCents: Long)

    data class TotalsCheck(
        val rootCents: Long,
        val childCents: Long,
        val totalCents: Long,
        val consistent: Boolean,
        val driftCents: Long
    )

    // ---------------------------------------------------------------
    /** 一级合计 = 二级合计 = 总支出；三个口径全部用 Long 累加。 */
    fun checkTotals(
        root: List<ChartSlice>,
        child: List<ChartSlice>,
        totalCents: Long
    ): TotalsCheck {
        val r = root.sumOf { it.cents }
        val c = child.sumOf { it.cents }
        val drift = maxOf(abs(r - c), abs(r - totalCents), abs(c - totalCents))
        return TotalsCheck(
            rootCents = r,
            childCents = c,
            totalCents = totalCents,
            consistent = r == c && c == totalCents,
            driftCents = drift
        )
    }

    /**
     * 阶段 6b：毛支出切片按分类 id 减去 SETTLEMENT 合计，得到净支出切片。
     * 纯展示层减法 —— 既有 6 条聚合 SQL 与 StatRules 常量一行不动。
     * 净额 <= 0 的分类不绘制扇区（报销多于支出时双保险钳制）。
     */
    fun netSlices(expense: List<ChartSlice>, settlements: Map<Long, Long>): List<ChartSlice> =
        expense.map { s ->
            val refund = s.id?.let { settlements[it] } ?: 0L
            s.copy(cents = (s.cents - refund).coerceAtLeast(0L))
        }.filter { it.cents > 0L }

    /** 按自然月天数补齐 1..N；缺日为 0，不依赖设备时区。 */
    fun fillDailySeries(yearMonth: Int, rows: List<DailyPoint>): List<DailyPoint> {
        val (_, last) = MonthRange.of(yearMonth)
        val days = last % 100
        val byDay = rows.associateBy { it.day }
        return (1..days).map { d -> byDay[d] ?: DailyPoint(d, 0L, 0L) }
    }

    /** 年度固定 12 个月；空月补 0，避免折线断线或缺月。 */
    fun fillYearSeries(year: Int, rows: List<MonthPoint>): List<MonthPoint> {
        val byMonth = rows.associateBy { it.month }
        return (1..12).map { m -> byMonth[m] ?: MonthPoint(m, 0L, 0L) }
    }

    // ---------------------------------------------------------------
    /** 金额 → 扇区角度。总和 <= 0 或单项 <= 0 的扇区不绘制。 */
    fun sectors(slices: List<ChartSlice>): List<ChartSector> {
        val total = slices.sumOf { it.cents }
        if (total <= 0L) return emptyList()
        var cursor = -90f
        return slices.filter { it.cents > 0L }.map { s ->
            val sweep = 360f * s.cents.toFloat() / total.toFloat()
            val sector = ChartSector(s, cursor, sweep)
            cursor += sweep
            sector
        }
    }

    /** 角度归一化到 [0, 360)。 */
    fun normalizeAngle(deg: Float): Float {
        var a = deg % 360f
        if (a < 0f) a += 360f
        return a
    }

    /**
     * 点击角度落在哪个扇区。扇区可能跨 0°，因此分两种区间判断。
     * 入参角度与 drawArc 一致（0° = 3 点方向，顺时针为正）。
     */
    fun sectorHit(sectors: List<ChartSector>, clickAngleDeg: Float): Int? {
        if (sectors.isEmpty()) return null
        val a = normalizeAngle(clickAngleDeg)
        sectors.forEachIndexed { i, s ->
            val start = normalizeAngle(s.startAngle)
            val end = start + s.sweepAngle
            if (end <= 360f) {
                if (a >= start && a < end) return i
            } else {
                if (a >= start || a < end - 360f) return i
            }
        }
        return null
    }

    // ---------------------------------------------------------------
    /**
     * 阶段 8：年初基准 + 逐月净变动 = 逐月末资产余额。
     * 纯 JVM 累计，不查库、不混算；校准记录不进资产线（见 design.md）。
     */
    fun assetSeries(yearStartBalanceCents: Long, deltas: Map<Int, Long>): List<Long> {
        var cursor = yearStartBalanceCents
        return (1..12).map { m ->
            cursor += deltas[m] ?: 0L
            cursor
        }
    }

    /** 多系列共用一个最大值，避免收入线与支出线各自缩放导致走势误读。 */
    fun seriesMax(series: List<List<Long>>): Long =
        series.flatten().maxOrNull() ?: 0L

    /** Long 分 → 绘图用 0..1 比例；全 0 或负数一律 0。 */
    fun normalize(values: List<Long>, maxCents: Long): List<Float> {
        if (maxCents <= 0L) return values.map { 0f }
        return values.map { it.coerceAtLeast(0L).toFloat() / maxCents.toFloat() }
    }

    // ---------------------------------------------------------------
    /** 无 colorHex 或解析失败时按索引取一个稳定颜色（ARGB Long）。 */
    private val PALETTE = longArrayOf(
        0xFFC62828L, 0xFF1565C0L, 0xFF2E7D32L, 0xFFEF6C00L,
        0xFF6A1B9AL, 0xFF00838FL, 0xFFAD1457L, 0xFF5D4037L
    )

    fun fallbackArgb(index: Int): Long =
        PALETTE[((index % PALETTE.size) + PALETTE.size) % PALETTE.size]

    /** 分类颜色统一入口：优先 colorHex，失败回退调色板。 */
    fun sliceArgb(hex: String?, index: Int): Long = parseHexArgb(hex) ?: fallbackArgb(index)

    // ---------------------------------------------------------------
    /**
     * 手写 #RRGGBB → ARGB Long。
     * 刻意不用 android.graphics.Color.parseColor：图表数据层要能纯 JVM 单测。
     */
    fun parseHexArgb(hex: String?): Long? {
        if (hex == null) return null
        val h = hex.trim().removePrefix("#")
        if (h.length != 6) return null
        return try {
            0xFF000000L or h.toLong(16)
        } catch (e: NumberFormatException) {
            null
        }
    }
}
