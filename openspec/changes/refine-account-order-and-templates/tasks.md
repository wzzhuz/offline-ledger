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
- [x] 9.9 推送后逐 blob tree + content 双校验
- [x] 9.10 push CI 与出包 CI 全绿；Release `state=uploaded` + `digest` 一致
- [x] 9.11 实测编号回填；归档校验仍保持 `[ ]`

## 10. 第 61 批真机待验（刻意保留 `[ ]`）

- [ ] 10.1 两张同名银行卡在记一笔四处分别显示不同后四位、搜索可命中
- [ ] 10.2 旧库含后缀的账户在记一笔也不出现 `(1122)(1122)`
- [ ] 10.3 分类快捷栏 28/24dp 与账户 10dp 小标志观感，分类管理网格仍 40dp
- [ ] 10.4 导入 LIABILITY 带「信」、ASSET 不误标；编辑改卡种后落库且重进保持
- [ ] 10.5 记一笔 / 资产页 / 独立排序页 / 模板 sheet 零回归
- [ ] 10.6 §8 第 45–60 批全部真机未验项整体继承

## 11. 第 62 批（v0.2.43）—— 账户显示名全量排查

- [x] 11.1 全仓 grep 所有 `AccountEntity.name` / DAO 账户名投影，
      区分「展示名」与「数据键 / 图标入参 / 分类」
- [x] 11.2 抽 `accountLabel(name, last4)` 唯一实现，`accountBaseLabel` 复用
- [x] 11.3 `RecordRow` / `ReviewRow` 加 last4 字段，两条 SQL 补列
      （CSV 导出投影零改动 → **第 63 批注：已作废**，见第 13 节）
- [x] 11.4 筛选面板账户选项 + picker 搜索键
- [x] 11.5 明细筛选 chip fallback、明细列表副行、流水详情页
- [x] 11.6 统计页账户排行（连带下钻 chip）
- [x] 11.7 删除确认、账户详情标题、默认账户、对账 / 核对、回收站
- [x] 11.8 新增 `AccountDisplayLabelTest` 5 用例
- [x] 11.9 `ledger-transactions` 修订卡号条文；`reporting-ui` 新增 1 条 Requirement
- [x] 11.10 版本号 `0.2.43`；活文档 §7.44 / project.md 同步
- [x] 11.11 推送逐 blob 双校验 / 双 CI 全绿 / Release digest 一致
- [x] 11.12 实测回填；归档校验仍保持 `[ ]`

## 12. 第 62 批真机待验（`[ ]`）

- [ ] 12.1 筛选面板、picker 搜索、明细筛选 chip 带后四位且可区分同名卡
- [ ] 12.2 统计账户排行与下钻 chip 带后四位；`maxLines=1` 是否截断
- [ ] 12.3 明细列表副行 / 流水详情页 / 账户详情 / 删除确认 / 默认账户 /
      对账 / 核对 / 回收站全部一致
- [ ] 12.4 ~~CSV 导出与备份账户列仍是纯名~~ → **第 63 批改**：
       CSV 导出带卡号 + 复合匹配；加密备份仍是纯名
- [ ] 12.5 旧库含后缀账户所有页面只显示一次
- [ ] 12.6 §8 第 45–61 批全部真机未验项整体继承

## 13. 第 63 批（v0.2.44）—— 导出带卡号 + 导入复合匹配

- [x] 13.1 组合逻辑下沉 `core/AccountLabel.kt`；ui 层转发、4 处 import 改指 core
- [x] 13.2 新增 `AccountCsvMatch.kt`（解析 + 复合匹配 + 歧义报错）
- [x] 13.3 `CsvRow` / `CsvCodec` 加可选 `cardLast4` 第 13 列，三处 optional 兼容老文件
- [x] 13.4 `CsvRebuildResolver` 新签名 `List<AccountRef>` + 保留旧 `Set<String>` 重载
- [x] 13.5 `CsvSourceRow` 加列 + 导出 SQL 补 `a.cardLast4`；导出写展示名 + 卡号列
- [x] 13.6 四处消费点：`findImportCandidates` / `importRecords` /
      `rebuildFromOwnCsv` / `importFirefly`
- [x] 13.7 Firefly 映射：`targetCardLast4`、下拉显示展示名、同名多条不自动映射
- [x] 13.8 逐处标注第 62 批「CSV 导出 MUST NOT 改」作废（spec / proposal /
      design / tasks / 活文档 / project.md）
- [x] 13.9 单测：`AccountCsvMatchTest` 8、`CsvCodecTest` +3、
      `CsvRebuildResolverTest` +2、`AccountDisplayLabelTest` +1
- [x] 13.10 版本号 `0.2.44`；spec 新增 1 条 Requirement + 修订 1 条
- [x] 13.11 推送逐 blob 双校验 / 双 CI 全绿 / Release digest 一致
- [x] 13.12 实测回填；归档校验仍保持 `[ ]`

## 14. 第 63 批真机待验（`[ ]`）

- [ ] 14.1 两张同名卡导出 CSV 在 Excel 里 `account` / `cardLast4` 两列可区分、不乱码
- [ ] 14.2 该文件重建 / 导入后两笔流水分别回到原 id 账户
- [ ] 14.3 老 CSV 无列时唯一同名账户仍可导入；同名多条报错并整批回滚
- [ ] 14.4 旧库 `name` 已含后缀的账户导出再导入命中自身
- [ ] 14.5 Firefly 映射下拉能区分同名两卡、选中卡号精确落位
- [ ] 14.6 加密备份往返零回归、`duplicateKey()` 幂等性不变
- [ ] 14.7 §8 第 45–62 批全部真机未验项整体继承

## 15. 第 64 批（v0.2.45）—— 账户品牌图标补齐

- [x] 15.1 调研资源来源与授权：IconFont / Simple Icons /
      icongo/bank-logos / Wikimedia / 官方素材
- [x] 15.2 取回 21 家银行 SVG（MIT）+ 4 个 Simple Icons SVG（CC0）
- [x] 15.3 统一裁切 + 白圆底转 128×128 PNG，落地 `drawable-nodpi/` 26 个
- [x] 15.4 京东白条原创占位（不临摹商标）
- [x] 15.5 `AccountIconCatalog.drawableFor()` 补 27 个编译期 `when` 分支
- [x] 15.6 `assets/NOTICE` 追加第 64 批（[A]~[E] + 27 行逐项登记 + MIT 全文）
- [x] 15.7 `tools/build_account_icons.py` + `tools/account_icon_sources.md`
- [x] 15.8 `privacy-security` spec 修订 Q7 条文 + 新增 1 条 Requirement
- [x] 15.9 版本号 `0.2.45`；proposal / design / tasks / 活文档 / project.md 同步
- [x] 15.10 推送逐 blob 双校验（37 文件含 26 PNG）/ push CI
      `45386038965` / 出包 CI `45386948136` / tag `v0.2.45` /
      SHA256 `3bad8260338ff1c51cbc7d93d8ad500351fe80e92357eb432af117fa0e4f47f9` / 重复 run `45387413474` 已 cancel 且无二次发布
- [x] 15.11 实测回填（见活文档 §7.46 与 project.md）；
      正式归档校验仍保持 `[ ]`

## 16. 第 64 批真机待验（`[ ]`）

- [ ] 16.1 27 个键位三页均显示品牌图形，零网络请求
- [ ] 16.2 自定义账户仍回退中性圆 + 首字
- [ ] 16.3 卡种 12dp 小角标不与白圆底冲突、不挤名称
- [ ] 16.4 28/34dp 下银行横版含字 logo 的可辨识度与整体观感
- [ ] 16.5 R8 混淆后资源引用正常、NOTICE 与随包资源一一对应
- [ ] 16.6 §8 第 45–63 批全部真机未验项整体继承

