#!/usr/bin/env python3
"""第 68 批：统计页合体卡 / 彩色图标范围 / 零回归静态校验。

纯文本校验，不依赖 Android SDK / Gradle。重点防止「合并两张卡时顺手删掉
下钻契约四层」与「彩色图标越界到记一笔 / 明细 / 账户详情」（活文档 §7.12 / §2.6）。
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
    vm = read("ui/stats/StatsViewModel.kt")
    db = read("data/local/AppDatabase.kt")
    mig = read("data/local/Migrations.kt")
    dao = read("data/local/dao/RecordDao.kt")

    # 1. 合体：不再有独立排行卡，只有一个合体卡
    if "RankInsightCard" in stats:
        errors.append("StatsScreen 仍引用独立 RankInsightCard（应合为一张卡）")
    if "CategoryInsightCard(" not in stats:
        errors.append("StatsScreen 缺合体卡 CategoryInsightCard 调用")
    if "DonutInsightCard(" in stats:
        errors.append("StatsScreen 仍调用旧 DonutInsightCard")

    # 2. 蓝底身份区 + 双 Tab
    for key in ("StatsHeader(", "TabRow(", "CategoryHeaderBlue", '"分析"', '"概览"'):
        if key not in stats:
            errors.append("StatsScreen 缺蓝底/双 Tab 要素: " + key)
    if "statsTab == 0" not in stats or "statsTab == 1" not in stats:
        # 概览判断目前用 == 0 分支，允许只有一处；至少要有 Tab 分流
        if "statsTab" not in stats:
            errors.append("StatsScreen 缺 Tab 分流状态")

    # 3. 下钻契约四层：扇区 / 图例整行 / 三维度 / 二级展开子项
    for key in ("onOpenCategoryInDetail", "onOpenCounterpartyInDetail",
                "onOpenAccountInDetail", "state.drillRange", "state.currencyCode",
                "onChildClick", "expandedChildren", "onExpandToggle"):
        if key not in stats:
            errors.append("合体卡下钻契约缺: " + key)
    if "分类管理 ›" not in stats:
        errors.append("合体卡丢了「分类管理 ›」短跳转")
    if "合计不一致" not in stats:
        errors.append("合计一致性警告未置顶保留")

    # 4. 一羽式图例 + 彩色图标（Q2A）
    for key in ("CategoryGlyph(", "CategoryIcon(", "StatsLegend.rows",
                "StatsLegend.percentText", "percentText"):
        if key not in donut:
            errors.append("DonutChart 缺一羽式图例要素: " + key)
    if "heightIn(min = 44.dp)" not in donut:
        errors.append("图例整行触摸区未保持 >=40dp")
    # 禁用项：只查 import 与真实调用，注释里提到名字不算（基线原文也写着禁用理由）。
    for key in (
        "import androidx.compose.foundation.layout.FlowRow",
        "import androidx.compose.ui.text.TextMeasurer",
        "import androidx.compose.foundation.layout.BoxWithConstraints",
    ):
        if key in donut:
            errors.append("DonutChart 引入实验性/版本相关 API: " + key)
    if "drawText(" in donut or "TextMeasurer(" in donut:
        errors.append("DonutChart 引入 Canvas 文本测量")

    # 5. 彩色图标范围：只允许分类管理三页 / 两个选择器 / 统计占比图例
    # 只检查**调用点**（`CategoryGlyph(`），注释里提到名字不算。
    # 放行：目录定义文件、分类管理三页（列表 / 排序灰阶）、两个全量选择器、
    # 第 68 批统计占比图例。记一笔 / 明细 / 账户详情 MUST 零引用。
    allowed = {
        "ui/components/CategoryIconCatalog.kt",
        "ui/settings/CategoryManagerDialog.kt",
        "ui/settings/CategorySortScreen.kt",
        "ui/components/CategorySelectSheet.kt",
        "ui/settings/CategoryPickerScreen.kt",
        "ui/charts/DonutChart.kt",
    }
    forbidden = {
        "ui/record/RecordEditorDialog.kt",
        "ui/detail/DetailScreen.kt",
        "ui/detail/RecordDetailPage.kt",
        "ui/assets/AssetsScreen.kt",
    }
    for f in sorted(SRC.rglob("*.kt")):
        rel = str(f.relative_to(SRC))
        text = f.read_text(encoding="utf-8")
        if "CategoryGlyph(" in text and rel not in allowed:
            errors.append("彩色图标越界到: " + rel)
        if rel in forbidden and "CategoryGlyph(" in text:
            errors.append("记一笔/明细/账户详情出现彩色图标调用: " + rel)

    # 6. 零回归：统计口径 / 聚合 / 版本
    if "ChartData.netSlices" not in vm or "SUM(amountCents)" not in dao:
        errors.append("统计口径或既有聚合 SQL 被改动")
    if "version = 7" not in db:
        errors.append("AppDatabase.version 不再是 7（本批零改表）")
    if "emptyArray()" not in mig:
        errors.append("Migrations.ALL 不再为空")
    if "colorHex" not in donut:
        # DonutChart 本就不直接读 colorHex，只确认扇区色板来源
        if "ChartData.chartArgb" not in donut:
            errors.append("扇区色板未沿用 ChartData.chartArgb")

    # 7. 6 条 MODIFIED 标题必须命中基线（整块替换锚点）
    titles = [
        "统计页落地月年切换与三种图表",
        "分类占比卡提供分类管理短跳转",
        "分类全量选择器采用分组 chips 且不用下拉框",
        "环形图支持一级与二级切换并落地三级下钻",
        "分类图标不做字符串映射",
        "分类管理彩色图标目录与缺项兜底",
    ]
    base_text = "\n".join(f.read_text(encoding="utf-8") for f in BASE.rglob("spec.md"))
    delta_text = "\n".join(f.read_text(encoding="utf-8") for f in DELTA.rglob("spec.md"))
    for t in titles:
        if "### Requirement: " + t not in base_text:
            errors.append("MODIFIED 标题未命中基线: " + t)
        if "### Requirement: " + t not in delta_text:
            errors.append("delta 缺 MODIFIED: " + t)

    if errors:
        for e in errors:
            print("  FAIL", e)
        print(f"check_stats_merge: {len(errors)} 项失败")
        return 1
    print("check_stats_merge: PASS (合体卡 / 双 Tab / 下钻四层 / 图例范围 / 零回归 / 6 MODIFIED)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
