# 任务清单：资产账户页借鉴一羽的 UI 优化

## 1. 阶段 0 对齐

- [x] 1.1 五项对齐结论、三项拍板（Q1A / Q2A / Q3A）
- [x] 1.2 核查本 App 真实代码，**收回上一轮把一羽参考图误当现状**的判断
- [x] 1.3 逐条核对七条基线硬约束，登记「不能照抄」的四类元素

## 2. 四件套

- [x] 2.1 `proposal.md`：为什么改 / 改什么 / 不改什么 / 五项对齐 / 分级判定
- [x] 2.2 `design.md`：核查结论、约束表、三处实现、风险与验证
- [x] 2.3 `specs/reporting-ui/spec.md`：ADDED 三条 Requirement
- [x] 2.4 `specs/ledger-transactions/spec.md`：MODIFIED「快捷账户模板替代预制账户行」
      （标题沿用基线原名命中整块替换，MUST NOT 追加 ADDED 留两条相反要求）
- [x] 2.5 `tasks.md`（本文件）

## 3. 代码（第 59 批）

- [x] 3.1 `AssetsScreen.kt`：顶部蓝底身份区（逐币种小计，MUST NOT 单一净资产总数）
- [x] 3.2 分组标题改蓝色小竖条 + 账户数；不显示跨币种分组金额
- [x] 3.3 `AccountCard`：图标圆 + 名称/卡号后四位 + 金额右对齐 + 副文案；
      5 个 `MiniAction` 删除、7 项操作收进「⋮」；拖柄改整格长按、去掉「≡」；
      `ListDivider()` 保留
- [x] 3.4 `AddAccountDialog` 改近全屏 `Dialog`：蓝底 TopBar + 分组小竖条 +
      图标名称小卡 + 底部大保存；字段与 `onConfirm` 签名零改动
- [x] 3.5 新增 `ui/assets/AccountTemplateSheet.kt`：遮罩 + 底部圆角卡 +
      按 group 分组 + 2 列图标网格 + 「自定义名称」入口 + 纯 `onPick` 回填
- [x] 3.6 `EditAccountDialog` / `AssetsViewModel` / `AccountEntity` /
      `LedgerApp` / `AccountTemplates` 零改动；`AppDatabase.version = 5` 不变
- [x] 3.7 `defaultVersionName` → `0.2.40`

## 4. 文档同步

- [x] 4.1 活文档 §7.41（纠错 + 七条约束 + 等价适配）+ §8 第 59 批待验
- [x] 4.2 `openspec/project.md` 第 59 批
- [x] 4.3 六个静态检查、spec 结构、括号 / 字符串自查全部 PASS

## 5. 校验与交付

- [x] 5.1 推送后按 commit sha 逐文件 tree + content 双校验（`7b4515fba3d5`，10 文件 `远端校验: PASS`）
- [x] 5.2 push CI `45329111449` 与出包 CI `45329663851` 全绿；Release 附件 `state=uploaded` + `digest` 与 notes 一致
- [x] 5.3 实测编号回填活文档 §7.41 / project.md / tasks.md
- [ ] 5.4 归档校验（无重复 Requirement 标题、每条 ≥1 Scenario、MODIFIED 整块替换）
      —— 本地结构自查已 PASS（4 条 Requirement / 8 个 Scenario / MODIFIED 标题命中基线），
      但 5.4 归档校验未正式执行，本项保持 `[ ]`，归档需使用者明确说「可以归档」
      —— **第 59 批实测**：`7b4515fba3d5` / CI `45329111449` / `45329663851` /
      tag `v0.2.40` / SHA256 `4c1474fbd2625adfd19258713ba1a9d70e69a77aeb9c8e71901df1d41166c9e1`；
      dispatch 因工具层 502 重放两次，第二个 run `45329992971` 已 cancel
- [ ] 5.5 **明确写出仍未验证的真机项**，不用「CI 全绿」替代观感 / 手势 / 键盘验证

## 6. 真机待验（第 59 批新增，刻意保留 `[ ]`）

- [ ] 6.30 整格长按拖动与「⋮」菜单点击互不误触；上移 / 下移在菜单中可达
- [ ] 6.31 360dp 屏蓝底区两币种账户分别显示小计、不出现跨币种单一净资产总数
- [ ] 6.32 新建页键盘弹起时底部大保存按钮仍可见；表单字段与保存逻辑零回归
- [ ] 6.33 模板 sheet 2 列下「工行储蓄卡」等 5 字名完整可见；点选自动填名称与类型、
      「自定义名称」不误填；`needsLast4` 模板仍可填卡号后四位并落库
- [ ] 6.34 覆盖安装（v0.2.39 → v0.2.40）数据不丢；4 Tab、记一笔 / 统计 /
      分类 / 明细 / 对账零回归；`EditAccountDialog` 仍是小弹窗（已知边界）
- [ ] 6.35 §8 第 45–58 批全部真机未验项整体继承
