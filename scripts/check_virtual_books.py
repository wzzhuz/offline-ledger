#!/usr/bin/env python3
"""第 91 批：虚拟账本（视图级台账）静态契约检查。

核心是守住三条口径：零账本维度、切换不带录入归属、台账聚合不过滤不计入。
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def read(rel):
    with open(os.path.join(ROOT, rel), encoding="utf-8") as f:
        return f.read()


def main():
    e = []

    files = [
        "app/src/main/java/com/zwz/offlineledger/core/prefs/VirtualBookCodec.kt",
        "app/src/main/java/com/zwz/offlineledger/core/prefs/VirtualBookStore.kt",
        "app/src/main/java/com/zwz/offlineledger/data/local/dao/VirtualBookDao.kt",
        "app/src/main/java/com/zwz/offlineledger/ui/virtual/VirtualBookScreen.kt",
        "app/src/main/java/com/zwz/offlineledger/ui/virtual/VirtualBookSwitcher.kt",
        "app/src/main/java/com/zwz/offlineledger/ui/settings/VirtualBookSettingsScreen.kt",
        "app/src/test/java/com/zwz/offlineledger/core/prefs/VirtualBookCodecTest.kt",
    ]
    for f in files:
        if not os.path.exists(os.path.join(ROOT, f)):
            e.append("缺少文件 " + f)

    dao = read("app/src/main/java/com/zwz/offlineledger/data/local/dao/VirtualBookDao.kt")
    # 台账记录刻意勾了「不计入收支」，聚合若沿用 tagStats 口径会一条都查不到
    if "excludeFromStats = 0" in dao:
        e.append("台账聚合 SQL MUST NOT 过滤 excludeFromStats")
    for k in ["CASE WHEN r.type = 'INCOME'", "CASE WHEN r.type = 'EXPENSE'",
              "currencyCode = :currencyCode", "GROUP BY party"]:
        if k not in dao:
            e.append("台账聚合缺少：" + k)

    codec = read("app/src/main/java/com/zwz/offlineledger/core/prefs/VirtualBookCodec.kt")
    if 'const val TAG_PREFIX: String = "台账:"' not in codec:
        e.append("台账标签统一前缀丢失")
    if "改名不改标签" not in codec:
        e.append("改名不改标签的口径注释丢失")

    store = read("app/src/main/java/com/zwz/offlineledger/core/prefs/VirtualBookStore.kt")
    if "getSharedPreferences" not in store or "@Dao" in store or "@Entity" in store:
        e.append("定义 MUST 走 SharedPreferences，不进 Room")

    switcher = read("app/src/main/java/com/zwz/offlineledger/ui/virtual/VirtualBookSwitcher.kt")
    if "不携带录入归属" not in switcher:
        e.append("切换器 MUST 明确只切浏览范围、不携带录入归属")

    record_dao = read("app/src/main/java/com/zwz/offlineledger/data/local/dao/RecordDao.kt")
    if "vt.name LIKE '台账:%'" not in record_dao:
        e.append("主账本明细缺少台账标签排除条件")
    if ":tagName IS NOT NULL OR NOT EXISTS" not in record_dao:
        e.append("按标签筛选 MUST 能查回台账记录")

    editor = read("app/src/main/java/com/zwz/offlineledger/ui/record/RecordEditorDialog.kt")
    for k in ["presetType", "presetTagNames", "presetExcludeFromStats", "vm.applyPreset"]:
        if k not in editor:
            e.append("记一笔缺少台账预填出口：" + k)

    # 自动记账 MUST 永远落主账本：文本入口不得感知虚拟账本
    intake = read("app/src/main/java/com/zwz/offlineledger/ui/intake/TextIntakeActivity.kt")
    if "台账" in intake or "irtualBook" in intake:
        e.append("自动记账入口 MUST NOT 感知虚拟账本（切换不得改变落库位置）")

    app = read("app/src/main/java/com/zwz/offlineledger/ui/LedgerApp.kt")
    for k in ["virtual-book/{id}", "virtual-book-settings", "VirtualBookSwitcher",
              "presetTagNames = editorPreset"]:
        if k not in app:
            e.append("主界面缺少：" + k)

    db = read("app/src/main/java/com/zwz/offlineledger/data/local/AppDatabase.kt")
    if "version = 9," not in db:
        e.append("虚拟账本 MUST 零改表（version 仍 9）")
    if "fun virtualBookDao(): VirtualBookDao" not in db:
        e.append("VirtualBookDao 未注册")

    gd = read("app/build.gradle.kts")
    if 'defaultVersionName = "0.2.70"' not in gd:
        e.append("版本号未升 0.2.70")

    proj = read("openspec/project.md")
    if "虚拟台账视图不属此禁令" not in proj:
        e.append("project.md 缺少「虚拟台账视图不属永久不做」界定句")

    if e:
        for x in e:
            print("FAIL:", x)
        print("虚拟账本静态检查失败：%d 项" % len(e))
        return 1
    print("虚拟账本静态检查通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
