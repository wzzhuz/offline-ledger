#!/usr/bin/env python3
"""第 77 批：记一笔首屏分层 / 单按钮 / 无边框内联 / 动态色 / edge-to-edge 静态校验。"""
import pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
CH = ROOT / "openspec/changes/rethink-record-entry-layout"
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
    for r in rs:
        if r["s"]=="ADDED" and r["t"] in base:e.append("ADDED 冲突: "+r["t"])
        if r["s"]=="MODIFIED" and r["t"] not in base:e.append("MODIFIED 未命中基线: "+r["t"])
        if r["sc"]<1 or not r["rfc"]:e.append("缺 Scenario/RFC2119: "+r["t"])
    if len(rs)!=11:e.append("应为 11 条 Requirement，实际 %d"%len(rs))

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
    for k in ["MorePanel(",'"更多 ›"',"statusBarsPadding()","navigationBarsPadding()",
              "Color.Transparent","showMorePanel"]:
        if k not in rec:e.append("RecordEditorDialog 缺: "+k)
    # 首屏不得再各自占入口
    if "更多（备注 · 不计入收支）" in rec:e.append("旧「更多（备注…）」入口未删")
    if '"▸ 添加优惠"' in rec:e.append("旧优惠入口未并入 MorePanel")
    # 分类快捷栏必须保留
    if "CategoryStrip(" not in rec:e.append("分类快捷栏被误删（低摩擦优点）")

    tpl=read("app/src/main/java/com/zwz/offlineledger/ui/record/TemplateQuickPick.kt")
    if '"模板 ›"' not in tpl:e.append("模板未收敛成单按钮")
    if "chunked(TEMPLATES_PER_ROW)" in tpl:e.append("模板仍两行铺开")

    acc=read("app/src/main/java/com/zwz/offlineledger/ui/record/RecordEditorDialog.kt")
    i=acc.find("private fun AccountStrip(")
    j=acc.find("\n@Composable", i+10)
    if i>=0 and "AccountQuickRow(" in acc[i:(j if j>i else i+2600)]:
        e.append("AccountStrip 仍铺快捷 chips")

    db=read("app/src/main/java/com/zwz/offlineledger/data/local/AppDatabase.kt")
    if "version = 8," not in db:e.append("零改表口径被破坏")
    gd=read("app/build.gradle.kts")
    if 'defaultVersionName = "0.2.57"' not in gd:e.append("版本号未升 0.2.57")
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
    print("check_record_layout: PASS (10 delta / 分层 / 单按钮 / 无边框 / 动态色 / edge-to-edge / 零改表)")
    return 0
if __name__=="__main__":sys.exit(main())
