# 离线账本（offline-ledger）

**纯本地、零网络权限、无广告的个人 Android 记账应用。** 知道自己的资产与支出构成，
数据不托管给任何第三方，也不依赖长期开机的服务器或 NAS。

> 状态：**设计已归档，代码未开始**。
> 本仓库当前只有 `openspec/` 规格与决策记录，没有 Kotlin 源码、没有 Gradle、没有 GitHub Actions。

---

## 30 秒架构速览

| 项 | 选择 |
|---|---|
| 语言 / UI | Kotlin + Jetpack Compose（Material 3） |
| 架构 | MVVM + Clean Architecture（UI → ViewModel → Repository → Room DAO） |
| 存储 | Room (SQLite) + SQLCipher，密钥由 Android Keystore 保管 |
| 异步 / DI | Coroutines + StateFlow / Hilt |
| 序列化 | kotlinx.serialization（备份 DTO 与 Entity 分离） |
| 分页 / 图表 | Paging 3 / 原生 Canvas 自绘 |
| 权限 | **不声明 `INTERNET`**，仅用 SAF 导入导出 |
| 最低版本 | Android 8.0（API 26）；targetSdk 跟随当年官方要求 |
| 默认本地化 | `Asia/Hong_Kong`、默认币种 `HKD`（v1 单币种，字段预留） |

三条铁律（详见 `openspec/project.md`）：**数据是你的** / **账要算得准** / **记账不能变慢**。

---

## 目录结构

```
offline-ledger/
├── openspec/
│   ├── AGENTS.md             AI 行为指引（新会话强制开场）
│   ├── project.md            项目铁律、技术栈、已确认不做
│   ├── specs/                真理源：系统「现在」应当是什么样
│   │   ├── money-precision/       金额分制、带符号余额、净资产
│   │   ├── ledger-transactions/   事务联动、余额重算、统计口径
│   │   ├── reconciliation/       对账锚点、尾差校准、金额反查
│   │   ├── category-stats/       两级分类、统计与下钻
│   │   ├── privacy-security/     无网络权限、加密、备份排除、应用锁
│   │   ├── backup-restore/       schemaVersion、导入重算
│   │   └── reporting-ui/         4 Tab、低摩擦、万级数据性能
│   └── changes/archive/      已归档的历史 change（含决策理由）
└── docs/
    └── 设计规格书_v2.md      历史快照（非真理源，冲突时以 specs 为准）
```

`specs/` 写系统 **SHALL** 做什么；`changes/archive/*/design.md` 写某次变更**为什么**这么做；
`project.md` 写长期稳定约束；本文件写架构速览。**技术决策不写在 `docs/` 里**——
历史记录写完即定型，独立技术文档必然腐化。

---

## 当前进行中的改动

无。初始设计已归档于 `openspec/changes/archive/2026-11-12-add-initial-specs/`。

---

## 下一步

按 `openspec/project.md` 的分阶段计划，从**阶段 0（工程骨架 + 隐私基线）**与
**阶段 1（Room 数据层 + 余额重算 + 最小 JSON 导出 + 单元测试）**提新 change。
AI 分阶段生成代码时，每阶段结束必须能编译运行并跑一遍导出备份。

编译 APK 走 GitHub Actions（沙盒无 Android SDK / Gradle / Java 编译器），
workflow 在实现阶段的 change 中建立。

---

## 工作流

本仓库遵循 [`wzzhuz/my-app-workflow`](https://github.com/wzzhuz/my-app-workflow) 的
OpenSpec 规范驱动流程：**需求先对齐，再写码；改完代码，同步更新规格。**
不依赖 OpenSpec CLI 或斜杠命令，任何 AI 工具都能读写同一份规范。
