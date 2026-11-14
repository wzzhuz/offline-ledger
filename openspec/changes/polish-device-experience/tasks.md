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
- [x] CI 与出包结果回填（见 `design.md` 第 11 节）

## 9. 第 3 批：真机反馈（6 条）

### 9.1 模板草稿语义与 CRUD
- [x] `Repository.createTemplate/updateTemplate` 放宽金额为可空；`recordFromTemplate` 拦截 0 元草稿
- [x] `TemplateQuickPickBar` 改为回调模板 + 选中态高亮，不再直接生成流水
- [x] `RecordEditorViewModel.applyTemplate` 自动填充，金额留空、时间取现在、全字段可改
- [x] 模板金额可留空（周期规则仍必填）、列表行显示类型 / 金额待填 / 分类路径
- [x] 模板编辑 / 删除（二次确认）/ 类型 segment 高亮 / 转账不显示交易对方
- [x] `TemplateDao.deleteById` + `Repository.deleteTemplate` + `TemplateManagerViewModel.updateTemplate`

### 9.2 账户管理
- [x] `AccountDao` 补 `updateType` / `setArchived` / `deleteById` / `updateSortOrder`
- [x] `Repository` 补 `updateAccountType` / `setAccountArchived` / `deleteAccountIfUnused` / `reorderAccounts`
- [x] `AssetsViewModel` 补类型修正 / 删除 / 归档恢复 / 上移下移 / 显示已归档开关
- [x] 资产页编辑弹窗加账户类型；账户卡加详情/对账/编辑/↑/↓/归档恢复/删除
- [x] 账户卡留白再压缩：单行名称与余额、附加信息合并一行、小按钮一行放下
- [x] 上一批遗留的「自定义账户排序」本批兑现

### 9.3 图表
- [x] `ChartData.seriesMaxSingle` + `ChartData.yAxisLabels`（竖轴 5 个元刻度）
- [x] `LineChart` 加 `yLabels` / `chartHeight` / `labelEvery`，横轴标签均匀铺开
- [x] 月度与年度单线按自身最大值缩放；年度也支持支出/收入/两者切换
- [x] 新增 `ChartFullscreenActivity`（横屏、全标签、双指视觉缩放、点击取值）
- [x] Manifest 注册横屏 Activity（`exported=false`，零新增权限）
- [x] 三张图各加「⛶ 横屏放大」入口
- [x] `ChartAxisTest` 纯 JVM 单测（独立 max / 竖轴刻度 / 空与负值）

### 9.4 分类管理
- [x] `CategoryDao` 补 `deleteById` / `setArchived` / `linkedRecordCount`
- [x] `Repository.deleteCategory`（有流水归档、无流水删除）/ `restoreCategory`
- [x] `SettingsViewModel` 补删除与恢复；UI 接上已有的 `moveCategory`
- [x] 父级选择改「搜索 + 可滚动全量一级列表」，不再被截断
- [x] 已归档分类可展开恢复

### 9.5 详情标题
- [x] `AccountDetailDialog` 接收资产页实时账户名，移除重复改名输入框

### 9.6 门禁与收尾
- [x] `check_transaction_annotations.py` 写方法清单扩到 39 个，全部在事务内
- [x] 六个静态检查本地全 PASS
- [x] CI 与出包结果回填（见 `design.md` 第 12.5 节）：`43583695307` / `43584134091` 均 success，坚果云上传成功
- [x] 临时诊断步骤与 `diag/test.log` 已移除，远端核查无残留
- [ ] 真机实测：按使用者决定整体推迟，清单仍含 R8 启动、SQLCipher 开库、
      BiometricPrompt、SAF 选择器、Excel 打开 CSV、5 万条切月耗时、
      横屏图表与双指缩放手感、模板填充与账户排序手感
- [ ] 归档本 change（需使用者明确发话）
