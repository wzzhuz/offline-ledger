# 设计：Firefly III 6.6.x CSV 迁移

## 1. 真实样本事实（2026-11-15 导出，6.6.6，1916 行 / 53 列）

以使用者提供的 `2026_11_15_transaction_export.csv` 为准，**不凭官方文档猜**：

| 项 | 实测 |
|---|---|
| 编码 / BOM | UTF-8 无 BOM；逗号分隔；表头 53 列 |
| 类型分布 | Withdrawal 1753 / Transfer 75 / Deposit 74 / Opening balance 14；**无 Reconciliation 类型行** |
| 金额符号 | Withdrawal 为负（-112.59），Transfer / Deposit 为正（31.72），Opening balance 带符号（可为 -1374.96）|
| 币种 | 全部 CNY，`foreign_currency_code` / `foreign_amount` 全空 |
| 日期 | `2026-11-15T11:22:00+08:00`，ISO 8601 带偏移；范围 2010-03-24 ~ 2026-11-15 |
| 账户类型 | source: Asset 1830 / Revenue 74 / Initial balance 12；dest: Expense 1753 / Asset 160 / Initial balance 2 / Debt 1 |
| 分类 | 单列「一级-二级」，如 `食-堂食`、`活-购物`、`入-工资`；也有单级 `宠`；30 行空 |
| 拆分交易 | 同 `group_id` 多行共 7 组（2~3 行），**每行有独立 `journal_id` / 金额 / 描述 / 分类**；`group_title` 非空 15 行 |
| 其他 | tags 77 行非空、notes 42 行、budget 全空 |

**两条对既有假设的修正，必须记住**：

1. 旧 issue 说「amount 全为负」「每 journal 只导一行、第二笔不导出」。6.6.6 的真实导出
   **已经是带符号且每个 split 独立一行**，明细一行没丢。因此原先「split 合并成一笔」
   的方案会凭空把「鞋 124 + 袜子 39.9」压成 163.9 并丢掉描述与分类 —— **改为按行逐条导入**，
   `group_title` 写进备注作为分组线索。这是本批唯一一处按真实样本推翻已拍板方案，
   已在交付说明里明示。
2. 官方导出教程标「不等于备份」「Firefly III 没有导入功能」，指的是规则 / 订阅 / 附件 /
   存钱罐 / 标签系统 / 预算等附属对象不闭环。本项目本来就没有这些概念，
   所以**不导入它们不构成损失**，只在 UI 明示范围。

## 2. 复式 → 单式映射

| FF3 type | 本项目 | 账户 | counterparty |
|---|---|---|---|
| Withdrawal | EXPENSE | source_name | destination_name（收款方） |
| Deposit | INCOME | destination_name | source_name（付款方 / 来源） |
| Transfer | TRANSFER | source → destination 双账户 | 空（转账隐藏该字段） |
| Opening balance | 不进流水 | 非 `Initial balance account` 那侧 | 空 |
| Reconciliation / 未知 | SKIPPED 并列入报告 | — | — |

- 流水金额一律 `BigDecimal.abs()` → `Long` 分，**恒正**；期初余额保留原符号
- 时间：`OffsetDateTime.parse` → `toInstant().toEpochMilli()` → `LedgerClock.fromEpochMilli`
  派生 `localDate` / `yearMonth`（Asia/Shanghai，与样本 `+08:00` 一致），
  避免指南 §3 T13 的 UTC 午夜差一天
- 分类：`substringBefore('-')` 为一级、`substringAfter('-', "")` 为二级，
  按 FF3 type 分 `EXPENSE` / `INCOME` 两组建树（同名一级跨类型分别建，不冲突）
- 转账不设分类（原 `内流-转账` 写进备注），避免污染收支统计唯一出口
- 外部账户（Expense / Revenue / Initial balance）**不建成账户**，只转 `counterparty`

## 3. 账户类型建议（预览页可逐个改）

| 命中词 | 建议类型 |
|---|---|
| 信用卡 / 花呗 / 月付 / 白条 / 债- | LIABILITY |
| 加油卡 / 充值卡 / 公交卡 / 一卡通 / 储值 | PREPAID |
| 证券 / 基金 / 余额宝 / 零钱通 / 小金库 / 理财 | INVESTMENT |
| 其余 | ASSET |

样本 21 个待映射账户据此得到 6 个 LIABILITY、2 个 PREPAID、5 个 INVESTMENT、8 个 ASSET。
净资产仍只按 `includeInTotal` 汇总，不按 type 分支。

## 4. 期初余额语义

`Opening balance` 行不生成流水，而是转成账户 `initialBalanceCents`：

- 映射到**新建账户**：直接把该账户所有期初行求和（14 行合计 1,019,242.43 元）作为初值
- 映射到**已有账户**：默认**不覆盖**（开关默认关），对应期初行计入「跳过」报告。
  只有使用者显式打开「覆盖已有账户的期初余额」且同名映射时才写回。
  理由：悄悄改掉使用者正在核对的余额，比少一条期初更糟。

## 5. 幂等与去重（零表结构）

`note` 末尾固定带 `ff3:<journal_id>`，复用既有
`recordDao.countDuplicate(账户, 金额, 日期, 备注)`。同一份文件选两次，
第二次全部跳过，不新增字段、不加表、不改 `AppDatabase.version`。

## 6. 事务与一致性

`LedgerRepository.importFirefly` 是第 40 个写方法，包在 `db.withTransaction` 内：

1. 按需新建账户（含期初余额）
2. 按需新建两级分类（`createdRoots` / `createdChildren` 缓存，避免同组重复插入）
3. 逐行 `countDuplicate` → `recordDao.insert(withResolvedRoot(entity))`
4. 末尾 `rebuildAllBalancesInternal()`

任一行非法（账户未映射、转账缺转入、金额 ≤ 0）抛异常 → 整体回滚。
每条都经过 `withResolvedRoot`（一级分类解析 + 币种继承 + 转账同币种校验），
与 `insertRecord` 完全同构，不新开写余额的分支。

## 7. 回退顺序（若 CI 或真机失败）

1. 编译错误先看 `import` 与 `internal fun parseRows` 可见性；
   `firstNotNullOfOrNull` / `OffsetDateTime` 需要 Kotlin 1.5+ 与 java.time desugaring
   （本项目已具备，LedgerClock 已用 `Instant` / `ZoneId`）
2. 若 `LazyColumn` 的 `items` 或 `Modifier.weight` 报解析错误，
   退回 `Column + forEach`（本页最多 49 行，不是万级列表）
3. 若 `createdRoots` 的 Elvis 链推断失败，退回显式 `if` 分支 + 非空 `val`
4. 真机若发现期初余额方向反了，只改 `buildPlan` 里 `Opening balance` 那一行，
   不动 Repository

## 8. 明确不做

复式模型、对方账户建成账户、汇率折算 / 跨币种混算、标签系统、规则引擎、
订阅 / 附件 / 存钱罐 / 预算导入、API 直连与 PAT 凭据（留作可选二期）。

## 9. 实测（2026-11-17）

| 项 | 结果 |
|---|---|
| 代码 commit | `71baf76b35768b57e4c887a0ed970108d23f8de2`（21 文件，v0.2.6） |
| push CI | `44109301622` **success**，17 步全绿，首轮 Kotlin 编译零错误 |
| release | `44110502009` **success**，23 步绿 / 5 步按 input 跳过，**上传到坚果云 success** |
| 制品 | `offline-ledger-release-v0.2.6` 12,272,160 B；`offline-ledger-mapping-v0.2.6` 3,583,067 B；`offline-ledger-metrics-v0.2.6-p-m-nsfalse` 270 B |
| 静态检查 | 六个脚本本地与 CI 全 PASS；写方法清单 40 个 |
| 诊断 | 首轮零失败，未使用临时 `diag.yml`，`diag/` 零残留 |

`nsfalse` 表示坚果云上传步骤 `skipped=false`（认证 / 建目录 / PUT 均未失败）；
HEAD 落盘校验只写日志不中断，不当作已验证 HEAD 200。

**真机未验**：用真实 1916 行样本导入、核对各账户余额与统计页合计、
映射预览与结果报告在 6.73 寸上的可读性 —— 这三项由使用者装机确认，
CI 全绿只证明编译与单测通过。

