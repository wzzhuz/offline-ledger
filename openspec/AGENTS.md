# OpenSpec 使用指引

本目录是需求与实现的唯一真理源。**改动需求先改这里，再动代码。**

给 AI 助手的说明：本仓库使用 OpenSpec 规范驱动开发，遵循
[`wzzhuz/my-app-workflow`](https://github.com/wzzhuz/my-app-workflow)。
以下流程用任何 AI 工具都能执行，不依赖特定的斜杠命令。

---

## 每次新会话的强制开场

**在动手做任何事之前**，先实际读取 `openspec/AGENTS.md` 与 `openspec/project.md`
（不许凭印象），列出 `openspec/specs/` 下所有模块与 `openspec/changes/` 下所有进行中的改动，
然后用 3–5 句话复述：

> 当前项目：**离线账本**
> 它解决什么问题：**一句话**
> 当前状态：**版本 / 最近一次改动 / 是否已有可用产物**
> 进行中的改动：**change 列表，或「无」**
> 有什么是我需要你知道的？

**复述完停下来等使用者确认**，不要自作主张继续。
若找不到 `openspec/`，明确告知「未找到 openspec 项目」，不凭猜测继续。

开工前另读 `my-app-workflow/踩坑清单.md`（若工作区存在）。

---

## 目录含义

```
openspec/
├── project.md              项目级约定，跨所有 change 生效
├── specs/                  真理源：系统「现在」是什么样
│   ├── money-precision/        金额分制、带符号余额、净资产
│   ├── ledger-transactions/    事务联动、余额重算、统计口径
│   ├── reconciliation/         对账锚点、尾差校准、金额反查
│   ├── category-stats/         两级分类、统计与下钻
│   ├── privacy-security/       无网络权限、加密、备份排除、应用锁
│   ├── backup-restore/         schemaVersion、导入重算
│   ├── reporting-ui/           4 Tab、低摩擦、万级数据性能
│   └── build-and-ci/           构建工具链、CI 验证、固定签名、静态检查
├── changes/                进行中的提案，每个 change 一个文件夹
└── changes/archive/        已归档的历史 change
```

**核心区分**：`specs/` 描述当前行为；`changes/` 描述将要发生的变动。
`docs/设计规格书_v2.md` 是历史快照，冲突时**以 specs 为准**。

---

## 改动流程

### 阶段 0：对话对齐（硬门禁）

在对话里完成，不建目录、不写文件。产出**五项对齐结论**：
① 要解决什么问题 ② 改什么 / 不改什么 ③ 关键约束 ④ 验收标准（可测）⑤ 被否决的方案及原因。
缺哪项就问哪项，不许猜测填充。**输出后停下来等确认，不许在此之前创建任何 change 文件。**

### 1. 判断要不要建 change

**需要建**：新功能、破坏性变更（数据结构 / 文件格式 / 包名 / 签名）、架构调整、改变系统行为的优化。
**不需要建**：修 bug、拼写格式修正、非破坏性依赖升级、编译 / CI 配置调整。
不确定的默认建 change。**判定结果先告知并等确认**，不许默默建 change 或直接动代码。

### 2. 创建 change

在 `openspec/changes/<change-id>/` 下一次性建四件套：
`proposal.md`（含「对齐结论」节，是上下文入口）、`design.md`（含「考虑过的其他方案」表）、
`tasks.md`（带 checkbox）、`specs/<模块>/spec.md`（Delta）。
change-id 用动词开头的小写短横线命名。**写完停下来等确认**，然后推送到远端仓库。

### 3. Delta spec 格式

增量描述「变了什么」，不重写整个 spec：

```markdown
# Delta for <模块名>

## ADDED Requirements
### Requirement: <新能力>
系统 SHALL <做什么>。

#### Scenario: <场景名>
- **GIVEN** <前置条件>
- **WHEN** <触发动作>
- **THEN** <预期结果>

**理由**：<为什么这么定>

## MODIFIED Requirements
### Requirement: <被修改的能力>
（写修改后的完整要求文本，不要只写差异行）

## REMOVED Requirements
### Requirement: <旧能力>
（说明删除原因）
```

**格式硬性要求**：标题用 `### Requirement:`；需求文本用 SHALL / MUST / SHOULD / MAY（RFC 2119）；
每个 Requirement 至少一个 `#### Scenario:`；场景用 GIVEN / WHEN / THEN 且可测；
关键决策附「**理由**」，让半年后知道为什么这么定。

### 4. 实施

按 `tasks.md` 逐项完成并勾选，被打断可从断点继续。
**改完代码必须同步 spec**，否则规格腐烂。

### 5. 归档

归档 = **三件事**，缺一不可：

1. 把 delta **合并回** `openspec/specs/`（只挪目录不合并是最常见的流程失误）
2. 把 change 文件夹移到 `openspec/changes/archive/<日期>-<change-id>/`
3. 校验 specs 已反映新行为

---

## 给 AI 助手的行为准则

1. 动手前先读 `project.md` 和相关 spec，不要凭印象改代码
2. 需求有歧义就问，不要自行假设后实现
3. 不跳过阶段 0、不跳过 proposal 直接实现新功能
4. 与 `project.md` 三条铁律或「已确认不做」冲突时**立刻指出**，交使用者决策，不默默绕过
5. **推送前回显目标仓库与本地源目录**，推完用 API 列远端根目录校验
6. 沙盒无 Android SDK / Gradle / Java 编译器，**不要尝试本地编译 APK**；
   正确路径是推源码到 GitHub + Actions 在线编译
7. 本项目的三条铁律（数据是你的 / 账要算得准 / 记账不能变慢）**任何改动不得违反**
