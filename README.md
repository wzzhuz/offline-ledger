# 离线账本（offline-ledger）

**纯本地、零网络权限、无广告的个人 Android 记账应用。** 知道自己的资产与支出构成，
数据不托管给任何第三方，也不依赖长期开机的服务器或 NAS。

> 状态：**阶段 0 / 阶段 1 / 阶段 2 均已完成并归档，CI 全绿（run 43218438836）**；
> 阶段 3 处于对齐门禁，待确认后建 change。
> 现在可以打开应用记一笔、翻明细、组合筛选、恢复删除流水、导出明文 JSON。
> 资产与统计仍是标注「阶段 3 / 4」的占位页。
> 本仓库遵循 OpenSpec 规范驱动开发，规格是唯一真理源。

---

## 30 秒架构速览

| 项 | 选择 |
|---|---|
| 语言 / UI | Kotlin + Jetpack Compose（Material 3） |
| 架构 | MVVM + Clean Architecture（UI → ViewModel → Repository → Room DAO） |
| 存储 | Room (SQLite) + **SQLCipher**（阶段 1 已接入） |
| 异步 / DI | Coroutines + StateFlow / Hilt |
| 序列化 | kotlinx.serialization（备份 DTO 与 Entity 分离） |
| 分页 / 图表 | **Paging 3**（阶段 2 已接入）/ 原生 Canvas 自绘（阶段 4） |
| 权限 | **不声明 `INTERNET`**，仅用 SAF 导入导出 |
| 最低版本 | Android 8.0（API 26） |
| targetSdk | 36（跟随当年 Google Play 要求，每年复核） |
| 默认本地化 | `Asia/Hong_Kong`、默认币种 `HKD` |
| 包名 | `com.zwz.offlineledger` |

三条铁律（详见 `openspec/project.md`）：**数据是你的** / **账要算得准** / **记账不能变慢**。

### 锁定的版本组合

| Gradle | AGP | Kotlin | KSP | Compose BOM | Room | Hilt | JDK |
|---|---|---|---|---|---|---|---|
| 8.13 | 8.13.0 | 2.3.21 | 2.3.11 | 2026.06.00 | 2.8.4 | 2.57.1 | 17 |

SQLCipher `4.5.6@aar`、kotlinx-serialization-json `1.9.0`；
阶段 2 新增 `material-icons-core`（BOM 管理）、`navigation-compose:2.9.0`、
`lifecycle-viewmodel-compose:2.9.1`、`paging-common/compose:3.3.6`。
锁定依据、证据链与应急回退顺序见
`openspec/changes/archive/2026-11-12-scaffold-android-project/design.md` 第 1 节。

---

## 阶段 1 数据层

**5 张表**：`accounts` / `categories`（两级）/ `records`（冗余 `localDate` `yearMonth`
`rootCategoryId`）/ `balance_adjustments` / `trash`。

三条核心设计：

1. **余额是可重建缓存** —— `balance = initialBalance + 流水净变动 + 未撤销校准 delta`。
   所有写操作 `withTransaction { 写入 → rebuildAllBalances() }`，事务中途失败整体回滚。
   `LedgerEngine` 是唯一余额算法；生产由一条 SQL 把流水压成每账户一行再交给它。
2. **删除走 trash 表**，主表不含 `isDeleted`。恢复 = 插回 + 重算，与 insert 同路径；
   统计口径只需 `type IN ('EXPENSE','INCOME') AND excludeFromStats = 0`。
3. **校准独立成表**，不生成伪支出流水，因此不进明细、不进分类统计、重算时不会被重复扣。

数据库口令由 Android Keystore 的 AES-256/GCM 密钥加密保管，
`System.loadLibrary("sqlcipher")` 在 Application.onCreate 最早处加载。

**阶段 1 的最小 JSON 导出是明文**，仅供开发期防止表结构变动丢数据；
口令加密、SAF 文件选择器、CSV 属于阶段 5。请勿把导出文件放到公共目录。

**测试边界（如实说明）**：沙盒无 Android SDK / 模拟器，SQLCipher 的 native 库在 JVM 下
无法加载。因此纯 JVM 单测覆盖的是 `LedgerEngine` 这一唯一余额算法、分类合计、
统计口径与备份往返；**真实 SQLite 事务与加密库运行要等阶段 2 起真机验证**。
事务包裹与聚合 SQL 由两个静态检查脚本兜底。首次 CI 编译门禁全绿，
单测曾因测试自身写错净资产常数而失败，已修正（见 change 的 design.md）。详见
`openspec/changes/archive/2026-11-12-implement-room-ledger-core/design.md` 第 8 节。

---

## 阶段 2 交互层

**导航**：恰好 4 个 Tab（明细 / 资产 / 统计 / 设置）+ 中央 `+` FAB。
资产与统计放明确标注所属阶段的占位页，结构一次成型不返工。

**记一笔**：支出 / 收入 / 转账三 Tab、两级分类快选（点一级即可保存，
可选展开二级 chips）、内置 `BigDecimal` 计算器键盘、账户胶囊、按天调日期、
备注与「不计入收支」开关；编辑已有流水复用同一组件。
分类图标用 `colorHex` 彩色圆 + 中文首字，不做 30 个图标名的字符串映射。

**明细**：月度概览（支出 / 收入 / 结余 + 左右切月）、**Paging 3** 按日分组、
组合筛选（时间 + 账户 + 类型 + 一级 / 二级分类 + 金额区间 + 备注全文）、
点击行编辑、删除二次确认。

三条性能与精度约束：

1. `PagingData` 是 ViewModel 里**独立的 Flow**，MUST NOT 包进 `UiState`；
   `flatMapLatest` + `cachedIn(viewModelScope)` + 稳定 `itemKey`。
   写操作成功后 `RecordFilter.revision++` 重建 Pager 刷新列表。
2. **一条参数化 SQL** 覆盖全部组合筛选，`LEFT JOIN` 一次取回分类名与账户名；
   按日合计由 `GROUP BY localDate` 单独返回（每月最多 31 行），
   **不是**已加载分页数据求和，不会随滚动跳变。
3. 计算器全程 `BigDecimal`，禁止浮点；除法有限精度，结果经
   `setScale(2, HALF_UP)` 交 `Cents.fromYuan` 转整数分。

**回收站**：恢复走 `LedgerRepository.restoreRecord`（事务内插回 + 重算），
彻底删除只移除回收站记录。
**设置页**：临时明文 JSON 导出（写 `context.filesDir`，自校验可解析）、
账本健康自检、回收站入口。界面醒目标注「明文，勿外传」，
阶段 5 替换为 SAF 选择 + 口令加密导出。

**测试边界（如实说明）**：新增纯 JVM 单测覆盖计算器表达式优先级、除法舍入、
月份范围与筛选 revision；沙盒无 Android SDK / 模拟器，
**5 万条真机切月耗时、滚动掉帧、SQLCipher 真机打开数据库仍未实测**。
本阶段只验证分页 SQL 结构与依赖可编译，不把「编译通过」说成「性能达标」。

---

## 本地构建

```bash
./gradlew testDebugUnitTest       # 单元测试
./gradlew assembleDebug           # Debug APK：app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease         # Release APK（会跑 lintVital）
```

`gradle/wrapper/gradle-wrapper.jar` 已提交，Android Studio 可直接打开。
`app/debug.keystore` 已提交并固定引用，**各次编译签名一致，可覆盖安装不必卸载**。
该密钥等价于公开密钥，仅供个人自用，不要对外分发用它签名的包。

沙盒环境没有 Android SDK / Gradle / Java 编译器，
真正的编译验证走 GitHub Actions：`.github/workflows/build.yml`。
每次 push 到**任意分支**都会跑：五个静态检查 → 单元测试 → debug APK →
release（`lintVital`）→ 签名诊断 → 上传制品。

| 脚本 | 拦什么 |
|---|---|
| `scripts/check_manifest_privacy.py` | 误加 `INTERNET`、漏关自动备份、引入网络 SDK |
| `scripts/check_kotlin_strings.py` | Python 拼 Kotlin 时 `\n` 被转义成真实换行（犯过两次） |
| `scripts/check_docs_consistency.py` | 版本表 / targetSdk / 「已确认不做」清单与代码脱节 |
| `scripts/check_transaction_annotations.py` | 写方法漏包 `withTransaction`、出现破坏性迁移 |
| `scripts/check_stat_rules.py` | 余额聚合 SQL 或唯一统计口径被改歪 |

---

## 目录结构

```
offline-ledger/
├── openspec/
│   ├── AGENTS.md             AI 行为指引（新会话强制开场）
│   ├── project.md            项目铁律、技术栈、已确认不做、分阶段计划
│   ├── specs/                真理源：8 个模块
│   │   ├── money-precision/       金额分制、带符号余额、净资产
│   │   ├── ledger-transactions/   事务联动、余额重算、统计口径
│   │   ├── reconciliation/       对账锚点、尾差校准、金额反查
│   │   ├── category-stats/       两级分类、统计与下钻
│   │   ├── privacy-security/     无网络权限、加密、备份排除、应用锁
│   │   ├── backup-restore/       schemaVersion、导入重算
│   │   ├── reporting-ui/         4 Tab、低摩擦、万级数据性能
│   │   └── build-and-ci/         构建工具链、CI 验证、固定签名
│   └── changes/
│       └── archive/                      已归档（初始设计 / 阶段 0 / 阶段 1 / 阶段 2）
├── app/src/main/java/com/zwz/offlineledger/
│   ├── core/                 Cents / LedgerClock / LedgerCurrency
│   ├── data/model/           纯 JVM 数据模型
│   ├── data/local/           Entity / DAO / AppDatabase / Seed / Keystore
│   ├── data/di/DatabaseModule.kt
│   ├── data/repository/LedgerRepository.kt
│   ├── data/paging/          RecordFilter / RecordPagingSource
│   ├── engine/               LedgerEngine / CategoryTree / StatRules / Calculator
│   ├── backup/               BackupEnvelope / Payload / Exporter / Importer
│   └── ui/                   LedgerApp / theme / LedgerDi / components
│       ├── detail/            DetailScreen / DetailViewModel / FilterSheet
│       ├── record/            RecordEditorDialog / RecordEditorViewModel
│       ├── trash/             TrashScreen / TrashViewModel
│       ├── settings/          SettingsScreen（临时明文导出）
│       └── assets/ stats/     阶段 3 / 4 占位页
├── app/src/test/             纯 JVM 单测
├── scripts/                  五个 CI 静态检查
├── .github/workflows/build.yml
└── docs/设计规格书_v2.md     历史快照（非真理源，冲突时以 specs 为准）
```

`specs/` 写系统 **SHALL** 做什么；`changes/*/design.md` 写某次变更**为什么**这么做；
`project.md` 写长期稳定约束；本文件写架构速览。
**技术决策不写在 `docs/` 里**——历史记录写完即定型，独立技术文档必然腐化。

---

## 当前进行中的改动

**无**。阶段 0 / 1 / 2 均已按规范归档（delta 合并进 `specs/`、change 移入
`archive/`、逐模块校验通过）。归档阶段 2 时识别出两条「阶段限定 Requirement」
会成为规格债，已记入 `2026-11-12-build-record-entry-ui/design.md`，
要求阶段 3 用 MODIFIED 替换，避免 specs 出现互相矛盾的要求。

---

## 下一步

阶段 3：净资产看板、账户详情、余额调整记录、对账流程（阈值分流 / 对账锚点 /
金额反查 / 核对视图），并用 MODIFIED 替换阶段 2 遗留的两条阶段限定 Requirement。
之后依次是统计图表（4）、完整备份与设置（5）。
`release.yml`、正式签名 Secrets 与分发方案留到出包阶段单独提 change。

**每阶段结束必须能编译运行并跑一遍导出备份**，禁止一次性摊开全部页面。

---

## 工作流

本仓库遵循 [`wzzhuz/my-app-workflow`](https://github.com/wzzhuz/my-app-workflow) 的
OpenSpec 规范驱动流程：**需求先对齐，再写码；改完代码，同步更新规格。**
不依赖 OpenSpec CLI 或斜杠命令，任何 AI 工具都能读写同一份规范。
