#!/usr/bin/env python3
"""第 74 批：Firefly III 映射静态校验（分类选择器 / 三态期初 / 修暗条件）。

纯文本校验，不依赖 Android SDK / Gradle。防止：
1) 分类又回到 DropdownMenu 逐项列出；
2) 布尔开关 / targetName == name 暗条件以别的形式回来；
3) ADJUST 漏幂等或漏走 adjustments；
4) 顺手升表 / 改 CategorySelectDialog。
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/java/com/zwz/offlineledger"
BASE = ROOT / "openspec/specs"
DELTA = ROOT / "openspec/changes/refine-firefly-mapping/specs"


def main():
    errors = []
    screen = (SRC / "ui/settings/FireflyImportScreen.kt").read_text()
    models = (SRC / "backup/csv/FireflyModels.kt").read_text()
    repo = (SRC / "data/repository/LedgerRepository.kt").read_text()
    vm = (SRC / "ui/settings/SettingsViewModel.kt").read_text()
    db = (SRC / "data/local/AppDatabase.kt").read_text()

    # 1. 分类 MUST 用全量选择器，下拉里 MUST NOT 有分类
    for key in ("CategorySelectDialog(", "FireflyCategoryRow(choice, state.categories)",
                "targetCategoryId", "targetDisplayName", "RecordType.valueOf(choice.type)"):
        if key not in screen:
            errors.append("FireflyImportScreen 缺 Q1A 选择器要素: " + key)
    row = screen.split("private fun FireflyCategoryRow(", 1)
    if len(row) < 2:
        errors.append("缺 FireflyCategoryRow")
    else:
        seg = row[1]
        if "DropdownMenuItem(" in seg and seg.count("DropdownMenuItem(") != 2:
            errors.append("分类行下拉不是「只有两个动作」")
        for key in ("state.categories.forEach", "categories.forEach {"):
            if key in seg:
                errors.append("分类行下拉仍在逐个列分类: " + key)
        if "CategorySelectDialog(" not in seg:
            errors.append("分类行没有接 CategorySelectDialog")

    # 2. 三态期初
    for key in ("FireflyOpeningPolicy.values()", "setFireflyOpeningPolicy",
                "openingPolicy", "openingBySource", "当前期初"):
        if key not in screen:
            errors.append("FireflyImportScreen 缺 Q2A 三态要素: " + key)
    for key in ("Switch(", "overwriteOpening", "setFireflyOverwriteOpening"):
        if key in screen:
            errors.append("期初仍残留布尔开关: " + key)
    for key in ("enum class FireflyOpeningPolicy",
                "KEEP, OVERWRITE, ADJUST", "val openingPolicy:"):
        if key not in models:
            errors.append("FireflyModels 缺三态模型: " + key)
    if "overwriteOpeningForExisting" in models or "overwriteOpeningForExisting" in vm:
        errors.append("仍残留 overwriteOpeningForExisting")

    # 3. Repository 三分支 + 修暗条件 + ADJUST 幂等 + 按 id 分类
    for key in ("FireflyOpeningPolicy.KEEP", "FireflyOpeningPolicy.OVERWRITE",
                "FireflyOpeningPolicy.ADJUST", "FIREFLY_OPENING",
                "adjustmentDao.insert(", "adjustmentDao.allActive()",
                "val selectedId = choice?.targetCategoryId",
                "require(hit.type == type)", "categoryIds[ck.key()] = Pair("):
        if key not in repo:
            errors.append("LedgerRepository 缺第 74 批要素: " + key)
    # 代码里的暗条件（排除注释行）
    for line in repo.splitlines():
        code = line.strip()
        if code.startswith("*") or code.startswith("//") or code.startswith("/*"):
            continue
        if "targetName == name" in code:
            errors.append("仍残留 targetName == name 暗条件（代码行）")
    if "import com.zwz.offlineledger.backup.csv.FireflyOpeningPolicy" not in repo:
        errors.append("LedgerRepository 漏 import FireflyOpeningPolicy")

    # 4. 零升表 / 零改既有选择器
    # 第 74 批零改表锁 7；第 75 批升 8；第 82 批 accounts.iconRef 升 9。
    m = re.search(r"version = (\d+)", db)
    if not m or int(m.group(1)) not in (7, 8, 9):
        errors.append("AppDatabase.version 应为 7 / 8 / 9（第82批升表为 9）")
    picker = (SRC / "ui/components/CategorySelectSheet.kt").read_text()
    if "columns = 4" not in picker or "allowAll" not in picker:
        errors.append("CategorySelectDialog 被改动（本批 MUST 零改动复用）")

    # 5. delta spec 合规
    titles = []
    for f in sorted(DELTA.rglob("spec.md")):
        t = f.read_text()
        cur = None
        for l in t.splitlines():
            m = re.match(r"^### Requirement: (.+)", l)
            if m:
                cur = {"t": m.group(1).strip(), "sc": 0, "rfc": False}
                titles.append(cur)
            if cur and re.match(r"^#### Scenario:", l):
                cur["sc"] += 1
            if cur and re.search(r"\b(SHALL|MUST|MUST NOT|MAY)\b", l):
                cur["rfc"] = True
    base_titles = set()
    for f in BASE.rglob("spec.md"):
        for m in re.finditer(r"^### Requirement: (.+)$", f.read_text(), re.M):
            base_titles.add(m.group(1).strip())
    if len(titles) != 2:
        errors.append("delta 应为 2 条 ADDED，实际 %d" % len(titles))
    for r in titles:
        if r["t"] in base_titles:
            errors.append("ADDED 与基线标题冲突: " + r["t"])
        if r["sc"] < 1 or not r["rfc"]:
            errors.append("delta 条目缺 Scenario 或 RFC2119: " + r["t"])

    if errors:
        for e in errors:
            print("  FAIL", e)
        print("check_firefly_mapping: %d 项失败" % len(errors))
        return 1
    print("check_firefly_mapping: PASS (第74批 分类选择器按id / 三态期初 / ADJUST幂等 / 修暗条件 / 零升表 / 2 ADDED)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
