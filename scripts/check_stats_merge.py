#!/usr/bin/env python3
"""第 69 批：统计页修订的静态校验（撤回蓝底/混卡/跨Tab + 全局配色 + 页内 sheet）。

纯文本校验，不依赖 Android SDK / Gradle。重点防止：
1) 第 68 批跑偏的三处又以别的形式回来；
2) 删跨 Tab 回调时漏改 LedgerApp；
3) Q4B 新 SQL 漏唯一口径 / LIMIT；
4) Q5B 改主题时顺手改掉红绿语义或反转开关。
"""
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/java/com/zwz/offlineledger"
BASE = ROOT / "openspec/specs"
DELTA = ROOT / "openspec/changes/polish-stats-like-yiyu/specs"


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

    # 2. 分类占比卡只做分类 + 左上角单标签
    for key in ("CategoryShareCard(", "OtherRankCard(", '"按大类 ⇄"', '"按小类 ⇄"',
                '"收款方 ⇄"', '"账户 ⇄"', "分类管理 ›", "合计不一致"):
        if key not in stats:
            errors.append("StatsScreen 缺第 69 批要素: " + key)
    for key in ("CategoryInsightCard", "RankInsightCard", "RankDim",
                "expandableIds", "onChildClick"):
        if key in stats or key in donut:
            errors.append("仍残留三维度混卡 / ⌄展开: " + key)
    if "OtherDim" not in stats:
        errors.append("缺 OtherDim 维度枚举")

    # 3. 默认折叠 + 只读
    if "var expanded by rememberSaveable { mutableStateOf(false) }" not in stats:
        errors.append("收款方/账户卡不是默认折叠")
    if "只读展示" not in stats:
        errors.append("收款方/账户卡未标注只读")

    # 4. 灰色长条（Q2A）
    for key in ("0xFFEDEFF5", "CategoryHeaderBlue.copy(alpha = 0.75f)", "fillMaxWidth(row.percent)"):
        if key not in donut:
            errors.append("DonutChart 缺灰条: " + key)
    if "heightIn(min = 44.dp)" not in donut:
        errors.append("图例整行触摸区未保持 >=40dp")

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

    # 8. Q5B 全局清新配色 + 红绿语义零改动
    for key in ("0xFFF4F6FB", "0xFFFFFFFF", "0xFFEEF1F8", "0xFF1F2430",
                "0xFFDDE2EE", "0xFF5B7CFA", "surfaceContainerLow = Color"):
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
    if "version = 7" not in db:
        errors.append("AppDatabase.version 不再是 7（本批零改表）")
    if "emptyArray()" not in mig:
        errors.append("Migrations.ALL 不再为空")
    for key in ("import androidx.compose.foundation.layout.FlowRow",
                "import androidx.compose.ui.text.TextMeasurer",
                "import androidx.compose.foundation.layout.BoxWithConstraints"):
        if key in donut:
            errors.append("DonutChart 引入实验性 API: " + key)

    # 10. 8 条 MODIFIED 标题命中基线（整块替换锚点）
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

    if errors:
        for e in errors:
            print("  FAIL", e)
        print(f"check_stats_merge: {len(errors)} 项失败")
        return 1
    print("check_stats_merge: PASS (撤回蓝底/混卡/跨Tab / 只分类 / 灰条 / 折叠只读卡 / 页内 sheet / 全局配色 / 零改表 / 8 MODIFIED)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
