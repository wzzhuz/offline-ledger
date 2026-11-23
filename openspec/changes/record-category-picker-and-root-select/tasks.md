# Tasks：分类全量选择器与一级直记（第 52 批）

> 状态：代码与文档全部完成；真机验收（§5）待使用者实测。

## 1. 纯 JVM 出口与单测

- [x] 1.1 新增 `core/CategoryQuickCandidates.kt`（visibleRoots / visibleChildren / pickerGroups / searchHits / rootIdOf / labelOf）
- [x] 1.2 新增 `CategoryQuickCandidatesTest` 7 个用例（启用过滤、停用选中兜底、分组含停用不含归档、搜索全名、类型隔离）
- [x] 1.3 Python 复刻逻辑逐条核对断言自洽

## 2. ViewModel

- [x] 2.1 `load()` 数据源改全量 `categoryDao.all()`
- [x] 2.2 默认一级与 `setType()` 重置补 `enabled && !isArchived`
- [x] 2.3 新增 `selectRoot(rootId)`：点一级即按一级记、再点同一级收起二级栏
- [x] 2.4 删死代码 `setExpandedRoot`；`selectCategory` 保留给二级 / 全量选择器

## 3. 记一笔 UI

- [x] 3.1 `FormRow("分类", onRowClick=…)` 整行 › 入口，与「账户 ›」同构
- [x] 3.2 `CategoryStrip` 改吃 helper：快捷栏只列启用 + 当前选中兜底；一级选中态加底色
- [x] 3.3 删「就按xx记」TextButton 与 `onKeepRoot` 参数；二级 chips 改 `FilterChip` + `WrapRow`
- [x] 3.4 新增 `AllCategoriesDialog`：分组 WrapRow chips + 搜索、点选即关闭、460dp 上限滚动、停用角标、不用下拉框
- [x] 3.5 不新增依赖、不动首屏其它行

## 4. 筛选面板同步（按使用者确认）

- [x] 4.1 `rootOptions` / `childOptions` / `categoryOptions` 去掉 `enabled` 过滤，保留 `!isArchived`
- [x] 4.2 新增 `categoryOptionLabel` 统一「（停用）」角标，保留 `CategoryDisplay.resolve` 全名

## 5. 规格与文档

- [x] 5.1 `category-stats`：MODIFIED「启用开关」语义 + 新增「停用仍可选」Requirement 与 2 个 Scenario
- [x] 5.2 `reporting-ui`：MODIFIED 两级快选 / 模板条款 / 紧凑布局 / 类型过滤，新增「全量选择器」Requirement 与 3 个 Scenario
- [x] 5.3 四件套 `openspec/changes/record-category-picker-and-root-select/`
- [x] 5.4 `project.md` 第 52 批、活文档 §7.34
- [x] 5.5 `defaultVersionName` → `0.2.35`（核实未占用）

## 6. 构建

- [x] 6.1 本地六静态检查 + Kotlin 括号 + 死代码残留检查
- [x] 6.2 推送（单次 commit，parent 保持远端 main HEAD）并按新 sha 逐文件校验
- [x] 6.3 push CI 六静态检查 / 单测 / debug / lintVital
- [x] 6.4 出 R8 正式包 v0.2.35（打 tag + 坚果云）
- [x] 6.5 回填实测编号

## 7. 真机验收（沙盒不能验，CI 全绿不得替代）

- [ ] 7.1 记一笔默认快捷栏只出现 7 个启用一级，无「更多分类」chip
- [ ] 7.2 点「分类 ›」，分组 chips 与搜索框可见；小屏不撑破、可滚动
- [ ] 7.3 搜「话费」可见「居住 / 话费（停用）」，点选即关闭并回填，快捷栏临时显示「居住」+ 选中 chip
- [ ] 7.4 停用一级（学习娱乐 / 宠物 / 人情社交）整组可见可选；归档项不在选择器里
- [ ] 7.5 点一级直接保存 → `categoryId == rootCategoryId == 一级`，界面无「就按xx记」
- [ ] 7.6 选二级后再点同一级 → 回到一级且二级栏收起
- [ ] 7.7 一级选中态底色明显可辨
- [ ] 7.8 编辑一条引用停用分类的历史流水：快捷栏有选中态、保存成功、历史归属不变
- [ ] 7.9 筛选面板「分类」弹层能搜到并选中停用项，chip 显示「（停用）」角标，统计结果正确
- [ ] 7.10 收支类型切换后默认一级仍是启用支出 / 收入项，不落到停用扩展项

## 实测回填

待回填（代码 commit sha / push CI run id / release run id / 制品名与体积）。
