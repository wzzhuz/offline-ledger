# 把离线账本的设计按 OpenSpec 结构归档为项目基线

> **本文件是 change 的上下文入口。**
> 新会话只读这一份文件，就应当能还原本次改动的全貌，无需重新讨论。

## 为什么改

**背景（使用者原始场景）**：

- 市面记账软件（钱迹 / 随手记 / 鲨鱼记账等）数据存在厂商服务器，部分功能收费；
  「**资产数据是高度敏感数据，不信任第三方**」
- Firefly III 没有官方安卓版本，且需要服务器或 NAS 长期开机；
  「**定时开机再关机，不方便随手记，时间长了就没动力了**」
- 在 Firefly III 实际使用中「**和实际余额比有 1 分钱的差额，但是因为账单太多，
  要核算找到这笔支出太费事了，代价也大。最后直接记了一笔虚拟支出，让余额保持一致**」
- 追问了两个设计问题：10 年数据量会不会拖慢页面、多级分类怎么统计

**问题**：规格书 v2 已经定稿，但只存在于聊天产物里。隔几周回来做阶段 0 时，
AI 没有跨会话记忆，会重新猜测「为什么负债余额存负数」「为什么校准不能记一笔支出」，
把已经否决的方案重新提一遍。聊天记录会丢，规格不会。

同时，工作区里没有 `openspec/` 目录，按 `my-app-workflow` 的强制开场，
下一次会话会直接判定「未找到 openspec 项目」而无法续上。

## 做什么

1. 新建独立项目仓库 `wzzhuz/offline-ledger`（private）
2. 建立 `openspec/`：`AGENTS.md`、`project.md`、7 个 `specs/` 模块
3. 建立初始归档 change：`archive/2026-11-12-add-initial-specs/` 四件套，
   把 v2 的关键决策理由写进 `design.md`
4. 把 v2 规格书原文作为**历史快照**放入 `docs/`，显式标注不维护、冲突以 specs 为准
5. 写一份 30 秒架构速览 `README.md`
6. 推送后用远端 API 校验文件列表

## 不做什么

- ❌ 不写任何 Kotlin 源码、不建 Gradle 工程、不建 GitHub Actions workflow
- ❌ 不编译 APK（沙盒无 Android SDK / Gradle / Java 编译器）
- ❌ 不改 `wzzhuz/my-app-workflow` 仓库（它自身的 `add-alignment-gate` 变更不在本轮范围）
- ❌ 不引入 OpenSpec CLI 或斜杠命令
- ❌ 不把 v2 原文当作持续维护的真理源
- ❌ 不在本轮确定 AGP / Kotlin / Compose BOM 的具体版本号（留到阶段 0 查证后锁定）
- ❌ 不触碰 `已确认不做` 清单里的 v1.1 功能

## 对齐结论

> 阶段 0 在对话里产出，五项齐全才进入写作。
> 写进这里是为了：下次会话不必重新讨论。

1. **要解决什么问题**
   把已定稿的 v2 设计按 OpenSpec 结构落盘到独立 GitHub 仓库，
   让隔几周回来的会话有唯一真理源，不再靠聊天记录还原「为什么这么定」；同时把项目仓库建起来。

2. **范围边界**
   - 改：新建仓库 + `README.md` + `openspec/`（AGENTS、project、7 个 specs 模块、
     一个已归档的初始 change 四件套）+ 一份 v2 原文快照
   - 不改：不写 Kotlin 源码、不建 Gradle / Actions、不改 `my-app-workflow`、不引入 CLI
   - 「归档设计」按「specs 行为约定为真理源 + 归档 change 保留决策理由 +
      docs 留不维护的历史快照」理解，而非只传一份 Markdown

3. **关键约束**
   工具无关（不依赖斜杠命令）；spec 用 SHALL/MUST + GIVEN/WHEN/THEN + 「理由」；
   三条铁律写进 `project.md`；**归档 = delta 合并回 specs + change 移入 archive + 校验**，
   不能只挪目录；推送前回显目标仓库、推完用 API 校验远端；
   沙盒无 Android SDK，本轮不编译也不假装编译。

4. **验收标准**（可测）
   - **GIVEN** 一段新会话 **WHEN** 只读 `AGENTS.md` + `project.md`
     **THEN** 能复述项目状态并停下等待确认
   - **GIVEN** 任一 `spec.md` **WHEN** 逐条审阅
     **THEN** 每个 Requirement 至少有一个可测 Scenario 且附「理由」
   - **GIVEN** 初始归档 change **WHEN** 检查
     **THEN** 四件套齐全，且 delta 已原样合并进 `specs/`
   - **GIVEN** 推送完成 **WHEN** 用 Contents API 列远端根目录
     **THEN** 文件逐个存在且 sha 与本地一致
   - **GIVEN** 仓库内容 **WHEN** 检查
     **THEN** 无 token 明文、无 `.github/workflows/`（避开 fine-grained token 的 403）

5. **被否决的方案**
   一句话结论：**所有「单一大文档 / README 承载 / CLI 化 / 跳过 change / 顺手推 workflow」
   的方案均被否决**，采用「specs + 归档 change + 不维护快照」的分层结构。
   完整表格见 `design.md`「考虑过的其他方案」。

## 影响范围

**触及的 spec 模块**：7 个模块全部为初始新增（`ADDED`），无 `MODIFIED` / `REMOVED`。

**是否需要重新安装包**：否（本轮无代码、无 APK）

**是否会丢数据**：否（尚无用户数据）

**行为变化**：项目从「聊天里的设计」变为「仓库里的规格基线」；
后续所有变更都必须先对齐、再写 change、再实现。

## 与项目铁律是否冲突

对照 `openspec/project.md` 检查：

- [x] 不违反三条铁律（本轮只是把三条铁律写下来）
- [x] 不涉及「已确认不做」清单中的功能
- [x] 无冲突

**补充说明**：本轮刻意不建 `.github/workflows/`，
既因为本轮不含编译需求，也因为 fine-grained PAT 推 workflow 需要独立的 Workflows 权限位。
这不是绕过约束，而是把 workflow 留给阶段 0 的 change 单独处理。
