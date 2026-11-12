# 离线账本（offline-ledger）

**纯本地、零网络权限、无广告的个人 Android 记账应用。** 知道自己的资产与支出构成，
数据不托管给任何第三方，也不依赖长期开机的服务器或 NAS。

> 状态：**阶段 0 已完成并归档；阶段 1 数据层已推送，CI 验证结果见 Actions**。
> 目前只有占位页，没有记账 UI；Room 数据层与加密库已落地。
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
| 分页 / 图表 | Paging 3 / 原生 Canvas 自绘（各自阶段引入） |
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

SQLCipher `4.5.6@aar`、kotlinx-serialization-json `1.9.0`。
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
`openspec/changes/implement-room-ledger-core/design.md` 第 8 节。

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
│       ├── implement-room-ledger-core/   进行中：阶段 1 数据层
│       └── archive/                      已归档（初始设计 / 阶段 0 骨架）
├── app/src/main/java/com/zwz/offlineledger/
│   ├── core/                 Cents / LedgerClock / LedgerCurrency
│   ├── data/model/           纯 JVM 数据模型
│   ├── data/local/           Entity / DAO / AppDatabase / Seed / Keystore
│   ├── data/di/DatabaseModule.kt
│   ├── data/repository/LedgerRepository.kt
│   ├── engine/               LedgerEngine / CategoryTree / StatRules
│   └── backup/               BackupEnvelope / Payload / Exporter / Importer
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

`openspec/changes/implement-room-ledger-core/`（阶段 1，待归档）。
阶段 0 已按规范归档：delta 合并进 `specs/`、change 移入
`archive/2026-11-12-scaffold-android-project/`、逐模块校验通过。

---

## 下一步

阶段 2：计算器键盘「记一笔」、两级分类快选、明细首页 + Paging 3、搜索筛选、回收站恢复。
之后依次是资产对账（3）、统计图表（4）、完整备份与设置（5）。
`release.yml`、正式签名 Secrets 与分发方案留到出包阶段单独提 change。

**每阶段结束必须能编译运行并跑一遍导出备份**，禁止一次性摊开全部页面。

---

## 工作流

本仓库遵循 [`wzzhuz/my-app-workflow`](https://github.com/wzzhuz/my-app-workflow) 的
OpenSpec 规范驱动流程：**需求先对齐，再写码；改完代码，同步更新规格。**
不依赖 OpenSpec CLI 或斜杠命令，任何 AI 工具都能读写同一份规范。
