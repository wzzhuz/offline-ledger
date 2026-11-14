# 设计：真机体验打磨

## 0. 一个必须写清楚的措辞修正

前几轮我多次把「三段 Migration 叠加」列成最大残余风险。使用者指出：
**至今只装过一次、且只用测试数据，稳定版前不会保留旧库**。
全新安装走 `onCreate`，`Migration(1→2)` / `(2→3)` / `(3→4)` 从未在真机执行过，
也从未作用于任何真实账本。所以真实风险是「迁移脚本 + Room schema 校验 +
SQLCipher 打开数据库一次都没在真机验证」，不是「升级会丢数据」。

本 change 按使用者授权进一步简化：重置 `version = 1`、`Migrations.ALL` 清空，
迁移常量与兼容测试**保留但冻结**（删掉反而多一处改动面，且测试会红）。

## 1. 逐条查证结论（都读了代码，不靠猜）

| # | 反馈 | 查证 |
|---|---|---|
| 1 | 中国大陆，默认人民币；设置入口在哪 | `LedgerCurrency.DEFAULT = "HKD"`、`LedgerClock` 固定 `Asia/Hong_Kong`、`SeedData` 四个预置账户 SQL 硬编码 `'HKD'`、`RecordEntity.defaultValue = "'HKD'"`；`LedgerPrefs` 只有阈值/配色/FLAG_SECURE/应用锁/PIN，**设置入口根本不存在**。这是按「用户在香港」的错误假设写的，使用者实际在江苏南京。香港与上海同为 UTC+8、无夏令时，日期分组结果一直正确，改时区只修语义。 |
| 2 | 预置账户怎么设初始余额 | 新增账户弹窗有「期初余额（元，默认 0）」，但 `updateAccountMeta` 只支持名称/计入净资产/额度/排序，资产页**没有任何编辑账户入口**。目前只能走「余额校准」，而那是给「历史不再追溯」用的，不改进初余额。 |
| 3 | 日期只能一天天挪 | `DateRow` 只有「前一天 / 后一天 / 今天」三个按钮，没接日历。Material3 `DatePickerDialog` 零新增依赖。 |
| 4 | 没有收款方 | `RecordEntity` 无此字段。按使用者边界做成流水级自由文本，不建账户、不搞复式。 |
| 5 | 布局不紧凑 + 键盘 | `RecordEditorDialog` 把模板 chips、三 Tab、金额、10 个一级分类网格（4 列 3 行）、二级 chips、账户区、日期、备注、开关全塞进一个滚动 Column；键盘 20 键含 `÷ × − + ( ) =`。最初规格书「方便多件商品合并算账」的要求被本轮反馈推翻。 |
| 6 | `+` 常驻挡内容 | `LedgerApp` 用 `FabPosition.Center`，FAB 悬浮在 bottomBar 上方，`innerPadding` 不含 FAB 高度，列表末尾被盖。 |
| 7 | 模板分类混乱 | 下拉**没有按当前收支类型过滤**（选支出能选工资/月薪，选收入能选餐饮）；一级二级扁平堆叠，二级只加 `└ ` 前缀不显示归属；模板列表行根本不显示分类。 |

## 2. 关键取舍

- **`counterparty` 的落点**：`records` / `trash` / 模板 / 周期规则 / `BackupRecord` /
  `BackupTemplate` / `BackupRecurringRule` / `CsvRow` / `CsvSourceRow` / `RecordRow`
  全加带默认值 `""` 的字段。Kotlin data class 一律加在**参数列表末尾**，
  现有位置参数构造调用零改动。备份 DTO 加默认值让旧 JSON 仍可解析。
- **备份 DTO 的 `currencyCode = "HKD"` 默认值不动**：它是 6 份 v1/v2/v3 兼容测试
  断言的历史格式语义，改了会红且没有收益；新装默认 CNY 由
  `LedgerCurrency.DEFAULT` + `SeedData` + `exportSnapshot()` 显式传值保证。
  `project.md` 明确 HKD 在历史 Scenario 中只是多币种测试示例。
- **CSV 第 9 列 `counterparty` 做成可选**：导出必带，导入时缺列不报错、按空串处理，
  这样使用者上一版导出的 8 列 CSV 仍能导回来。
- **账户编辑改期初余额**：`AccountDao.updateAccountMeta` 加两列，
  `LedgerRepository.updateAccountMeta` 末尾补一次 `rebuildAllBalancesInternal()`；
  否则余额缓存与真相源漂移。
- **批量统一币种**：单事务更新 accounts / records / trash 三张表的冗余标签，
  再重算；不改金额、不跨币种混算。
- **FAB 内嵌**：从 `Scaffold` 移除 `floatingActionButton`，
  改为 `NavigationBar` 内 5 个槽位（明细 / 资产 / 中央 `+` / 统计 / 设置），
  中央槽位放一个不悬浮的 `FloatingActionButton`。Tab 仍是 4 个，
  按钮完全落在底栏高度内，零内容遮挡。
- **紧凑布局**：分类改横向单行 + 点一级展开二级单行 chips；账户与日期同一行；
  备注与收款方紧随金额；「不计入收支」收进「更多」折叠；键盘 4×4 共 13 键。
- **日历时区**：`DatePickerState` 的 millis 是 UTC 午夜，读写都经
  `LedgerClock.zoneId` 转换，避免差一天。

## 3. 版本与迁移

`AppDatabase version = 1`，`Migrations.ALL = emptyArray()`，
`DatabaseModule` 仍调 `.addMigrations(*Migrations.ALL)`（空数组展开合法）。
`check_transaction_annotations.py` 的迁移检查自 6c-2 起已是记录性 warnings，
不会使 CI 失败；写方法事务包裹与破坏性迁移禁令仍是硬检查。

⚠️ **已装机包必须卸载重装**。Room 打开已存在 version 4 的库时，
version 1 且无迁移会直接失败。使用者已授权此路径，装机前先导出 CSV 带走测试数据。

## 4. 实现期如实记录

### 首轮 CI `43459412013` failure（挂在「运行单元测试」，主源码编译通过）

根因不是新功能，而是 `MigrationContractTest` 里三个「迁移已注册」断言
（`migration_is_registered` / `migration_2_3_is_registered` /
`migration_3_4_is_registered`）与「稳定版前 `Migrations.ALL` 为空」直接冲突。
把三者合并成一个 `migrations_are_frozen_before_stable_release()` 断言，
SQL 常量校验保留供历史追溯。这是本仓库**第 6 次「测试断言描述的是旧决策」**，
与前几次「手写常数」不同：这次是需求变更后忘了同步契约测试。

另把 `LedgerClockTest` 的注释从 Hong_Kong 改成 Shanghai；断言日期结果不变
（两地同为 UTC+8、均无夏令时），正好印证 design 第 0 节的判断。

### 实现期偏离

- 「一键把现有账户全部改为 CNY」从资产页挪到**设置页**，与默认币种下拉并排，
  全局操作放设置更顺手；同时按事务同步更新 records/trash 的冗余币种标签，
  否则统计页按币种筛选会与账户不一致。
- 记一笔的备注与收款方做成同一行两个 `OutlinedTextField`（各 weight 1f），
  比原计划的上下两行更紧凑；仍保留 `verticalScroll` 兜底，避免极小屏被裁。
- `Migrations.kt` 的冻结 CREATE TABLE 常量**不含新增的 counterparty 列**——
  它们已不执行，补列反而会让「冻结」语义变模糊；在 design 里显式说明，
  避免新会话误以为常量与 Entity 必须逐字一致。

### 最终验证

| 验证 | run | 结论 |
|---|---|---|
| push CI（首推） | `43459412013` | failure，挂在单测（迁移注册断言），主源码编译通过 |
| push CI（修测试后） | `43460356310` | **success**，13 步全绿，debug APK **23,743,100 B** |
| release.yml（R8 + 坚果云） | `43461666785` | **success**，24 步全绿 |

release 的 metrics 制品名为 `...-nsfalse`，来自 `steps.nutstore.outputs.skipped`；
脚本只在 PROPFIND 认证通过、两级 MKCOL 成功、`curl -T` 返回 201/204 之后才写
`skipped=false`，因此上传请求成功可确认。HEAD 落盘校验只写日志不中断，
故「HEAD 200」不列为已验证事实。

六个静态检查在主推与 release 两次都 PASS；`check_transaction_annotations.py`
报「32 个写方法均在事务内；迁移/版本检查 2 项记录性提示」，说明门禁降级未误伤。

**Kotlin 编译从首推起就一次通过**：DatePicker / DatePickerDialog /
rememberDatePickerState、Room 加列与 version 重置、Compose 横向分类与底栏内嵌
FAB、Hilt 注入的 AssetsViewModel 加参，零编译错误。唯一失败来自测试断言
描述的是旧决策，不是新代码。

## 5. 真机验证累积清单（按使用者决定继续推迟）

- Room / SQLCipher 在真机打开加密数据库（全新安装走 `onCreate`）
- R8 release 包真机启动 —— **本轮已由使用者完成，未报崩溃**
- BiometricPrompt 弹窗 / SAF 文件选择器
- Excel 打开 CSV 是否乱码、中文 BOM 表现
- 5 万条切月 / 年报聚合耗时、滚动掉帧
- 模板快选、周期补记、桌面快捷方式手感
- 报销入口、预算进度条、净支出环形图
- 币种切换、分组小计、同币种转账提示
- CSV 导出文件选择器、账单导入确认流程、明细币种筛选手感
- **本 change 新增**：日历选择器、收款方补全、账户编辑后余额、底栏内嵌 `+`、
  模板分类下拉分组、默认币种设置与批量统一

## 6. 否决方案表

见 `proposal.md` 第 5 节；另否决：把 `counterparty` 做成独立表（多一张表 + 外键，
零收益）、用 `ExposedDropdownMenuBox` 做收款方（Compose 版本 API 风险，
改用 chips 更稳）、`DatePicker` 用 `System.currentTimeMillis()` 直接传
（UTC 午夜与本地日期差一天）。


## 10. 第 2 批真机反馈与实现期偏离

使用者在阶段 7 的包上继续体验，又提交 10 条反馈。本 change 尚未归档，因此
两批合并为**同一个 change** 落地，避免两个 change 并行导致 delta 叠加。

### 字段语义（使用者明确「大胆改表结构」）

`payee` 全量重命名为 `counterparty`：`records` / `trash` / 模板 / 周期规则 / 备份 DTO /
CSV 列名 / 测试 / specs / README 一次替换，历史归档与 `docs/` 快照不动。
`AppDatabase version` 仍为 1、`Migrations.ALL` 仍为空 —— 按使用者「稳定版前不考虑
升级迁移、不需要保留测试数据」的约束，全新安装走 `onCreate` 直接建新列，
**新包必须卸载重装**，无数据需要保留。

### 图表口径

月度改为默认单线 + 切换 chip；年度资产线**单独一张图**，不把第三条线塞进收支双线
（量级差大时必然贴底，正是使用者反馈 4 的根因）。资产线算法：
`当前该币种账户余额合计 − 全年逐月净变动之和 = 年初基准`，再由
`ChartData.assetSeries` 逐月累计。净变动查询按 `currencyCode` 过滤、转账净额为 0、
只统计 `excludeFromStats = 0`；校准记录不进资产线（量级极小且无币种字段），
这是有意简化，已在 UI 文案注明「按当前币种累计推算，不跨币种混算」。
点击取值用 `pointerInput + detectTapGestures` 按 x 定位最近月份，弹窗显示收入 / 支出 /
月末资产三项 `Long` 分格式化值。

### 实现期偏离（如实记录）

- **自定义账户排序（上移 / 置顶）本批未做**。常用账户自动置顶 + 全部账户弹层搜索
  已解决「10 多个账户要横向拖动」这一主要痛点；资产页手动排序需要新增 ViewModel
  方法与 UI 按钮，为避免本批一次改动面过大留到下一批，不与「已确认」冲突。
- 时间选择用 framework `android.app.TimePickerDialog` 而非 Material3 `TimePicker`：
  零 Compose API 版本风险，`timestamp` 仍是唯一真相源，`setTime` 只换本地时分。
- 资产线用「当前余额倒推」而非新增快照表：零表结构变更、零 Migration，
  符合「严格不混算」的既有约束。


## 11. 第 2 批 CI 与出包实测

| 项 | 结果 |
|---|---|
| 首推 `389a156ff2` / run `43534844564` | **failure**，挂在「运行单元测试」步骤 |
| 根因 | `LineChart` 把点数 `n` 定义在 Canvas 的 draw lambda 内，却在 `pointerInput(n)` 引用 → `Unresolved reference: n`。属阶段 8 新加点击取值的作用域错误，不是设计问题 |
| 修复 `e3488f0c47` / run `43535265042` | **success**（六个静态检查 / keystore / 单测 / debug APK / release lintVital / 签名诊断全绿） |
| release `43535979533` | **success**，24 步中 plain / AAB / 创建 Release 按 input 正常 skipped；R8 编译、单测、签名诊断、APK 与 mapping 上传、**上传到坚果云 success**、指标制品全绿 |
| 制品 | `offline-ledger-release-v0.1.0`（12,156,129 B 压缩）、`offline-ledger-mapping-v0.1.0`（3,329,247 B）、metrics 名 `...-nsfalse`（坚果云步骤未跳过且成功） |
| 重复触发 | 502 重试导致两次 dispatch，`43536313646` cancelled、`43536435628` 已手动取消（HTTP 202），以 `43535979533` 为唯一交付包 |

诊断说明：job logs 与 check-runs annotations 仍返回 403（本仓库已知限制），本次根因由
本地复读 `LineChart.kt` 作用域定位，修复后由 CI 一次验证通过，未临时回传 `diag/`。

**仍未验证**：真机覆盖安装（本批改了 `counterparty` 列名，`AppDatabase version` 仍为 1、
无 Migration，按使用者「稳定版前不考虑升级、不需要保留测试数据」的约束，
**新包必须卸载重装**）；R8 包真机启动、SQLCipher 开库、BiometricPrompt、SAF 选择器、
Excel 打开 CSV、5 万条切月耗时，均按使用者决定继续推迟。

## 12. 第 3 批：逐条查证与取舍

### 12.1 查证（都读了代码）

| # | 根因 |
|---|---|
| 1 | `TemplateManagerDialog.QuickEntryForm` 把 `amountCents` 当必填，且 `Repository.createTemplate` 有 `require(t.amountCents > 0L)`；`TemplateQuickPickBar` 点一下**直接调 `recordFromTemplate` 生成流水并关弹窗**，完全不经过表单，所以「自动填充再改」不可能。模板里金额恰是每次最可能变的字段，产品语义写反了。 |
| 2 | `TemplateDao` 只有 `insert/updateAmount/query`，列表行没有编辑/删除菜单；类型用 `AssistChip` 但无 `selected` 态；记一笔的模板 chip 也没有选中态。 |
| 3 | `updateAccountMeta` 不含 `type`；`archiveAccount` 只把 `isArchived` 置 1，资产页 `filter { !it.isArchived }` 后**没有任何查看入口**；`sortOrder` 字段在阶段 1 就预留了但资产页从未暴露排序。上一批如实记为「留下一批」。 |
| 4 | `ChartData.seriesMax(series: List<List<Long>>)` 把支出与收入**合并取最大值**，收入量级大时支出线贴底；`LineChart` 竖轴只有网格线无数值、横轴固定 3 个标签。竖屏 360dp 标 12 个月必然挤，横屏才是趋势图正确容器。 |
| 6 | `CategoryManagerDialog` 只有「归档」没有删除；新增二级分类的父级是**不滚动的横向 Row**，10 个一级被屏幕宽度截断；`SettingsViewModel.moveCategory` 已有但 UI 从未调用。 |
| 7 | `AccountDetailDialog` 的标题取 `state.account`（`load()` 一次性快照），名称输入框又是 `remember` 初始化一次不再更新；资产页改名后两处不同步。 |

### 12.2 关键取舍

- **模板 = 预填草稿，不是固定账单**：`createTemplate/updateTemplate` 放宽为
  `amountCents >= 0`，但 `recordFromTemplate` 加 `amountCents <= 0 → return null` 双保险，
  避免草稿模板被任何路径写成 0 元流水。周期规则金额仍必须 > 0（它要自动补记）。
- **模板选中后不再直接记账**：`TemplateQuickPickBar` 改为回调模板实体，
  `RecordEditorViewModel.applyTemplate()` 填表单；金额留空、时间取「现在」、
  所有字段仍可自由改。比「生成后再编辑」少一次写库，也更符合使用者描述。
- **账户排序用上移/下移按钮，不做拖拽**：拖拽要引入 `reorderable` 之类新依赖或自写
  `pointerInput` 手势，与「零新增依赖」冲突且首次真机手感无法验；`sortOrder` 已有，
  按钮足够解决「10 多个账户常用置顶」。
- **账户删除边界**：有流水 MUST 归档（历史流水外键与归属不能断），无流水才物理删。
  改 `type` 后统一走 `rebuildAllBalancesInternal()`，净资产归类随新类型生效。
- **图表独立缩放**：新增 `ChartData.seriesMaxSingle`，单线模式按该系列自身最大值；
  只有显式选「两者」才共用 max（保持可比）。竖轴 5 个刻度由纯 JVM `yAxisLabels`
  生成，不做金额运算、不回写任何金额。
- **横屏全屏页**：新增 `ChartFullscreenActivity`，Manifest 固定 `landscape`、
  `exported=false`，只通过 Intent 传 LongArray 金额与标签，不带数据库对象；
  双指缩放用 `Modifier.scale` 只做视觉放大，不改 Canvas 坐标逻辑，零手势坐标风险。
- **分类删除**：`Repository.deleteCategory` 查 `linkedRecordCount`，有流水自动降级归档，
  无流水才物理删；配恢复入口。父级选择改「搜索 + 可滚动全量列表」，不再被
  `DropdownMenu` 默认高度截断。
- **详情标题单一真相源**：由资产页把实时 `account.name` 传进详情弹窗，
  删除详情里重复的改名输入框，账户编辑只留资产页一个入口。

### 12.3 否决方案

| 否决 | 理由 |
|---|---|
| 模板金额用 0 元占位流水 + 事后编辑 | 会污染余额与统计，且违背「金额恒正」铁律 |
| 账户拖拽排序 | 需新依赖或自写手势，首次真机无法验，收益不抵风险 |
| 竖屏内塞 12 个月标签 | 360dp 宽必然挤成一团，横屏才是正解 |
| 双 Y 轴强行保留两条线 | 量级差大时仍互相干扰，且实现与读图都更复杂 |
| 任何账户都能物理删除 | 会连带断掉历史流水归属 |
| 分类物理删除有流水的分类 | 历史统计与下钻会出现孤儿分类 |
| 再建一个 `polish-device-experience-2` change | 前两批尚未归档，拆开会切碎同一轮装机打磨的决策史 |

### 12.4 稳定版前版本策略（继续生效）

本批**不改表结构、不改 `AppDatabase version`、不新增 Migration、不改备份 DTO**。
按使用者重申的约束，后续阶段不得再以「会破坏升级 / 要写 Migration」否决数据结构调整。

### 12.5 待回填

CI 与出包结果在本批推送后回填；真机实测按使用者决定继续推迟，
清单见 `tasks.md` 第 9 节。
