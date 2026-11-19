# 任务（第 31 批）

## 阶段一（本次真实完成）
- [x] 远端与本地基线核查：HEAD `d7bafd66e6e7` / 版本 0.2.14
- [x] 真实读取 `DatabaseKeyManager.kt` / `SecureBackupCodec.kt` 并修正设计
- [x] `SnapshotCodec` 纯 JVM 内核 + 单测
- [x] `BulkEditEngine` 纯 JVM 内核 + 单测
- [x] 版本 0.2.15、Manifest `hasFragileUserData`
- [x] 六个静态检查本地 PASS
- [x] push CI 与远端 blob 校验

## 阶段二（本次真实完成）
- [x] SAF 授权目录 + 原子写（.tmp 回读校验后改名）+ 保留 7 份策略接线
- [x] 每日定时调度（AlarmManager + SnapshotReceiver，零新增依赖、零权限）
- [x] 恢复页：选快照 → 校验 → 解密 → 单事务重建 → 末尾重算
- [x] 批量规整页：条件/动作/预览/单事务落库/一键撤销（新增 BulkEditRevertTest）
- [x] 出包 v0.2.15 并上传坚果云（代码 `84846cdf3f9a315c16da7b82da5ed9a100f250c7` / CI `44332258434` / release `44333917298`；制品 12342643 B / 3714261 B / 270 B；上传到坚果云 success）
- [ ] 真机复核（使用者，刻意保留）

### 一期实现边界（如实说明）
`BulkConditions` 内核支持对方相等、时间窗与范围参数，`BulkTarget` 未含账户字段；
阶段二 UI 只暴露「对方包含 / 备注包含 / 金额区间 / 改分类 / 改对方 / 备注替换 /
设为不计入收支」，范围固定为全部流水。时间窗、按 `importSource` 限定与改账户
留二期，不写入本次 Requirement，避免规格与实现出现两套清单。

## 阶段三（第 34 批，v0.2.16：真机反馈收尾）

- [x] 真读 `LedgerRepository.importBackup` / `BackupPayload` / `CsvCodec` /
      `SettingsViewModel` 定位根因，不拿门禁里的逻辑归因当源码结论
- [x] 修复 `FOREIGN KEY constraint failed (code 1811)`：事务内
      `PRAGMA defer_foreign_keys = ON`、清空前 `UPDATE categories SET parentId = NULL`
      断开自引用、分类按父先子后插入、补 `aliasDao.clear()`、提交前
      `PRAGMA foreign_key_check` 兜底；失败由 `withTransaction` 整库回滚
- [x] 备份 DTO 补齐 `cardLast4` / `enabled` / `counterpartyAliases`，
      导出与恢复双向带上，带默认值保持旧备份可解析
- [x] 口令仍必填，新增「在本机记住」（复用 `SnapshotPrefs` 的 Keystore 加密存储，
      卸载即失效）；文件本身始终加密
- [x] 按表头嗅探分流：自有 11 列格式投进「导入账单」给明确引导，
      新增「从 CSV 重建」入口走 `CsvCodec.parseRebuild`；
      转账 / 无账户 / 无一级分类行列进 notices 明确跳过，不静默丢弃
- [x] 新增 `CsvRebuildTest`（格式识别 / 来源保留 / 跳过报告 / 第三方拒绝）
- [x] 六个静态检查本地 PASS
- [x] push CI 与 release 实测回填、坚果云上传
- [x] 第 34 批恢复闭环真机复核（使用者 2026-11-19 反馈：导出加密备份 → 恢复、
      自动快照 → 恢复均暂未发现问题）；「从 CSV 重建」入口另在阶段四收尾

### 本期实现边界（如实说明）

「从 CSV 重建」只覆盖 `EXPENSE` / `INCOME` 且账户与一级分类齐备的行；
第 34 批复用 `importRecords` 导致「账户不存在或已归档」，第 35 批起
拆 `rebuildFromOwnCsv` 专用路径并保持 `importSource/sourceId` 幂等。
`TRANSFER` / `SETTLEMENT`、无账户、无一级分类的行明确列为「格式跳过」，
完整重建（含转账、账户、模板、别名、启用态）仍走「从加密备份恢复」或
「从快照恢复」。二者是互补入口，不是同一份数据的两套写法。

## 第 34 批实测回填（真实远端编号，2026-11-19）

| 项 | 实测 |
|---|---|
| 代码 commit | `4a86031926828f5a89e2f1e1ffed2cf4aa6783ad`（2 新增 / 15 修改，v0.2.16） |
| 首轮 push CI | `44440446947` **failure**，挂在「运行单元测试」；诊断回传日志定位 `BackupPayload.kt:54/58 Redeclaration`（脚本中断后重跑，前缀锚点幂等判断漏判，重复插入 `BackupAlias`） |
| 诊断通道 | 临时 dispatch-only `diag.yml` → run `44441001168`（artifact 下载被出网策略挡）、`44441535608`（改用 contents API 回传日志成功）；用完即删 |
| 修复 commit | `5cde279ee70d0ba63bd020d345839c5ef424da06`（只改 `BackupPayload.kt`） |
| 修复后 push CI | `44441996336` **success**（17 步，`CsvRebuildTest` 由单测步骤跑过） |
| release | `44443061506` **success**，23 步绿 / 5 步按 input 跳过，**「上传到坚果云」completed success** |
| 制品（Actions artifact zip 压缩后尺寸） | `offline-ledger-release-v0.2.16` 12350729 B；`offline-ledger-mapping-v0.2.16` 3718037 B；`offline-ledger-metrics-v0.2.16-p-m-nsfalse` 269 B |
| 坚果云 | `nsfalse`（认证 / 建目录 / PUT 均未失败）；HEAD 落盘校验只写日志不中断，目录内容以使用者所见为准，不当作已验证 HEAD 200 |
| 静态检查 | 六个 `check_*.py` 本地与 CI 均 PASS；写方法清单 41 个均在事务内 |

**产物口径**：约 24M 是 R8 release APK 原始字节，Actions 制品页约 12M 是 zip 二次压缩尺寸，两者同一个文件；不是 debug 变体。

**真实落盘**：`importBackup` 的 defer_foreign_keys / 断开自引用 / 父先子后 / foreign_key_check、备份 DTO 三处补齐、口令本机记住、`CsvCodec.detectFormat` + `parseRebuild`、设置页「从 CSV 重建」入口、`CsvRebuildTest`、两份 MODIFIED delta 与指南 §7.17。
`AppDatabase.version = 3` 未变 → **v0.2.15 上覆盖安装即可，不用卸载**。
`data-safety-and-bulk-rules` 与 `preset-data-and-counterparty` 仍进行中，未擅自归档。

## 阶段四（第 35 批，v0.2.17：自有 CSV 重建入口收尾）

- [x] 真读 `SettingsPages.kt` / `SettingsViewModel.kt` / `LedgerRepository.kt` /
      `CsvCodec.kt` 定位「账户不存在或已归档」根因，不拿门禁里的逻辑归因当源码结论
- [x] 拆 `LedgerRepository.rebuildFromOwnCsv` 专用写路径：单事务、末尾重算、
      失败整库回滚，与 `importBackup` 同骨架；`importRecords` 严格语义不动
- [x] 新增纯 JVM `CsvRebuildResolver`：归档账户 / 停用分类视为已知，
      缺项一次性收集而非首行抛异常；同名二级按 `一级/二级` 分别判定
- [x] `SettingsViewModel.confirmCsvImport` 按 `plan.rebuild` 分流，
      结果摘要含写入 / 重复跳过 / 缺项跳过 / 缺失账户与分类清单 / 重建引导
- [x] 改正两处自相矛盾文案：设置页帮助文本「清库后请用从 CSV 重建」、
      读文件嗅探里的「完整重建请用加密备份」表述；弹窗标题按模式区分
- [x] 新增 `CsvRebuildResolverTest`（归档不拒 / 空库逐项报告 / 换名清单 / 同名二级）
- [x] `check_transaction_annotations.py` 写方法清单登记 `rebuildFromOwnCsv`
- [x] 六个静态检查本地 PASS
- [x] push CI 与 release 实测回填、坚果云上传
- [ ] 真机复核（使用者，刻意保留）

### 本期实现边界（如实说明）

自有 CSV 仍不自包含：不带账户 / 分类 / 模板 / 别名 / 转账，完整重建只能走
加密备份或自动快照。表结构 `version = 3` 未变 → v0.2.16 上覆盖安装即可。

## 第 35 批实测回填（真实远端编号，2026-11-19）

| 项 | 实测 |
|---|---|
| 代码 commit | `e635c9a436ecd9a3ee33f90eedc00be7f1daccb6`（2 新增 / 10 修改，v0.2.17） |
| push CI | `44447674151` **success**，17 步全绿，新增 `CsvRebuildResolverTest` 由单测步骤跑过 |
| release | `44448470911` **success**，23 步绿 / 5 步按 input 跳过，**「上传到坚果云」completed success** |
| 制品（Actions artifact zip 压缩后尺寸） | `offline-ledger-release-v0.2.17` 12353413 B；`offline-ledger-mapping-v0.2.17` 3723958 B；`offline-ledger-metrics-v0.2.17-p-m-nsfalse` 269 B |
| 坚果云 | `nsfalse`（认证 / 建目录 / PUT 均未失败）；HEAD 落盘校验只写日志不中断，目录内容以使用者所见为准 |
| 静态检查 | 六个 `check_*.py` 本地与 CI 均 PASS；写方法清单 42 个均在事务内 |

**产物口径**：约 24M 是 R8 release APK 原始字节，Actions 制品页约 12M 是 zip 二次压缩尺寸，两者同一个文件；不是 debug 变体。
`AppDatabase.version = 3` 未变 → **v0.2.16 上覆盖安装即可，不用卸载**。
`data-safety-and-bulk-rules` 与 `preset-data-and-counterparty` 仍进行中，未擅自归档。


## 第 36 批（v0.2.18）：规则 2.0 与分类迁移映射表

**根因**：一期「即时批量规整」只有 AND 条件、单一固定目标值、规则用完即弃，
无法表达「旧分类 A/B/C 或对方关键词散乱记录一次圈出」与「20 组旧分类一次映射到新分类」。
Firefly III 的规则是「条件组 + 动作组 + 规则组有序 + strict/非 strict + 对历史回溯应用」，
本批借其模型，不引触发器自动跑 / 表达式引擎 / 标签体系 / stop processing。

**实现取舍（与门禁描述的一处偏差，如实登记）**：门禁写「规则四表」，
实作收敛为 `rule_groups` + `rule_definitions` **两张表**，条件与动作序列化为 JSON 列、零外键。
理由：四种操作符各建表会把恢复链路的 FK 拓扑序再拉长一层，v0.2.16 刚修完 1811，
不为结构纯度再冒一次风险；能力范围（可保存、有序、AND/OR、取反、多动作、回溯）不缩水。

| 项 | 落地 |
|---|---|
| 纯 JVM 内核 | `RuleModels.kt` / `RuleEngine.kt`：ALL/ANY、negate、七种操作符（含正则、为空、不等于）、多动作、组内 sortOrder 有序、`changeset` 逐字段撤销 |
| 分类迁移 | `CategoryMigration` + `migrationReport`（每组命中数 / 冲突组 / 未命中）+ 首次命中优先，与规则组共用同一 `applyBulkTargets` 写路径 |
| 持久化 | `AppDatabase.version = 4`，两张表零外键；恢复按 groups → definitions 顺序插入，纳入 `defer_foreign_keys` / `foreign_key_check` 同一事务 |
| 备份 | `BackupPayload.ruleGroups` 带默认值，旧备份缺键解析为空列表；导出与自动快照自动带上规则 |
| UI | 设置页按钮文案精简为「从 CSV 重建」并给同排按钮 `maxLines = 1`；「批量规整」页新增「规则与分类迁移」弹层，预览 → 单事务 → 末尾重算 → 一键撤销 |
| 不做 | 导入后自动触发（触发器默认关）、规则组嵌套、表达式引擎、标签/预算联动、stop processing |

**测试**：`RuleEngineTest` 6 条（ANY/ALL/取反/组顺序/映射命中与冲突/撤销还原）、
`RuleBackupRoundTripTest` 2 条（规则随备份往返、旧备份缺键仍可解析）。
门禁里写的「恢复 androidTest」未做：CI 无模拟器不跑 androidTest，
改为纯 JVM 备份往返单测覆盖 DTO 层，恢复链路仍待真机「导出→清数据→重装恢复」复核。

**`version = 4` → 必须卸载重装、装之前先导出**；v0.2.17 覆盖安装会开库失败。

## 第 36 批交付状态（真实远端编号，2026-11-19）

- [x] push CI 与 release 实测回填、坚果云上传
- [ ] 真机复核（使用者，刻意保留）

## 第 36 批实测回填（真实远端编号，2026-11-19）

| 项 | 实测 |
|---|---|
| 代码 commit | `3d8f3b8682eb4d3cf66cc497f13a5c3b7911b3c7`（8 新增 / 13 修改 / 0 删除，v0.2.18） |
| push CI | `44455688567` **success**，17 步 success，`RuleEngineTest` / `RuleBackupRoundTripTest` 由单测步骤跑过 |
| release | `44470577388` **success**，23 步 success / 5 步按 input 跳过，**「上传到坚果云」completed success** |
| 制品（Actions artifact zip 压缩后尺寸） | `offline-ledger-release-v0.2.18` 12386391 B；`offline-ledger-mapping-v0.2.18` 3762956 B；`offline-ledger-metrics-v0.2.18-p-m-nsfalse` 270 B |
| 坚果云 | `nsfalse`（认证 / 建目录 / PUT 均未失败）；HEAD 落盘校验只写日志不中断，目录内容以使用者所见为准 |
| 静态检查 | 六个 `check_*.py` 本地与 CI 均 PASS；写方法清单沿用 `applyBulkTargets` 等已登记方法 |

**真实落盘**：规则 2.0（`RuleModels` / `RuleEngine` / `RuleManagerDialog`）、
`rule_groups` + `rule_definitions` 两张零外键表、`BackupPayload.ruleGroups` 往返、
分类迁移映射表的命中 / 冲突 / 未命中报告、设置页「从 CSV 重建」文案精简与同排 `maxLines = 1`。

**如实边界**：门禁写「规则四表」，实作收敛为两张表；门禁写「恢复 androidTest」，
因 CI 无模拟器不跑 androidTest，改为纯 JVM `RuleBackupRoundTripTest` 覆盖 DTO 层，
恢复链路仍待真机「导出 → 清数据 → 卸载重装 → 恢复」复核。
`AppDatabase.version = 4`、无迁移层 → **必须卸载重装、装之前先导出**，
v0.2.17 覆盖安装会开库失败。约 24M 是 R8 release APK 原始字节，
Actions 制品页约 12M 是 zip 二次压缩尺寸，两者同一个文件；不是 debug 变体。
`data-safety-and-bulk-rules` 与 `preset-data-and-counterparty` 仍进行中，未擅自归档。


## 第 37 批：规则编辑器三段式（v0.2.19）

- [x] 真读 `RuleModels` / `RuleManagerScreen` / 分类选择器 / 联想 DAO，确认零改表
- [x] `RuleEditorCatalog` 目录单一来源（字段 → 操作符 / 值控件、动作 → 值控件、批量生成）
- [x] 枚举末尾追加值、BulkTarget 只读字段、RuleEngine 新字段 / NOT_EQUALS / APPEND_NOTE
- [x] `RecordDao.searchNotes` 限量联想 + 200ms 防抖
- [x] `RuleManagerScreen` 独立全屏动态行编辑器（标题 / ALL-ANY / 取反 / 多动作 / 启停）
- [x] 分类与账户弹窗含停用归档、显示「一级 / 二级」；对方与备注联想
- [x] 删独立映射表 UI，改「批量录入」生成 N 条普通规则，命中 / 冲突 / 未命中报告
- [x] 「保存并立即应用到历史」复用预览 → 单事务 → 末尾重算 → 撤销
- [x] `specs/bulk-edit/spec.md` 整块替换，删「固定动作清单 / 独立映射表」矛盾表述
- [x] 版本 0.2.19、六个静态检查本地 PASS
- [x] push CI / release / 坚果云实测回填（执行后填真实编号）
- [ ] 真机复核（使用者，刻意保留）：自建「分类等于旧类 → 设置分类为新类」并命中；
      对方 / 备注联想、分类弹窗能选到停用旧类；一条规则两动作、ANY + 取反命中散乱记录；
      批量录入冲突可见；撤销逐字段还原；导出恢复后规则仍在

## 第 37 批交付状态（真实远端编号，2026-11-19）

- [x] push CI 与 release 实测回填、坚果云上传
- [ ] 真机复核（使用者，刻意保留）

## 第 37 批实测回填（真实远端编号，2026-11-19）

| 项 | 实测 |
|---|---|
| 代码 commit | `997e4d195ee718993350a47142026aeb48166477`（主代码，3 新增 / 11 修改 / 0 删除）→ `8b983c2872dd`、`07787056d153`（两次单测断言修正），最终 HEAD `07787056d15329ce704e8a25b1ca1e8cf8397826`（v0.2.19） |
| push CI | 首轮 `44478463075` failure、并发 `44479256085` cancelled，修复后 `44479652044` **success**，17 步 success，静态检查与单测步骤均 success |
| release | `44480858062` **success**，23 步 success / 5 步按 input 跳过，**「上传到坚果云」completed success** |
| 制品（Actions artifact zip 压缩后尺寸） | `offline-ledger-release-v0.2.19` 12399036 B；`offline-ledger-mapping-v0.2.19` 3789871 B；`offline-ledger-metrics-v0.2.19-p-m-nsfalse` 270 B |
| 坚果云 | `nsfalse`（认证 / 建目录 / PUT 均未失败）；HEAD 落盘校验只写日志不中断，目录内容以使用者所见为准 |
| 静态检查 | 六个 `check_*.py` 本地与 CI 均 PASS；未新增写方法，`applyBulkTargets` 沿用 |

**真实落盘**：`RuleEditorCatalog`（字段 → 操作符 / 值控件单一来源）、
`RuleManagerScreen` 独立全屏动态行编辑器（标题 / ALL-ANY / 取反 / 多动作 / 启停）、
`RecordDao.searchNotes` 限量联想 + 200ms 防抖、分类与账户弹窗含停用归档并显示「一级 / 二级」、
删独立映射表 UI 改「批量录入」生成 N 条普通规则且给出命中 / 冲突 / 未命中报告、
「保存并立即应用到历史」复用预览 → 单事务 → 末尾重算 → 撤销。

**如实边界**：规则组嵌套、表达式引擎、标签 / 预算联动、stop processing、导入后自动触发
均不做（触发器默认关）；「描述」映射到已有「交易对方」、FF3 `notes` 映射到「备注」，
**零改表** → `AppDatabase.version = 4` 未变，**v0.2.18 上覆盖安装即可，不用卸载**。
约 24M 是 R8 release APK 原始字节，Actions 制品页约 12M 是 zip 二次压缩尺寸，
两者同一个文件；不是 debug 变体。`data-safety-and-bulk-rules` 与
`preset-data-and-counterparty` 仍进行中，未擅自归档。

## 第 38 批（v0.2.20）：规则列表与编辑器独立、保存后可再次应用

- [x] `RuleListScreen` 三个独立全屏页（列表 / 编辑器 / 查看匹配记录）+ 状态栈，零新增 NavHost 路由
- [x] 设置页「规则列表」一级入口排在「数据与备份」之后；删「数据与备份」内「批量规整」按钮
- [x] 列表行「应用 / 编辑 / 复制 / 删除」+「应用整个规则组」+ 上次应用结果；「预览」文案统一为「查看匹配记录」
- [x] `RuleEditorContent` 只承载编辑，已存规则不丢；批量录入仍在编辑器内
- [x] 应用复用 `previewRule` / `previewGroup` → 单事务 → 末尾重算 → `changeset` 撤销
- [x] 规格按标题整块 MODIFIED 替换旧条件清单，追加第 38 批决策史
- [ ] 真机复核（使用者，刻意保留）：列表与编辑器各自独立、设置一级入口位置、保存后再次应用命中数正确、
      撤销逐字段还原、规则组按序应用、动态编辑器求值正确性另行验证

## 第 39 批（v0.2.21）：补齐规则编辑器 4 条反馈

- [x] 批量录入分隔符容错（半角 | / 全角 ｜ / → / Tab / 前后空格）、空行与 # 注释跳过、逐行 issues 报告
- [x] 新分类不再靠手打：编辑器内「选新分类插入」弹窗回填「一级 / 二级」全名；「填入示例」两行样例
- [x] 字段或操作符切换时清空本行 value / categoryId / accountId / 金额与日期区间，不残留到新字段
- [x] 全部 OutlinedTextField 改为 placeholder-only，去掉带文字提示的边框 label
- [x] 规则组管理独立成第 4 个全屏页；列表行显示条件 / 动作摘要，分类显示「一级 / 二级」全名
- [x] 按钮按 M3 主次分级：主操作单个实心不拉满、次操作 TextButton、行内紧凑；触摸区不缩
- [x] 不动 RuleEngine / applyBulkTargets / changeset / 两张规则表 / version = 4 / 备份与恢复链路
- [ ] 真机复核（使用者，刻意保留）：粘贴含空格 / 全角分隔 / 空行能生成 N 条且逐行报错、
      分类→对方切换后值清空、分组管理独立页、列表摘要、按钮不霸屏、保存后再次应用与撤销

## 第 40 批（v0.2.22）：草稿预览与删批量录入

- [x] 规则编辑器底部加「查看匹配记录」：用当前未保存草稿走 `previewRule` 只读求值，不要求先保存
- [x] 草稿预览与列表「应用」共用 `RuleMatchContent`；`matchFrom` 让返回分别回编辑器 / 列表
- [x] 兼容旧壳 `RuleManagerScreen` / `RuleManagerDialog` 也接上草稿预览，旧路径点按钮不空转
- [x] 删「批量录入（分类迁移）」整个 UI 块、`generateBatch`、`batchRules` / `batchReport` / `BatchHit`
- [x] `RuleUiState.report` 字段一并清掉；`previewRule` 支持空名草稿
- [x] 规格按标题整块 MODIFIED 替换「分类迁移 / 批量录入」Requirement 与 Scenario，追加第 40 批决策史
- [x] `RuleEditorCatalogTest` 改为目录完整性 + 草稿预览经同一引擎求值与逐字段撤销
- [x] 不动 RuleEngine / applyBulkTargets / changeset / 两张规则表 / version = 4 / 备份与恢复链路
- [x] 删入口不丢已存规则：此前批量录入生成的规则仍是普通 `rule_definitions` 行
- [ ] 真机复核（使用者，刻意保留）：编辑页改完条件直接看命中数、返回仍在编辑器且草稿不丢、
      列表「应用」返回列表、批量录入入口消失且旧规则仍在、撤销逐字段还原

## 第 40 批实测回填（v0.2.22）

- 代码 commit：`ed79fd47a022b8589904f6db6831302693f8c36b`（9 文件：0 新增 / 9 修改 / 0 删除）
- push CI：`44508328826` **success**，17 步全 success / 0 跳过，首轮 Kotlin 编译与纯 JVM 单测均零错误
- release：`44509707175` **success**，28 步 = 23 success / 5 skipped，**「上传到坚果云」completed success**，head_sha 为代码 commit
- 制品：`offline-ledger-release-v0.2.22` 12389471 B（Actions 制品页 zip 压缩口径）、
  `offline-ledger-mapping-v0.2.22` 3786885 B、`offline-ledger-metrics-v0.2.22-p-m-nsfalse` 269 B
- `nsfalse` 只表示认证 / 建目录 / PUT 均未失败；HEAD 落盘校验只写日志不中断，
  坚果云目录以使用者实际看到的短 sha 为准
- 零改表（`version = 4` 未变）→ **v0.2.21 上覆盖安装即可，不用卸载**
- 真机复核仍留 `[ ]`：编辑页草稿预览命中数、返回仍在编辑器且草稿不丢、
  列表「应用」返回列表、批量录入入口消失且旧规则仍在、撤销逐字段还原

## 第 41 批（v0.2.23）：年月一步直达 + 时间范围语义解耦

- [x] 真读 `RecordFilter` / `RecordPagingSource` / `DetailViewModel` /
      `DetailScreen` / `FilterSheet` / `RecordEditorDialog`，确认
      「时间范围留空 = 隐式只查当前月」与「switchMonth 清掉范围」两处根因
- [x] 查证 Compose M3 无独立 YearMonthPicker、`displayedMonthMillis` 可写、
      三个开源库均不适配 → 按 A′ 零新依赖实现
- [x] `YearMonthMath` 纯 JVM 内核 + `YearMonthMathTest` 4 条
- [x] `YearMonthPickerDialog` 共享浮层：年份直达 + 12 格 + 回到本月 + 命中数角标
- [x] 明细页月份条可点、顶部 `timeSummary()` 明示时间语义
- [x] 记一笔与筛选面板起止日期的官方 DatePicker 加「跳转年月」，月末 clamp
- [x] `RecordFilter.allMonths` + `effective*` 返回 `Int?` 交 SQL 短路；
      `timeSummary()` / `isCrossMonth()` 单一出口
- [x] `switchMonth` 不再清筛选条件；新增 `selectMonth(ym)` 供 Picker 一步跳转
- [x] 筛选面板时间快捷段（当前月 / 近3月 / 今年 / 全部）
- [x] `RecordDao.monthCounts` 一条 GROUP BY 取 12 格角标（按年查，不逐月查）
- [x] `RecordFilterTest` 补 2 条（全部月份不注入当月 / 单端范围不双重夹击）
- [x] 版本 0.2.23、六个静态检查本地 PASS
- [x] push CI / release / 坚果云实测回填（执行后填真实编号）
- [ ] 真机复核（使用者，刻意保留）：点月份标题弹 12 格并一次跳到任意年月、
      记一笔 1/31 跳 2 月不丢不崩、时间留空时摘要明示、
      选「全部」跨月生效、切月筛选不丢、快捷段范围与月份不双重夹击、
      月格命中角标正确、规则与 CSV 重建不回退

## 第 41 批实测回填（v0.2.23）

- 代码 commit：`9704cb61f59043ba7ccec8da02ad9f7a8faa159d`（15 文件：3 新增 / 12 修改 / 0 删除）
- push CI：`44518136822` **success**，17 步全 success / 0 跳过，首轮 Kotlin 编译与纯 JVM 单测零错误
- release：`44519440667` **success**，28 步 = 23 success / 5 skipped，**「上传到坚果云」completed success**，head_sha 为代码 commit
- 制品：`offline-ledger-release-v0.2.23` 12402874 B（Actions 制品页 zip 压缩口径）、
  `offline-ledger-mapping-v0.2.23` 3807428 B、
  `offline-ledger-metrics-v0.2.23-p-m-nsfalse` 271 B
- `nsfalse` 只表示认证 / 建目录 / PUT 均未失败；HEAD 落盘校验只写日志不中断，
  坚果云目录以使用者实际看到的短 sha 为准
- 零改表（`version = 4` 未变）→ **v0.2.22 上覆盖安装即可，不用卸载**
- 真机复核仍留 `[ ]`：年月 12 格一步跳转、记一笔月末 clamp、
  时间留空摘要明示、全部月份跨月生效、切月筛选不丢、范围与月份不双重夹击、
  月格命中角标、规则与 CSV 重建不回退


## 第 42 批（v0.2.24）：不带时间直接看全部记录

- [x] 真读 `RecordFilter` / `RecordPagingSource` / `DetailViewModel` /
      `DetailScreen` / `FilterSheet` / `YearMonthPicker`，确认
      「`allMonths` 默认 false + `selectMonth` 把定位做成过滤」是根因
- [x] `allMonths` 默认 true，`clearFilter` 与三个跨 Tab 下钻工厂同步为 true
- [x] `timeSummary(totalCount)` 单一出口明示「全部记录 · 当前定位 · 共 N 条命中」
- [x] 独立 `_anchorMonth` Flow：左右箭头 / Picker 只定位，不动 `_filter`、
      不重建 Pager、不清筛选；空月灰显不可点
- [x] 跨月连续分页 + 月分组头（普通 header，复用已有 monthCounts，零新查询）
- [x] 「当前月」快捷段仍可主动切回按月视图
- [x] `RecordFilterTest` 补 3 条（默认全库 / 显式按月 / 摘要命中数）
- [x] 版本 0.2.24、六个静态检查本地 PASS
- [x] push CI / release / 坚果云实测回填（执行后填真实编号）
- [ ] 真机复核（使用者，刻意保留）：不带时间一屏滚完所有命中记录、
      跨月分组头、点 12 格里某月只定位不过滤、空月灰显、筛选条件不丢、
      点「当前月」切回按月、范围与月份不双重夹击、规则与 CSV 重建不回退

## 第 42 批实测回填（v0.2.24）

- 代码 commit：`ab8c3ded0d7eb2ee5e16c3bb6da767771aa11baa`（11 文件：0 新增 / 11 修改 / 0 删除）
- push CI：`44526465899` **success**，17 步全 success / 0 跳过，首轮 Kotlin 编译与纯 JVM 单测零错误
- release：`44527496670` **success**，28 步 = 23 success / 5 skipped，**「上传到坚果云」completed success**，head_sha 为代码 commit
- 制品：`offline-ledger-release-v0.2.24` 12403219 B（Actions 制品页 zip 压缩口径）、
  `offline-ledger-mapping-v0.2.24` 3811084 B、
  `offline-ledger-metrics-v0.2.24-p-m-nsfalse` 269 B
- `nsfalse` 只表示认证 / 建目录 / PUT 均未失败；HEAD 落盘校验只写日志不中断，
  坚果云目录以使用者实际看到的短 sha 为准
- 零改表（`version = 4` 未变）→ **v0.2.23 上覆盖安装即可，不用卸载**
- 真机复核仍留 `[ ]`：不带时间一屏滚完所有命中记录、跨月分组头、
  点 12 格里某月只定位不过滤、空月灰显、筛选条件不丢、
  点「当前月」切回按月、范围与月份不双重夹击、规则与 CSV 重建不回退
- 取舍（见 `design.md`）：月分组头非 sticky、不显示金额合计（不新增聚合查询）、
  锚点只对已加载分页项生效，目标月未载入时给提示而非伪装全表扫描
