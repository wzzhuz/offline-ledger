#!/usr/bin/env python3
"""第 77 + 80 批：记一笔 chips 直达 / 底部 sheet / 联想回输入框 / 动态色 / edge-to-edge 静态校验。"""
import pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
_ACTIVE = ROOT / "openspec/changes/rethink-record-entry-layout"
_ARCHIVED = ROOT / "openspec/changes/archive/2026-11-26-rethink-record-entry-layout"
# 第 80 批归档后 change 移入 archive，优先活跃目录、回退归档目录。
CH = _ACTIVE if (_ACTIVE / "proposal.md").is_file() else _ARCHIVED
BASE = ROOT / "openspec/specs"
def read(p): return ROOT.joinpath(p).read_text()

def main():
    e = []
    for f in ["proposal.md","design.md","tasks.md","specs/reporting-ui/spec.md","specs/privacy-security/spec.md"]:
        if not (CH/f).is_file(): e.append("缺四件套: "+f)

    rs=[]
    for f in sorted((CH/"specs").rglob("spec.md")):
        sect,cur=None,None
        for l in f.read_text().splitlines():
            m0=re.match(r"^## (ADDED|MODIFIED|REMOVED) Requirements",l)
            if m0: sect=m0.group(1)
            m=re.match(r"^### Requirement: (.+)",l)
            if m:
                cur={"t":m.group(1).strip(),"s":sect,"sc":0,"rfc":False};rs.append(cur)
            if cur:
                if re.match(r"^#### Scenario:",l): cur["sc"]+=1
                if re.search(r"\b(SHALL|MUST|MUST NOT|MAY|SHOULD)\b",l): cur["rfc"]=True
    base=set()
    for f in BASE.rglob("spec.md"):
        for m in re.finditer(r"^### Requirement: (.+)$",f.read_text(),re.M):base.add(m.group(1).strip())
    archived = CH == _ARCHIVED
    for r in rs:
        if archived:
            # 归档后：delta 的每一条（含 ADDED）都必须已合并进 specs。
            if r["t"] not in base:e.append("归档后 delta 未合并: "+r["t"])
        else:
            if r["s"]=="ADDED" and r["t"] in base:e.append("ADDED 冲突: "+r["t"])
            if r["s"]=="MODIFIED" and r["t"] not in base:e.append("MODIFIED 未命中基线: "+r["t"])
        if r["sc"]<1 or not r["rfc"]:e.append("缺 Scenario/RFC2119: "+r["t"])
    if len(rs)!=12:e.append("应为 12 条 Requirement，实际 %d"%len(rs))

    theme=read("app/src/main/java/com/zwz/offlineledger/ui/theme/LedgerTheme.kt")
    for k in ["dynamicColor: Boolean = true","dynamicLightColorScheme","Build.VERSION.SDK_INT",
              "Build.VERSION_CODES.S","lightColorScheme("]:
        if k not in theme:e.append("LedgerTheme 缺: "+k)
    if "LedgerColors.applyReverseColors" in theme:e.append("反转开关不应挪进 Theme")

    main_act=read("app/src/main/java/com/zwz/offlineledger/MainActivity.kt")
    for k in ["WindowCompat.setDecorFitsSystemWindows","safeDrawingPadding"]:
        if k not in main_act:e.append("MainActivity 缺 edge-to-edge: "+k)

    app=read("app/src/main/java/com/zwz/offlineledger/ui/LedgerApp.kt")
    if "contentWindowInsets = WindowInsets(0, 0, 0, 0)" not in app:
        e.append("Scaffold 未置 contentWindowInsets=0（双重 padding）")

    rec=read("app/src/main/java/com/zwz/offlineledger/ui/record/RecordEditorDialog.kt")
    # 第 80 批：撤掉「更多」层，低频项由键盘上方单行 5 枚 chips 各自直达底部 sheet。
    for k in ["EntryChipBar(","BottomPickerSheet(","AccountPickerSheet(","NoteSheet(",
              "CounterpartySuggestionRow(","TemplatePickerSheet(","onFocusChanged",
              "statusBarsPadding()","navigationBarsPadding()","Color.Transparent"]:
        if k not in rec:e.append("RecordEditorDialog 缺: "+k)
    for k in ["private fun MorePanel(","private fun MoreEntry(","private fun AccountStrip(",
              '"更多 ›"','"模板 ›"',"showMorePanel"]:
        if k in rec:e.append("第 80 批残留旧结构: "+k)
    # 分类快捷栏必须保留（低摩擦优点）
    if "CategoryStrip(" not in rec:e.append("分类快捷栏被误删（低摩擦优点）")
    # chips 点击区不得低于 40dp
    if "heightIn(min = 40.dp)" not in rec:e.append("chips / 联想条点击区被压缩到 40dp 以下")

    # 第 81 批：转账态两卡常驻 + 移除转账账户 chip + 四处一键清空
    for k in ["TransferAccountCards(","TransferAccountCard(","ClearFieldIcon(",
              "lockTarget","清空描述","清空备注"]:
        if k not in rec:e.append("第 81 批缺: "+k)
    for k in ['"转出/转入"',"账号 chips 转账"]:
        if k in rec:e.append("第 81 批残留: "+k)
    # 第 83 批：账户搜索清空随账户网格下沉到通用组件，rec 内剩 3 处调用 + 定义。
    if rec.count("ClearFieldIcon(")<4:
        e.append("一键清空出口未覆盖收款方/描述/备注（含定义应 >=4 处）")

    # 归档后：第 81 批 delta 的每条 Requirement 必须已合并回主 spec（§4.2）。
    a81 = sorted(ROOT.glob(
        "openspec/changes/archive/*polish-transfer-accounts-and-clear-fields*/specs/*/spec.md"))
    for f in a81:
        for m in re.finditer(r"^### Requirement: (.+)$", f.read_text(), re.M):
            if m.group(1).strip() not in base:
                e.append("第 81 批 delta 未合并: "+m.group(1).strip())
    a82 = sorted(ROOT.glob(
        "openspec/changes/archive/*account-icon-ref-and-weighted-chips*/specs/*/spec.md"))
    for f in a82:
        for m in re.finditer(r"^### Requirement: (.+)$", f.read_text(), re.M):
            if m.group(1).strip() not in base:
                e.append("第 82 批 delta 未合并: "+m.group(1).strip())

    # ---- 第 82 批：非等宽 chips / 账户图标接回 / 本机导入 / 升表 9 ----
    for k in ["EntryChip(accountLabel, 2.2f", 'EntryChip("模板", 0.9f',
              "weight(chip.weight)", "AccountIconBox(", "accountSecondaryLabel("]:
        if k not in rec:e.append("第 82 批缺: "+k)
    if "Cents(a.balanceCents)" in rec:e.append("账户选择面板仍显示余额（使用者明确不需要）")
    if "AccountKindBadge(" in rec:e.append("账户 sheet 仍用 12dp 小角标而非 AccountIconBox")
    ent=read("app/src/main/java/com/zwz/offlineledger/data/local/entity/AccountEntity.kt")
    if 'ColumnInfo(name = "iconRef")' not in ent:e.append("accounts 未加 iconRef 列")
    dao=read("app/src/main/java/com/zwz/offlineledger/data/local/dao/AccountDao.kt")
    if "iconRef = :iconRef" not in dao:e.append("编辑账户未写回 iconRef")
    cust=ROOT/"app/src/main/java/com/zwz/offlineledger/ui/assets/AccountCustomIcons.kt"
    if not cust.is_file():e.append("缺 AccountCustomIcons 本机导入组件")
    else:
        c=cust.read_text()
        for k in ["contentResolver","BitmapFactory","openInputStream","filesDir","透明底"]:
            if k not in c:e.append("AccountCustomIcons 缺: "+k)
        if "http://" in c or "https://" in c:e.append("导入组件不得出现网络地址")
    cat=read("app/src/main/java/com/zwz/offlineledger/ui/assets/AccountIconCatalog.kt")
    if "AccountCustomIcons.load" not in cat:e.append("AccountIconBox 未接自定义图标")
    notice=read("app/src/main/assets/NOTICE")
    if "本机导入图标" not in notice:e.append("NOTICE 未登记本机导入图标授权口径")

    # ---- 第 83 批：通用账户选择器 / 双布局 / 撤老下拉 ----
    asel=ROOT/"app/src/main/java/com/zwz/offlineledger/ui/components/AccountSelectSheet.kt"
    if not asel.is_file():e.append("缺通用 AccountSelectSheet")
    else:
        a=asel.read_text()
        for k in ["AccountPickerLayout","GLOBAL","GROUPED","quickOrder","sortOrder",
                  "AccountTypeOrder","accountSecondaryLabel","AccountIconBox(",
                  "allowNone","noneLabel","rememberAccountPickerLayout",
                  "ModalBottomSheet(","ExperimentalMaterial3Api","LayoutGridGlyph",
                  "AccountSlot.None","Icons.Filled.List","cardLast4",
                  "LazyColumn(","chunked(2)",
                  "rememberModalBottomSheetState(","skipPartiallyExpanded = true",
                  "sheetState = sheetState"]:
            if k not in a:e.append("AccountSelectSheet 缺: "+k)
        for k in ["FilterChip(","OutlinedTextField(","Icons.Filled.Close",
                  "清空账户搜索","搜索账户名","DialogProperties"]:
            if k in a:e.append("第 84 批残留旧控件: "+k)
        if "Cents(" in a or "balanceCents" in a:e.append("通用账户选择器仍显示余额")
    prefs=read("app/src/main/java/com/zwz/offlineledger/core/prefs/LedgerPrefs.kt")
    if "accountPickerLayout" not in prefs:e.append("偏好未持久化账户布局（第83批 3B）")
    tplm=read("app/src/main/java/com/zwz/offlineledger/ui/templates/TemplateManagerDialog.kt")
    if "DropdownSelector(" in tplm:e.append("模板/周期仍残留老下拉")
    for k in ["CategorySelectDialog(","AccountSelectDialog(","SelectorRow(","不指定账户"]:
        if k not in tplm:e.append("模板/周期缺: "+k)
    filt=read("app/src/main/java/com/zwz/offlineledger/ui/detail/FilterSheet.kt")
    if "AccountSelectDialog(" not in filt:e.append("明细/统计账户筛选未换通用选择器")
    if "AccountPickerSheet(" not in rec:e.append("记一笔账户薄封装被误删")
    if "AccountSelectDialog(" not in rec:e.append("记一笔账户 sheet 未接通用选择器")
    a83 = sorted(ROOT.glob(
        "openspec/changes/archive/*account-picker-layouts-and-shared-selectors*/specs/*/spec.md"))
    for f in a83:
        for m in re.finditer(r"^### Requirement: (.+)$", f.read_text(), re.M):
            if m.group(1).strip() not in base:
                e.append("第 83 批 delta 未合并: "+m.group(1).strip())
    a84 = sorted(ROOT.glob(
        "openspec/changes/archive/*simplify-account-picker-sheet*/specs/*/spec.md"))
    for f in a84:
        for m in re.finditer(r"^### Requirement: (.+)$", f.read_text(), re.M):
            if m.group(1).strip() not in base:
                e.append("第 84 批 delta 未合并: "+m.group(1).strip())
    # ---- 第 85 批（修 bug）：跳过半屏态 + 系统返回直接关闭 ----
    spec_txt=read("openspec/specs/reporting-ui/spec.md")
    for k in ["skipPartiallyExpanded = true","第 85 批修订","系统返回直接关闭"]:
        if k not in spec_txt:e.append("主 spec 未登记第 85 批修订: "+k)
    if "EntryChip(accountLabel, 2.2f" not in rec:
        e.append("收支态账户 chip 未获最宽权重 / 入口被误删")

    tpl=read("app/src/main/java/com/zwz/offlineledger/ui/record/TemplateQuickPick.kt")
    if "TemplatePickerSheet(" not in tpl:e.append("模板未改成一羽式底部 sheet")
    if "ModalBottomSheet(" in tpl or "AlertDialog(" in tpl:
        e.append("模板 sheet 不得用实验 API 或居中 AlertDialog")

    db=read("app/src/main/java/com/zwz/offlineledger/data/local/AppDatabase.kt")
    if "version = 9," not in db:e.append("第 82 批升表口径被破坏（version 必须为 9）")
    gd=read("app/build.gradle.kts")
    if 'defaultVersionName = "0.2.70"' not in gd:e.append("版本号未升 0.2.70")
    wf=read(".github/workflows/build.yml")
    if "check_record_layout.py" not in wf:e.append("build.yml 未挂脚本")
    man=read("app/src/main/AndroidManifest.xml")
    # 注释里会提到「不声明 INTERNET」，只检查真正的 uses-permission 行。
    if re.search(r'<uses-permission[^>]*android\.permission\.INTERNET', man):
        e.append("不得引入 INTERNET")
    for p,k in [("docs/页面优化经验与避坑指南.md","§7.59"),("openspec/project.md","第 77 批")]:
        if k not in read(p):e.append("文档未登记: "+p)

    if e:
        for x in e:print("  FAIL",x)
        print("check_record_layout: %d 项失败"%len(e));return 1
    print("check_record_layout: PASS (10 delta / 分层 / chips / 通用选择器 / 双布局 / 账户图标 / 升表 9 / 动态色 / edge-to-edge)")
    return 0
if __name__=="__main__":sys.exit(main())
