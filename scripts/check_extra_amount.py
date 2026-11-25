#!/usr/bin/env python3
"""第 67 批：附加金额（优惠 / 手续费）全链路静态校验。

纯文本校验，不依赖 Android SDK / Gradle。检查「余额双实现必须同步」
与「附加字段从 Entity 到 UI 一路不丢」，避免只改一半导致账实不符。
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/java/com/zwz/offlineledger"


def read(p):
    return (SRC / p).read_text(encoding="utf-8")


def main():
    errors = []
    dao = read("data/local/dao/RecordDao.kt")
    engine = read("engine/LedgerEngine.kt")
    rules = read("engine/ExtraAmountRules.kt")
    entity = read("data/local/entity/RecordEntity.kt")
    trash = read("data/local/entity/TrashEntryEntity.kt")
    dto = (
        ROOT / "app/src/main/java/com/zwz/offlineledger/backup/dto/BackupPayload.kt"
    ).read_text(encoding="utf-8")
    csv_models = (
        ROOT / "app/src/main/java/com/zwz/offlineledger/backup/csv/CsvModels.kt"
    ).read_text(encoding="utf-8")
    csv_codec = (
        ROOT / "app/src/main/java/com/zwz/offlineledger/backup/csv/CsvCodec.kt"
    ).read_text(encoding="utf-8")
    mappers = read("data/local/LedgerMappers.kt")
    db = read("data/local/AppDatabase.kt")
    migrations = read("data/local/Migrations.kt")
    editor = read("ui/record/RecordEditorDialog.kt")
    detail = read("ui/detail/DetailScreen.kt")
    detail_page = read("ui/detail/RecordDetailPage.kt")

    # 1. 余额双实现同步：DAO SQL 与纯 JVM 引擎都只在 TRANSFER 用 fee
    if "CASE WHEN type = 'TRANSFER' THEN feeCents" not in dao:
        errors.append("RecordDao 聚合 SQL 未按 TRANSFER 分支处理 feeCents")
    if "COALESCE(" not in dao.split("aggregateAccountDeltas")[0][-1200:]:
        errors.append("RecordDao 聚合 SQL 缺 COALESCE，可空 fee 会让整笔变 NULL")
    if "r.type == RecordType.TRANSFER" not in engine or "r.feeCents" not in engine:
        errors.append("LedgerEngine.deltasFrom 未与 DAO 同步 fee 逻辑")
    if "amountCents AS deltaCents" not in dao:
        errors.append("to 侧必须仍为 +amount，手续费不能加到转入账户")

    # 2. 字段全链路
    for name, text in [("RecordEntity", entity), ("TrashEntryEntity", trash),
                       ("BackupRecord", dto), ("CsvRow", csv_models)]:
        for field in ("discount", "fee"):
            if field not in text:
                errors.append(f"{name} 缺 {field} 字段")
    for key in ("discountCents = discountCents, feeCents = feeCents",):
        if mappers.count(key) < 4:
            errors.append("LedgerMappers 4 处往返映射未全带附加金额")
    for key in ('"discount"', '"fee"'):
        if key not in csv_codec:
            errors.append("CsvCodec 表头/解析缺 " + key)
    if '"$accountName|$amountYuan|$date|$note"' not in csv_models:
        errors.append("CsvRow.duplicateKey() 被改动，会破坏导入去重与复合匹配")

    # 3. 升表口径
    # 第 67 批升 7；第 75 批新增 tags / record_tags 升 8 为预期。
    m = re.search(r"version = (\d+)", db)
    if not m or int(m.group(1)) not in (7, 8):
        errors.append("AppDatabase.version 应为 7（第67批）或 8（第75批）")
    if "emptyArray()" not in migrations:
        errors.append("Migrations.ALL 不再为空，与「测试数据不考虑迁移」口径冲突")

    # 4. UI 三处
    if "ExtraAmountSection" not in editor or "ExtraAmountSummary" not in editor:
        errors.append("记一笔缺优惠/手续费入口或三行金额")
    if "TextDecoration.LineThrough" not in detail:
        errors.append("明细行缺划线原价")
    if '"原始金额"' not in detail_page:
        errors.append("详情页缺三行金额")

    # 5. 校验器唯一入口
    if "object ExtraAmountRules" not in rules:
        errors.append("缺 ExtraAmountRules 唯一校验入口")
    vm = read("ui/record/RecordEditorViewModel.kt")
    if "ExtraAmountRules.validate" not in vm:
        errors.append("ViewModel 未接唯一校验入口")

    if errors:
        for e in errors:
            print("  FAIL", e)
        print(f"check_extra_amount: {len(errors)} 项失败")
        return 1
    print("check_extra_amount: PASS (余额双实现同步 / 字段全链路 / UI 三处 / 升表口径)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
