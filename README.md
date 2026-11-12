# 离线账本（offline-ledger）

**纯本地、零网络权限、无广告的个人 Android 记账应用。** 知道自己的资产与支出构成，
数据不托管给任何第三方，也不依赖长期开机的服务器或 NAS。

> 状态：**阶段 0 工程骨架已推送，CI 验证结果见 Actions**。
> 目前只有占位页，没有记账功能；Room 数据层属于阶段 1。
> 本仓库遵循 OpenSpec 规范驱动开发，规格是唯一真理源。

---

## 30 秒架构速览

| 项 | 选择 |
|---|---|
| 语言 / UI | Kotlin + Jetpack Compose（Material 3） |
| 架构 | MVVM + Clean Architecture（UI → ViewModel → Repository → Room DAO） |
| 存储 | Room (SQLite) + SQLCipher（阶段 1 接入） |
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
`openspec/changes/scaffold-android-project/design.md` 第 1 节。

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
每次 push 到**任意分支**都会跑：三个静态检查 → 单元测试 → debug APK →
release（`lintVital`）→ 签名诊断 → 上传制品。
首轮实测：三个编译门禁全部通过；诊断步骤曾因 `grep -q && echo` 的返回码
误判整条流水线，已修复（见 `scaffold-android-project/design.md`）。

三个检查脚本：

| 脚本 | 拦什么 |
|---|---|
| `scripts/check_manifest_privacy.py` | 误加 `INTERNET`、漏关自动备份、备份规则漏排除数据库、引入网络 SDK |
| `scripts/check_kotlin_strings.py` | Python 拼 Kotlin 时 `\n` 被转义成真实换行（犯过两次） |
| `scripts/check_docs_consistency.py` | 版本表 / targetSdk / 「已确认不做」清单与代码脱节 |

---

## 目录结构

```
offline-ledger/
├── openspec/
│   ├── AGENTS.md             AI 行为指引（新会话强制开场）
│   ├── project.md            项目铁律、技术栈、已确认不做、分阶段计划
│   ├── specs/                真理源：系统「现在」应当是什么样（7 模块）
│   │   ├── money-precision/       金额分制、带符号余额、净资产
│   │   ├── ledger-transactions/   事务联动、余额重算、统计口径
│   │   ├── reconciliation/       对账锚点、尾差校准、金额反查
│   │   ├── category-stats/       两级分类、统计与下钻
│   │   ├── privacy-security/     无网络权限、加密、备份排除、应用锁
│   │   ├── backup-restore/       schemaVersion、导入重算
│   │   └── reporting-ui/         4 Tab、低摩擦、万级数据性能
│   └── changes/
│       ├── scaffold-android-project/   进行中：阶段 0 工程骨架（含 build-and-ci delta）
│       └── archive/                    已归档的历史 change
├── app/src/main/java/com/zwz/offlineledger/
│   ├── OfflineLedgerApp.kt / MainActivity.kt   最小闭环占位页
│   ├── core/money/Cents.kt           Long 分 + BigDecimal 输入/展示
│   ├── core/time/LedgerClock.kt       Asia/Hong_Kong → localDate / yearMonth
│   ├── core/currency/LedgerCurrency.kt
│   └── backup/dto/BackupEnvelope.kt  备份信封头部 + schemaVersion=1
├── app/src/test/             Cents / LedgerClock / BackupEnvelope 单测
├── scripts/                  三个 CI 静态检查
├── .github/workflows/build.yml
└── docs/设计规格书_v2.md     历史快照（非真理源，冲突时以 specs 为准）
```

`specs/` 写系统 **SHALL** 做什么；`changes/*/design.md` 写某次变更**为什么**这么做；
`project.md` 写长期稳定约束；本文件写架构速览。
**技术决策不写在 `docs/` 里**——历史记录写完即定型，独立技术文档必然腐化。

---

## 当前进行中的改动

`openspec/changes/scaffold-android-project/`（阶段 0，待归档）。
新增第 8 个 spec 模块 `build-and-ci`，归档时合并到 `openspec/specs/`。

---

## 下一步

按 `openspec/project.md` 的分阶段计划，阶段 1 是
**Room 全部 Entity / DAO / Callback / 索引 / Migration + Repository 事务 +
余额重算与自检 + 两级分类解析 + 最小 JSON 导出 + 单元测试**。
AI 分阶段生成代码时，每阶段结束必须能编译运行并跑一遍导出备份。

`release.yml`、正式签名 Secrets 与分发方案留到出包阶段单独提 change。

---

## 工作流

本仓库遵循 [`wzzhuz/my-app-workflow`](https://github.com/wzzhuz/my-app-workflow) 的
OpenSpec 规范驱动流程：**需求先对齐，再写码；改完代码，同步更新规格。**
不依赖 OpenSpec CLI 或斜杠命令，任何 AI 工具都能读写同一份规范。
