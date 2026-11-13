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

- **`payee` 的落点**：`records` / `trash` / 模板 / 周期规则 / `BackupRecord` /
  `BackupTemplate` / `BackupRecurringRule` / `CsvRow` / `CsvSourceRow` / `RecordRow`
  全加带默认值 `""` 的字段。Kotlin data class 一律加在**参数列表末尾**，
  现有位置参数构造调用零改动。备份 DTO 加默认值让旧 JSON 仍可解析。
- **备份 DTO 的 `currencyCode = "HKD"` 默认值不动**：它是 6 份 v1/v2/v3 兼容测试
  断言的历史格式语义，改了会红且没有收益；新装默认 CNY 由
  `LedgerCurrency.DEFAULT` + `SeedData` + `exportSnapshot()` 显式传值保证。
  `project.md` 明确 HKD 在历史 Scenario 中只是多币种测试示例。
- **CSV 第 9 列 `payee` 做成可选**：导出必带，导入时缺列不报错、按空串处理，
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

## 4. 实现期如实记录（写完再回填）

_待 CI 结果与实现期偏离回填。_

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

见 `proposal.md` 第 5 节；另否决：把 `payee` 做成独立表（多一张表 + 外键，
零收益）、用 `ExposedDropdownMenuBox` 做收款方（Compose 版本 API 风险，
改用 chips 更稳）、`DatePicker` 用 `System.currentTimeMillis()` 直接传
（UTC 午夜与本地日期差一天）。
