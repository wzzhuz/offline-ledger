#!/usr/bin/env python3
"""第 86 批：把「零权限文本诊断入口」变成可执行断言。"""
import pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
C = lambda p: ROOT.joinpath(p).read_text(encoding='utf-8')
CH = ROOT / "openspec/changes/text-intake-diagnosis"


def main():
    e = []
    # 1. 四件套（本 change 刻意保持活跃）
    for f in ["proposal.md", "design.md", "tasks.md",
              "specs/privacy-security/spec.md", "specs/reporting-ui/spec.md"]:
        if not (CH / f).is_file():
            e.append("缺活跃 change 四件套: " + f)

    # 2. Manifest：两个 intent-filter、exported、excludeFromRecents、零新增权限
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

    # 3. 代码：只读，不落库、不联网、不常驻
    act = C("app/src/main/java/com/zwz/offlineledger/ui/intake/TextIntakeActivity.kt")
    for k in ["TextIntakeInspector.from", "AppLockScreen", "excludeFromRecents",
              "remember { mutableStateOf(initialRaw)", "SelectionContainer",
              "EXTRA_PROCESS_TEXT", "EXTRA_TEXT"]:
        if k not in act:
            e.append("Activity 缺: " + k)
    for k in ["LedgerRepository", "withTransaction", "Room", "startForeground",
              "registerListener", "rememberSaveable", "openOutputStream"]:
        if k in act:
            e.append("Activity 不得出现: " + k)

    insp = C("app/src/main/java/com/zwz/offlineledger/core/intake/TextIntakeInspector.kt")
    for k in ["MAX_CHARS = 20_000", "readyForAutoCommit", "import android."]:
        if k == "import android." and k in insp:
            e.append("Inspector 不得依赖 Android 类（须可纯 JVM 单测）")
        elif k != "import android." and k not in insp:
            e.append("Inspector 缺: " + k)

    test = ROOT / "app/src/test/java/com/zwz/offlineledger/core/intake/TextIntakeInspectorTest.kt"
    if not test.is_file():
        e.append("缺 TextIntakeInspectorTest.kt")

    # 4. 版本号、脚本挂载、文档登记
    if 'defaultVersionName = "0.2.65"' not in C("app/build.gradle.kts"):
        e.append("版本号未升 0.2.65")
    if "check_text_intake.py" not in C(".github/workflows/build.yml"):
        e.append("build.yml 未挂 check_text_intake.py")
    for p, k in [("docs/页面优化经验与避坑指南.md", "§7.68"),
                 ("openspec/project.md", "第 86 批")]:
        if k not in C(p):
            e.append("文档未登记: " + p)

    if e:
        print("check_text_intake: FAIL")
        for x in e:
            print("  -", x)
        return 1
    print("check_text_intake: PASS（零权限 / 两入口 / 应用锁 / 内存态 / 不落库 / 版本 / 文档）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
