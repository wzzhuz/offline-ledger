#!/usr/bin/env python3
"""第 75 批：账单多标签静态校验（实现批）。

校验数据模型 / 四条往返 / UI / 文档登记齐备，且不破坏既有契约
（Migrations.ALL 仍空、CSV duplicateKey 不含 tags、预制分类零改动）。
"""
import pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
CH = ROOT / "openspec/changes/add-record-tags"
BASE = ROOT / "openspec/specs"
APP = ROOT / "app/src/main/java/com/zwz/offlineledger"

def read(*parts):
    return (ROOT.joinpath(*parts)).read_text()

def main():
    errs = []

    # 1. 四件套齐备
    need = ["proposal.md", "design.md", "tasks.md",
            "specs/ledger-transactions/spec.md", "specs/backup-restore/spec.md",
            "specs/reporting-ui/spec.md", "specs/category-stats/spec.md"]
    for f in need:
        if not (CH / f).is_file():
            errs.append("缺四件套文件: " + f)

    # 2. delta spec 合规
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
    for r in rs:
        if r["s"] == "ADDED" and r["t"] in base: errs.append("ADDED 冲突基线: " + r["t"])
        if r["s"] == "MODIFIED" and r["t"] not in base: errs.append("MODIFIED 未命中基线: " + r["t"])
        if r["sc"] < 1 or not r["rfc"]: errs.append("条目缺 Scenario / RFC2119: " + r["t"])
    if len(rs) != 9: errs.append("应为 9 条 Requirement，实际 %d" % len(rs))

    # 3. 数据模型
    db = read("app/src/main/java/com/zwz/offlineledger/data/local/AppDatabase.kt")
    for k in ["version = 8,", "TagEntity::class", "RecordTagCrossRef::class", "fun tagDao(): TagDao"]:
        if k not in db: errs.append("AppDatabase 缺: " + k)
    for path, keys in [
        ("app/src/main/java/com/zwz/offlineledger/data/local/entity/TagEntity.kt",
         ['tableName = "tags"', 'tableName = "record_tags"', "ForeignKey.CASCADE"]),
        ("app/src/main/java/com/zwz/offlineledger/data/local/dao/TagDao.kt",
         ["namesForRecord", "clearForRecord", "insertRefs", "tagStats"]),
        ("app/src/main/java/com/zwz/offlineledger/core/TagNames.kt",
         ["splitTagNames", "joinTagNames", "MAX_PER_RECORD"]),
    ]:
        t = read(path)
        for k in keys:
            if k not in t: errs.append(path.split("/")[-1] + " 缺: " + k)

    # 4. 四条往返
    repo = read("app/src/main/java/com/zwz/offlineledger/data/repository/LedgerRepository.kt")
    for k in ["bindTags", "setRecordTags", "tagNames(recordId", "splitTagNames(e.tags)",
              "splitTagNames(row.tags)", "joinTagNames(it.tagNames)"]:
        if k not in repo: errs.append("Repository 缺: " + k)
    csv = read("app/src/main/java/com/zwz/offlineledger/backup/csv/CsvCodec.kt")
    if '"discount", "fee", "tags"' not in csv: errs.append("CsvCodec HEADER 缺 tags 列")
    if csv.count('"tags")') < 3: errs.append("CsvCodec optional 集合未同步三处")
    dto = read("app/src/main/java/com/zwz/offlineledger/backup/dto/BackupPayload.kt")
    for k in ["data class BackupTag", "tagNames: List<String>", "tags: List<BackupTag>"]:
        if k not in dto: errs.append("BackupPayload 缺: " + k)
    trash = read("app/src/main/java/com/zwz/offlineledger/data/local/entity/TrashEntryEntity.kt")
    if 'name = "tagNames"' not in trash: errs.append("TrashEntryEntity 缺 tagNames")
    dim = read("app/src/main/java/com/zwz/offlineledger/data/di/DatabaseModule.kt")
    if "fun provideTagDao(db: AppDatabase): TagDao" not in dim:
        errs.append("DatabaseModule 缺 TagDao 的 Hilt @Provides 绑定（Dagger/MissingBinding）")
    seed = read("app/src/main/java/com/zwz/offlineledger/data/local/SeedCallback.kt")
    for k in ["给老婆", "家用", "旅行"]:
        if k not in seed: errs.append("SeedCallback 缺预置标签: " + k)

    # 5. UI
    for path, keys in [
        ("app/src/main/java/com/zwz/offlineledger/ui/record/TagPickerDialog.kt", ["TagPickerDialog"]),
        ("app/src/main/java/com/zwz/offlineledger/ui/settings/TagManagerScreen.kt", ["TagManagerScreen"]),
        ("app/src/main/java/com/zwz/offlineledger/ui/components/TagChips.kt", ["TagChips"]),
        ("app/src/main/java/com/zwz/offlineledger/ui/stats/StatsScreen.kt", ["TagRankCard", "不算百分比"]),
        ("app/src/main/java/com/zwz/offlineledger/ui/LedgerApp.kt", ['composable("tags")']),
        ("app/src/main/java/com/zwz/offlineledger/ui/detail/FilterSheet.kt", ["tagText"]),
    ]:
        t = read(path)
        for k in keys:
            if k not in t: errs.append(path.split("/")[-1] + " 缺: " + k)

    # 6. 不破坏既有契约
    mig = read("app/src/main/java/com/zwz/offlineledger/data/local/Migrations.kt")
    if "val ALL: Array<Migration> = emptyArray()" not in mig:
        errs.append("Migrations.ALL 必须保持空数组")
    csvmodels = read("app/src/main/java/com/zwz/offlineledger/backup/csv/CsvModels.kt")
    if '"$accountName|$amountYuan|$date|$note"' not in csvmodels:
        errs.append("CSV duplicateKey 契约被改动")
    dao = read("app/src/main/java/com/zwz/offlineledger/data/local/dao/RecordDao.kt")
    if "AS tagNames" not in dao or ":tagName IS NULL" not in dao:
        errs.append("pagedFiltered 缺 tagNames 投影或 EXISTS 短路")
    if "LIKE '%家用%'" in dao:
        errs.append("MUST NOT 用 LIKE 匹配标签")

    # 7. 版本与文档
    gradle = read("app/build.gradle.kts")
    if 'defaultVersionName = "0.2.54"' not in gradle:
        errs.append("版本号未升到 0.2.54")
    for path, key in [("docs/页面优化经验与避坑指南.md", "§7.57"),
                      ("openspec/project.md", "第 75 批")]:
        if key not in read(path): errs.append("文档未登记: " + path)

    if errs:
        for e in errs: print("  FAIL", e)
        print("check_record_tags: %d 项失败" % len(errs)); return 1
    print("check_record_tags: PASS (9 条 delta / 2 表 / 4 条往返 / UI / 零契约破坏 / 文档齐备)")
    return 0

if __name__ == "__main__": sys.exit(main())
