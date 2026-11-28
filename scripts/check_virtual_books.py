#!/usr/bin/env python3
"""第 91/93 批：虚拟账本（视图级台账）静态契约检查。

核心守住四条口径：零账本维度、切换不带录入归属、台账聚合不过滤不计入、
台账流水 MUST NOT 从主账本明细排除（否则全局失去编辑入口）。
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
    # 第 93 批 3B：切换器只在明细页标题行占一枚图标按钮，MUST NOT 自带整行容器
    if ".statusBarsPadding()" in switcher or "Surface(color" in switcher:
        e.append("切换器 MUST NOT 再自带 Surface / 状态栏避让（太占位置）")
    if "IconButton(" not in switcher or "Modifier.size(36.dp)" not in switcher:
        e.append("切换器 MUST 是一枚 36dp 图标按钮")

    # 第 93 批 1A：撤回第 91 批的明细排除 SQL，台账流水在主账本明细可见可改
    record_dao = read("app/src/main/java/com/zwz/offlineledger/data/local/dao/RecordDao.kt")
    if "vt.name LIKE '台账:%'" in record_dao:
        e.append("主账本明细 MUST NOT 排除台账记录（会让流水全局失去编辑入口）")
    if ":tagName IS NOT NULL OR NOT EXISTS" in record_dao:
        e.append("按标签筛选不再承担『查回台账记录』职责，排除条件残留")

    # 第 93 批 2A：台账页总览 / 分段 / 编辑 / 删除 / 固定录入区
    screen = read("app/src/main/java/com/zwz/offlineledger/ui/virtual/VirtualBookScreen.kt")
    for k in ["onEditRecord", "enum class BookSegment", "本台账总览",
              "往来汇总", "流水", "repository.deleteRecord(id)",
              "lifecycleScope.launch", "pendingDelete", "底部固定录入区"]:
        if k not in screen:
            e.append("台账页缺少第 93 批能力：" + k)
    if "rememberCoroutineScope()" in screen:
        e.append("台账删除 MUST NOT 用 composition scope（第 89 批坑）")
    if "LaunchedEffect(bookId, refreshTick, reload)" not in screen:
        e.append("台账删除后 MUST 触发本地重载")

    detail = read("app/src/main/java/com/zwz/offlineledger/ui/detail/DetailScreen.kt")
    if "virtualBookEntry: @Composable () -> Unit" not in detail:
        e.append("明细页标题行缺少虚拟账本入口 slot")
    app = read("app/src/main/java/com/zwz/offlineledger/ui/LedgerApp.kt")
    if "virtualBookEntry = {" not in app:
        e.append("LedgerApp 未把切换器挂进明细页标题行")

    editor = read("app/src/main/java/com/zwz/offlineledger/ui/record/RecordEditorDialog.kt")
    for k in ["presetType", "presetTagNames", "presetExcludeFromStats",
              "vm.load(recordId, presetType, presetTagNames, presetExcludeFromStats)"]:
        if k not in editor:
            e.append("记一笔缺少台账预填出口：" + k)
    # 第 92 批：预填 MUST 在 load 协程内部合并，外部同步 apply 会被整体重置覆盖
    if "vm.applyPreset(" in editor:
        e.append("外部同步 applyPreset 已被 load 整体赋值覆盖，MUST 删除")

    vm = read("app/src/main/java/com/zwz/offlineledger/ui/record/RecordEditorViewModel.kt")
    for k in ["fun load(\n", "applyPresetTo(", "it.type == effectiveType",
              "tagNames = tagNames.distinct()", "excludeFromStats = excludeFromStats"]:
        if k not in vm:
            e.append("VM 缺少台账预填合并实现：" + k)

    tag_names = read("app/src/main/java/com/zwz/offlineledger/core/TagNames.kt")
    for k in ["fun addPickedTag(", "fun visibleTagNames("]:
        if k not in tag_names:
            e.append("标签新建纯函数出口缺失：" + k)
    picker = read("app/src/main/java/com/zwz/offlineledger/ui/record/TagPickerDialog.kt")
    for k in ["visibleTagNames(", "addPickedTag(", "已选 "]:
        if k not in picker:
            e.append("标签选择器未接新建可见化：" + k)

    # 自动记账 MUST 永远落主账本：文本入口不得感知虚拟账本
    intake = read("app/src/main/java/com/zwz/offlineledger/ui/intake/TextIntakeActivity.kt")
    if "台账" in intake or "irtualBook" in intake:
        e.append("自动记账入口 MUST NOT 感知虚拟账本（切换不得改变落库位置）")

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
    if 'defaultVersionName = "0.2.72"' not in gd:
        e.append("版本号未升 0.2.72")

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
