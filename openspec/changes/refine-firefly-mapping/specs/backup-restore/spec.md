# backup-restore · Delta Specification

> 第 74 批：Firefly III 导入映射补全。两条均为 ADDED，
> 与既有 273 条 Requirement 标题零冲突。

## ADDED Requirements

### Requirement: Firefly III 分类映射使用全量分类选择器按 id 精确映射

Firefly III 导入预览页 SHALL 为每个 FF3 分类键（`EXPENSE|一级|二级` /
`INCOME|一级|二级`）提供分类映射，MUST NOT 用 `DropdownMenu` 逐项列出分类。
分类本体 SHALL 通过既有只读全量选择器 `CategorySelectDialog` 选择：
搜索 + 左侧一级分组导航 + 右侧彩色图标网格，按 `RecordType` 过滤、
归档项不进选择器、点任一项立即回填并关闭。
选择器返回值 SHALL 以 `CategorySelectChoice.categoryId` **按 id** 写入
`FireflyCategoryChoice.targetCategoryId`，`displayLabel` 写入
`targetDisplayName` 供预览回显；可指向一级或二级分类。
「更多映射方式」下拉 MUST 只含「按原名自动新建」「不设分类」两个**动作**，
MUST NOT 含任何分类项。
执行层 `importFirefly` 在 `targetCategoryId != null` 时 SHALL 直接按 id
命中既有 `categories` 行，MUST NOT 再按名字新建 / 复用，也 MUST NOT
把 FF3 的二级名挂成新子类；选一级时 `rootCategoryId = id` 且
`categoryId = rootCategoryId`，选二级时 `rootCategoryId = parentId`
且 `categoryId = id`。类型不一致（`EXPENSE` 映射到收入分类）SHALL
报错并整批回滚；目标 id 不存在 SHALL 报错并整批回滚。
`targetCategoryId == null && !skip` 时 SHALL 保留「按 FF3 原名自动新建」
旧路径；`skip` SHALL 保持「不设分类」。
同名一级自动预填 SHALL 同样按 id 写入，MUST NOT 再按名字匹配。
`CategorySelectDialog` / `CategorySelectChoice` / `CategoryDisplay` /
`colorHex` / 统计口径 / 加密备份 DTO MUST NOT 改动；
映射模型只在内存，MUST NOT 升表。

#### Scenario: 用选择器映射到已有二级分类

- **GIVEN** 库里有预制「餐饮 / 早餐」，FF3 分类键为 `EXPENSE|食|堂食`
- **WHEN** 点该行的「选择分类」，在搜索框输「早餐」并点选
- **THEN** 选择器关闭，副行显示「映射到 · 餐饮 / 早餐」
- **AND** `targetCategoryId` 为早餐的 id，`targetDisplayName` 为「餐饮 / 早餐」

#### Scenario: 选已有二级后不新建子类

- **GIVEN** 上一步映射已保存
- **WHEN** 确认写入并检查导入的支出流水
- **THEN** `categoryId` = 早餐 id、`rootCategoryId` = 餐饮 id
- **AND** 库里 MUST NOT 新增名为「堂食」或「食」的分类

#### Scenario: 用选择器映射到预制一级

- **GIVEN** 库里有预制一级「交通」，FF3 分类键为 `EXPENSE|行|交通`
- **WHEN** 在左分组导航点「交通」并点该一级格
- **THEN** 副行显示「映射到 · 交通」，`targetCategoryId` 为该一级 id
- **AND** 导入流水 `categoryId` 与 `rootCategoryId` 都等于该 id

#### Scenario: 下拉不含任何分类

- **WHEN** 点分类映射行的「更多映射方式」
- **THEN** 下拉只有「按原名自动新建」「不设分类」两项
- **AND** MUST NOT 出现任何分类名称或分类网格

#### Scenario: 两个动作仍可用

- **WHEN** 在下拉里分别点「按原名自动新建」「不设分类」
- **THEN** 前者清空 `targetCategoryId` / `targetDisplayName` / `skip`
  并走按 FF3 原名建树；后者置 `skip = true`，流水不带分类

#### Scenario: 按收支类型过滤

- **GIVEN** FF3 分类键 type 为 `INCOME`
- **WHEN** 打开选择器
- **THEN** 只列出收入类分类，MUST NOT 出现支出类分类
- **AND** 强制映射成支出 id 时 SHALL 报「分类映射类型不一致」并整批回滚

#### Scenario: 目标 id 不存在

- **GIVEN** 映射里 `targetCategoryId` 指向已被清理的 id
- **WHEN** 确认写入
- **THEN** 报「分类映射目标不存在」并整批回滚，账本不变

#### Scenario: 同名自动预填按 id

- **GIVEN** 库里已有一级「餐饮」，FF3 分类根名也是「餐饮」
- **WHEN** 进入预览页（未手动改映射）
- **THEN** 该行 `targetCategoryId` 自动为「餐饮」的 id
- **AND** MUST NOT 靠名字字符串匹配落位

#### Scenario: 零升表

- **WHEN** 检查 `AppDatabase` / `Migrations.ALL` / 各 Entity
- **THEN** `version` 仍为 7、`Migrations.ALL` 仍为空、Entity 零改动
- **AND** v0.2.52 可直接覆盖安装，数据不丢

### Requirement: Firefly III 已有账户期初采用保持覆盖差额调整三态策略

`FireflyMapping` SHALL 用 `FireflyOpeningPolicy { KEEP, OVERWRITE, ADJUST }`
取代原布尔 `overwriteOpeningForExisting`，默认 `KEEP`。
预览页 SHALL 提供三态可选控件并逐态说明后果；账户映射行 SHALL 显示
「当前期初 ¥X · FF3 期初 ¥Y / 无（不动）」，使空账户与已有流水账户可区分。
`KEEP` 映射到已有账户时 SHALL 完全不动 `initialBalanceCents`，
FF3 期初行计入 `openingsSkipped`；新建账户 SHALL 仍用导出值作期初。
`OVERWRITE` SHALL 把 FF3 期初写入 `initialBalanceCents`，
MUST NOT 再附带 `targetName == name` 前置条件（改名映射同样生效）；
说明 MUST 明示「会重写余额真相源、已有流水的账户全部历史余额平移、
已对账余额会变」。
`ADJUST` SHALL 不改 `initialBalanceCents`，按
`delta = FF3 期初 − 当前期初` 经既有 `BalanceAdjustmentEntity`
（`source = FIREFLY_OPENING`）写入调整；末尾
`rebuildAllBalancesInternal()` SHALL 把 adjustments 计入余额，
使最终余额与 `OVERWRITE` 一致但历史起点不变。
同一账户已有 `FIREFLY_OPENING` 调整时 SHALL 不再插入第二条
（第二次导入同一份文件 MUST NOT 重复累加差额）。
三种策略 SHALL 只在「映射到已有账户且 FF3 文件里有该账户的 OPENING 行」
时生效；无 OPENING 行时 SHALL 不做任何处理，新建账户期初记 0。
FF3 期初行的 `currency_code` 仍 SHALL 被忽略、新建账户写死 `CNY`
（与「外币不折算、按主币种落账」一致）。

#### Scenario: KEEP 默认跳过

- **GIVEN** 已有账户「工商银行」当前期初 ¥1000，FF3 期初 ¥800
- **WHEN** 保持默认策略导入
- **THEN** `initialBalanceCents` 仍为 100000 分，期初行计入跳过报告
- **AND** 余额 = 原期初 + 已有流水 + 新导入流水，期初不被改写

#### Scenario: OVERWRITE 改名映射也生效

- **GIVEN** FF3 源名「Bank A」映射到已有「工商银行」（名字不同）
- **WHEN** 选「覆盖」并导入
- **THEN** `initialBalanceCents` 被写成 FF3 期初值
- **AND** MUST NOT 因 `targetName != name` 而静默跳过

#### Scenario: OVERWRITE 已有流水账户平移历史余额

- **GIVEN** 目标账户已有 12 条流水且已做过对账
- **WHEN** 选「覆盖」并导入
- **THEN** 期初被替换，`rebuildAllBalancesInternal()` 重算后
  所有历史余额整体平移，已对账快照与当前余额不再一致
- **AND** 预览页说明 MUST 已提前明示该后果

#### Scenario: ADJUST 不改写历史起点且终值一致

- **GIVEN** 当前期初 ¥1000、FF3 期初 ¥800、已有流水若干
- **WHEN** 选「差额调整」并导入
- **THEN** `initialBalanceCents` 仍为 100000 分
- **AND** 插入一条 `deltaCents = -20000`、`source = FIREFLY_OPENING` 的调整
- **AND** 最终余额与「覆盖」相同，但历史起点 / 已对账余额不变

#### Scenario: ADJUST 二次导入不重复累加

- **GIVEN** 已用「差额调整」导入过同一份文件
- **WHEN** 再次导入（流水按 `(FIREFLY_III, journal_id)` 全跳过）
- **THEN** 期初段 MUST NOT 再插入第二条 `FIREFLY_OPENING` 调整
- **AND** 余额 MUST NOT 被差额重复扣减

#### Scenario: 无 OPENING 行

- **GIVEN** FF3 文件里没有该账户的 `Opening balance` 行
- **WHEN** 分别用三种策略导入
- **THEN** 已有账户的期初三种策略都不做任何处理
- **AND** 新建账户期初记 0

#### Scenario: 预览两值回显

- **GIVEN** 账户映射到已有账户
- **WHEN** 查看预览页账户行
- **THEN** 显示「当前期初 ¥X · FF3 期初 ¥Y」，无 OPENING 行时显示「无（不动）」
- **AND** ¥X 取自 `(targetName, targetCardLast4)` 复合命中的账户，
  ¥Y 与 repository 的 OPENING 累加口径一致
