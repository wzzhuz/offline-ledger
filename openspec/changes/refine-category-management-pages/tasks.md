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
- [x] 2.2 `design.md` —— 含「考虑过的其他方案」表、取舍、风险、依赖与许可
- [x] 2.3 `tasks.md` —— 本文件
- [x] 2.4 `specs/category-stats/spec.md` —— 2 MODIFIED + 4 ADDED
- [x] 2.5 `specs/reporting-ui/spec.md` —— 1 MODIFIED
- [ ] 2.6 **停下来等使用者确认四件套**（会改、会砍、会补充，很正常）

## 3. 推送到 GitHub（开发前独立一步）

- [ ] 3.1 回显「目标仓库 `wzzhuz/offline-ledger` + 本地源目录」，**等使用者确认**
- [ ] 3.2 推送 change 四件套；推送前比较本地与远端 sha，确认本地未落后（踩坑清单 4.5）
- [ ] 3.3 推完用 Contents / Trees API 列远端根目录并逐文件核对，**不查本地**
- [ ] 3.4 推送目标与步骤 3 全部结果如实回报，不把「声称完成」当完成

## 4. 实现（按 3.3 校验通过后开始）

### 4.1 图标数据与目录
- [ ] 4.1.1 `assets/default_categories.json` 每行补 `iconKey` / `iconTintHex`，
      **不动** `id / name / type / parentId / level / enabled / sortOrder`
- [ ] 4.1.2 `SeedCatalog.Row` / `parse` / `insertStatements` 同步解析并写 `iconName`；
      新字段可选，旧 JSON 缺字段 MUST 仍可解析
- [ ] 4.1.3 `SeedData` fallback 同构补齐，与 `SeedCatalog` 输出一致
- [ ] 4.1.4 新增 `ui/components/CategoryIconCatalog.kt`：编译期显式常量 +
      `else` 回退 `CategoryIcon` 中性圆 + 首字，MUST NOT 运行时反射 / 查表
- [ ] 4.1.5 **图标资源来源与许可证待使用者确认 / 提供**（开放项，确认前不伪造 106 个矢量）；
      确认后把来源、许可证、传递依赖审计回填 `design.md`「依赖与许可」节
- [ ] 4.1.6 新增纯 JVM 单测：缺图标兜底、清单解析兼容旧字段、`colorHex` 不被改写

### 4.2 列表页
- [ ] 4.2.1 蓝底 TopBar：返回 / 标题 / 右上「排序」+ ⋮；支出 / 收入 Tab 坐进标题栏下部
- [ ] 4.2.2 顶部常驻说明灰字收进 ⋮ →「使用说明」；删除确认弹窗保留三条口径说明
- [ ] 4.2.3 一级行改「彩色图标 + 名称 + 二级数角标 + 单 ⋮ 菜单」；
      `≡` / `›` 收进菜单，菜单七项（改名 / 看流水 / 停用 / 上移 / 下移 / 删除 / 使用说明）全可达
- [ ] 4.2.4 二级 `WrapRow` + `FilterChip` → 5 列等宽图标网格（`Row + weight(1f)`、
      固定格高、均匀 gutter、文字单行 ellipsis），窄屏降 4 列
- [ ] 4.2.5 末位固定「添加子类」格；停用改小角标，MUST NOT 拼进名称
- [ ] 4.2.6 底部胶囊：「已归档（N）」/「停用项管理」+ 蓝色大 `+` FAB；
      TopBar 新增入口仍固定可见（reporting-ui 约束）
- [ ] 4.2.7 `ListDivider()` 单一来源不变；展开态继续 `rememberSaveable`；笔数仍一条 `GROUP BY`

### 4.3 独立排序页
- [ ] 4.3.1 `LedgerApp.kt` 挂 `category-sort` 路由（不改 4 Tab 格数）
- [ ] 4.3.2 一级横排 5 列 + 选中项蓝底角标；选中一级的二级收进圆角白卡、灰阶单色
- [ ] 4.3.3 底部提示「长按拖动一级，点按拖动二级」+「保存」；返回 / 放弃有未保存改动时明示
- [ ] 4.3.4 ViewModel 新增 `draftOrder` 原子状态（沿用 `_state.update { }`），
      与已落库 `sortOrder` 完全分离；保存才一次性写回
- [ ] 4.3.5 排序页内二级临时排成一维列表，`draggableHandle` 挂项首小拖柄；
      一级 `longPressDraggableHandle` 挂格子；`ReorderableItem` key 与 `items(key)` 一致
- [ ] 4.3.6 保存复用既有 `commitRootOrder` / `commitChildOrder` / `moveCategory`，
      三元组作用域不变、全量写回不丢项
- [ ] 4.3.7 新增纯 JVM 单测：放弃零写库、保存三元组隔离、收入分类不动

### 4.4 分类选择页（借一羽添加子类页版式）
- [ ] 4.4.1 `LedgerApp.kt` 挂 `category-picker` 路由（或页内视图，实施时定）
- [ ] 4.4.2 左侧分组导航 + 右侧等宽网格 + 顶部搜索 + 父级/收支类型预填 +
      标题写明「添加到 餐饮 › 支出」
- [ ] 4.4.3 「继续添加」：保存后只清名称、保留父级与类型与焦点，逐条独立事务
- [ ] 4.4.4 同版式用于「停用项管理」批量启用；MUST NOT 改成从预设库挑分类加进来
- [ ] 4.4.5 补同父级重名校验，失败就地提示不写库；输入框仍 `placeholder` + `heightIn(min = 40.dp)`
- [ ] 4.4.6 新增纯 JVM 单测：重名拒绝、连续添加不丢父级

### 4.5 文档与规格同步
- [ ] 4.5.1 修订 `docs/页面优化经验与避坑指南.md` §2.6 / §7.3：
      明确「中性单色圆 / 彩色图标目录不进清单」适用范围收窄为记一笔 / 明细 / 统计页；
      补记本批撤回的外推（组织方式 ≠ 卡片堆叠）
- [ ] 4.5.2 §8 继承既有真机未验清单 + 追加本批新增项；未验项 MUST NOT 写成已通过
- [ ] 4.5.3 `openspec/project.md` 补第 55 批记录（版本号待定，先核实未被 tag / artifact 占用）
- [ ] 4.5.4 改完代码**同步 spec**：3 条 delta 与实现逐条对读，不新增矛盾文本
- [ ] 4.5.5 `app/build.gradle.kts` 版本号递增（若出包）

## 5. 校验与交付

- [ ] 5.1 六个静态检查本地 + CI 全 PASS
- [ ] 5.2 `check_kotlin_strings.py`：块级精确替换后无跨行字符串字面量
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
- [ ] 6.8 图标缺项回退中性圆 + 首字，无崩溃、无整页空白
- [ ] 6.9 记一笔 / 明细 / 统计页分类标识仍是中性圆 + 首字（本批零回归）
- [ ] 6.10 横屏与旋转屏幕后展开态、draft 状态、网格列数
- [ ] 6.11 §8 既有全部未验项整体继承

## 7. 归档（使用者明确说「可以归档」才做）

- [ ] 7.1 把 3 条 MODIFIED 按标题整块替换回 `openspec/specs/`，4 条 ADDED 合并
- [ ] 7.2 change 文件夹移到 `openspec/changes/archive/<日期>-refine-category-management-pages/`
- [ ] 7.3 校验 specs 已反映新行为，无规格腐烂
