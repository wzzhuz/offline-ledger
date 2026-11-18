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

    /**
     * 第 4 批：折线图常驻数值标注的点集合。
     * 竖屏只标峰值 / 末点 / 当前选中点（31 天或 12 月全标会糊成一团），
     * 横屏全屏页 annotateAll 全量标。纯 JVM，金额换算不在这里做。
     */
    fun annotationIndices(
        values: List<Float>,
        selectedIndex: Int?,
        annotateAll: Boolean
    ): Set<Int> {
        if (values.isEmpty()) return emptySet()
        if (annotateAll) return values.indices.toSet()
        val out = LinkedHashSet<Int>()
        val peak = values.indices.maxByOrNull { values[it] } ?: -1
        if (peak >= 0 && values[peak] > 0f) out.add(peak)
        out.add(values.lastIndex)
        selectedIndex?.let { if (it in values.indices) out.add(it) }
        return out
    }


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

    /** 第 23 批：多年视图的逐年汇总点。 */
    data class YearPoint(val year: Int, val incomeCents: Long, val expenseCents: Long)

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

    /** 第 23 批：多年视图固定「近 5 年」，缺年补 0，不按有流水的年数折算。 */
    fun fillYearlySeries(endYear: Int, years: Int, rows: List<YearPoint>): List<YearPoint> {
        val byYear = rows.associateBy { it.year }
        return (endYear - years + 1..endYear).map { y ->
            byYear[y] ?: YearPoint(y, 0L, 0L)
        }
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

    /** 多系列共用一个最大值，仅在显式选择「两者」对比时使用。 */
    fun seriesMax(series: List<List<Long>>): Long =
        series.flatten().maxOrNull() ?: 0L

    /**
     * 第三批：单线模式 MUST 用该系列自己的最大值缩放。
     * 之前支出与收入共用一个 max，收入量级一大支出就贴底看不出变化。
     */
    fun seriesMaxSingle(values: List<Long>): Long =
        (values.maxOrNull() ?: 0L).coerceAtLeast(0L)

    /**
     * 第 23 批：净结余线（收入 - 支出）允许负值。
     * 以绝对值的最大值为半轴，把 [-max, +max] 映射到 [0, 1]，0 落在正中。
     */
    fun seriesMaxAbsSigned(values: List<Long>): Long =
        (values.maxOfOrNull { abs(it) } ?: 0L).coerceAtLeast(1L)

    fun normalizeSigned(values: List<Long>, maxAbs: Long): List<Float> =
        values.map { ((it.toFloat() / maxAbs.toFloat()) + 1f) / 2f }

    /** 有符号竖轴 5 个刻度：+max / +max/2 / 0 / -max/2 / -max。 */
    fun yAxisLabelsSigned(maxAbs: Long): List<String> =
        (4 downTo 0).map { i ->
            java.math.BigDecimal.valueOf(maxAbs * (i - 2).toLong() / 2L, 2).toPlainString()
        }

    /** 竖轴 5 个刻度（元，两位小数）；纯 JVM 可单测，不做金额运算。 */
    fun yAxisLabels(maxCents: Long): List<String> {
        if (maxCents <= 0L) return List(5) { "0.00" }
        return (4 downTo 0).map { i ->
            java.math.BigDecimal.valueOf(maxCents * i.toLong() / 4L, 2).toPlainString()
        }
    }

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
     * 第 23 批：图表专用低饱和同色系色板。
     * 分类标识（列表 / chip）仍保持中性单色圆，图表扇区才需要区分度；
     * 自动生成的分类 colorHex 恒为 #888888，直接读它会让所有扇区一个颜色。
     * 按分类 id 取模分配，MUST NOT 按金额排名索引分配 —— 否则每次占比变化就变色。
     */
    private val CHART_PALETTE = longArrayOf(
        0xFF3F6C8BL, 0xFF5B8AA6L, 0xFF77A6B6L, 0xFF94BFC7L,
        0xFF6E8F7BL, 0xFF8FAE91L, 0xFFB3C9A9L, 0xFF7A7FA8L,
        0xFF9C9BC0L, 0xFFB98F7BL
    )

    fun chartArgb(id: Long?, index: Int): Long {
        val seed = id ?: index.toLong()
        val k = ((kotlin.math.abs(seed) % CHART_PALETTE.size).toInt()
                + CHART_PALETTE.size) % CHART_PALETTE.size
        return CHART_PALETTE[k]
    }

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
