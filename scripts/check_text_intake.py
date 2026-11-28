#!/usr/bin/env python3
"""第 87 批：把「零权限自动记账 + 规则覆盖层 + 永不误记」变成可执行断言。"""
import pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
C = lambda p: ROOT.joinpath(p).read_text(encoding='utf-8')
CH = ROOT / "openspec/changes/text-intake-diagnosis"


def main():
    e = []
    for f in ["proposal.md", "design.md", "tasks.md",
              "specs/privacy-security/spec.md", "specs/reporting-ui/spec.md"]:
        if not (CH / f).is_file():
            e.append("缺活跃 change 四件套: " + f)

    man = C("app/src/main/AndroidManifest.xml")
    for k in ["android.intent.action.PROCESS_TEXT", "android.intent.action.SEND",
              'android:name=".ui.intake.TextIntakeActivity"',
              'android:exported="true"', 'android:excludeFromRecents="true"',
              'android:mimeType="text/plain"', "android.intent.category.DEFAULT"]:
        if k not in man:
            e.append("Manifest 缺: " + k)
    declared = re.findall(r'<uses-permission[^>]*android:name="([^"]+)"',
                          re.sub(r'<!--.*?-->', '', man, flags=re.S))
    if declared != ["android.permission.USE_BIOMETRIC"]:
        e.append("新增了 uses-permission: " + repr(declared))
    if "BIND_ACCESSIBILITY_SERVICE" in man or "NotificationListenerService" in man:
        e.append("不得注册无障碍 / 通知监听服务")

    act = C("app/src/main/java/com/zwz/offlineledger/ui/intake/TextIntakeActivity.kt")
    for k in ["@AndroidEntryPoint", "@Inject lateinit var repository",
              "AppLockScreen", "TextIntakeRules.withPatch", "TextIntakeInspector.from",
              "repository.commitTextIntake", "repository.deleteRecord",
              "textIntakeAutoCommitEnabled", "textIntakeRulePatch", "textIntakeLog",
              "SelectionContainer", "EXTRA_PROCESS_TEXT", "EXTRA_TEXT",
              "ACTION_TEXT_INTAKE_DEBUG", "takeLast(6)",
              "intent.clipData", "initialRaw.isNotBlank()", "本次入口状态",
              "RULE_PROMPT", "复制规则生成提示词", "未收到文本",
              # 第 89 批：写事务 MUST 在 Activity scope，页面 MUST 三分区
              "lifecycleScope.launch", "CancellationException", "commitSafely",
              "TabRow", "ResultBanner", "本次结果", "规则与试解析", "记录",
              "未达严格充分条件"]:
        if k not in act:
            e.append("Activity 缺: " + k)
    # 剥注释后再判禁止项：KDoc 里写「MUST NOT 用 rememberCoroutineScope」不该自误报
    act_code = "\n".join(
        line for line in act.splitlines()
        if not line.lstrip().startswith("//")
        and not line.lstrip().startswith("*")
        and not line.lstrip().startswith("/*")
    )
    for k in ["rememberSaveable", "openOutputStream", "startForeground",
              "registerListener", "withTransaction", "Room", "openFileOutput",
              "rememberCoroutineScope", "scope.launch"]:
        if k in act_code:
            e.append("Activity 不得出现: " + k)
    # 写事务不得落在 LaunchedEffect 块里
    le_start = act_code.find("LaunchedEffect(")
    while le_start >= 0:
        le_end = act_code.find("LaunchedEffect(", le_start + 1)
        if le_end < 0:
            le_end = len(act_code)
        if "commitTextIntake" in act_code[le_start:le_end]:
            e.append("LaunchedEffect 块内不得调用 commitTextIntake")
        le_start = act_code.find("LaunchedEffect(", le_start + 1)
        if le_start >= len(act_code):
            break

    rules = C("app/src/main/java/com/zwz/offlineledger/core/intake/TextIntakeRules.kt")
    for k in ["MAX_PATCH_CHARS", "状态词", "优惠词", "噪声", "分类", "val invalid"]:
        if k not in rules:
            e.append("TextIntakeRules 缺: " + k)
    insp = C("app/src/main/java/com/zwz/offlineledger/core/intake/TextIntakeInspector.kt")
    for k in ["MAX_CHARS = 20_000", "readyForAutoCommit", "页面特征不足",
              "原价 / 优惠 / 实付算术不成立", "ExtraAmountRules.validate"]:
        if k not in insp:
            e.append("Inspector 缺: " + k)
    if "import android." in insp:
        e.append("Inspector 不得依赖 Android 类（须可纯 JVM 单测）")

    repo = C("app/src/main/java/com/zwz/offlineledger/data/repository/LedgerRepository.kt")
    for k in ["commitTextIntake(draft: TextIntakeCommitDraft)", "countByImportSource",
              "ExtraAmountRules.validate", "withResolvedRoot(entity)", "rebuildAllBalancesInternal()",
              "ParsedAccount(draft.accountName, draft.cardLast4)"]:
        if k not in repo:
            e.append("Repository 缺: " + k)

    prefs = C("app/src/main/java/com/zwz/offlineledger/core/prefs/LedgerPrefs.kt")
    for k in ["textIntakeRulePatch", "textIntakeAutoCommitEnabled", "textIntakeLog",
              "KEY_TEXT_INTAKE_RULE_PATCH", "KEY_TEXT_INTAKE_AUTO_COMMIT", "KEY_TEXT_INTAKE_LOG"]:
        if k not in prefs:
            e.append("LedgerPrefs 缺: " + k)

    settings = C("app/src/main/java/com/zwz/offlineledger/ui/settings/SettingsScreen.kt")
    for k in ["自动记账 / 文本诊断", "TextIntakeActivity.ACTION_TEXT_INTAKE_DEBUG"]:
        if k not in settings:
            e.append("SettingsScreen 缺内部入口: " + k)

    strings = C("app/src/main/res/values/strings.xml")
    if "离线账本 · 自动记账" not in strings or "离线账本 · 文本诊断" in strings:
        e.append("入口标签未改为「离线账本 · 自动记账」")
    if "自动识别并记账" not in strings:
        e.append("PROCESS_TEXT 菜单标签未改")
    if "本批还不能记账" in C("app/src/main/AndroidManifest.xml"):
        e.append("Manifest 仍有过期注释「本批还不能记账」")

    prompt_doc = ROOT / "docs/自动记账规则生成提示词.md"
    if not prompt_doc.is_file():
        e.append("缺 docs/自动记账规则生成提示词.md")
    else:
        d = prompt_doc.read_text(encoding='utf-8')
        for k in ["状态词 +=", "优惠词 +=", "噪声 +=", "分类 支付宝账单分类",
                  "结构变化", "删掉订单号", "隐私提醒", "可复制的完整提示词"]:
            if k not in d:
                e.append("提示词文档缺: " + k)
    for k in ["状态词 +=", "优惠词 +=", "噪声 +=", "结构变化", "删掉订单号"]:
        if k not in act:
            e.append("RULE_PROMPT 常量缺关键句: " + k)

    for p in ["app/src/main/res/xml/backup_rules.xml", "app/src/main/res/xml/data_extraction_rules.xml"]:
        s = C(p)
        if 'domain="sharedpref" path="offline_ledger_prefs.xml"' not in s:
            e.append("未排除 prefs: " + p)

    test = ROOT / "app/src/test/java/com/zwz/offlineledger/core/intake/TextIntakeInspectorTest.kt"
    if not test.is_file():
        e.append("缺 TextIntakeInspectorTest.kt")
    else:
        t = test.read_text(encoding='utf-8')
        for k in ["all six alipay samples", "unknown page degrades", "rule patch only adds"]:
            if k not in t:
                e.append("单测缺场景: " + k)

    gd_raw = C("app/build.gradle.kts")
    gd = "\n".join(line.split("//")[0] for line in gd_raw.splitlines()).lower()
    if 'defaultVersionName = "0.2.72"' not in gd_raw:
        e.append("版本号未升 0.2.72")
    for dep in ["mlkit", "text-recognition", "coil", "glide", "okhttp", "retrofit"]:
        if dep in gd:
            e.append("不得新增依赖: " + dep)
    if "check_text_intake.py" not in C(".github/workflows/build.yml"):
        e.append("build.yml 未挂 check_text_intake.py")
    for p, k in [("docs/页面优化经验与避坑指南.md", "§7.71"),
                 ("openspec/project.md", "第 89 批"),
                 ("openspec/changes/text-intake-diagnosis/proposal.md", "第 89 批修正")]:
        if k not in C(p):
            e.append("文档未登记: " + p)

    if e:
        print("check_text_intake: FAIL")
        for x in e:
            print("  -", x)
        return 1
    print("check_text_intake: PASS（B 方案 / 严格充分条件 / 规则覆盖层 / 自动落库 / 撤销 / 去重 / 留痕 / 文档）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
