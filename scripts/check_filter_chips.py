#!/usr/bin/env python3
"""第 76 批：明细标签轻量化 / 标签选择器 / 统一筛选 chips 静态校验。"""
import pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
CH = ROOT / "openspec/changes/refine-tag-filter-chips"
BASE = ROOT / "openspec/specs"
APP = ROOT / "app/src/main/java/com/zwz/offlineledger"

def read(*parts):
    return ROOT.joinpath(*parts).read_text()

def main():
    errs = []
    for f in ["proposal.md", "design.md", "tasks.md", "specs/reporting-ui/spec.md"]:
        if not (CH / f).is_file():
            errs.append("缺四件套: " + f)

    # delta 合规
    rs = []
    for f in sorted((CH / "specs").rglob("spec.md")):
        sect, cur = None, None
        for l in f.read_text().splitlines():
            m0 = re.match(r"^## (ADDED|MODIFIED|REMOVED) Requirements", l)
            if m0: sect = m0.group(1)
            m = re.match(r"^### Requirement: (.+)", l)
            if m:
                cur = {"t": m.group(1).strip(), "s": sect, "sc": 0, "rfc": False}
                rs.append(cur)
            if cur:
                if re.match(r"^#### Scenario:", l): cur["sc"] += 1
                if re.search(r"\b(SHALL|MUST|MUST NOT|MAY|SHOULD)\b", l): cur["rfc"] = True
    base = set()
    for f in BASE.rglob("spec.md"):
        for m in re.finditer(r"^### Requirement: (.+)$", f.read_text(), re.M):
            base.add(m.group(1).strip())
    for r in rs:
        if r["s"] == "ADDED" and r["t"] in base: errs.append("ADDED 冲突: " + r["t"])
        if r["s"] == "MODIFIED" and r["t"] not in base: errs.append("MODIFIED 未命中基线: " + r["t"])
        if r["sc"] < 1 or not r["rfc"]: errs.append("缺 Scenario/RFC2119: " + r["t"])
    if len(rs) != 4: errs.append("应为 4 条 Requirement，实际 %d" % len(rs))

    chips = read("app/src/main/java/com/zwz/offlineledger/ui/components/TagChips.kt")
    for k in ["fun TagChips(raw: String?, limit: Int", "10.sp", "TagBadge", "+"]:
        if k not in chips: errs.append("TagChips 缺轻量标记: " + k)
    # 注释里会提到这些历史名词，只检查 import 行是否真的残留。
    for k in ["import androidx.compose.material3.AssistChip",
              "import androidx.compose.foundation.layout.FlowRow",
              "import androidx.compose.foundation.layout.ExperimentalLayoutApi"]:
        if k in chips: errs.append("TagChips 残留操作型/实验性组件: " + k)

    detail = read("app/src/main/java/com/zwz/offlineledger/ui/detail/RecordDetailPage.kt")
    if "limit = Int.MAX_VALUE" not in detail: errs.append("详情页未展开全量标签")

    fs = read("app/src/main/java/com/zwz/offlineledger/ui/detail/FilterSheet.kt")
    for k in ['PickRow("标签"', 'tagDao.active()', 'produceState', '"tag" -> PickerData']:
        if k not in fs: errs.append("FilterSheet 缺标签选择器: " + k)
    if 'placeholder = { Text("标签名' in fs:
        errs.append("标签手输 OutlinedTextField 未删除")

    rf = read("app/src/main/java/com/zwz/offlineledger/data/paging/RecordFilter.kt")
    for k in ["enum class FilterField", "fun clearField(field: FilterField)"]:
        if k not in rf: errs.append("RecordFilter 缺: " + k)
    m = re.search(r"fun activeCount\(\): Int = listOf\((.*?)\)", rf, re.S)
    if not m or "tagName" not in m.group(1):
        errs.append("activeCount() 未计入 tagName")
    if "fun clearField(field: FilterField)" not in rf:
        errs.append("clearField 单一出口缺失")
    if "revision + 1" not in rf.split("fun clearField")[1][:2200]:
        errs.append("clearField 未递增 revision")

    vm = read("app/src/main/java/com/zwz/offlineledger/ui/detail/DetailViewModel.kt")
    if "fun clearFilterField(field: FilterField)" not in vm:
        errs.append("DetailViewModel 缺 clearFilterField")

    ds = read("app/src/main/java/com/zwz/offlineledger/ui/detail/DetailScreen.kt")
    for k in ["buildFilterChips", "clearFilterField", "horizontalScroll", "FilterConditionChip"]:
        if k not in ds: errs.append("DetailScreen 缺统一 chips: " + k)
    if "val drillLabel" in ds: errs.append("drillLabel 三选一 when 未删除")

    dao = read("app/src/main/java/com/zwz/offlineledger/data/local/dao/RecordDao.kt")
    if "IN (:tag" in dao: errs.append("标签筛选 MUST NOT 改成 IN 集合")

    db = read("app/src/main/java/com/zwz/offlineledger/data/local/AppDatabase.kt")
    if "version = 9," not in db: errs.append("第 82 批 accounts.iconRef 升表口径被破坏（version 必须为 9）")
    test = ROOT / "app/src/test/java/com/zwz/offlineledger/data/FilterChipClearTest.kt"
    if not test.is_file(): errs.append("缺 clearField 纯 JVM 单测")
    gradle = read("app/build.gradle.kts")
    m = re.search(r'defaultVersionName = "0\.2\.(\d+)"', gradle)
    if not m or int(m.group(1)) < 55: errs.append("版本号应 >= 0.2.55")
    wf = read(".github/workflows/build.yml")
    if "check_filter_chips.py" not in wf: errs.append("build.yml 未挂静态脚本")
    for path, key in [("docs/页面优化经验与避坑指南.md", "§7.58"),
                      ("openspec/project.md", "第 76 批")]:
        if key not in read(path): errs.append("文档未登记: " + path)

    if errs:
        for e in errs: print("  FAIL", e)
        print("check_filter_chips: %d 项失败" % len(errs)); return 1
    print("check_filter_chips: PASS (4 条 delta / 轻量标记 / 标签选择器 / 统一 chips / 升表 9 / 文档齐备)")
    return 0

if __name__ == "__main__": sys.exit(main())
