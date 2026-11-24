# 任务清单：账户排序拆分、模板正规化与回归修复

## 1. 阶段 0 对齐

- [x] 1.1 八条反馈逐条核查代码事实，区分「本会话回归 / 真缺口 / 需升表 / 外部权利」
- [x] 1.2 七件拍板（Q1A / Q2A 没弹窗 / Q3A / Q4A / Q5A / Q6A 小标志 / Q7 自定义）
- [x] 1.3 Q7 按使用者判断执行，如实登记判断与公开权利声明的差异、不写成结论
- [x] 1.4 核查 `Migrations.ALL = emptyArray()` 与 `MigrationContractTest` 断言；
      使用者补充「测试数据、不考虑升级迁移」→ **不注册 migration、测试零改动**

## 2. 四件套

- [x] 2.1 `proposal.md`：为什么改、Q7 处置、改什么、九条硬约束、分级判定
- [x] 2.2 `design.md`：核查事实、七条实现、知识产权登记、风险、10 条验收
- [x] 2.3 `specs/ledger-transactions/spec.md`：2 条 MODIFIED + 2 条 ADDED
- [x] 2.4 `specs/reporting-ui/spec.md`：3 条 ADDED
- [x] 2.5 `specs/privacy-security/spec.md`：1 条 ADDED（Q7 资源登记）
- [x] 2.6 `tasks.md`（本文件）

## 3. 数据层（第 60 批，升表）

- [x] 3.1 `AccountEntity` 加 `quickOrder` / `cardKind`
- [x] 3.2 `AppDatabase version = 6`；`Migrations.ALL` 保持空、不新增常量
      （测试数据不考虑迁移）
- [x] 3.3 `SeedData` accounts INSERT 补两列
- [x] 3.4 `BackupAccount` 加两字段（带默认值），`LedgerMappers` 双向补齐
- [x] 3.5 `MigrationContractTest` **零改动**（ALL 仍为空，断言原样通过）
- [x] 3.6 `AccountDao` 加 `allByQuickOrder()` / `updateQuickOrder()`，
      `UPDATE ... SET` 补 `cardLast4` / `cardKind`
- [x] 3.7 `LedgerRepository.updateAccountMeta` 加 `cardLast4` / `cardKind`，
      加 `reorderQuickAccounts(ids)`

## 4. ViewModel

- [x] 4.1 `addAccount` 的 `name` 不再拼 `(1122)`，写 `cardKind` 与 `quickOrder`
- [x] 4.2 `editAccount` 加 `last4` / `cardKind`，打通写路径
- [x] 4.3 新增 `commitQuickOrderAll(ids)`；`moveAccount` / `commitAccountOrderAll` 零改动
- [x] 4.4 `AssetsViewModel` 暴露按 quickOrder 排序的账户源给记一笔

## 5. UI

- [x] 5.1 `AssetHeader`：逐币种「净资产」+ 负债红字负号
- [x] 5.2 删除确认 `pendingDelete + LaunchedEffect(menu)` 解耦 + 加强弹窗
- [x] 5.3 `AccountCard`：展示名单次组合 + 旧库去重 + 12dp 小标志
- [x] 5.4 `EditAccountDialog`：卡号输入 + 初始化剥历史后缀
- [x] 5.5 `AccountTemplates` 扩到 48 条、正规全称、`cardKind` / `iconKey`
- [x] 5.6 `AccountTemplateSheet` 4 组 + 2 列 + 小标志
- [x] 5.7 `AccountQuickItem` 加 `cardKind`；`RecordEditorDialog` 候选按 quickOrder + 小标志
- [x] 5.8 新增 `AccountQuickSortScreen`（reorderable + 保存/放弃 + draftOrder）
- [x] 5.9 `LedgerApp` 加 `account-quick-sort` 路由；资产页蓝底区加入口
- [x] 5.10 新增 `AccountIconCatalog`（`when(iconKey)` + else 回退），**资源目录为空**
- [x] 5.11 `defaultVersionName` → `0.2.41`

## 6. 文档同步

- [x] 6.1 活文档 §7.42（不注册迁移、两套排序、Q7 登记）+ §8 第 60 批
- [x] 6.2 `openspec/project.md` 第 60 批
- [x] 6.3 六个静态检查、spec 结构、括号 / 字符串 / 引用自查全部 PASS

## 7. 校验与交付

- [x] 7.1 推送后按 commit sha 逐文件 tree + content 双校验
- [x] 7.2 push CI 与出包 CI 全绿；Release 附件 `state=uploaded` + `digest` 与 notes 一致
- [x] 7.3 实测编号回填活文档 / project.md / tasks.md
- [ ] 7.4 归档校验 —— 本地结构自查 PASS（8 条 Requirement / 15 Scenario /
      2 条 MODIFIED 命中基线），但正式归档校验未执行，本项保持 `[ ]`
      —— **第 60 批实测**：`cc3001ccf25d6934a4d394e1b73f0e7ea5f6abb7` / push CI `45349768835` /
      出包 CI `45350453088` / tag `v0.2.41` / SHA256 `9bad3782c6f2d09df0376afb8f5dc94cca3870b740bd0e84154e8e0dac7673f0` /
      dispatch 单次无重复
- [ ] 7.5 **明确写出仍未验证的真机项**，不用「CI 全绿」替代

## 8. 真机待验（第 60 批新增，刻意保留 `[ ]`）

- [ ] 8.1 卸载重装后全新库 `SeedData` 列对齐、账户与模板数据正常（**装前导出**）
- [ ] 8.2 旧备份恢复后 `quickOrder` / `cardKind` 取默认值且不崩；
      **覆盖安装不在验收范围**（测试数据，不考虑迁移）
- [ ] 8.3 删除确认一定弹出；取消不删、确认走既有归档/删除语义
- [ ] 8.4 编辑改卡号后四位列表只显示一次后缀；记一笔 chip 同样
- [ ] 8.5 分组内拖动后记一笔候选不变；全账户排序页保存后记一笔前 4 变化、放弃零变化
- [ ] 8.6 48 条模板 sheet 滚动顺滑、4 组不混排、12dp 小标志不挤名称
- [ ] 8.7 蓝底区两币种分别显示净资产、负债红字负号、无跨币种虚假总数
- [ ] 8.8 §8 第 45–59 批全部真机未验项整体继承
- [ ] 8.9 Q7 品牌图标资源补齐后的观感与许可登记（本批机制就位，资源待补）

## 9. 第 61 批（v0.2.42）—— 记一笔卡号 / 图标密度 / 卡种可编辑

- [x] 9.1 核查三条反馈：`cardLast4` 未进候选数据、分类快捷栏 40/34dp +
      12dp 小标志、导入缺 `cardKind` 且 Edit 的 `onConfirm` 写死原值
- [x] 9.2 新增 `AccountDisplayLabel.kt`（组合点 + 后缀剥除，不进 core）
- [x] 9.3 记一笔候选 /「当前：」/ 全账户弹层 / 搜索键统一带卡号
- [x] 9.4 分类快捷栏 40/34→28/24，`AccountKindBadge` 可配 10dp，Spacer 收窄
- [x] 9.5 Add / Edit 补三态卡种选择；Edit 传选中 `cardKind`
- [x] 9.6 导入建账户补 `quickOrder` 与保守默认 `cardKind`
- [x] 9.7 `reporting-ui` 修订 1 条 + 新增 1 条 Requirement；
      `ledger-transactions` 修订卡号条文并加 Scenario
- [x] 9.8 版本号 `0.2.42`；活文档 §7.43 / project.md 同步
- [ ] 9.9 推送后逐 blob tree + content 双校验
- [ ] 9.10 push CI 与出包 CI 全绿；Release `state=uploaded` + `digest` 一致
- [ ] 9.11 实测编号回填；归档校验仍保持 `[ ]`

## 10. 第 61 批真机待验（刻意保留 `[ ]`）

- [ ] 10.1 两张同名银行卡在记一笔四处分别显示不同后四位、搜索可命中
- [ ] 10.2 旧库含后缀的账户在记一笔也不出现 `(1122)(1122)`
- [ ] 10.3 分类快捷栏 28/24dp 与账户 10dp 小标志观感，分类管理网格仍 40dp
- [ ] 10.4 导入 LIABILITY 带「信」、ASSET 不误标；编辑改卡种后落库且重进保持
- [ ] 10.5 记一笔 / 资产页 / 独立排序页 / 模板 sheet 零回归
- [ ] 10.6 §8 第 45–60 批全部真机未验项整体继承
