# design · refine-tag-filter-chips

## 决策 1：轻量标记不用 AssistChip

Material3 `AssistChip` 自带最小高度、左右内边距与图标占位，是「可点的操作」，
而明细行标签是「只读的归属标记」。改用 `Box + clip + background + Text`：
10sp 字、水平 5dp / 垂直 1dp 内边距、primary 8% 底 / 75% 字色，
单行 `Row` 不换行，默认最多 2 个 + 「+N」计数。
详情页传 `limit = Int.MAX_VALUE` 展开全量，但共用同一套配色与组件，不再两套重量标准。

项目 T12 禁用 `FlowRow` / `ExperimentalLayoutApi`，纯 `Row` 也顺带满足。

## 决策 2：标签选择器复用既有 picker，不动构造参数

`FilterSheet` 已有 `picker: String?` + `PickerData` + 带搜索 `LazyColumn` 单选弹层，
账户 / 一级 / 二级 / 币种都走这条路。标签加一个 `"tag"` 分支即可：

- `tagOptions` 由 `produceState` 直查 `LedgerDi.entryPoint(context).tagDao().active()`，
  与 `StatsScreen.TagRankCard` 同款；统计卡已验证该路径可用。
- 首项 `("全部标签", null)`，其余 `(name, name)`（按标签名精确匹配，无需 id）。
- `FilterSheet` 构造参数一字不加——`detail-filter-and-transfer-edit` 与
  `detail-scope-and-record-page` 两个活跃 change 正在改明细侧，扩签名会正面撞上。
- 删掉第 75 批那个独立 `OutlinedTextField`，同时修复基线
  「同区域 MUST NOT 混用 filled 与 outlined 输入框」的违规。

**实现偏差回写**：第 75 批 design 写「筛选面板是文本输入框而非 chips 选择器，
为不扩 FilterSheet 参数签名」。本批在不扩签名的前提下用 `produceState` + 既有 picker
达成选择器，该偏差作废。

## 决策 3：chips 数据单一出口与清除联动

`RecordFilter` 新增：

```
enum class FilterField { TYPE, CATEGORY, ACCOUNT, AMOUNT, CURRENCY,
                         NOTE, DESCRIPTION, COUNTERPARTY, TAG, RANGE, PERIOD }

fun clearField(field: FilterField): RecordFilter
```

`clearField` 是**唯一**清除联动出口，处理三处易漏的耦合：

| 字段 | 连带清理 |
|---|---|
| CATEGORY | `rootCategoryId` + `categoryId` + `categoryDisplayName` |
| ACCOUNT | `accountId` + `accountDisplayName` |
| COUNTERPARTY | `counterpartyQuery` + `counterpartyDisplayName` |
| RANGE | `fromDate` + `toDate` + `periodDisplayName` |
| PERIOD | 只读 `periodDisplayName` |

清完按 `resolveAllMonths()` 同一口径重算 `allMonths`，并 `revision + 1`
让 `flatMapLatest` 重建 Pager；**MUST NOT** 在 UI 侧各自 `copy()`。

chips 列表由 `buildFilterChips(filter, accounts, categories)` 纯函数派生，
覆盖全部 `activeCount()` 条件 + `periodDisplayName`（来自统计，只读），
分类 / 账户展示名沿用「展示名优先、否则按 id 反查」的既有逻辑，
`duplicateKey` / `timeSummary()` 一字不动。

每枚 chip 是自定义 `Surface` 小圆角：点文字区打开筛选面板，
内层 `Box` 的 ✕ `clickable` 消费事件、只调 `vm.clearFilterField(field)`，
不会冒泡到外层（M3 `FilterChip` 的 trailingIcon 会冒泡，故不用）。
横向 `horizontalScroll` 不挤压列表高度，末尾「清空」保留全清快捷入口。

## 决策 4：activeCount 补 tagName

`activeCount()` 的 `listOf(...)` 补 `tagName`。这是纯 JVM 函数，
影响「筛选 (N)」角标、`resolveAllMonths()`、`showMonthOverview()` 三处派生；
补完后按标签筛选时月概览会按既有「有筛选不显示错月数字」口径隐藏。

## 风险与边界

- `periodDisplayName` 不计入 `activeCount()`（基线明写不是独立筛选条件），
  但仍出一枚只读 chip，避免「来自统计的时间」看不见也清不掉。
- 时间范围 chip 与 `timeSummary()` 常驻行会同时出现「时间：X ~ Y」，属职责不同
  （一个可清除的条件、一个查看范围说明），刻意不删 `timeSummary()` 以免碰第 47/48 批口径。
- 零改表 → 覆盖安装，与 v0.2.54 的「卸载重装」不同。
