# 任务清单

## 1. 归档与准备
- [x] 归档 6c-2（10 ADDED + 1 MODIFIED 按标题替换 + 移入 archive + 校验）
- [x] 写本 change 四件套
- [x] `project.md` 落档「真机体验口径」覆盖声明与默认本地化修正

## 2. 基础层：默认值与偏好
- [x] `LedgerCurrency.DEFAULT = CNY` + `SUPPORTED` 列表
- [x] `LedgerClock.zoneId = Asia/Shanghai`
- [x] `SeedData` 预置账户 SQL 改 CNY
- [x] `LedgerPrefs.defaultCurrency`
- [x] 设置页默认币种下拉 + 批量统一币种按钮
- [x] `SettingsViewModel` / `SettingsState` 同步

## 3. 数据结构：收款方与版本重置
- [x] `RecordEntity` / `TrashEntryEntity` / 模板 / 规则 Entity 加 `counterparty`
- [x] `RecordEntity` / `TrashEntryEntity` 的 `defaultValue` 改 CNY
- [x] `AppDatabase version = 1`；`Migrations.ALL = emptyArray()`
- [x] 备份 DTO（`BackupRecord` / `BackupTemplate` / `BackupRecurringRule`）加 `counterparty`
- [x] `LedgerMappers` 全量补 `counterparty` 映射；`exportSnapshot()` 显式传 CNY

## 4. 账户编辑与批量币种
- [x] `AccountDao.updateAccountMeta` 支持期初余额与币种
- [x] `RecordDao` / `TrashDao` 批量币种更新
- [x] `LedgerRepository.updateAccountMeta` 末尾重算；新增 `applyCurrencyToAll`
- [x] 资产页新增「编辑账户」弹窗（期初余额 / 币种 / 名称 / 额度 / 计入净资产）
- [x] `AssetsViewModel.addAccount` 取默认币种

## 5. 记一笔：紧凑 / 日历 / 收款方 / 键盘
- [x] `EditorState` 加 `counterparty`；`setCounterparty` / `setDate` / `counterparties` 常用收款方
- [x] `RecordDao.distinctCounterparties()`；`pagedFiltered` 与 `csvRows` 带 `counterparty`
- [x] 重写 `RecordEditorDialog`：横向分类、单行账户+日期、备注/收款方首屏可见、更多折叠
- [x] 纯数字键盘（13 键）
- [x] Material3 `DatePickerDialog` 日历选择
- [x] 明细行显示收款方

## 6. 底栏与模板
- [x] `LedgerApp` 移除悬浮 FAB，改为底栏内嵌 `+`
- [x] 模板下拉按收支类型过滤 + 一级分组 + 标签带归属
- [x] 模板列表行显示分类；类型切换清空分类；保存前校验类型一致

## 7. CSV 与备份
- [x] `CsvCodec.HEADER` 加可选 `counterparty` 列
- [x] `CsvRow` 加 `counterparty` 默认值；导出 / 解析 / 导入全链路带 `counterparty`
- [x] 去重键不含 `counterparty`（保持既有语义）

## 8. 测试与验证
- [x] 新增纯 JVM 单测：默认币种/时区、counterparty 备份与 CSV 往返、8 列 CSV 兼容
- [x] 六个静态检查本地预跑
- [x] 单次 commit 推送后用远端 API 逐文件校验 sha（PASS）
- [x] CI 全绿（`43460356310` success；首推 `43459412013` 失败已修）
- [x] release 手动触发并上传坚果云（`43461666785` success，`nsfalse`），产出可装的 R8 包

## 9. 收尾
- [x] `design.md` 回填实现期偏离与 CI 实测
- [ ] 使用者确认后归档（ADDED 合并 + 2 MODIFIED 按标题替换 + 移入 archive）

> 真机实测整体推迟到完成度更高后统一装机体验；但本 change 的包会被使用者
> 立即安装，因此第 8 节的 release 出包是必须的。卸载重装提醒见 `design.md` 第 3 节。


## 10. 第 2 批真机反馈（阶段 8，与第 1 批合并为同一 change）

- [x] 字段语义统一：`payee` 全量改为 `counterparty`（交易对方），支出「收款方」/ 收入「付款方 · 来源」/ 转账隐藏
- [x] 交易对方联想：新增 `searchCounterparties`，键入一两个字即从全部历史模糊匹配；空输入仍列最近 8 个
- [x] 记一笔可选具体时间（framework TimePickerDialog，保留已选日期），明细行显示 `HH:mm`
- [x] 分类按收支类型统一过滤：`setType` 重置分类 + `save()` 校验类型一致
- [x] 账户选择：常用账户（最近 30 天频次）平铺 6 个 + 「全部账户」弹层按名称搜索
- [x] 月度趋势默认单线（支出 / 收入 / 两者可切换），不再两条糊在一起
- [x] 年度新增「资产余额趋势」单独一张图，按当前币种累计推算、不混算；点图弹窗显示具体金额
- [x] 资产页账户卡片紧凑化（留白压缩，首屏可多显示数个账户）
- [x] 新增只读查询 4 条、`ChartData.assetSeries` 纯 JVM 算法 + 单测
- [ ] 自定义账户排序（上移 / 置顶）：本批未做，见 `design.md` 第 10 节，留下一批
- [ ] CI 与出包结果回填（见 `design.md`）
