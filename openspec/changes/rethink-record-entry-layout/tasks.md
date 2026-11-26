# tasks · rethink-record-entry-layout

- [x] 0.1 阶段 0 对齐（Q1A Q2A Q3A Q4B Q5B，验收按拟）
- [x] 0.2 新建 change 四件套 + 静态脚本
- [x] 1.1 `LedgerTheme` 加 dynamicColor 开关 + API 31+ 动态色
- [x] 1.2 `MainActivity` edge-to-edge + 根 safeDrawingPadding
- [x] 1.3 `LedgerApp` Scaffold contentWindowInsets 置 0
- [x] 2.1 `TemplateQuickPickBar` 改单按钮
- [x] 2.2 `AccountStrip` 隐藏 chips、保留按钮与全量弹层
- [x] 2.3 收款方/描述去边框（透明 colors）
- [x] 2.4 新增「更多 ›」入口 + `MorePanel`（Dialog 底部 Surface）
- [x] 2.5 `RecordEditorDialog` 首屏重排 + Dialog insets 避让
- [x] 3.1 静态脚本断言覆盖 12 项（纯 UI 批次，新增纯 JVM 单测无收益，已在 design 登记）
- [x] 3.2 版本号 0.2.56、活文档 §7.59 / project.md 登记
- [x] 3.3 推送 → 双 CI → 出 v0.2.56 包 → 交付说明

## 第 80 批（v0.2.59）：真机反馈修订

> 阶段 0 对齐（五项结论）与使用者三项确认完成后才动文件；本批与第 77 批
> **并进同一个 change 一起归档**，不新建 change、不产生两层 delta。

- [x] 4.1 核查远端 HEAD / CI / Release 与第 77 批 change 归档状态
- [x] 4.2 阶段 0 五项对齐结论 + 三项确认（A 单行 5 枚 / 一起归档 / 联想回输入框）
- [x] 4.3 撤 `MoreEntry` / `MorePanel` / `showMorePanel`，新增 `EntrySheet`
- [x] 4.4 新增 `BottomPickerSheet`（同包 public）供 5 类弹层复用
- [x] 4.5 新增 `EntryChipBar` 单行 5 枚（≥40dp、编辑态摘要）
- [x] 4.6 `AccountPickerSheet`：两列 + 余额 + 搜索 + 转账分段
- [x] 4.7 `TemplateQuickPick.kt` 重写为 `TemplatePickerSheet`（2 次点击预填）
- [x] 4.8 `NoteSheet`（备注 + 不计入）/ 优惠 chip 复用 `ExtraAmountSection`
- [x] 4.9 收款方联想条回到输入框正下方（聚焦 / 有内容才出现，互斥共用一条）
- [x] 4.10 `check_record_layout.py` 断言翻转 + archive 路径回退 + 版本号 0.2.59
- [x] 4.11 活文档 §7.63 / §8 待验更新、`project.md` 第 80 批登记
- [x] 4.12 delta 整块改写（7 MODIFIED 最终形态 + 4 ADDED）、四件套修订
- [x] 4.13 12 个静态检查全 PASS
- [x] 4.14 推送 → 双 CI → 出 v0.2.59 包
- [x] 4.15 归档：delta 合并回 specs（reporting-ui + privacy-security）+ 移入 archive
- [x] 4.16 用 GitHub API 按 commit sha 逐文件校验远端内容

## 实测结果（2026-11-26）

- push CI `45746395557` 一次全绿；出包 CI `45747016651` 全绿；单次 dispatch
- tag `v0.2.56 → 7e68e83188c2`；APK 3,512,596 B /
  SHA256 `58f5cf237e806adcaf15b060c0eb2c1f48fcd20e29b58f900873fbace455ec83`
- 零改表 → 覆盖安装；一次成包、无诊断 commit、`build.yml` 无临时改动
- 12 个静态检查全 PASS；出包轮询一次查询层超时，短调用复查确认 success，无重发
- §8 真机待验项全部保持 `[ ]`；第 45–76 批既有待验项整体继承



## 第 80 批实测（归档时回填）

- 归档目录 `openspec/changes/archive/2026-11-26-rethink-record-entry-layout/`
- 两批共用一份 delta：reporting-ui `7 MODIFIED + 4 ADDED`、
  privacy-security `1 ADDED`，合计 12 条 Requirement
- 既有 Requirement 标题一律不改（归档匹配基准），正文为最终形态
- 真机待验项全部保持 `[ ]`，见活文档 §7.63
