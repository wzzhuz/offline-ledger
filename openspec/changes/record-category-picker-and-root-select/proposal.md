# 记一笔分类：停用语义收窄、全量选择器、一级直记

## Why

第 51 批把 59 个扩展二级 / 3 个一级设为 `enabled=false`，但现行语义是
「停用 = 录入侧不可见」：`RecordEditorViewModel.load()` 直接在数据源 `filter { it.enabled }`
把它们删掉，连「选择更多」都选不到。使用者反馈三点：

1. 记一笔默认只应展示启用的分类；不启用的应能像「选择更多账户」那样，点「分类」进入更多，
   且**不要用下拉框**，要尽可能方便选择；筛选面板同步修改。
2. 未选二级时本就该按一级记，不该显示「就按xx记」；选了不想要的二级后，
   再点对应一级就应回到一级。

核查后发现第 2 点的**保存逻辑本来就是对的**：`selectCategory(root.id, root.id)` 已把
`categoryId` 置为一级，`CategoryTree.resolveRootId` 对 level 0 返回自身。
真正的问题是「就按xx记」按钮只调 `setExpandedRoot(null)` 收起二级栏、完全不改
`categoryId`，文案把用户误导成「还得再确认一次」；以及一级选中态只用 `labelSmall` 换色，
视觉上像没选中。

## What Changes

- **停用语义收窄**（改 spec）：从「录入侧不可见」改为「默认快捷栏不展示，
  全量选择器 / 筛选面板仍可选」。数据源改回全量，过滤下沉到 UI。
- **新增 `CategoryQuickCandidates`**（纯 JVM）：快捷栏只列启用项 +
  当前选中项所属一级 / 该二级 MUST 追加；全量分组、搜索命中、停用角标。
- **「分类 ›」全量选择器**：`FormRow` 整行可点（与账户 › 同构），
  分组 WrapRow chips + 搜索，点选即关闭，不用下拉框、不用竖排长列表。
- **一级直记**：新增 `selectRoot`，点一级即 `categoryId = 一级`，
  再点同一级收起二级栏；删除「就按xx记」按钮与死代码 `setExpandedRoot`；
  一级选中态加 `secondaryContainer` 底色。
- **筛选面板同步**：`rootOptions` / `childOptions` / `categoryOptions`
  去掉 `enabled` 过滤，停用项带「（停用）」角标；归档项仍不进选择器。
- 版本号 `0.2.35`（已核实未被 tag / release / artifact 占用）。

## Capabilities

- `reporting-ui`：两级分类快选、记一笔紧凑布局、分类候选类型过滤（MODIFIED + 新增全量选择器 Requirement）
- `category-stats`：预置分类启用开关语义（MODIFIED + 新增「停用仍可选」Requirement）

## Impact

- **非破坏性**：不改分类表数据、不改表结构、`AppDatabase.version` 仍 5、
  `Migrations.ALL` 仍空。v0.2.34 上**直接覆盖安装即可生效**，不需要导出/卸载/导入。
- **不改**：统计口径、余额重算、导出加密、自动备份排除、应用锁、三条铁律、
  第 51 批的 ID 规则与删除策略、三个挂账 change。
- **文件**：新增 2 个 kt（helper + 单测）、改 3 个 kt（ViewModel / Dialog / FilterSheet）、
  2 份 spec、build.gradle.kts、project.md / 活文档、本 change 四件套。
- **风险**：全量选择器一次组合约 106 个 chip（低频打开，可接受）；
  弹层高度上限 460dp + 内部滚动，避免小屏溢出；搜索框与键盘共存需真机验。
