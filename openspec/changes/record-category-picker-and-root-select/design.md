# Design：分类默认快捷栏 / 全量选择器 / 一级直记（第 52 批）

## 0. 口径复述

使用者第 2 点原文「筛选忙不也同步修改」按**「筛选面板也同步修改」**执行：
筛选面板的三处分类选项一并去掉 `enabled` 过滤并给停用项加角标。
筛选面板本身已有「分类」PickRow → 带搜索的全量单选弹层（第 27 批），
**不再新增「更多」按钮或 chip**，避免与「首屏无更多 chip」既有条款冲突。

## 1. 停用语义：数据源与视图分层

| 层 | 原 | 新 |
|---|---|---|
| `RecordEditorViewModel.load()` | `categoryDao.all().filter { it.enabled }` | `categoryDao.all()` 全量 |
| 默认一级 / `setType` 重置 | 取第一个一级 | 取第一个**启用且未归档**的一级 |
| 快捷栏一级 | 全量里筛 level/type/archived | `visibleRoots` = 启用且未归档 + 当前选中项所属一级 |
| 快捷栏二级 | 全量里筛 parentId | `visibleChildren` = 启用且未归档 + 当前选中的二级 |
| 全量选择器 | 无 | `pickerGroups` = 未归档一级（含停用）+ 组内未归档二级 |
| 筛选面板 | 4 处 `&& it.enabled` | 去掉，停用项加「（停用）」角标 |

**为什么要「当前选中项追加」**：`save()` 的校验查 `categoryDao.all()` 全量，
所以编辑一条引用停用分类的历史流水不会保存失败；但旧代码 `_categories` 不含停用项 →
快捷栏既无对应一级图标也无高亮，用户会看到「分类明明选着，界面什么都不亮」。
照搬第 44 批 `AccountQuickCandidates.select(items, selectedId, N)` 的思路，
用纯 JVM 出口统一保证。归档项同样由这条兜底显示，但不进全量选择器（归档 = 不再新选）。

## 2. 纯 JVM 出口 `CategoryQuickCandidates`

放在 `core`，零 Android / Room 依赖，7 个纯 JVM 单测覆盖：
默认只含启用、选中停用一级仍可见、选中停用二级其父与自身都可见、
全量分组含停用不含归档、搜索命中带父级全名、`rootIdOf`、收支类型隔离。

`type` 用 String（`"EXPENSE"` / `"INCOME"`）而非 `RecordType`，helper 彻底无依赖；
`CategoryQuickItem` 带 `colorHex` 供 `CategoryIcon` 使用，默认空串不影响单测。

## 3. 「分类 ›」全量选择器（B 方案）

**入口**：`FormRow("分类", onRowClick = { showAllCategories = true })`。
`FormRow` 已有能力：传 `onRowClick` 时 label 自动追加 `›`、整行可点。
与 `AccountStrip` 的「账户 ›」完全同构，不新增 chip、不占首屏高度，
满足 `reporting-ui` 「一屏无需上滑」硬要求。

**弹层**：`Dialog(usePlatformDefaultWidth = false)` + `Surface(fillMaxWidth)`，
不引入 ModalBottomSheet、不新增依赖（免依赖审计）。结构：

```
标题「全部分类」+ 关闭 ✕
[ 搜索一级或二级分类名… ]
Column(heightIn(max = 460dp), verticalScroll)
 ├ 餐饮  ← 组标题 TextButton，点 = 按一级直记
 │  [正餐][外卖][聚餐][买菜做饭][零食（停用）][水果（停用）]…
 ├ 交通
 │  [公共交通][打车][长途交通][共享单车][租车（停用）]…
 ├ 学习娱乐（停用）  ← 停用一级也可整组进入
 │  [课程书籍（停用）][影音会员（停用）]…
```

- 分组数据来自 `pickerGroups`，组内用项目已有的 `WrapRow`（`ui.components`），
  与分类管理页的 chips 风格一致。
- 组标题 / chip 点一下 **立即 `onSelect(id, rootId)` 并 `onDismiss()`**，
  MUST NOT 二次确认。一级传 `(root.id, root.id)`，二级传 `(child.id, root.id)`。
- 搜索非空时改为跨组平铺命中项，二级 label 为「一级 / 二级」全名
  （`searchHits` 内拼，等价于 `CategoryDisplay.resolve` 的展示口径），避免同名混淆。
- 选中项用 `secondaryContainer` 底色；停用项 `labelOf` 加「（停用）」角标、
  归档项不出现。
- 高度上限 460dp + 内部 `verticalScroll`，避免 15 组 / 106 chips 撑破小屏。

**被否决**：
- A 仿账户 `AlertDialog` 竖排 TextButton：106 行要长距离滚动，层级靠拼字符串分辨；
- C 快捷栏尾部加「更多」chip 切全量平铺：挤占首屏，
  重蹈活文档 §7「模板改成 6 个但实际在一行，还是要拖动选更多」；
- Spinner / ExposedDropdown / DropdownMenu：使用者明确否决下拉框。

## 4. 一级直记与「就按xx记」

| 动作 | 新行为 |
|---|---|
| 点一级 X（首次） | `selectRoot(X)` → `categoryId = X`，`expandedRootId = X`（展开二级供可选细分） |
| 点一级 X（已展开 X） | `selectRoot(X)` → `categoryId = X`，`expandedRootId = null`（收起） |
| 点二级 Y | `selectCategory(Y, X)` → `categoryId = Y` |
| 选了 Y 后再点 X | `selectRoot(X)` → `categoryId` 回到 X，收起二级栏 |
| 直接保存 | `CategoryTree.resolveRootId` 对 level 0 返回自身 → `rootCategoryId = X` |

删掉 `TextButton("就按「xx」记")` 与死代码 `setExpandedRoot` /
`onKeepRoot`。一级选中态由「文字换色」升级为
`background(secondaryContainer, RoundedCornerShape(8.dp))`，与账户 `FilterChip` 的
底色表达一致。`load()` 默认一级与 `setType()` 重置补 `enabled && !isArchived`，
避免新记一笔默认落到停用扩展项上。

## 5. 筛选面板同步

`rootOptions` / `childOptions` / `categoryOptions` 三处去掉 `&& it.enabled`，
仍保留 `!it.isArchived`（归档不新选）。新增私有
`categoryOptionLabel(c) = if (c.enabled) c.name else c.name + "（停用）"`，
三处 label 统一走它。`categoryOptions` 的二级仍经 `CategoryDisplay.resolve`
拼「一级 / 二级（停用）」。`displayName` 由 `categoryOptions` 反查，
因此筛选 chip 会如实显示停用角标；`initial.categoryDisplayName` 回填路径不变。

## 6. 交互次数（铁律 3：记账不能变慢）

- 常规：打开 → 点一级 → 输入金额 → 保存，**仍是 ≤3 次交互**，
  且比原方案少一个「就按xx记」按钮与一次潜在确认。
- 选停用项：打开 → 点「分类 ›」→（搜索）→ 点一项 → 输入金额 → 保存，
  属于低频路径，弹层点选即关闭，不增加默认路径任何一步。
- 不改计算器键盘、不改账户 / 模板 / 对方 / 描述 / 备注任何既有交互。

## 7. 版本与出包

`defaultVersionName` → `0.2.35`（未占用）。零改表 → **v0.2.34 直接覆盖安装生效**，
不需要导出 / 卸载 / 导入。出 R8 包、打 tag、传坚果云。
