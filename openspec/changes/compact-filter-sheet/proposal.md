# compact-filter-sheet · 提案

## 为什么

使用者在 v0.1.7 装机体验后反馈：筛选弹窗「整体留白挺多、涉及不紧凑、需要滑动才能看到所有内容」，
并列出 7 条具体问题。这是 `docs/页面优化经验与避坑指南.md` 成为必读输入之后，
**第一次把该指南应用到「记一笔」以外的页面**——验证 §6.2 清单是否真的可复用，
而不只是写给记一笔看。

## 分级判定

UI 重构 + 只读查询扩展 + 4 份规格 MODIFIED → **必须建 change**。
`polish-device-experience` 已于 `a4bdac93c04b` 归档、进行中 change 为无，
故**新建** change-id `compact-filter-sheet`（动词开头），不并入历史 change。

## 五项对齐结论

### 1. 要解决什么问题

把筛选从「近两屏、靠滑」压成「一屏看全、点两下选完」，并补上核对时最需要的
**交易对方筛选**。同时修掉两处静默 bug（金额区间不回填、选中态风格分裂）。
目标不是把空白塞满，而是让 9 个筛选维度落在统一网格里，高度**不随账户 / 分类数量增长**。

### 2. 改什么 / 不改什么

**改**
- `FilterSheet` 整体重排：`OptionRow(chunked(3))` → 统一行网格（整行可点 + 单选弹层带搜索；
  类型 / 币种用 `FilterChip` 单行）、删 `● ` 前缀、双层 padding 收一层、删副标题
- 5 个输入框：`label → placeholder` + `bodyMedium` + `heightIn(min = 40.dp)`；
  日期改 `DatePickerDialog`（UTC 锚点）；金额区间从 `initial` 回填
- 新增交易对方筛选：`RecordFilter` / `FilterValues` / `pagedFiltered` 一条参数化条件 /
  `RecordPagingSource` / `applyFilter` / `clearFilter` / `activeCount()` / 全量 DISTINCT 联想
- 版本号 `0.1.7 → 0.1.8`
- `openspec/specs/` 4 份 MODIFIED（条件清单补「交易对方」）+ 2 条 ADDED
- 活文档 `docs/页面优化经验与避坑指南.md` 追加本批记录并更新 §8 继承状态

**不改**
- 表结构 / 备份格式 / `schemaVersion` / `AppDatabase version`（仍 1）→ 覆盖安装即可
- 4 Tab 导航、明细分页机制、`PagingData` 独立 `Flow` + `cachedIn` + 稳定 `itemKey`
- 统计口径唯一出口 `StatRules.SQL_STAT_RELEVANT`、`dailyTotals`、既有 13 条 `...ForCurrency` 聚合 SQL
- 金额铁律（`Long` 分 / `Cents.fromYuan` / `BigDecimal` 仅展示层）
- 筛选状态提升到 Activity 作用域（第 5 批成果，跨 Tab 保持）
- Manifest 权限（仍无 `INTERNET`）、`release.yml`、`build.yml`、历史快照、`my-app-workflow`
- Dialog 载体：筛选是**操作型浮层**，按指南 §2 不升成独立页（多一层返回）

### 3. 关键约束

- 零表结构变更 → **覆盖安装即可，不用卸载**；零新增依赖、零新增权限、零新增 Gradle 项
- `pagedFiltered` MUST 仍是**一条带可选参数的 `@Query`**，全部值参数绑定，
  MUST NOT 用 `@RawQuery`、字符串拼接或另开一条查询
- `activeCount()` MUST 含 `counterpartyQuery`；`applyFilter` / `clearFilter` 不得漏字段
- 筛选改动 MUST NOT 让列表弹回顶部；`PagingData` 仍独立 `Flow` + `cachedIn(viewModelScope)`
- 可点区 ≥ 40dp；placeholder 与输入文字同 `bodyMedium`；**只用 `heightIn(min)`，不用固定 `height`**
- `● ` 文字前缀 MUST 与记一笔一起统一成 `FilterChip` 填充底色
- 日期读写用 UTC 锚点与设备时区解耦；`LedgerClock.zoneId` 仍只负责 `timestamp → localDate`
- 选项弹层 MUST 带搜索，10+ 账户、20 个二级分类下长名称 MUST 完整可见、面板高度恒定
- 4 份 MODIFIED 按标题整块替换，**不追加 ADDED 制造两份条件清单**；
  「明细筛选 MUST NOT 混入按差额反查 / 对账锚点 / 疑似标记」的功能归属边界不变
- 动手前先把根因与三级回退顺序写进 `design.md`（已写）；六个静态检查全过；
  推送用 Git Trees API 单次 commit；Python 拼 Kotlin 后必跑 `check_kotlin_strings.py`
- 沙盒无 SDK / 模拟器，编译与单测由 Actions 验证

### 4. 验收标准

1. **GIVEN** 打开筛选面板 **WHEN** 在 6.73 寸屏查看 **THEN** 9 个维度一屏可见、无需上滑
2. **GIVEN** 10+ 账户 / 20 个二级分类 **THEN** 面板高度不随选项数增长、长名称不被压窄
3. **GIVEN** 类型与币种 **THEN** 各占 1 行、选中态为底色、无 `● ` 前缀
4. **GIVEN** 金额 / 时间 / 备注 / 交易对方输入框 **WHEN** 聚焦 **THEN** 边框不撑高、
   placeholder 不裁切、字号与网格一致
5. **GIVEN** 时间行 **WHEN** 点击 **THEN** 弹 `DatePickerDialog` 选日期、显示 `YYYY-MM-DD`，
   往返不串一天
6. **GIVEN** 已设金额区间后关闭面板再打开 **THEN** 区间值仍回填、点「应用」不静默清空
7. **GIVEN** 输入交易对方一两个字 **THEN** 联想命中历史对方，且与其他条件叠加生效
8. **GIVEN** 设好筛选后切到资产页再切回 **THEN** 条件与滚动位置仍在
9. **GIVEN** 归档后校验 specs **THEN** 4 份 MODIFIED 已按标题替换、无两份条件清单
10. **GIVEN** 推送完成 **WHEN** 用 API 按最终 commit sha 校验 **THEN** blob sha 全一致、
    无 `diag/` 残留、无 token 明文；CI 全绿、版本 0.1.8、制品名带短 sha

### 5. 被否决的方案

| 方案 | 否决理由 |
|---|---|
| 不建 change 直接改 | 决策与 4 份 MODIFIED 失溯源；且进行中 change 为无，本就是新批次 |
| 保留 `chunked(3)` 只调 padding | 根因不动，横向压窄长名称的问题仍在 |
| chips 单行横滚 +「更多」 | 使用者刚在模板那批否掉横滚 |
| 两行网格 +「展开更多」 | 高度仍随选项数增长 |
| 只压缩输入框不接日历 | 手输 `YYYYMMDD` 易错，且与记一笔第 1 批能力不一致 |
| 交易对方另开一条 `@Query` / `@RawQuery` | 违反「一条参数化 SQL 覆盖全部组合」，注入面与组合爆炸 |
| 只加 ADDED 不改 4 份条件清单 | 指南 §5：specs 会出现两份互相矛盾的筛选条件清单 |
| 把筛选升成独立页 | 操作型浮层，多一层返回；分类 / 回收站升页是因为它们是**管理型**（要搜索拖拽） |
| 为紧凑把可点区压到 40dp 以下 | 指南 §7 已证伪，第 5 / 8 / 9 / 10 批连续四次回摆 |
| 固定 `height(48.dp)` / `contentPadding` / `BasicTextField` | 第 8 批已证伪裁切与重载陷阱 |
| 顺手改统计页、对账视图或表结构 | 范围蔓延；对账锚点与金额反查归属资产页，边界不变 |

完整取舍与回退顺序见 `design.md`。

## 影响分析

| 模块 | 影响 |
|---|---|
| `reconciliation` | 主：3 份 MODIFIED（条件清单、功能归属边界） |
| `ledger-transactions` | 1 份 MODIFIED（单条参数化 SQL 的条件集） |
| `reporting-ui` | ADDED：一屏紧凑布局、行高量级、跨 Tab 状态保持 |
| `category-stats` | 只读：二级分类选项来源不变 |
| `money-precision` | 只读：金额区间仍走 `Cents.fromYuan` / `toYuanString` |
| `backup-restore` / `privacy-security` | 只读：备份格式与权限不变 |
| `build-and-ci` | 版本 0.1.8、静态检查扩项核对 |

**不与三条铁律或「已确认不做」清单冲突**。CSV 明文导出、升级迁移暂缓等既有决策不受影响。

## 风险

- `FilterSheet` 是阶段 2 起 10 批打磨都**没碰过的老文件**，整页重排 + 4 份 MODIFIED，
  首次 CI 大概率要一轮 import / `@OptIn` / 参数顺序修复；按 `design.md` §6 每次只改一处单独推送
- **「CI 全绿」不等于「6.73 寸上一屏真的放得下、长账户名不挤」**，这两项必须由使用者装机确认
- 累积未验项（BiometricPrompt、SAF、Excel 打开 CSV、5 万条切月耗时等）继续按使用者决定推迟，
  由指南 §8 继承
- `debug.keystore` 是随仓库的公开密钥，别拿它签的包对外分发
