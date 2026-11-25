# 设计 · Firefly III 导入映射补全（第 74 批 / v0.2.53）

## 1. 分层与零升表

```
FireflyImportScreen (Compose)
  ├─ 分类映射行 → CategorySelectDialog(既有只读组件，零改动)
  │                  └─ onSelect(CategorySelectChoice{categoryId, displayLabel, level})
  ├─ 期初策略行  → FilterChip × 3 (FireflyOpeningPolicy)
  └─ 账户映射行  → 现有 accountBaseLabel + 新增期初两值回显
        ↓ 内存态
FireflyCategoryChoice(+targetCategoryId/+targetDisplayName)
FireflyMapping(openingPolicy: FireflyOpeningPolicy)
        ↓ 单事务
LedgerRepository.importFirefly
  ├─ 分类：按 id 命中 → (rootId, childId?) 直接进 categoryIds
  └─ 期初：KEEP / OVERWRITE / ADJUST(→ balance_adjustments)
```

映射模型是纯内存态（`FireflyModels.kt` 全是 data class / enum），
**不碰 Room Entity、不升 `version = 7`、不注册 Migration**。
所以 v0.2.52 可直接覆盖安装，数据不丢。

## 2. Q1A：为什么用 `CategorySelectDialog` 而不是扩展下拉

`CategorySelectDialog`（第 56–58 批）已经是记一笔「分类 ›」与明细筛选
「分类」共用的只读组件：

- `categories: List<CategoryEntity>` / `type: RecordType?` /
  `selectedId: Long?` / `allowAll: Boolean` / `title` /
  `onSelect: (CategorySelectChoice) -> Unit` / `onDismiss`
- 内部已按 `!isArchived && (type == null || it.type == type)` 过滤，
  搜索跨组平铺，左分组导航 + 右 4 列彩色图标网格
- `choose(c)` 返回 `CategorySelectChoice(c.id, rootId, c.name, displayLabel, c.level)`
  —— **id 与拼父级的显示名都有**，正好对应本批的两个新字段

复用它的三个好处：① 分类数量再多也不挤（下拉会变成上百项长列表）；
② 与活跃 change `record-category-picker-and-root-select` 共用同一个组件，
   不会造出第二套选择器；③ 零新增依赖、零写库路径。

`allowAll = false`：左导航首项「全部一级」只切分组、不触发 `onSelect`，
因此选择器返回的 `categoryId` 恒非 null。`⋯` 下拉保留两个动作项
（「按原名自动新建」「不设分类」），**不含任何分类** —— 这正是使用者
「不要下拉框一个个分类选」的边界。

### 执行层按 id 而不是按名字

```kotlin
val selectedId = choice?.targetCategoryId
if (selectedId != null) {
    val hit = existingCategories.firstOrNull { it.id == selectedId && !it.isArchived }
        ?: throw IllegalArgumentException(...)
    require(hit.type == type) { "分类映射类型不一致" }
    val rootId = if (hit.level == 0) hit.id else (hit.parentId ?: hit.id)
    categoryIds[ck.key()] = Pair(rootId, if (hit.level == 1) hit.id else null)
    return@forEach                       // 不再走按原名建树
}
```

`Pair(rootId, childIdOrNull)` 与既有写流水语句
`categoryId = pair?.second ?: pair?.first` 完全兼容：
选一级时 `second = null` 落到 root；选二级时 `second = childId`。
`withResolvedRoot()` 仍会在写库前重算 `rootCategoryId`，双保险。

未选 id 时保留旧路径（`targetRoot` 覆写 rootName + 按名字复用/新建子类），
即「按原名自动新建」。`SettingsViewModel` 的同名一级自动预填也改成按 id
写入 `targetCategoryId`，默认行为等价但不再按名字匹配。

## 3. Q2A：三态期初与「重写历史起点」的风险

`initialBalanceCents` 是余额真相源，`rebuildAllBalancesInternal()` 按
「期初 + 全部流水增量 + adjustments」算 `balanceCents`。因此：

| 策略 | 写什么 | 空账户 | 已有流水账户 |
|---|---|---|---|
| `KEEP`（默认） | 什么都不写 | 余额 = 原期初 | 余额 = 原期初 + 全部流水 |
| `OVERWRITE` | `initialBalanceCents = FF3期初` | 余额 = FF3期初（≈ 改余额，安全） | **全部历史余额整体平移**，已对账余额会变 |
| `ADJUST` | 一条 `BalanceAdjustmentEntity(delta)` | 同 OVERWRITE 终值 | 终值同 OVERWRITE，但 **期初保持原值、历史起点不重写** |

`delta = FF3期初 − 当前期初`。`ADJUST` 插一条
`source = "FIREFLY_OPENING"` 的调整，末尾统一重算后：

```
余额 = 原期初 + (FF3期初 − 原期初) + 流水 = FF3期初 + 流水
```

与 `OVERWRITE` 终值一致，但 `initialBalanceCents` 没变，
对账快照 / 核对基线不受影响。这正是把「覆盖」与「调整」分开的价值。

### 幂等

`OVERWRITE` 天然幂等（第二次 set 同值）。`KEEP` 什么都没做。
**`ADJUST` 不幂等**：第二次导入同一份文件时流水会被
`(FIREFLY_III, journal_id)` 全跳过，但期初段会再跑一遍，
若再插一条相同 `delta` 就会把差额累加两次。
因此插入前查 `adjustmentDao.allActive()` 中同账户同 `FIREFLY_OPENING`
的调整，已存在则不再插。代价：第一次导入后若手工改过该账户期初，
第二次导入不会重新调整 —— 如实登记为边界，比重复累加安全。

### Q3A：修掉暗条件

原代码 `if (mapping.overwriteOpeningForExisting && targetName == name)`。
`targetName == name` 要求映射目标名与 FF3 原名完全相同，
改名映射时开关开着也静默走 `openingsSkipped`。
本批 `OVERWRITE` 分支去掉该条件，并在 UI 说明里明示后果。

## 4. 预览行两值回显

`FireflyImportState` 新增 `openingBySource: Map<String, Long>`，
在 ViewModel 用与 repository **完全相同**的口径
（OPENING 行按 `sourceName` 累加 `Cents.fromYuan(...).value`）算出，
供账户行显示「当前期初 ¥X · FF3 期初 ¥Y / 无（不动）」。
当前期初从 `state.accounts` 按 `(targetName, targetCardLast4)` 复合匹配取。
不加新 DAO 查询、不改 `RecordDao`。

## 5. 明确未动

`CategorySelectDialog.kt`、`CategoryDisplay`、`AccountEntity`、
`CategoryEntity`、所有 DAO、`AppDatabase.version = 7`、`Migrations.ALL`、
`SeedData`、备份 DTO / SnapshotCodec、CSV 复合匹配键、
FF3 幂等键、统计口径、`LedgerTheme`、`build.yml`、零 `INTERNET`。
