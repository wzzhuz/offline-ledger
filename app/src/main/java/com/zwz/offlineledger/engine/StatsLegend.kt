package com.zwz.offlineledger.engine

/**
 * 第 68 批：统计页「分类占比与排行合体卡」图例列表的纯 JVM 数据准备层。
 *
 * 与 ChartData 同样的金额纪律：金额求和 / 比较全是 Long 分，
 * 唯一用 Float 的地方是「占比 → 百分比文本」的显示换算，
 * 结果只进 Text，MUST NOT 回写金额、MUST NOT 参与统计或扇区角度。
 *
 * 一羽式图例每行 = 图标 + 名称 + 百分比 + 金额；
 * 分类维度 ChartSlice 无 count，本批 MUST NOT 伪造「(N条)」笔数
 * （新增分类笔数聚合会撞基线「既有聚合 SQL 不改」）。
 */
object StatsLegend {

    data class LegendRow(
        val index: Int,
        val id: Long?,
        val name: String,
        val cents: Long,
        val percent: Float,
        val percentText: String
    )

    /** 占比只用于显示与进度条；总量 <= 0 时一律 0。 */
    fun percent(cents: Long, totalCents: Long): Float =
        if (totalCents <= 0L || cents <= 0L) 0f
        else (cents.toFloat() / totalCents.toFloat()).coerceIn(0f, 1f)

    /** 一羽式两位小数百分比，固定 Locale.US 避免阿拉伯数字/逗号分隔符差异。 */
    fun percentText(cents: Long, totalCents: Long): String {
        val p = percent(cents, totalCents) * 100f
        return java.lang.String.format(java.util.Locale.US, "%.2f%%", p)
    }

    /** 顺序与 ChartData.sectors 完全一致，MUST NOT 在此重新排序。 */
    fun rows(slices: List<ChartData.ChartSlice>, totalCents: Long): List<LegendRow> =
        slices.mapIndexed { i, s ->
            LegendRow(
                index = i,
                id = s.id,
                name = s.name,
                cents = s.cents,
                percent = percent(s.cents, totalCents),
                percentText = percentText(s.cents, totalCents)
            )
        }
}
