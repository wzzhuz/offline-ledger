# 设计：附加金额（优惠 / 手续费）

## 1. 真相源决策（核心）

**`amountCents` = 实际金额（实付）**，与竞品官方 CSV 手册
「金额：账单/转账金额，填写**实际的金额**」一致。

| 方案 | 结论 |
|---|---|
| A `amount`=实付 + 附加列 | ✅ 采用：30 多处 `SUM(amountCents)` 一行不改，CSV 金额列语义不变 |
| B `amount`=原价，统计时减优惠 | ❌ 否决：统计/预算/余额/图表/CSV 全线要改，且违背「一个字段别管两种语义」 |

原价 = `amountCents + discountCents`，**不落库**（单一真相源推导）。
这与第 60 批 `name` 只存纯名、`cardLast4` 单一真相源是同一条原则。

## 2. 字段与校验

`records`：`discountCents: Long? = null`、`feeCents: Long? = null`（单位分）。
`trash` 同步两列，否则删除→恢复会静默丢信息（第 43 批 description 的教训）。

| 字段 | 适用 | 约束 |
|---|---|---|
| `discountCents` | `EXPENSE` / `INCOME` | MUST `0 < d <= amountCents`（官方明文「必须为正数，且不能超过账单金额」） |
| `feeCents` | `TRANSFER` | 正数=手续费；**负数=转账优惠**（官方「填写负数金额表示转账优惠」）；`abs(fee) < amount` |

一个带符号 `feeCents` 覆盖手续费/转账优惠两类，比拆两列更贴竞品，
也少一个字段。`discountCents` 与 `feeCents` 互斥（一笔不会既是优惠又是手续费）。

## 3. 余额联动（唯一需要改算法的地方）

现状（`RecordDao.aggregateAccountDeltas` + `LedgerEngine.deltasFrom` 等价）：
`from -> -amount`、`to -> +amount`，不按 type / 账户类型分支。

本批**只给 TRANSFER 的 from 侧加 fee**：
`from -> -(amount + fee)`、`to -> +amount`。
EXPENSE / INCOME / SETTLEMENT 一行不改。手续费不产生对应入账、
不进收支统计（`StatRules` 只认 EXPENSE/INCOME），UI 必须明示
「手续费不计入收支，仅影响账户余额」。

MUST 同时改两处并补纯 JVM 单测锁一致性：
① DAO 的 `UNION ALL` 聚合 SQL；② `LedgerEngine.deltasFrom`。
只改一处会让生产与单测出现两个余额真相。

## 4. 信用卡还款优惠（Q3A）

复用既有 `SETTLEMENT`（退款/报销收回款，`toAccountId` = 原支出账户、
`linkedRecordId` 指向原流水、`StatRules` 天然不进收支概览）：
还款优惠生成一笔 `SETTLEMENT` 冲减，把欠款平掉。

**与竞品的路径差异（如实登记）**：一羽把还款视为转账操作，
用「负手续费（转账优惠）」表达；本批按使用者 Q3A 走 SETTLEMENT 冲减。
账效等价、路径不同，MUST NOT 写成「与一羽实现一致」。

## 5. UI

- 记一笔：金额输入区下方「优惠 / 手续费」折叠入口；
  支出/收入只显示优惠、转账只显示手续费；填入后即时显示
  「原价 / 优惠 / 实付」三行或「转出 / 手续费 / 实付扣款」。
- 明细行：蓝色「优惠 29.99」小标签 + 划线原价（借截图版式，
  不复制视觉资产）；手续费用中性灰标签，避免和优惠色混。
- 详情页：原始金额 / 优惠或手续费 / 实际金额三行，可编辑，
  编辑态回填按 `amount + discount` 推导，MUST NOT 直接读一个不存在的原价列。
- 不做会员墙；一羽把此功能放在付费墙后，本批全量免费开放。

## 6. CSV / 备份 / 回收站

- `CsvRow` 加 `discountYuan: String = ""`、`feeYuan: String = ""`；
  导出必带、导入缺列按空串（老 8~13 列兼容，沿用第 43/63 批做法）。
  **`duplicateKey()` 一字不动**，账户复合匹配 `(name, cardLast4)` 不受影响。
- `BackupRecord` / `LedgerSnapshot` / trash 加两可选字段（默认 `null`/0）；
  `BackupExporter/Importer`、`SnapshotCodec` 补映射；旧备份按默认值解析。
- Firefly III 映射无对应字段，保持空值，MUST NOT 把优惠塞进 description。

## 7. 升表

`AppDatabase.version 6 → 7`；`Migrations.ALL` 保持 `emptyArray()`、
`MigrationContractTest` 零改动（使用者「测试数据、不考虑升级迁移」）。
`SeedData` 无 records 插入，INSERT 列清单无需改。
`check_transaction_annotations.py` 若核对 version/ALL，需同步预期值。

## 8. 与活跃 change 的交叉（不默默绕）

| change | 交叉点 | 处置 |
|---|---|---|
| `detail-filter-and-transfer-edit` | 转账编辑态表单重叠 | 本批只加金额附加区，MUST NOT 碰账户 id 回填逻辑；归档/收口时合并校验 |
| `ff3-description-field` | 筛选 / 规则字段扩展 | 本批**不支持**「按优惠金额筛选 / 规则改写」，留到其收口后 |
| `restructure-preset-categories` | 预置清单 | 不新增「手续费」预置分类 |

## 9. 竞品调研结论（2026-11-24 查证）

- 官方 CSV 手册：`金额`=实际金额；`优惠`=支出附加金额（正、≤ 账单金额）；
  `手续费`=转账附加金额（负数表示转账优惠）；另有 `不计预算` / `不计入收支` 开关。
- 商店描述：「还原真实消费情况」；功能清单：「优惠券、手续费记录管理」。
- 资产管理手册：还款相当于转账；分期每期可带「原账单 + 手续费账单」，
  手续费影响总负债与可用额度 → 印证「手续费参与余额」。
- **未发现**一羽有「累计节省 / 省钱报表」。同类「省钱总额报表 / 省钱罐」
  属咔皮记账、省吗、省省账等另一类产品思路，MUST NOT 安到一羽头上，
  也不在本批范围。若后续要做，按 `discountCents` 汇总即可，不改余额。

## 10. 真机待验（全部留 `[ ]`）

记一笔三行金额键盘不遮挡、优惠非法输入提示、转账手续费后两账户余额、
转账优惠（负 fee）文案、还款优惠冲减后欠款归零、明细划线原价与蓝色标签、
详情页编辑往返、CSV 14 列往返与老 CSV 兼容、旧备份恢复、
回收站删除→恢复、卸载重装后数据、§8 第 45–65 批既有项整体继承。
CI 全绿不替代真机。

## 11. 实现校准说明（第 67 批补齐后回填）

### 11.1 Q3A 路径变更（如实，不包装成一致）

四项拍板里 Q3A 原为「复用 `SETTLEMENT + linkedRecordId` 自动补一笔减免把
欠款平掉」。核查草稿与一羽官方文档后**改为 `TRANSFER` + 负 `feeCents`**：

- 官方资产管理手册：「还款相当于转账操作」；CSV 手册：「手续费填写**负数**
  金额表示**转账优惠**」。自动建 `SETTLEMENT` 反而不是一羽的做法。
- 账效等价且更省：账单 500 / 优惠 29.99 → 转账金额填 **500.00**、
  转账优惠填 **29.99** → 转出实扣 `500 − 29.99 = 470.01`、转入 500、
  欠款归零。零新增流水、零 `linkedRecordId` 联动、零统计口径改动。
- 代价：使用者 MUST 把「金额」填账单全额而非实付，UI 用「转出金额 /
  转账优惠 / 实际扣款」三行预览明示，避免误填。
- **`SETTLEMENT` 自动冲减本批未实现**。`ExtraAmountRules` 明确拒绝给
  SETTLEMENT 附加金额，保持退款/报销语义干净。若后续仍需「自动补一笔
  减免」，须单独建 change 处理双流水的删除/恢复/CSV 往返联动。

> 注意：转账场景的 `amountCents` 语义是「转账面额」而非「实付」，
> 与 EXPENSE 优惠的「实付」不同。这不违反真相源原则（各自场景单一含义），
> 但 MUST NOT 写成「全类型 amountCents 一律等于实付」。

### 11.2 备份链路不需要改三个文件（正向发现）

`BackupExporter` / `BackupImporter` / `SnapshotCodec` 的 grep 无附加金额命中，
一度被判为「链路未闭合」。实际 `LedgerRepository` 导出用 `.toDto()`、
导入用 `.toEntity()`、回收站用 `.toTrash()/.toRecord()`，
全部收敛在 `LedgerMappers.kt` 的 4 处映射里；草稿已把 4 处都补上，
**三个文件零改动即完成往返**。`tasks 5.2` 据此订正。

### 11.3 余额双实现的可测性

`RecordDao.aggregateAccountDeltas` 的 SQL 在 `@Query` 注解里，纯 JVM 单测
取不到。本批**不抽常量**（避免 KSP 解析注解常量引用的编译风险），改为：
- `ExtraAmountBalanceTest` 锁住 `LedgerEngine.deltasFrom` 的数值行为；
- 新增 `scripts/check_extra_amount.py` 静态断言 DAO SQL 含
  `CASE WHEN type = 'TRANSFER' THEN feeCents`、`COALESCE`、to 侧仍为
  `+amount`，并核对引擎同步、字段全链路、UI 三处、version 7 / ALL 空。
七个静态检查全 PASS。MUST NOT 只改一侧余额实现。

### 11.4 落地清单

| 层 | 状态 |
|---|---|
| Entity / Trash / version 7 / Migrations 空 | ✅ |
| DAO 余额 SQL + 投影两处 / CsvSourceRow / RecordRow | ✅ |
| `LedgerEngine.deltasFrom` | ✅ |
| `ExtraAmountRules` 唯一校验入口 | ✅ |
| `LedgerMappers` 4 处往返 | ✅ |
| `CsvRow` / `CsvCodec` 15 列 + 老 CSV 兼容 | ✅ |
| `BackupRecord` / 快照 / 回收站（经 Mappers） | ✅ |
| `LedgerRepository` 导出换算 + 两处导入换算 + trash 导出 | ✅ |
| `RecordEditorViewModel` 校验 / 回填 / 保存 | ✅ |
| **记一笔折叠入口 + 三行金额** | ✅ 本批补齐 |
| **明细行划线原价 + 蓝/灰标签** | ✅ 本批补齐 |
| **详情页三行只读金额** | ✅ 本批补齐 |
| 纯 JVM 单测 3 组 / 静态脚本 | ✅ 本批补齐 |
| 版本号 0.2.47 | ✅ 本批补齐 |

**明确未做（不假装完成）**：筛选 / 规则按优惠金额查询
（等 `ff3-description-field` 收口）；累计优惠统计（一羽没有，属另一产品思路）；
`SETTLEMENT` 自动冲减（见 11.1）；「不计预算」开关（本 App 已有
`excludeFromStats`，语义不完全等价，留待决策）。

