#!/usr/bin/env python3
"""第 69 批：统计页修订的静态校验（撤回蓝底/混卡/跨Tab + 全局配色 + 页内 sheet）。

纯文本校验，不依赖 Android SDK / Gradle。重点防止：
1) 第 68 批跑偏的三处又以别的形式回来；
2) 删跨 Tab 回调时漏改 LedgerApp；
3) Q4B 新 SQL 漏唯一口径 / LIMIT；
4) Q5B 改主题时顺手改掉红绿语义或反转开关。
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/java/com/zwz/offlineledger"
BASE = ROOT / "openspec/specs"
# 第 73 批归档后 change 目录已移入 archive；优先活跃目录，
# 否则读归档副本，保证同一套断言在归档前后都能跑。
_ACTIVE = ROOT / "openspec/changes/polish-stats-like-yiyu/specs"
_ARCHIVED = ROOT / "openspec/changes/archive/2026-11-25-polish-stats-like-yiyu/specs"
DELTA = _ACTIVE if _ACTIVE.exists() else _ARCHIVED


def read(p):
    return (SRC / p).read_text(encoding="utf-8")


def main():
    errors = []
    stats = read("ui/stats/StatsScreen.kt")
    donut = read("ui/charts/DonutChart.kt")
    sheet = read("ui/stats/DrillDownDialog.kt")
    theme = read("ui/theme/LedgerTheme.kt")
    ledger = read("ui/LedgerApp.kt")
    dao = read("data/local/dao/RecordDao.kt")
    vm = read("ui/stats/StatsViewModel.kt")
    db = read("data/local/AppDatabase.kt")
    mig = read("data/local/Migrations.kt")

    # 1. 撤回蓝底 / 双 Tab
    for key in ("StatsHeader(", "TabRow(", '"分析"', '"概览"', "statsTab"):
        if key in stats:
            errors.append("StatsScreen 仍残留第 68 批蓝底/双Tab: " + key)
    if 'Text(text = "统计"' not in stats:
        errors.append("StatsScreen 缺白底「统计」标题")
    if "PeriodSwitcher(state, vm)" not in stats or "CurrencySwitcher(state, vm)" not in stats:
        errors.append("StatsScreen 缺既有周期 / 币种切换")

    # 2. 分类占比卡只做分类 + 第 70 批「文本 + 独立图标」标题
    for key in ("CategoryShareCard(", "OtherRankCard(", '"大类"', '"小类"',
                "SwitchLevelIcon()", "StatsCard {", '"收款方 ⇄"', '"账户 ⇄"',
                "分类管理 ›", "合计不一致"):
        if key not in stats:
            errors.append("StatsScreen 缺第 69/70 批要素: " + key)
    # 第 70 批：文本里 MUST NOT 再带 ⇄、MUST NOT 出现支出切换按钮或蓝底 Tab。
    for key in ('"按大类 ⇄"', '"按小类 ⇄"', '"支出 ⇌"', '"分析"', '"概览"',
                "StatsHeader(", "TabRow("):
        if key in stats:
            errors.append("第 70 批不加回项残留: " + key)
    for key in ("CategoryInsightCard", "RankInsightCard", "RankDim",
                "expandableIds", "onChildClick"):
        if key in stats or key in donut:
            errors.append("仍残留三维度混卡 / ⌄展开: " + key)
    if "OtherDim" not in stats:
        errors.append("缺 OtherDim 维度枚举")
    if "CardDefaults.cardElevation(defaultElevation = 0.dp)" not in stats:
        errors.append("StatsCard 未把 M3 tonalElevation 归零")
    if "CardDefaults.cardColors(containerColor = Color.White)" not in stats:
        errors.append("StatsCard 未显式纯白")

    # 3. 默认折叠 + 只读
    if "var expanded by rememberSaveable { mutableStateOf(false) }" not in stats:
        errors.append("收款方/账户卡不是默认折叠")
    if "只读展示" not in stats:
        errors.append("收款方/账户卡未标注只读")

    # 4. 灰色长条（Q2A）+ 第 70 批环外指示线
    for key in ("0xFFEDEFF5", "CategoryHeaderBlue.copy(alpha = 0.75f)", "fillMaxWidth(row.percent)"):
        if key not in donut:
            errors.append("DonutChart 缺灰条: " + key)
    if "heightIn(min = 44.dp)" not in donut:
        errors.append("图例整行触摸区未保持 >=40dp")
    for key in ("onSizeChanged", "GuideLine(", "RawGuide(", "drawLine(", "drawCircle(",
                "absoluteOffset", "StatsLegend.percentText", "endX", "0.60f"):
        if key not in donut:
            errors.append("DonutChart 缺指示线要素: " + key)
    # 第 72 批：径向折点 + 相邻间隔判据 + 不交错优先；MUST NOT 回到固定 x 竖线
    for key in ("radialR", "Offset(cx + radialR * c", "minAdjacent", "it.knee.y",
                "take(3)", "dropLast(1)", "Float.POSITIVE_INFINITY", "g.knee",
                "end = Offset(g.endX, g.y)", "g.y - 16.dp.toPx()",
                "g.anchor, end = g.knee"):
        if key not in donut:
            errors.append("DonutChart 缺第 72 批径向折点要素: " + key)
    for key in ("kneeX", "coerceIn(pad", "Float.NEGATIVE_INFINITY", "capacity",
                "maxOf(desired", "sortedBy { it.anchor.y }", "var y: Float", "p2"):
        if key in donut:
            errors.append("第 72 批已撤回的旧布局残留（会再次交错）: " + key)
    # 第 72 批：切换图标 MUST 是「小细淡」半箭头（4 条 drawLine）
    si = stats.split("private fun SwitchLevelIcon()", 1)
    if len(si) < 2:
        errors.append("缺 SwitchLevelIcon")
    else:
        seg = si[1][:900]
        n = seg.count("drawLine(")
        if n != 4:
            errors.append("SwitchLevelIcon 不是半箭头（drawLine 应为 4，实际 %d）" % n)
        for key in ("Modifier.size(14.dp)", "1.2f", "copy(alpha = 0.55f)",
                    "yu - tip", "yd + tip"):
            if key not in seg:
                errors.append("SwitchLevelIcon 缺第 72 批小细淡要素: " + key)
        for key in ("Modifier.size(20.dp)", "val sw = 2f",
                    "val c = CategoryHeaderBlue\n"):
            if key in seg:
                errors.append("SwitchLevelIcon 仍是上一版大/粗/饱和箭头: " + key)
    if "modifier = Modifier.size(24.dp)" not in stats:
        errors.append("切换按钮包围盒未收紧到 24dp")
    if "modifier = Modifier.size(32.dp)" in stats:
        errors.append("切换按钮包围盒仍是 32dp（大且突兀）")
    # 只查 import 与真实调用：注释里写「零 drawText / TextMeasurer」不算。
    for key in ("import androidx.compose.ui.text.TextMeasurer",
                "import androidx.compose.foundation.layout.FlowRow",
                "import androidx.compose.foundation.layout.BoxWithConstraints"):
        if key in donut:
            errors.append("DonutChart 引入禁用 import: " + key)
    for key in ("drawText(", "TextMeasurer(", "FlowRow(", "BoxWithConstraints("):
        if key in donut:
            errors.append("DonutChart 用了禁用文本测量/实验性 API: " + key)
    # 命中半径 MUST 与绘制半径同源
    if donut.count("0.60f") < 2:
        errors.append("绘制半径与点击命中半径未同源")

    # 5. Q4B 页内底部 sheet + 明细行样式
    for key in ("ModalBottomSheet", "StatsDrillSheet", "ExperimentalMaterial3Api",
                "DrillRecordRow", "accountLabel", "signedAmount",
                "TextDecoration.LineThrough", "优惠", "手续费"):
        if key not in sheet:
            errors.append("StatsDrillSheet 缺明细行要素: " + key)
    for key in ("DrillDownDialog(", '"查看该大类全部流水 ›"', 'Text("明细")'):
        if key in sheet:
            errors.append("sheet 仍残留跨 Tab 出口: " + key)
    if "state.drillCategoryId != null" not in stats or "StatsDrillSheet(state" not in stats:
        errors.append("StatsScreen 未接页内 sheet")

    # 6. Q6A 彻底解耦
    for key in ("onOpenCategoryInDetail", "onOpenCounterpartyInDetail",
                "onOpenAccountInDetail"):
        if key in ledger:
            errors.append("LedgerApp 仍传跨 Tab 回调: " + key)
    if "detailVm.filterBy" in ledger:
        errors.append("LedgerApp 仍向明细 VM 注入统计筛选")
    if "StatsScreen(" not in ledger or "refreshTick = refreshTick" not in ledger:
        errors.append("LedgerApp StatsScreen 调用形态异常")

    # 7. Q4B DAO 投影与 SQL 口径
    for key in ("fromAccountLast4", "toAccountLast4", "discountCents", "feeCents",
                "categoryColorHex", "counterparty", "description", "timestamp"):
        if ("val " + key) not in dao:
            errors.append("DrillRow 缺字段: " + key)
    if "drillDownRowsForRootCurrency" not in dao:
        errors.append("缺按大类下钻 SQL")
    for fn in ("drillDownRows", "drillDownRowsForCurrency", "drillDownRowsForRootCurrency"):
        i = dao.find("fun " + fn)
        if i < 0:
            errors.append("缺下钻查询 " + fn)
            continue
        q = dao[dao.rfind("@Query", 0, i):i]
        if "excludeFromStats = 0" not in q or "LIMIT :limit" not in q:
            errors.append("下钻查询丢唯一口径或 LIMIT: " + fn)
    if "openSliceRows" not in vm:
        errors.append("StatsViewModel 缺 openSliceRows")

    # 8. Q5B + 第 70 批中性灰底 / 纯白卡 + 红绿语义零改动
    for key in ("0xFFF2F3F7", "0xFFFFFFFF", "0xFFE9EBF1", "0xFF1F2430",
                "0xFFE2E4EB", "0xFF5B7CFA", "surfaceContainerLow = Color"):
        if key not in theme:
            errors.append("LedgerTheme 缺清新配色槽位: " + key)
    for key in ("applyReverseColors", "ExpenseRed = GREEN", "IncomeGreen = RED"):
        if key not in theme:
            errors.append("红绿语义 / 反转开关被改动: " + key)
    if "0xFFC62828" not in theme or "0xFF2E7D32" not in theme:
        errors.append("红绿基础色被改动")

    # 9. 零回归
    if "ChartData.netSlices" not in vm or "SUM(amountCents)" not in dao:
        errors.append("统计口径或既有聚合 SQL 被改动")
    # 第 69-74 批零改表锁 7；第 75 批升 8 为预期，不再硬编码单一值。
    m = re.search(r"version = (\d+)", db)
    if not m or int(m.group(1)) not in (7, 8):
        errors.append("AppDatabase.version 应为 7 或 8（第75批升表）")
    if "emptyArray()" not in mig:
        errors.append("Migrations.ALL 不再为空")
    for key in ("import androidx.compose.foundation.layout.FlowRow",
                "import androidx.compose.ui.text.TextMeasurer",
                "import androidx.compose.foundation.layout.BoxWithConstraints"):
        if key in donut:
            errors.append("DonutChart 引入实验性 API: " + key)

    # 10. 8 条 MODIFIED 标题命中主 spec（归档后已整块折叠），
    #     且归档副本仍在；顺带锁住第 72 批径向折点已进主 spec。
    titles = [
        "统计页落地月年切换与三种图表",
        "分类占比卡提供分类管理短跳转",
        "分类全量选择器采用分组 chips 且不用下拉框",
        "环形图支持一级与二级切换并落地三级下钻",
        "分类图标不做字符串映射",
        "分类管理彩色图标目录与缺项兜底",
        "三级下钻路径",
        "下钻明细复用组合筛选且不全量加载",
    ]
    base_text = "\n".join(f.read_text(encoding="utf-8") for f in BASE.rglob("spec.md"))
    delta_text = "\n".join(f.read_text(encoding="utf-8") for f in DELTA.rglob("spec.md"))
    for t in titles:
        if "### Requirement: " + t not in base_text:
            errors.append("MODIFIED 标题未命中基线: " + t)
        if "### Requirement: " + t not in delta_text:
            errors.append("delta 缺 MODIFIED: " + t)
    if "统计页蓝底身份区与分析概览双 Tab" in delta_text:
        errors.append("第 68 批蓝底 ADDED 未删除")
    if not DELTA.exists():
        errors.append("归档副本与活跃 delta 都不存在")
    for key in ("### Requirement: 分类占比卡采用一羽式图例列表、灰色长条与环外指示线",
                "径向折点", "最小相邻间隔", "14dp", "1.2f"):
        if key not in base_text:
            errors.append("主 spec 缺第 72 批折叠后的规格措辞: " + key)

    if errors:
        for e in errors:
            print("  FAIL", e)
        print(f"check_stats_merge: {len(errors)} 项失败")
        return 1
    print("check_stats_merge: PASS (第73批归档后 主spec已折叠 / 第72批 径向折点 / 小细淡半箭头 / 零改表 / 8 MODIFIED)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
