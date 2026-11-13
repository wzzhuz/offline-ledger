# 技术设计：add-csv-export-and-import

## 0. 使用者新约束如何落地（本 change 的最高优先级）

> 「暂时不用考虑升级场景，在出一个满意的稳定版本之前不会立即使用，
> 只会使用测试数据，数据结构完全不需要考虑升级迁移，
> 除非我说明需要考虑升级迁移。」

这是**条件性暂缓**，不是永久废除，已写进 `project.md`
「稳定版前的版本策略」。本 change 的落点：

| 项 | 6c-2 做法 |
|---|---|
| `AppDatabase version` | 保持 4，**不改** |
| `Migrations.ALL` | 三段代码保留冻结，**不新增** |
| `BackupSchema.CURRENT` / `MIN_SUPPORTED` | 保持 4 / 1，**不改** |
| 旧备份兼容 | 代码保留，但**不再是验收条件** |
| `check_transaction_annotations.py` | version / ALL / DEFAULT 检查**降级为记录性提示，不失败** |
| 真机清单 | **移除**「三段 Migration 覆盖升级」，其余保持未验证 |

**为什么代码保留而不是删掉**：删除三段 Migration 与兼容逻辑
反而增大改动面；冻结它们、只把门禁降级，是最小改动。
恢复条件由 `project.md` 统一管理，本 design 不重复定义。

**本 change 明确不做**：不再以「会破坏升级 / 要写 Migration /
旧备份读不了」为由否决任何实现选择（包括去重字段、CSV 列扩展）。
前几轮把「三次叠加迁移」写成「最大残余风险」的措辞已在
6c-1 归档 `design.md` 与本次 `project.md` 修正为「未验证」。

## 1. 写码前实际读了什么

| 文件 | 结论 |
|---|---|
| `SettingsScreen.kt` | 已有 SAF `CreateDocument` / `OpenDocument`；仍写着「v1 不提供 CSV 导出（明文文件）」，本批改掉 |
| `SettingsViewModel.kt` | `exportTo` / `importFrom` 走 `Dispatchers.IO` + `contentResolver` 流式；`SettingsState` 有 `message` / `error` / `busy` 可直接复用 |
| `BackupExporter.kt` / `BackupImporter.kt` | 纯 JVM 编解码范式，CSV 层照此设计 |
| `RecordDao.pagedFiltered` | 11 个可选参数 + 参数绑定 SQL，**无 `currencyCode`**；加第 12 个可选参数即可 |
| `RecordDao.dailyTotals(ym)` | 也不带币种；同样加可选参数，否则日合计与列表口径不一致 |
| `RecordFilter.kt` | 11 字段 + `activeCount()` + `bumped()`；加字段要同步 `activeCount()` 清单 |
| `RecordPagingSource.kt` | 单处调用 `pagedFiltered`，补一个参数即可 |
| `check_transaction_annotations.py` | 第 85–131 行是 version / ALL / DEFAULT 硬检查，改为 warning |
| `ledger-transactions/spec.md` | 「显式迁移且 seed 覆盖升级场景」原文已取到，1 份 MODIFIED 按标题整块替换 |

## 2. CSV 格式（导出与导入互为镜像）

**表头固定 8 列**（顺序即契约，导入按表头名映射、不强依赖顺序）：

```
date,type,amount,account,categoryRoot,category,note,currencyCode
2026-11-13,EXPENSE,38.50,微信,餐饮,食材,午餐,HKD
2026-11-13,INCOME,8000.00,招行,工资,,11月薪,CNY
```

| 列 | 规则 |
|---|---|
| `date` | `YYYY-MM-DD`，解析成 `localDate` / `yearMonth` 由 `LedgerClock` 负责 |
| `type` | 只接受 `EXPENSE` / `INCOME`；`TRANSFER` / `SETTLEMENT` MUST 拒绝并报行号 |
| `amount` | **元**，两位小数，`BigDecimal` 校验后转 `Long` 分；`amountCents` 恒正 |
| `account` | 账户**名称**，导入时精确匹配未归档账户；找不到 MUST 报错，不自动建账户 |
| `categoryRoot` / `category` | 一级 / 二级分类名；二级为空时只记一级；找不到 MUST 报错 |
| `note` | 备注，允许逗号（CSV 引号转义）与空值 |
| `currencyCode` | 导出必带；**导入时忽略该列，币种一律从账户继承**（`withCurrency` 已实现），避免文件写错币种污染账本 |

**编码**：UTF-8 with BOM（`EF BB BF`），Excel 中文不乱码；
换行用 `\r\n`；字段含逗号 / 引号 / 换行时用双引号包裹并把 `"` 转义为 `""`。
**导出范围**：全部未删除流水（含 `excludeFromStats` 与 SETTLEMENT，
方便在 Excel 里全量核对）；概览、报表口径不受影响。

**隐私边界**：明文文件只写入用户通过 SAF 自选的位置；
App 无 `INTERNET` 权限、无网络上传、不在应用私有目录留副本。
设置页文案改为「明文文件由你自行保管；应用不会上传任何数据」，
**不再出现「默认关闭 / 勿放公共目录」这类以明文风险为前提的措辞**。

## 3. 纯 JVM 编解码（`CsvCodec`）

```kotlin
object CsvCodec {
    fun export(rows: List<CsvRow>): ByteArray      // 含 BOM
    fun parseImport(bytes: ByteArray): CsvParseResult  // 全量校验
}
data class CsvParseResult(
    val rows: List<CsvRow>,
    val errors: List<String>   // 「第 N 行：xxx」
)
```

- `parseImport` **先解析全部行再统一校验**，
  一行非法就返回 `errors`，调用方 MUST NOT 进入事务
- 金额用 `Cents.fromYuan`（已有 `BigDecimal` 实现），
  禁止 `Double` / `Float` 参与
- 支持 CSV 与 TSV：按首行分隔符自动判定（含 tab 则按 `\t`）
- 自动跳过 UTF-8 BOM、空行；表头缺列 MUST 报明确错误
- **不加任何第三方 CSV 库**：字段少、规则固定，手写解析器 ~120 行，
  纯 JVM 可单测，零依赖风险

## 4. 账单导入：事务化批量写入

**UI 流程**：
`OpenDocument` → 读字节 → `CsvCodec.parseImport` →
若有错误只显示报告、不写库 → 否则弹「将写入 N 条流水」常规确认 →
确认后调用 `repository.importRecords(rows)`。

**Repository 新增 `importRecords`**（登记进 `WRITE_METHODS`，30 → 31）：

```
db.withTransaction {
    rows.forEach { row ->
        // 1) 名称解析：accountDao / categoryDao 查未归档项
        // 2) 去重：existingDuplicate(...)，命中则跳过并计入 skipped
        // 3) withResolvedRoot(entity)（含 withCurrency 币种继承）
        // 4) recordDao.insert
    }
    rebuildAllBalancesInternal()   // 末尾一次重算
}
```

- **任一步抛异常 → 事务整体回滚**，账本零变化
- 去重键 = `(账户, amountCents, localDate, note)`，
  走一条参数化 `SELECT COUNT(*)` 查询，**不加新字段、不改表结构**
- 导入流水 MUST 只含 `EXPENSE` / `INCOME`；
  `timestamp` 由 `localDate` 当天 12:00 生成（避免时区串日），
  `excludeFromStats = false`
- 结果回报「写入 N 条 / 跳过 M 条重复 / 余额已重算」
- **不新开写余额路径**：`withResolvedRoot` 与 `insertRecord` 完全同构

## 5. 明细页与统计页币种口径一致性

`pagedFiltered` 与 `dailyTotals` 各加一个可选参数：

```sql
AND (:currencyCode IS NULL OR r.currencyCode = :currencyCode)
```

- `RecordFilter` 加 `currencyCode: String? = null`，
  `activeCount()` 清单同步加一项（**注意阶段 2 曾把维度数硬编码成 8 而挂测试**，
  这次单测用 `RecordFilter()` 基线 + 逐字段设置，不手写常数）
- `RecordPagingSource` 传 `filter.currencyCode`
- `FilterSheet` 加「币种」选择：默认「全部币种」，
  选项来自活跃账户去重币种；选中后 `DetailViewModel` 的日合计
  也按同币种聚合
- `DetailScreen` 概览里 6c-1 那句「全部币种（按币种筛选请到统计页）」
  改为显示当前筛选币种
- **口径一致性**：明细页选 HKD 时，列表 + 日合计只含 HKD，
  与统计页同月份 HKD 合计一致（两者都走 `excludeFromStats = 0`）

`check_stat_rules.py` 的 marker 清单不变：
`dailyTotals` 与 `pagedFiltered` 本就不在统计聚合 marker 里，
它们是明细查询；新增的 `dailyTotalsForCurrency` 已在 6c-1 纳入。

## 6. 门禁降级（记录性提示，不失败）

`check_transaction_annotations.py` 把下列检查从 `problems.append`
改成 `warnings.append`，**只打印不 `return 1`**：

- `AppDatabase version = 4`
- `Migrations.ALL` 含三段、`addMigrations(*Migrations.ALL)`
- 三条 CREATE / 四条 ALTER SQL 常量与 `DEFAULT 'HKD'`

**保留硬检查**（改一处即 FAIL）：
- 31 个写方法 MUST 包在 `db.withTransaction` 内
- 任何源码 `fallbackToDestructiveMigration` 仍 FAIL
  （`project.md` 把禁令标为条件性暂缓，但代码里出现即视为误用）
- `check_manifest_privacy.py` / `check_stat_rules.py` /
  `check_kotlin_strings.py` / `check_docs_consistency.py` /
  `check_workflow_syntax.py` 五个完全不动

输出改为：
`check_transaction_annotations: PASS（31 个写方法均在事务内；迁移检查 3 项记录性提示，稳定版前暂缓）`。

## 7. 1 份 MODIFIED 收口顶层 Requirement

`ledger-transactions`「显式迁移且 seed 覆盖升级场景」按标题整块替换为
「稳定版前的版本策略」：写清「稳定版前不要求显式迁移 / 数据结构可自由调整 /
已落地代码冻结 / 恢复条件 / MUST NOT 以升级为由否决结构调整」，
并保留「`fallbackToDestructiveMigration` 在稳定版前暂缓」的准确措辞。

**其余十几条历史 Requirement 保留不删**：它们记录了 6a/6b/6c-1 当时的决策，
改写它们会造成十几份 MODIFIED 与规格腐烂；
由 `project.md`「暂缓执行清单」统一声明「本节优先级高于 specs 里同主题 Requirement」。
这正是 6c-1 反复吃过的「阶段绑定表述」教训的解法。

## 8. 测试策略

| 层 | 覆盖 |
|---|---|
| `CsvCodecTest` 纯 JVM | BOM 存在、8 列表头、两位小数、逗号/引号转义、TSV、缺列表头、非法金额、非法 type、非法日期、空行跳过 |
| `CsvImportPlanTest` 纯 JVM | 全量校验失败不产生写入计划、去重键构造、币种列被忽略（继承账户） |
| `RecordFilterCurrencyTest` 纯 JVM | 加币种后 `activeCount()` 正确、`bumped()` 保留、null 视为全部币种 |
| `MigrationGateRelaxedTest` 纯 JVM | 门禁脚本含 `warnings` 且 version/ALL 检查不进 `problems` |
| 六个静态检查 | 31 写方法在事务内、25 个统计 marker、无 INTERNET、workflow 语法 |
| Actions | 单测 + debug + release lintVital |

## 9. 真机验证累积清单（使用者明确继续推迟，全部未验证）

沿用 6a 的决定、6b / 6c-1 再次确认、6c-2 继续沿用：
**先不做真机实测，等完成度更高再统一装机体验**。
按使用者新约束，**「三段 Migration 覆盖升级」一项已移除**。
其余各项**全部保持未验证**，不得改写成「已通过」：

- Room / SQLCipher 在真机打开加密数据库（全新安装走 `onCreate`）
- R8 release 包真机启动
- BiometricPrompt 弹窗 / SAF 文件选择器（**6c-2 的 CSV 导出与导入都靠它**）
- Excel 打开 CSV 是否乱码、中文 BOM 表现（纯 JVM 单测只能验证字节正确）
- 5 万条切月 / 年报聚合耗时、滚动掉帧
- 模板快选、周期补记、桌面快捷方式手感
- 报销入口、预算进度条、净支出环形图
- 6c-1 新增：币种切换、分组小计、同币种转账提示
- **6c-2 新增**：CSV 导出文件选择器、账单导入确认流程、明细币种筛选手感

**装机前建议先用当前版本导出一份加密备份**（虽然目前只有测试数据）。

## 考虑过的其他方案

| 方案 | 为什么没选 |
|---|---|
| 仍以「明文风险」拦 CSV / 做「默认关闭 + 强制二次确认」 | 该理由已被使用者在 6c-1 正式推翻；specs 里相关约束已 MODIFIED 清除 |
| 把已加密的 JSON 备份降级成明文 | 「明文可接受」≠ 拆掉已有能力；倒退无收益 |
| 导入绕过 `insertRecord` 直接 `recordDao.insert` | 会漏掉币种继承、一级分类解析、末尾重算，等于新开一条写余额路径 |
| 解析失败部分写入、报告跳过错误行 | 违反「失败整体回滚」铁律，会留半套数据 |
| 为去重新增 `importHash` 字段又因「要写 Migration」放弃 | 新约束已明确不许这样否决；且不加字段也能用四元组查询去重 |
| 适配支付宝 / 微信 / 银行私有账单格式 | 字段随版本变、含大量无关列、编码不可控，稳定版前不值得 |
| 逐条 MODIFIED 十几份历史 Requirement | 改动面大、易漏，且抹掉历史决策；改为 1 份顶层 + project.md 暂缓清单 |
| 直接删掉三段 Migration 代码 | 删除反而增大改动面；冻结 + 门禁降级是最小改动 |
| 给 `dailyTotals` 另起一个 ForCurrency 方法 | 明细筛选是可选参数，另起方法要维护两份几乎相同的 SQL；加可选参数更简 |
| 顺手做自动记账 / 跨币种混算 / 小组件 | 自动记账仍待决策；后两者已移入永久不做 |
| 6c-2 与「出稳定版 / 真机验收」合并 | 违反「每阶段可编译运行」与使用者「先继续下一阶段」的决定 |

## 取舍

选择「闸门放行 + 1 份 MODIFIED + 纯 JVM CSV 编解码 +
SAF 明文导出 + 事务化批量导入 + 明细可选币种筛选 + 门禁记录性降级」：

- **牺牲**：CSV 只支持自定义极简模板，不能直接导支付宝账单；
  导出是全量流水（含不计入统计的），Excel 里要自己筛
- **换取**：零新增依赖 / 权限 / 网络行为；表结构零改动；
  导入失败整体回滚；明细与统计口径一致；
  **稳定版前不再为升级背负担**；错误隐私论据与阶段绑定表述彻底清掉

## 实现期补充与踩坑（如实记录）

1. **第 14 次检查器自误报**：`check_kotlin_strings.py` 逐行数
   未转义双引号，奇数即判「字符串未闭合」。
   `CsvCodec` 里 CSV 解析必需的字符字面量 `'"'` 每行贡献 1 个双引号，
   被连报 5 行。**改写检查器风险更大**（可能漏掉真正的 `\n` 跨行字面量），
   故改代码：用 `private const val QUOTE_CODE = 0x22` 与
   `ch.code == QUOTE_CODE`，代码本身语义不变。
   这是本仓库第 14 次「检查器比被检查对象更容易出错」，
   前 13 次见各阶段归档 design.md。
2. **第 15 次同类**：批 1a 脚本把 `dailyTotals` 的
   Python 锚点误写成两个独立的三引号字符串（第二个没有赋值给 `old`），
   导致替换只改 SQL 不加参数；写完前自查发现并改成显式字符串拼接。
3. **`NumberFormatException` catch 不可达**：`Cents.fromYuan` 已把
   `NumberFormatException` 包成 `IllegalArgumentException`，
   同时 catch 两者会让后者永远不执行；已删掉多余分支。
4. **`MigrationGateRelaxedTest` 的实现偏离**：写作稿说用 Kotlin 单测
   验证门禁降级，但 `scripts/` 不在单测 classpath 上，
   靠 `user.dir` 猜路径不可靠。改为 `check_transaction_annotations.py --self-test`
   （脚本内自测分支）+ Kotlin 三个单测类
   （`CsvCodecTest` / `CsvImportPlanTest` / `RecordFilterCurrencyTest`）。
   门禁降级由 Python 自测与 CI 实际运行双重证明，不假报「Kotlin 测过」。
5. **CSV 导出走一条 JOIN SQL**：`csvRows()` 直接取账户名与一级/二级分类名，
   不把 5 万条 `RecordEntity` inflate 进内存；符合「面向用户的查询走 SQL」。
   导出是天然全量操作，但仍避免对象化的内存峰值。
6. **`RecordFilterTest` 的 `assertEquals(7, ...)` 没有改**：
   那个构造没设 `currencyCode`，`activeCount()` 仍是 7；
   6c-2 新增的维度验证用**相对断言**
   `base.activeCount() + 1`，避免阶段 2 把维度数硬编码成常数再次踩坑。

## 首轮 CI 实测与根因（如实记录）

| run | commit | 结论 | 说明 |
|---|---|---|---|
| `43428723816` | `89dcef9f66` | **success** | 仅 change 文档，六检查 + 编译全绿 |
| `43429618643` | `68d51e6c25` | **failure** | 6c-2 实现首推，挂在「运行单元测试」 |
| `43430455367` | `b39309b51f` | **failure** | 临时诊断回传，拿到 18 行精确错误 |

诊断日志给出的一串错误（`Unresolved reference 'escape'` /
`'parseRows'` / `'validDate'`、`'private' is not applicable to 'local function'`、
`'return' is prohibited here`、`Missing '}`）**看似满屏，其实是同一个根因**：

批 1a 写完 `CsvCodec.kt` 后，为删掉不可达的
`catch (e: NumberFormatException)` 分支，替换目标写成了
「第一个 catch 的闭合 `}` + 第二个 catch 整段」，
**把第一个 catch 的闭合括号一起删了**。
于是 `export()` 之后的所有函数都被解析成 `parseImport()` 内部的局部函数，
`private` 修饰符、跨函数引用、return 全部报错，最后 `Missing '}`。

这是本仓库**第 16 次「凭印象拼字符串 / 检查器自误报」类错误的新变体**，
也是「删代码块 MUST 先核对边界花括号」的第一例。
修法：把闭合 `}` 补回，其余一行不动。
诊断步骤与 `diag/` 回传文件用完即移除，远端核查无残留。

**注意**：这 18 行错误里没有一个指向 CSV 设计本身——
纯 JVM 编解码、事务导入、明细筛选、门禁降级的逻辑都未被执行到，
因此首轮失败不能当成「设计有问题」，也不能当成「已验证」。

## 第二轮 CI 与最终验证

| run | commit | 结论 | 说明 |
|---|---|---|---|
| `43431400249` | `af40aeefca` | **failure** | 补回括号后仍挂在单测 |
| `43432655826` | `f40a83f5d0` | **success** | 13 步全绿（诊断步骤正常 skipped） |

**第二轮根因是我自己写的两个测试断言**：
`CsvParseResult` 的语义是「任一行非法 → `errors` 非空 → 调用方按 `ok`
整体废弃」，但**合法行仍留在 `rows` 里**（解析器不做回滚式清空）。
测试却断言 `rows.size == 0`，于是
`illegal_amount_reports_line_number_and_no_rows` 与
`one_illegal_row_discards_whole_plan` 必然红。
已改为断言 `!ok` + 错误行号，并加注释写明「调用方 MUST 按 ok 判定」。

这是本仓库**第 17 次「凭印象写常数 / 断言」**
（与阶段 1 的 147000、阶段 2 的维度 8、阶段 5 的 4 字口令同源）。
同时做了一次全项目 Kotlin 括号平衡自查，脚本自己又误报了
`ChartData.kt`（正则剥字符串时把注释里的括号算进去）——
**第 18 次检查器自误报**，因该文件已通过多次 CI 编译，
按「FAIL 走第二条独立路径复核」确认无问题，未改动。

**最终验证**：
- 六个静态检查全 PASS；`check_transaction_annotations.py --self-test` PASS
- `testDebugUnitTest` / `assembleDebug` / `assembleRelease`（含 lintVital）/
  签名诊断 / 制品上传 全绿
- `offline-ledger-debug-apk` **23,703,226 B**
- 诊断步骤与 `diag/` 已移除，远端核查无残留
- 隐私门禁仍 PASS：无 `INTERNET`、无新增权限、无网络 SDK

**验收标准逐条对照**（可自动验证的部分）：
CSV BOM / 8 列表头 / 两位小数 / 逗号与引号转义 / TSV / 空行跳过 /
非法行带行号 / 去重键忽略币种列 / `activeCount()` 相对断言 /
31 写方法在事务内 / 迁移门禁降级 —— 均由纯 JVM 单测与静态检查覆盖。
**导入事务真实回滚、SAF 选择器、Excel 打开 CSV 仍需真机实测**
（使用者明确推迟，见第 9 节清单）。

## 验证

- [x] 阶段 0 五项对齐结论齐全、分级判定已告知并等确认
- [x] 使用者确认「按推荐来」（A + A′ + A），并明确稳定版前不考虑升级迁移
- [x] 阶段 6c-1 已归档，基线 172 条 Requirement / 284 个 Scenario
- [x] 闸门已放行 6c-2 三项；`project.md`「稳定版前的版本策略」已落档
- [x] 写码前实际读了设置页 / ViewModel / 备份编解码 /
  `pagedFiltered` / `RecordFilter` / PagingSource / 门禁脚本 / 待 MODIFIED 原文
- [x] delta 每条至少一个 GIVEN/WHEN/THEN + 「理由」，1 份 MODIFIED 写完整替换文本
- [x] CI 全绿（`43432655826` success，13 步全绿）
- [x] 每次推送均用远端 Git Trees API 逐文件比对 blob sha（PASS）
- [ ] 使用者确认后归档本 change（**本轮不擅自归档**）

### 最终验证（使用者「跑一下上传坚果云的 actions」后补记）

| 验证 | run | 结论 |
|---|---|---|
| push CI（六个静态检查 / 单测 / debug / release lintVital / 签名诊断） | `43432655826` | **success**，13 步全绿，debug APK 23,703,226 B |
| release.yml 手动触发（不打 tag、不传坚果云、`minify=on`） | `43389145224` | **success**，24 步全绿 |
| release.yml 手动触发（`upload_nutstore=true`，使用者装机用） | `43446433266` | **success**，24 步全绿，坚果云上传步骤 `skipped=false` |

`43446433266` 的 metrics 制品名为 `...-nsfalse`，来自 `steps.nutstore.outputs.skipped`；
脚本只在 PROPFIND 认证通过、两级 MKCOL 成功、`curl -T` 返回 201/204 之后才写
`skipped=false`，因此上传请求成功可确认。HEAD 落盘校验只写日志不中断，
故「HEAD 200」不列为已验证事实。该包已由使用者装机体验，产生本 change 之后的
真机反馈（见 `polish-device-experience`）。
