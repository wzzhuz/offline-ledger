# refine-firefly-mapping · Firefly III 导入映射补全

## 为什么

第 73 批归档后核查 `importFirefly` 链路，发现两条真问题：

1. **分类映射只有「按原名自动新建 / 不设分类」**。`FireflyCategoryChoice`
   有 `targetRoot`、`LedgerRepository` 也真的支持「复用一级」，但
   `FireflyCategoryRow` 的 `DropdownMenu` 只给了两个动作项 ——
   模型与执行层都留了口子，唯独 UI 没给入口。预制分类（「餐饮 / 早餐」）、
   别的已有一级、已有二级都选不了；FF3 的 `一级-二级` 永远按原名
   在选定一级下新建或复用，无法映射成我们已有的两级分类。
   而且按名字匹配而不是按 id，同名分类会串。
2. **账户映射到已有账户时期初语义不清晰**。原布尔开关默认关闭 →
   完全不动期初；开启时还藏了一个没写进 UI 说明的
   `targetName == name` 前置条件，改名映射（FF3「Bank A」→ 已有
   「工商银行」）会静默不覆盖。把 FF3 期初覆盖到一个已在 App 里
   手工记过账的账户，会重写 `initialBalanceCents` 这个余额真相源，
   所有历史余额整体平移、已对账余额也会变，通常不是使用者想要的。

## 改什么

- **Q1A**：分类本体改用现成的只读全量选择器 `CategorySelectDialog`
  （搜索 + 左分组导航 + 右彩色网格），按 `RecordType` 过滤、
  一级/二级都能选、**按 id 精确映射**。使用者明确要求「分类用分类选择器，
  不要下拉框一个个分类选」。`⋯` 下拉只保留「按原名自动新建 / 不设分类」
  两个动作，不含任何分类项。
- **Q2A**：期初布尔开关升级为三态 `KEEP / OVERWRITE / ADJUST`。
  「差额调整」不改写历史起点，按「FF3 期初 − 当前期初」走既有
  `BalanceAdjustmentEntity` 机制，最终余额与覆盖一致但已对账余额不变。
- **Q3A**：修掉 `targetName == name` 暗条件，改名映射同样生效，
  并在预览页明示覆盖会平移历史余额。
- 账户映射行补「当前期初 ¥X · FF3 期初 ¥Y / 无（不动）」，
  让「空账户 / 已有流水账户」一眼可判。

## 不改什么

- `CategorySelectDialog` / `CategorySelectChoice` / `CategoryDisplay` /
  `colorHex` / 统计口径 / 加密备份 DTO 一字不动（复用，不造第二套选择器）
- `AccountEntity` / `CategoryEntity` / DAO / `AppDatabase.version = 7` /
  `Migrations.ALL` 全不动 —— **映射模型只在内存，零升表**
- CSV 自有导出 / 导入的 `(name, cardLast4)` 复合匹配键不动
- FF3 幂等键 `(importSource = FIREFLY_III, sourceId = journal_id)` 不动
- 外币不折算、新建账户写死 `CNY` 的既有口径不动
- 不新增 Dependency、不申请 `INTERNET`、不改 `build.yml`

## 验收标准（由我拟）

1. 分类映射行点「选择分类」弹出全量选择器；搜索、左分组导航、彩色网格可用
2. 选择器可选到预制一级与已有二级，回填后副行显示「映射到 · 餐饮 / 早餐」
3. `⋯` 下拉只有两个动作项，MUST NOT 出现任何分类
4. 选已有二级后导入，流水 `categoryId` 为该二级 id、
   `rootCategoryId` 为其父级，MUST NOT 新建同名子类
5. 类型不一致 / 目标 id 不存在时报错并整批回滚
6. 三态可选且各有说明；账户行显示当前期初与 FF3 期初
7. `KEEP` 默认跳过；`OVERWRITE` 改名映射也生效；
   `ADJUST` 不改写 `initialBalanceCents` 且终值与覆盖一致
8. 第二次导入同一份文件，`ADJUST` MUST NOT 重复累加差额
9. 无 OPENING 行时三种策略都不处理；新建账户仍用导出值
10. 8+1 静态检查全 PASS、双 CI 全绿、v0.2.52 直接覆盖安装不丢数据

## 被否决的方案

- 把分类塞进 `DropdownMenu` 逐项列出（使用者明确否决）
- 只让选已有一级、二级仍按原名挂（Q1B，丢已有二级映射能力）
- 只修 `targetName == name` + 补 UI 说明、不加差额调整（Q2B，
  无法解决「重写历史起点」这个根本风险）
- 为差额调整新增一张表或新 DAO 查询（复用既有 `balance_adjustments`）
- 改全局主题 / 动其它页面

## 分级判定

改变系统行为 + 复用并联动既有只读组件 + 与活跃 change
`record-category-picker-and-root-select`（正在改分类选择器）交叉
→ **新建 change `refine-firefly-mapping`**。
不动 4 个已归档 change（含刚归档的 `polish-stats-like-yiyu`）
与其余 6 个进行中 change。
