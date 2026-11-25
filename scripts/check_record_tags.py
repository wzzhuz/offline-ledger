#!/usr/bin/env python3
"""第 75 批：账单多标签静态校验（阶段 0 版）。

停推送门禁时：校验四件套 / spec delta / 文档登记齐备，且 **App 代码零改动**
（version 仍为 7、无 TagEntity / record_tags 实现），证明确实停在门禁。
编码批会把「代码零改动」段替换为「实现齐备」断言，并接入 build.yml。
"""
import pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
CH = ROOT / "openspec/changes/add-record-tags"
BASE = ROOT / "openspec/specs"

def main():
    errs = []
    # 1. 四件套齐备
    need = ["proposal.md", "design.md", "tasks.md",
            "specs/ledger-transactions/spec.md", "specs/backup-restore/spec.md",
            "specs/reporting-ui/spec.md", "specs/category-stats/spec.md"]
    for f in need:
        if not (CH / f).is_file():
            errs.append("缺四件套文件: " + f)

    # 2. delta spec 合规：标题不冲突、每条 >=1 Scenario + RFC2119
    rs = []
    for f in sorted((CH / "specs").rglob("spec.md")):
        t = f.read_text(); sect = None; cur = None
        for l in t.splitlines():
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
    added = [r for r in rs if r["s"] == "ADDED"]
    modified = [r for r in rs if r["s"] == "MODIFIED"]
    for r in added:
        if r["t"] in base: errs.append("ADDED 与基线标题冲突: " + r["t"])
    for r in modified:
        if r["t"] not in base: errs.append("MODIFIED 未命中基线标题: " + r["t"])
    for r in rs:
        if r["sc"] < 1 or not r["rfc"]:
            errs.append("条目缺 Scenario 或 RFC2119: " + r["t"])
    if len(rs) != 9: errs.append("应为 9 条 Requirement（6 ADDED + 3 MODIFIED），实际 %d" % len(rs))

    # 3. 文档登记
    for path, key in [("docs/页面优化经验与避坑指南.md", "§7.57"),
                      ("openspec/project.md", "第 75 批")]:
        p = ROOT / path
        if not p.is_file() or key not in p.read_text():
            errs.append("文档未登记: " + path + " / " + key)
    proposal = (CH / "proposal.md").read_text()
    for k in ["Q1A", "Q2A", "Q3A", "Q4A", "Q5A", "version 8", "卸载重装"]:
        if k not in proposal: errs.append("proposal 缺关键口径: " + k)

    # 4. 阶段 0：App 代码 MUST 零改动（停在门禁的证据）
    db = (ROOT / "app/src/main/java/com/zwz/offlineledger/data/local/AppDatabase.kt").read_text()
    if "version = 7," not in db: errs.append("阶段0 不应已升 version")
    ent = ROOT / "app/src/main/java/com/zwz/offlineledger/data/local/entity"
    if (ent / "TagEntity.kt").exists(): errs.append("阶段0 不应已实现 TagEntity")
    if "record_tags" in (ROOT / "scripts/check_firefly_mapping.py").read_text():
        errs.append("无关脚本被误改")

    if errs:
        for e in errs: print("  FAIL", e)
        print("check_record_tags: %d 项失败" % len(errs)); return 1
    print("check_record_tags: PASS (阶段0 四件套齐备 / 9 条 delta / 基线零冲突 / 文档已登记 / App 代码零改动=停在门禁)")
    return 0

if __name__ == "__main__": sys.exit(main())
