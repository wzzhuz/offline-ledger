# compact-filter-sheet · 设计决策

> 动码前先写本文件（指南 §6.1）。这是 `docs/页面优化经验与避坑指南.md` 挂进
> `AGENTS.md` 必读位置之后，**第一次改非「记一笔」页面**：把 §6.2 的行高量级 /
> 触摸目标 / 长名称 / 状态作用域逐条兑现到筛选面板，而不是重新试错。

## 0. 当前基线（不凭记忆，开工前查远端）

- 远端 HEAD `a4bdac93c04b`，版本 **v0.1.7**，CI `43843579027` success
- `polish-device-experience` 上一轮已归档，**进行中 change 为无** → 本轮必须新建 change，不能并入
- 使用者真机反馈：记一笔「基本满意」；筛选面板「留白多、要滑动、7 条具体问题」
- 本批零表结构变更 → **覆盖安装即可，不用卸载**；`AppDatabase` 仍 `version = 1`

## 1. 根因（1–4 条是同一个函数）

`FilterSheet.kt` 的 `OptionRow`：

```kotlin
options.chunked(3).forEach { group ->
    Column(modifier = Modifier.weight(1f)) {
        group.forEach { (label, value) -> TextButton { Text(...) } }
    }
}
```

它把选项按「3 个一列的**纵向列**」排，再用 `weight(1f)` 平分宽度。后果：

| 使用者观察 | 实际机制 |
|---|---|
| 类型加标题占 4 行 | 4 项 → `chunked(3)` = `[3,1]` → 2 列 × 恒 3 行 TextButton + `SectionTitle(padding(top=10dp))` |
| 币种占 3 行 | 3 项 → 1 组 → 单列纵排 3 行，本来一行就够 |
| 账户 / 一级 / 二级各 3 行 | 同上恒 3 行 |
| 「账户更多会无限往下延伸」 | **纠正**：不会往下，会**横向无限压窄**。列数 = `ceil(选项数/3)`，每列 `weight(1f)`。11 个账户 → 4 列 × 78dp；20 个二级 → 7 列 × 44dp，`XX银行0101` 会被挤扁或截断，比往下延伸更糟 |

叠加 `Dialog → Surface(padding(16.dp)) → Column(padding(16.dp))` **双层外边距**、
5–7 个 `SectionTitle` 各 10dp、5 个默认 56dp `OutlinedTextField`、底部 3 个 `TextButton`，
粗算内容高约 **1200dp**，6.73 寸屏要滑近两屏。「留白多 + 要滑动」由此而来。

### 1.1 顺带查出的两处「改一处漏一处」（指南 §3 T5 同型）

- 选中态仍是 `● $label` 文字前缀 + 绿色文字；第 8 批记一笔已换成 `FilterChip` 底色，这里没同步
- `minText` / `maxText` 用 `remember { mutableStateOf("") }`，**不从 `initial` 回填**；
  `fromText` / `toText` / `noteText` 都回填了。→ 重新打开面板时金额框是空的，
  但只要点「应用」就把已有金额条件**悄悄清成 null**。真 bug，本批修

## 2. 5 / 7 条：直接复用已证伪结论，不重新试错

两处都是 `OutlinedTextField + label = { Text(...) }`：金额区间 `label("最小"/"最大")`、
时间 `label("起始 YYYYMMDD"/"结束 YYYYMMDD")`。聚焦后浮动 label 浮到边框线上把框撑高，
正是第 7 批使用者说的「边框环绕文本提示冗余」。

时间还有一层：**根本没接日历，是手输 YYYYMMDD 字符串**，而记一笔第 1 批早已接
`DatePickerDialog`。所以「超大」之外还有「手输易错」。

指南 §3 T1/T4、§7 已写死，本批照抄：

- `label → placeholder`，显式 `style = bodyMedium`（placeholder 默认 bodyLarge，与输入文字不同字号）
- `textStyle = bodyMedium`、`singleLine = true`
- **`Modifier.heightIn(min = 40.dp)`**，只降官方 56dp 下限、不设上限
- **MUST NOT** 用固定 `height`（第 8 批裁切已证伪）
- **MUST NOT** 给 `String` 版加 `contentPadding` 命名参数（第 8 批重载解析陷阱已证伪）
- **MUST NOT** 上 `BasicTextField + DecorationBox`（要自接管边框 / 焦点 / 清空）

直接复用 `RecordEditorDialog.kt` 第 185–215 / 285–293 / 490–498 行的已验证写法，保持两页一致。

## 3. 布局方案（推荐 A，与「账户 ›」「模板 ›」同构）

每个维度压成**一行**：固定 52dp 标签列 + 值 + `›`，整行 `clickable` + `heightIn(min = 40.dp)`。

- **类型 / 币种**（≤4 项）→ `FilterChip` 单行铺开，选中态用填充底色，删 `● ` 前缀
- **账户 / 一级分类 / 二级分类**（可能 10+ / 20 项）→ 整行「标签 + 已选名称 + ›」，
  点开全量**单选弹层（带搜索）**。面板高度**不随选项数增长**，也不会横向压窄长名称
- **选项数 > 4 时币种也降级为整行弹层**，避免多币种下 chips 溢出（不引入横滚，
  使用者刚在模板那批否掉横滚）
- 细分割线用 1dp `Box`（项目已有写法），不用 `HorizontalDivider` 以免多一个 API 依赖
- 双层 16dp padding 收成单层 12dp；`SectionTitle` 间距归整；删掉顶部那行
  「时间 / 账户 / 类型 / …」副标题（维度本身自解释）
- 外层 `Column` 保留 `verticalScroll` 作为**小屏兜底**，但目标高度按一屏设计：
  标题 24 + 9 行 × 40 + 3 个输入框 × 约 48 + 按钮 40 + padding 24 ≈ **532dp**，
  6.73 寸可用高度足够；交易对方联想只在**有输入时**展开，常态不占高度

### 3.1 日期用 UTC 锚点，不碰设备时区

M3 `DatePicker` 的 `selectedDateMillis` 按 **UTC 午夜**计。若用 `Asia/Shanghai` 当日零点
（= 前一日 UTC 16:00）往返，会串一天（指南 §3 T13 同类坑）。本批纯日期选择器用
`LocalDate.atStartOfDay(ZoneOffset.UTC)` 写入、`Instant.atZone(ZoneOffset.UTC)` 读回，
与设备时区完全解耦；UI 显示 `YYYY-MM-DD`，写入 `localDate: Int` 是同一天。
`LedgerClock.zoneId` 仍只负责 `timestamp → localDate`（记账时刻），两者职责不混。

## 4. 交易对方筛选（新增，改动面比看起来小）

`RecordDao` 已有 `distinctCounterparties()`（LIMIT 20）与 `searchCounterparties(q)`（阶段 8 给记一笔）。
筛选面板需要**全量历史对方**做联想，故新增一条纯只读 `allCounterparties()`
（DISTINCT、无 LIMIT、按名排序），由 `DetailViewModel` 在 init 与 `bumpRevision()` 时加载成
`StateFlow<List<String>>`。理由：筛选是 Composable，传 `List<String>` 比传 suspend 回调少一条
异步路径；记一笔仍用 `searchCounterparties`，两者互不改动。

链路：`RecordFilter` / `FilterValues` 加 `counterpartyQuery` → `pagedFiltered` 加一条
`(:counterpartyQuery IS NULL OR COALESCE(r.counterparty,'') LIKE '%' || :counterpartyQuery || '%')`
→ `RecordPagingSource` 传参 → `applyFilter` / `clearFilter` / `activeCount()` 同步。

- 仍是**一条参数化 SQL 覆盖全部组合**，零字符串拼接，零注入面
- 字段名中性叫「交易对方」，跨支出 / 收入，不随 `RecordType` 改文案
- `activeCount()` MUST 含新条件，否则「筛选 (N)」角标少算
- 联想只在输入非空时出现，最多 4 行、每行 ≥40dp；空输入不占空间
- 筛选状态仍提升到 Activity 作用域（第 5 批成果），跨 Tab 返回条件与滚动位置 MUST 保持

## 5. 规格处理：4 份 MODIFIED，不是追加 ADDED

`reconciliation` 三条、`ledger-transactions` 一条 Requirement 把筛选条件**列举成封闭清单**
（「时间 + 账户 + 类型 + 一级/二级分类 + 金额区间 + 备注全文」）。按指南 §5，
本批 MUST 对这 4 条写 `## MODIFIED Requirements` 的**完整替换文本**补上「交易对方」，
而不是另加一条 ADDED 让 specs 出现两份清单。

同时保持 `reconciliation`「阶段边界明确」的功能归属边界不变：
明细筛选面板 MUST NOT 混入「按差额反查」「对账锚点」「疑似问题标记」。

新增的布局与联想约束放 `## ADDED Requirements`（`reporting-ui` 一条 + `reconciliation` 一条），
与 MODIFIED 的「条件清单」职责分离。

## 6. 三级回退顺序（首轮 CI 若红，每次只改一处单独推送）

1. **编译错误**：先查 `DatePickerDialog` / `rememberDatePickerState` / `FilterChip` 的
   `@ExperimentalMaterial3Api` 是否漏 `@OptIn`；再查新增 import 是否齐
   （第 6 批漏 `Box`、第 8 批漏 `PaddingValues` 都是这一类）
2. **SQL / Room 校验**：`pagedFiltered` 新增参数名与 `@Query` 占位符必须逐字一致；
   `RecordPagingSource` 调用参数顺序核对；`allCounterparties()` 列名
   `counterparty` 与 Entity 一致
3. **单测 / 静态检查**：`check_stat_rules.py` 的 marker 回溯用 `dao.find('fun ' + marker)`，
   新增方法名 MUST NOT 以已有 marker 为前缀（否则会回溯到错误的 `@Query`）——
   `allCounterparties` 不冲突；`check_kotlin_strings.py` 检查字符串转义，本批直接整文件写、
   不用 Python 拼 Kotlin
4. 若仍红：临时把 Gradle 日志回传 `diag/` 从 Contents API 读（job logs 403 老问题），
   **用完 MUST 移除诊断步骤与 `diag/` 全部文件**

## 7. 已证伪、本批不得重试（指南 §7）

`height(固定值)` 压输入框 / `contentPadding` 命名参数 / `BasicTextField` 自绘 /
为简洁砍常用项或把可点区压到 40dp 以下 / 单行横滚 + 更多 / 点标签本身作唯一入口 /
`● ` 文字前缀表选中 / 只追加 ADDED 不改旧 Requirement / 拿迁移或明文当否决理由。

## 8. 验收与真机边界

- CI 全绿、六个静态检查 PASS、版本 **0.1.7 → 0.1.8**、制品名带短 sha
- 远端按**最终 commit sha** 逐文件比对 blob sha；给可点开的 commit + run id
- **沙盒验不了**：6.73 寸上是否真的一屏放得下、10+ 账户长名在弹层里是否完整、
  placeholder 对比度、DatePicker 弹窗观感、`FilterChip` 底色是否够清楚 → 必须由使用者装机看一眼
- 累积未验清单（BiometricPrompt / SAF / Excel CSV / 5 万条切月耗时等）由指南 §8 继承，
  不因本批归档或出包被改写成「已通过」


## 9. 实测回填（第 11 批）

| 项 | 结果 |
|---|---|
| 代码 commit | `0e5d1ada719e`（14 个文件：7 源码 + 版本号 + 活文档 + change 四件套） |
| push CI | `43857699177` **success**，17 步全绿，**首轮 Kotlin 编译零错误** |
| 六个静态检查 | 本地与 CI 全 PASS；`check_transaction_annotations.py` 的 2 项迁移提示按「稳定版前暂缓」为记录性，不失败 |
| release | `43858676724` **success**，23 步绿 / 5 步按 input 跳过（plain、AAB、创建 Release 未触发；lint 开启） |
| 坚果云 | 步骤 `completed success`；metrics 制品名 `nsfalse` 表示 `skipped=false`（认证 / 建目录 / PUT 均未失败）。**HEAD 落盘校验只写日志不中断，不当作已验证 HEAD 200** |
| 制品 | `offline-ledger-release-v0.1.8` 12,199,500 B；`mapping-v0.1.8` 3,436,220 B；`metrics-v0.1.8-p-m-nsfalse` 269 B |
| 版本号 | `defaultVersionName = 0.1.8`，产物名带版本，**不再同名覆盖旧包** |
| 零结构变更 | `AppDatabase` 仍 `version = 1`，无 Migration、无新增依赖 / 权限，Manifest 仍无 `INTERNET` → 覆盖安装即可 |

**首轮零编译错误的原因**（指南 §6.1 第三次奏效）：动码前先写了本文件 §1 根因、§3 已证伪结论、§6 三级回退顺序；
直接复用 `RecordEditorDialog.kt` 已验证的输入框写法；推送前本地粗扫了 import 清单与括号平衡。
`FilterChip` 在 360dp 下用 `weight(1f)` 平分，避免 4 个 chip 溢出被裁——这是推送前主动补的一处风险点，
不是 CI 报出来的。

**未验证（必须由使用者装机确认）**：6.73 寸上 9 个维度是否真的一屏放得下、10+ 账户长名在全量弹层里是否完整、
placeholder 对比度、`FilterChip` 底色是否清楚、`DatePickerDialog` 弹窗观感、切 Tab 返回后条件与滚动位置保持。
累积未验清单（BiometricPrompt / SAF / Excel CSV / 5 万条切月耗时等）由活文档 §8 继承，不因本批出包改写。
