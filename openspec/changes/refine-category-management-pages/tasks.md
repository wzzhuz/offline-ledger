# 任务清单：refine-category-management-pages

## 1. 阶段 0 对齐（硬门禁）

- [x] 1.1 复述需求，撤回「等宽图标网格必然留白大」的错误外推
- [x] 1.2 核查远端 `HEAD` / 最近 3 条 commit / Actions / 制品与活文档必读章节
- [x] 1.3 核查清单 v4 数量分布、最长名、`iconName` / `colorHex` 现状、导航与依赖
- [x] 1.4 产出五项对齐结论；验收标准按上轮约定由本 change 拟一版写入 proposal
- [x] 1.5 使用者确认四项：彩色图标放行 / 等宽网格放行 / 借版式做选择页（A）/ 进 change
- [x] 1.6 判定「需建 change」并告知，获确认

## 2. 写 change 四件套

- [x] 2.1 `proposal.md` —— 含完整「对齐结论」节与上下文入口声明
- [x] 2.2 `design.md` —— 含「考虑过的其他方案」表、取舍、风险、依赖与许可、方案 A 实施记录
- [x] 2.3 `tasks.md` —— 本文件
- [x] 2.4 `specs/category-stats/spec.md` —— 2 MODIFIED + 4 ADDED（本批按实现修订 3 处，未新增第二份 MODIFIED）
- [x] 2.5 `specs/reporting-ui/spec.md` —— 1 MODIFIED
- [x] 2.6 **停下来等使用者确认四件套**
      —— 2026-11-23 使用者回复「确认，继续下一步」，无删改

## 3. 推送到 GitHub（开发前独立一步）

- [x] 3.1 回显「目标仓库 `wzzhuz/offline-ledger` + 本地源目录」，**等使用者确认**
      —— 回显于上一轮，使用者本轮确认
- [x] 3.2 推送 change 四件套；推送前比较本地与远端 sha，确认本地未落后（踩坑清单 4.5）
      —— 远端 HEAD 与本地副本同为 `3f386b7`，目标目录远端不存在（新建非覆盖）
- [x] 3.3 推完用 Contents / Trees API 列远端根目录并逐文件核对，**不查本地**
      —— 按新 commit sha 查 tree，5 个文件 tree_sha 与 content 双比对全部 PASS
- [x] 3.4 推送目标与步骤 3 全部结果如实回报，不把「声称完成」当完成
      —— 四件套 commit `6e188f227274d9e989e3efac113047f2393d1488`；勾选回写 `0b1d00da5f9659839b88a68dac16ce04b454c0b6`

## 4. 实现（按 3.3 校验通过后开始）

> 使用者 2026-11-23 选定**方案 A**：三页布局与交互全部落地，
> 图标资源与许可证后置；`CategoryIconCatalog` 为空目录，三页全回退中性圆 + 首字。

### 4.1 图标数据与目录
- [ ] 4.1.1 `assets/default_categories.json` 每行补 `iconKey` / `iconTintHex`
      —— **方案 A 后置，未做**：图标资源未定，补空字段无收益且徒增 106 行改动风险；
      本批清单**一字未动**（零改数据），资源确认后一次补齐
- [x] 4.1.2 `SeedCatalog.Row` / `parse` / `insertStatements` 同步解析并写 `iconName`；
      新字段可选，旧 JSON 缺字段仍可解析，`colorHex` 仍固定 `#888888`
- [x] 4.1.3 `SeedData` fallback 同构核对
      —— 复核后 `CategorySeed` 本就有 `iconName` / `colorHex` 且 INSERT 已写入，
      `c()` 给空串；无需改代码，已在 `design.md` 如实登记
- [x] 4.1.4 新增 `ui/components/CategoryIconCatalog.kt`：
      编译期显式常量出口（方案 A 空目录）+ `CategoryGlyph` 全回退 + `CategoryIconGrid`
      5 列等宽网格（`Row + weight(1f)`、固定格高、均匀 gutter、长名 ellipsis、停用小角标）
- [ ] 4.1.5 **图标资源来源与许可证待使用者确认 / 提供**（开放项）
      —— 确认前不伪造 106 个矢量、不复制一羽视觉资产；
      确认后回填 `design.md`「依赖与许可」表
- [x] 4.1.6 新增 `CategoryManagementRulesTest` / `SeedCatalogOptionalIconTest`
      —— 网格分行与补空位、重名忽略大小写、三元组重建与不丢项、纯函数性、旧清单兼容

### 4.2 列表页
- [x] 4.2.1 蓝底 TopBar：返回 / 标题 / 右上「排序」+ ⋮；支出 / 收入 Tab 坐进标题栏下部
- [x] 4.2.2 顶部常驻说明灰字收进 ⋮ →「使用说明」；删除确认弹窗保留三条口径说明
- [x] 4.2.3 一级行改「彩色图标 + 名称 + 二级数角标 + 单 ⋮ 菜单」；
      `≡` / `›` 收进菜单，菜单七项全可达；列表页**移除就地拖拽**
- [x] 4.2.4 二级 `WrapRow` + `FilterChip` → 5 列等宽图标网格，窄屏由 `columns` 参数可降 4 列
- [x] 4.2.5 末位固定「添加子类」格；停用改小角标，MUST NOT 拼进名称
- [x] 4.2.6 底部胶囊「已归档（N）」/「停用项管理」+ 蓝色大 `+` FAB；
      归档恢复改由底部弹窗承载（能力不丢），列表不再单独平铺归档区
- [x] 4.2.7 `ListDivider()` 单一来源不变；展开态继续 `rememberSaveable`；笔数仍一条 `GROUP BY`

### 4.3 独立排序页
- [x] 4.3.1 `LedgerApp.kt` 挂 `category-sort/{type}` 路由（不改 4 Tab 格数）
- [x] 4.3.2 一级横排 5 列 + 选中项蓝底；选中一级的二级收进圆角白卡、灰阶参数已留
- [x] 4.3.3 底部提示「长按拖动一级，点按拖动二级」+ 右上 / 底部双「保存」；
      返回 / 放弃有未保存改动时弹「放弃修改」确认
- [x] 4.3.4 ViewModel 新增 `sortDraftRootIds` / `sortDraftChildIds` / `sortDirty` 原子状态
      （全部 `_state.update { }`），与已落库 `sortOrder` 完全分离
- [x] 4.3.5 排序页内二级临时排成一维列表，`draggableHandle` 挂项首小拖柄；
      一级 `longPressDraggableHandle` 挂格子内小手柄；
      `ReorderableItem` key 与父容器 `item(key = ...)` 一致；
      `rememberReorderableLazyGridState` 已在 v3.1.0 README 查证存在
- [x] 4.3.6 保存由纯 JVM `CategoryManagementRules.rebuildOrder` 按三元组一次性重建全量
      id 顺序并写回，不丢项、不混入另一收支类型或另一父级；
      既有 `commitRootOrder` / `commitChildOrder` / `moveCategory` 保留供列表页上移 / 下移兜底
      —— **与 design 原表述的偏差已如实登记并同步修订 delta spec**
- [x] 4.3.7 `CategoryManagementRulesTest` 覆盖放弃零写库的纯函数前提、
      保存三元组隔离、收入分类与归档项次序不变、draft 漏项补齐

### 4.4 分类选择页（借一羽添加子类页版式）
- [x] 4.4.1 `LedgerApp.kt` 挂 `category-picker/{parentId}/{type}/{mode}` 路由
- [x] 4.4.2 左侧分组导航 + 右侧 4 列等宽网格 + 顶部搜索 + 父级 / 收支类型预填 +
      标题「添加到 〈父级〉› 〈类型〉」
- [x] 4.4.3 「继续添加」：成功只清名称并保留父级 / 类型，逐条独立事务（`createCategory` 原路径）
- [x] 4.4.4 同版式用于「停用项管理」批量启用；未改成从预设库挑分类加进来
- [x] 4.4.5 同父级重名校验（`validatePickerName` → `pickerError` 就地提示不写库）；
      输入框 `placeholder` + `heightIn(min = 40.dp)`
- [x] 4.4.6 `CategoryManagementRulesTest` 覆盖重名忽略大小写与空白

### 4.5 文档与规格同步
- [x] 4.5.1 活文档 §2.6 / §7.3 适用范围收窄；新增 §7.37 记录外推被推翻与八条沉淀
- [x] 4.5.2 活文档 §8 第 55 批继承既有清单 + 新增 14 项真机待验；未验项未写成已通过
- [x] 4.5.3 `openspec/project.md` 补第 55 批记录；`defaultVersionName` → `0.2.36`
      （已核实未被 tag / release / artifact 占用）
- [x] 4.5.4 改完代码同步 spec：按实现修订 3 处矛盾文本（就地拖 / 松手落库 / 复用方法），
      按标题整块替换，不新增第二份 MODIFIED
- [x] 4.5.5 `app/build.gradle.kts` 版本号递增

## 5. 校验与交付

- [ ] 5.1 六个静态检查本地 + CI 全 PASS
      —— 本地已全 PASS（`check_docs_consistency` / `check_kotlin_strings` /
      `check_manifest_privacy` / `check_stat_rules` / `check_transaction_annotations` /
      `check_workflow_syntax`）；**CI 待推送后查 Actions，不以本地代替**
- [x] 5.2 `check_kotlin_strings.py` PASS；另跑去注释/字符串括号平衡、重复 import、
      `setValue` / `getValue` 导入自查，10 个文件全 OK
- [ ] 5.3 按最终 commit sha 查 tree，逐文件比对 blob sha，`远端校验: PASS`
- [ ] 5.4 归档校验：无重复 Requirement 标题、每条 ≥1 Scenario、含 RFC2119、MODIFIED 整块替换
- [ ] 5.5 给出真实 commit sha + run id；制品名带短 sha、不同名覆盖；`mapping.txt` 随对应 APK
- [ ] 5.6 无 `diag/` 残留、无 token 明文、Manifest 仍无 `INTERNET`
- [ ] 5.7 **明确写出仍未验证的真机项**，不用「CI 全绿」替代观感 / 性能 / 弹窗验证（§1）

## 6. 真机待验（使用者，刻意保留 `[ ]`）

- [ ] 6.1 5 列网格在 360dp / 6.73 寸的观感与 gutter 均匀度、窄屏降 4 列是否正常
- [ ] 6.2 7 字长名单行省略、点开弹层仍可看全名；停用小角标可辨且不撑破格子
- [ ] 6.3 蓝底 TopBar、底部胶囊 + 大 `+` FAB 不遮挡列表末行；滚动时入口固定
- [ ] 6.4 ⋮ 菜单七项操作逐项可达；改名 / 看流水 / 停用 / 上移 / 下移 / 删除不丢能力
- [ ] 6.5 排序页长按一级、点按二级的手感、实时让位补间与边缘滚动
- [ ] 6.6 放弃后顺序零变化；保存后一级只在支出一级间换位、二级只在父级内换位，
      重进页面顺序保持
- [ ] 6.7 连续添加不清父级 / 类型、重名提示、键盘与搜索共存
- [ ] 6.8 图标缺项回退中性圆 + 首字，无崩溃、无整页空白（方案 A 当前全部走此路径）
- [ ] 6.9 记一笔 / 明细 / 统计页分类标识仍是中性圆 + 首字（本批零回归）
- [ ] 6.10 横屏与旋转屏幕后展开态、draft 状态、网格列数
- [ ] 6.11 排序页一级网格限高 240dp 在 15 个一级下是否需要内滚
- [ ] 6.12 §8 既有全部未验项整体继承

## 7. 归档（使用者明确说「可以归档」才做）

- [ ] 7.1 把 3 条 MODIFIED 按标题整块替换回 `openspec/specs/`，4 条 ADDED 合并
- [ ] 7.2 change 文件夹移到 `openspec/changes/archive/<日期>-refine-category-management-pages/`
- [ ] 7.3 校验 specs 已反映新行为，无规格腐烂
