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

> 使用者 2026-11-23 选定**方案 A**（布局先行、图标后置），随后确认「按 Tabler 来」
> **完成收口**：Tabler Icons outline v3.34.0 / MIT，90 个本地 drawable 覆盖全部 106 个
> 预置分类；目录缺项才回退中性圆 + 首字。未结项仅剩真机观感验收与出包。

### 4.1 图标数据与目录
- [x] 4.1.1 `assets/default_categories.json` 106 行各补一行 `iconKey`
      （最小 diff，保留原 2 空格缩进）；`id/name/type/parentId/level/enabled/sortOrder`
      逐字段零改动。配色由编译期常量表给，清单**不补** `iconTintHex`（如实登记偏差）
- [x] 4.1.2 `SeedCatalog.Row` / `parse` / `insertStatements` 同步解析并写 `iconName`；
      新字段可选，旧 JSON 缺字段仍可解析，`colorHex` 仍固定 `#888888`
- [x] 4.1.3 `SeedData` fallback 同构核对
      —— 复核后 `CategorySeed` 本就有 `iconName` / `colorHex` 且 INSERT 已写入，
      `c()` 给空串；无需改代码，已在 `design.md` 如实登记
- [x] 4.1.4 `ui/components/CategoryIconCatalog.kt`：
      首批方案 A 为空目录 + 全回退，Tabler 落地后改为 90 分支编译期常量表 +
      「浅色圆底 + 单色线性图标」`CategoryGlyph`；`CategoryIconGrid` 5 列等宽网格
      （`Row + weight(1f)`、固定格高、均匀 gutter、长名 ellipsis、停用小角标）不变
- [x] 4.1.5 图标资源与许可证（已确认）
      —— Tabler Icons outline **v3.34.0**（pin tag，非 `@latest`）/ MIT /
      `Copyright (c) 2018-2026 Tabler`；`design.md`「依赖与许可」表与
      `assets/NOTICE`（MIT 全文）均已回填
- [x] 4.1.6 单测：`CategoryManagementRulesTest` / `SeedCatalogOptionalIconTest`
      —— 网格分行与补空位、重名忽略大小写、三元组重建与不丢项、纯函数性、旧清单兼容
- [x] 4.1.7 从 Tabler zipball 取 90 个 outline SVG，脚本转成
      `res/drawable/ic_cat_*.xml`（90 个 VectorDrawable，XML 解析自检全通过）；
      零新增 Gradle 依赖、零 AAR、零运行时网络
- [x] 4.1.8 `CategoryIconCatalog` 编译期常量表（90 个 `when` 分支）+ `CategoryGlyph`
      「浅色圆底 + 单色线性图标」；缺项回退中性圆 + 首字
- [x] 4.1.9 `engine/CategoryIconTints.kt`（11 种低饱和领域色、同组同色、
      排序页灰阶）与 `engine/CategoryIconKeys.kt`（106 个预置 id → iconKey）
- [x] 4.1.10 **覆盖安装兜底**：`iconName` 为空时按 id 回查预置表，
      v0.2.35 覆盖安装也能立刻显示图标；零写库 / 零 Entity 变更 / 零改表。
      4 个 `CategoryGlyph` 调用点全部补 `categoryId`
- [x] 4.1.11 新增 `CategoryIconTintsTest` / `CategoryIconKeysTest`
      —— 106 个 iconKey 全有配色、106 个预置 id 全覆盖、未知 key / 自定义 id 返 null

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
- [x] 4.3.5 排序页内二级临时排成一维列表，`draggableHandle` 挂项首小拖柄
（第 1 轮实现；**第 2 轮 4.6 撤回此项布局**，第 3 轮改 5 列网格 +
       第二套 `rememberReorderableLazyGridState`，不自实现手势）；
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

### 4.6 排序页按一羽图 3 做 1:1（第 2 轮 + 第 3 轮技术澄清）
- [x] 4.6.1 **两态互斥**：一级态只渲染 5 列 `LazyVerticalGrid` 可拖网格、
       二级区折叠不渲染；点一级进入二级态，同一时刻只有一个可拖容器
- [x] 4.6.2 一级态沿用 `rememberReorderableLazyGridState` + `ReorderableItem` 稳定 key；
       容器内无 span / header，`from.index` 直接对应 `draftOrder` 下标
- [x] 4.6.3 二级态：外层一级区改**非惰性** `Column + Row(weight(1f))` 手工 5 列
       （15 个一级 = 3 行，静态不可拖、选中项蓝底）；
       圆角白卡**插在选中一级所在行之后**、其余一级行之前，保留 1:1 视觉
- [x] 4.6.4 白卡内二级改**第二套** `LazyVerticalGrid(Fixed(5))` +
       `rememberReorderableLazyGridState`（库原生，MUST NOT 自写 `pointerInput`）；
       单父级最多 11 项 = 3 行，白卡限高、外层 Column 不滚动
- [x] 4.6.5 两套 grid state 各管各段 `draftOrder`、互不干涉；
       `onMove` MUST 在返回前同步更新列表（README FAQ，否则拖动项闪烁）；
       若容器内插入任何非拖 item，索引 MUST 按 README 减偏移
- [x] 4.6.6 上移 / 下移改为每格 `semantics.customActions` 的 TalkBack 兜底（只列可行方向）；`rebuildOrder` 三元组保存语义零改动；
       「保存 / 放弃」行为与第 1 轮一致
- [x] 4.6.7 灰阶降噪保留（一羽图 3 自身降饱和）；彩色只在列表页与选择器；
       两态切换 MUST NOT 丢失任一层的 `draftOrder`

### 4.7 共享只读分类选择组件 `CategorySelectSheet`（第 2 轮）
- [x] 4.7.1 抽只读组件：顶部搜索 + 左侧一级分组导航 + 右侧 5 列等宽彩色图标格；
       **无名称输入框、无「继续添加」/ 保存按钮、无写库路径**
- [x] 4.7.2 记一笔 `AllCategoriesDialog` 替换为该组件；选中立即回填
       `categoryId` / `rootCategoryId` 并关闭；快捷栏当前选中项兜底不变
- [x] 4.7.3 `FilterSheet` 的 `PickRow("分类")` 单选弹层替换为**同一组件**；
       回填 `rootId` / `categoryId` / `displayName`，保留「全部分类」与一级 / 二级层级区分
       （实现改为 `CategorySelectChoice.level` 显式字段，不再拼 `r:` / `c:` 字符串前缀）
- [x] 4.7.4 继承既有约束：停用项全可见可搜带角标、归档项不进、按收支类型过滤、
       搜索跨组平铺、二级 label 经 `CategoryDisplay` 拼「一级 / 二级」、点选无二次确认
- [x] 4.7.5 `CategoryPickerScreen` **零改动**：添加子类 / 父级预填 / 继续添加 /
       重名校验全部保留（使用者第 3 问「保留」）
- [x] 4.7.6 彩色图标只进选择器；记一笔快捷栏 / 明细列表行 / 账户详情 / 统计页
       仍中性圆 + 首字，`colorHex` 零改动

### 4.8 规格与文档（第 2 轮 + 第 3 轮核查）
- [x] 4.8.1 `category-stats` MODIFIED「分类图标不做字符串映射」正文放宽 + Scenario 改名
       「彩色图标只进分类选择组件」
- [x] 4.8.2 `category-stats` ADDED「独立分类排序页与临时顺序」就地改写为
       1:1 两态互斥布局 + 两套 grid state，并新增「拖一级时二级折叠」
       与「两级拖拽互不干涉」两个 Scenario
- [x] 4.8.3 `category-stats` 新增 ADDED「记一笔与筛选复用分类网格选择组件」（≥3 Scenario）
- [x] 4.8.4 `reporting-ui` 新增 MODIFIED「分类全量选择器采用分组 chips 且不用下拉框」，
       标题沿用基线原名作整块替换锚点，正文改分组导航 + 等宽网格、保留全部 Scenario
- [x] 4.8.5 `proposal.md` / `design.md` / 活文档 §2.6 §7.3 §8 同步；
       版本号递增（下一版本，v0.2.36 已发布不回改）

## 5. 校验与交付

- [x] 5.1 六个静态检查本地 + CI 全 PASS
      —— 本地已全 PASS（`check_docs_consistency` / `check_kotlin_strings` /
      `check_manifest_privacy` / `check_stat_rules` / `check_transaction_annotations` /
      `check_workflow_syntax`）；**CI 待推送后查 Actions，不以本地代替**
- [x] 5.2 `check_kotlin_strings.py` PASS；另跑去注释/字符串括号平衡、重复 import、
      `setValue` / `getValue` 导入自查，10 个文件全 OK
- [x] 5.3 按最终 commit sha 查 tree，逐文件比对 blob sha，`远端校验: PASS`
- [x] 5.4 归档校验：无重复 Requirement 标题、每条 ≥1 Scenario、含 RFC2119、MODIFIED 整块替换
- [ ] 5.5 给出真实 commit sha + run id；制品名带短 sha、不同名覆盖；`mapping.txt` 随对应 APK
       —— v0.2.39 已出 Release 附件与 tag，制品名带短 sha `bd2e3d7c`；
       **Actions artifacts 为 3 个**（release / mapping / metrics），
       全步骤 `outcome` 无 failure、无 `continue-on-error` 染绿，`mapping.txt` 确实随制品留存，
       以 Release assets + `asset.digest` 为准。
       **一处操作偏差**：dispatch 因工具层 502 被重放两次，第二个 run `45316726495`
       排队 pending 时主动 cancel（最终 `completed cancelled`），未产生第二次发布；
       详见活文档 §7.40。5.4 归档校验未做，本项仍保持 `[ ]`，
       归档需使用者明确说「可以归档」
       —— v0.2.38 曾出 Release 附件与 tag（短 sha `ae8f1b3a`），
       Actions artifacts 同为 3 个、mapping 随制品留存
       —— v0.2.37 曾出 Release 附件与 tag 但 **Actions 制品列表为 0**：
       「制品上传失败告警」步骤实际执行，可反证三个 continue-on-error 上传步骤
       outcome=failure 被染绿（§7.33/§7.35），`mapping.txt` 未随制品留存
       —— v0.2.36 已出 Release 附件与 tag，**但 Actions 制品列表未查到 v0.2.36 条目**
       （v0.2.34/35 同样不在，历史清理口径遗留，非本批引入）
- [x] 5.6 无 `diag/` 残留、无 token 明文、Manifest 仍无 `INTERNET`
       （诊断脚本已删、comment 已删、`build.yml` 按远端原版 byte-for-byte 恢复）
- [x] 5.7 **明确写出仍未验证的真机项**，不用「CI 全绿」替代观感 / 性能 / 弹窗验证（§1）

## 6. 真机待验（使用者，刻意保留 `[ ]`）

- [ ] 6.1 5 列网格在 360dp / 6.73 寸的观感与 gutter 均匀度、窄屏降 4 列是否正常
- [ ] 6.2 7 字长名单行省略、点开弹层仍可看全名；停用小角标可辨且不撑破格子
- [ ] 6.3 蓝底 TopBar、底部胶囊 + 大 `+` FAB 不遮挡列表末行；滚动时入口固定
- [ ] 6.4 ⋮ 菜单七项操作逐项可达；改名 / 看流水 / 停用 / 上移 / 下移 / 删除不丢能力
- [ ] 6.5 排序页长按一级、点按二级的手感、实时让位补间与边缘滚动
- [ ] 6.6 放弃后顺序零变化；保存后一级只在支出一级间换位、二级只在父级内换位，
      重进页面顺序保持
- [ ] 6.7 连续添加不清父级 / 类型、重名提示、键盘与搜索共存
- [ ] 6.8 图标缺项回退中性圆 + 首字，无崩溃、无整页空白（手工新建分类走此路径）
- [ ] 6.9 记一笔快捷栏 / 明细列表行 / 账户详情 / 统计页分类标识仍是中性圆 + 首字
       （第 2 轮口径：彩色只进四个页面/组件，零回归）
- [ ] 6.10 横屏与旋转屏幕后展开态、draft 状态、网格列数
- [ ] 6.11 排序页一级网格限高 240dp 在 15 个一级下是否需要内滚
- [ ] 6.12 §8 既有全部未验项整体继承
- [ ] 6.13 **图标观感**：90 个 Tabler 图标在 40dp 圆底里的辨识度、
      11 种领域色是否仍显杂乱、浅色圆底与文字对比度、
      排序页灰阶降噪是否可辨、收入统一绿是否与「支出红/收入绿」语义冲突
- [ ] 6.14 **覆盖安装**：从 v0.2.35 覆盖安装后（库内 iconName 为空串）
      三页是否都能按 id 显示图标；全新安装是否同样正常

- [ ] 6.15 **拖一级时二级折叠**：一级态长按拖动全程二级区不渲染，
       无 span item、无索引偏移、让位与边缘滚动是否实时
- [ ] 6.16 点一级进入二级态后，白卡确实「插在网格中间」而非上下兄弟容器；
       一级区冻结不可拖，白卡内二级 5 列网格独立拖，两套 state 互不干涉
- [ ] 6.17 两态来回切换时两层 `draftOrder` 都不丢；15 个一级 + 白卡
       在 360dp / 6.73 寸是否需内滚、选中行视觉是否连贯
- [ ] 6.18 库 `onMove` 同步更新后无拖动项闪烁；白卡限高与外层非惰性 Column
       的滚动边界无冲突、二级网格边缘自动滚动仍生效
- [ ] 6.19 记一笔「分类 ›」与筛选「分类」是否确为同一组件、无输入框 / 无保存按钮
- [ ] 6.20 选择器彩色图标与记一笔快捷栏中性圆同屏并存是否割裂；
       快捷栏 / 明细列表行 / 账户详情 / 统计页零回归
- [ ] 6.21 从网格选停用项后记一笔快捷栏兜底、筛选回填后再次打开值是否保留、
       归档项不出现、搜索跨组平铺无同名混淆
- [ ] 6.22 原分类选择页添加子类 / 继续添加 / 重名校验零回归
- [ ] 6.23 v0.2.36 → 新版覆盖安装、全新安装两条路径；§8 既有清单整体继承

## 7. 归档（使用者明确说「可以归档」才做）

- [ ] 7.1 把 **4 条 MODIFIED** 按标题整块替换回 `openspec/specs/`，**5 条 ADDED** 合并；
       并把 `reporting-ui`「分类全量选择器采用分组 chips 且不用下拉框」标题
       更名为「…采用分组导航与等宽网格」（**延后**：进行中 change 仍以旧标题作
       ADDED 锚点，更名会加剧规格腐烂；见归档报告 R4，待其收口后统一处理）
- [ ] 7.2 change 文件夹移到 `openspec/changes/archive/<日期>-refine-category-management-pages/`
- [ ] 7.3 校验 specs 已反映新行为，无规格腐烂

### 4.9 第 56 批编码落地

- [x] 4.9.1 `CategorySortScreen.kt` 完整重写为两态互斥：一级态单个 `LazyVerticalGrid(Fixed(5))`、
       二级区折叠不渲染；二级态外层非惰性 `Column + Row(weight)` 静态一级区冻结、
       白卡插在选中行之后、卡内第二套 `LazyVerticalGrid(Fixed(5))` 拖二级
- [x] 4.9.2 两套 `rememberReorderableLazyGridState` 各管各段 `draftOrder`；
       `onMove` 由 ViewModel 同步 `_state.update`，无 `GridItemSpan`、无自实现手势
- [x] 4.9.3 上移 / 下移改为每格 `semantics.customActions` 的 TalkBack 兜底
       （只列出可能的移动方向），不额外堆按钮
- [x] 4.9.4 新增 `ui/components/CategorySelectSheet.kt`：只读 `CategorySelectDialog`
       （搜索 + 左分组导航 + 右 5 列彩色图标格），无输入框 / 无「继续添加」/ 无写库路径
- [x] 4.9.5 记一笔 `AllCategoriesDialog` 与筛选 `PickRow("分类")` 接入同一组件；
       `CategoryPickerScreen` 零改动，`colorHex` 零改动
- [x] 4.9.6 `defaultVersionName` → `0.2.37`；活文档 §2.6 / §7.3 / §7.38 / §8
       与 `openspec/project.md` 同步

### 4.10 第 4 轮交互优化（Q1A / Q2A / Q3A，第 57 批）

- [x] 4.10.1 排序页一级 / 二级**整格**挂拖动手柄，删掉所有「≡」小手柄；
       一级短按展开与长按拖动共享 `MutableInteractionSource` 共存（v3.1.0 README「Use with Card」写法）
- [x] 4.10.2 二级态外层末行不足 5 列时补 `weight(1f)` 空占位，格宽恒 1/5；
       白卡内维持 `GridCells.Fixed(5)` 不改自适应列
- [x] 4.10.3 Q1A：二级态静态一级格 `detectTapGestures(onLongPress)` 长按即自动收起二级区，
       删「收起，排一级」按钮；返回键与再点已选中一级格仍可收起
- [x] 4.10.4 Q2A：删白卡内小字、顶部「一级区已冻结…」、一级态两行引导、
       底部「长按拖动一级，点按拖动二级」，只留分组标题与「保存 / 放弃」；
       白卡底色 `0xFFF7F8FC` → `0xFFEAF0FF`，二级格仍白底圆角
- [x] 4.10.5 Q3A：`CategorySelectChoice` 拆 `tileName`（裸名）与
       `displayLabel`（`CategoryDisplay` 拼父级）；网格与搜索结果显示裸名，
       筛选回填 `displayName` 仍拼父级；`CategoryPickerScreen` / `CategoryDisplay` 零改动
- [x] 4.10.6 spec 两份 / design / proposal / 活文档 §2.6 §7.3 §7.39 §8 / project.md 同步；
       `defaultVersionName` → `0.2.38`

### 6.24–6.28 第 4 轮新增真机待验

- [ ] 6.24 一级格整格长按拖动、短按展开二级两种手势共存且互不误触（共享 `MutableInteractionSource` 的实际手感）
- [ ] 6.25 二级态长按任意一级格立即收起二级并回到一级态；收起后两层 `draftOrder` 不丢；
       再长按一次可开始一级拖动（Q1A 已知代价：同一次手势无法跨容器接续）
- [ ] 6.26 一级数非 5 倍数时末行空占位让列线对齐；白卡内二级末排格宽与前排一致，不被拉宽
- [ ] 6.27 删光文本提示后首次进入是否仍能猜到可拖（可发现性）；浅蓝灰白卡 + 白格
       是否足以区分二级区，与灰阶图标对比度是否够
- [ ] 6.28 选择器网格与搜索结果二级均为裸名、不再被省略成「餐饮 / …」；
       筛选 chip / 明细筛选 / 统计占比图例仍显示「一级 / 二级」；跨组搜索同名项归属缺失可接受

### 4.11 第 5 轮选择器密度优化（1A / 2B，第 58 批）

- [x] 4.11.1 `CategorySelectSheet.kt` 右侧 `columns = 5 → 4`（1A）
- [x] 4.11.2 2B 收窄左区：Dialog 外 padding 12→8、Surface 内 padding 12→8、
       导航 `width(96.dp) → 80.dp`、`Spacer(width(8.dp)) → 4.dp`（共 5 处数值）
- [x] 4.11.3 **范围锁定核查**：列表页 `columns = 5`、排序页两套 `Fixed(5)`、
       原「分类选择」页 `columns = 4`、`CategoryIconGrid` / `CategoryTileCell`
       格高与行距一字未动；无 compact 参数、无共用组件改动
- [x] 4.11.4 spec 两份 / design / proposal / 活文档 §7.40 §8 / project.md 同步；
       `defaultVersionName` → `0.2.39`

### 6.29 第 5 轮新增真机待验

- [ ] 6.29 360dp 屏下选择器 4 列、4 字二级名完整可见不再省略成 2 字；
       7 字长名仍单行省略、格子不撑高；导航 80dp 后左区一级名（如「学习娱乐」）
       不被截断；收窄后左右分区的疏离感是否改善、网格与导航是否仍对齐；
       分类管理列表页 / 排序页仍为 5 列且观感与 v0.2.38 一致（零回归）

---

## 归档执行记录（2026-11-24）

- 使用者明确「可以归档」，本 change 于本日收口。
- delta 已按 change 顺序折叠进 `openspec/specs/`，共 9 条；
  目录整体移入 `archive/2026-11-24-refine-category-management-pages/`。
- 归档校验（无重复 Requirement 标题 / 每条 ≥1 Scenario / 含 RFC2119 /
  MODIFIED 整块替换 / specs 反映现行行为）已执行，结果见 `归档报告.md`。
- 此前各批留 `[ ]` 的「归档校验」项统一勾核（1 处）。
- 归档时发现并修正的规格腐烂 R1–R5，详见归档报告「合并修订清单」。
