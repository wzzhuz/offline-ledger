# 技术设计：add-initial-specs

## 现状

工作区不存在 `openspec/` 目录，也没有任何项目仓库。
设计成果只有一份 `纯本地记账App_规格书_v2.md`（聊天产物），
内容是「要做什么」的叙述性规格，不是 SHALL 级行为约定，也没有决策溯源。

按 `my-app-workflow` 的强制开场，这种状态下新会话会判定「未找到 openspec 项目」，
无法续上任何迭代。

## 方案

### 1. 把 v2 拆成 7 个 spec 模块

拆分依据照抄 `examples/生活手记.md` 的取舍：优先覆盖**容易改错、改了影响大、有隐性约束**三类，
其余（视觉细节、预置分类的完整清单、设置页每个开关）等实际要改时再补。

| 模块 | 覆盖什么 | 为什么必须单独成章 |
|---|---|---|
| `money-precision` | 整数分、带符号余额、净资产 = SUM(balance) | v1 公式会凭空虚高净资产，最容易算错钱 |
| `ledger-transactions` | 事务、余额可重建、统计口径、归档语义 | 长期一致性不可能只靠 `@Transaction` |
| `reconciliation` | 尾差校准、对账锚点、金额反查 | 直接对应使用者「差 1 分钱核算太费事」的真实场景 |
| `category-stats` | 两级分类、rootCategoryId、下钻 | 新增诉求，且有「不能增加记账摩擦」的隐性约束 |
| `privacy-security` | 无 INTERNET、allowBackup、加密、应用锁 | 这是整个项目存在的理由，必须可逐条核查 |
| `backup-restore` | schemaVersion、导入事务、导入后重算 | 「数据是你的」的可验证证据，且必须早于 UI 落地 |
| `reporting-ui` | 4 Tab、低摩擦、Paging、性能基线 | 10 年数据规模与「没动力」的痛点都在这里收口 |

7 个模块共 46 条 Requirement、55 个 GIVEN/WHEN/THEN 场景，每条 Requirement 均附「理由」。

### 2. delta 与 specs 由单一数据源渲染

初始 change 的 delta 全部是 `ADDED`，内容与 `specs/` 下的正式 spec 完全一致。
为避免「归档时只挪目录不合并 delta」以及两份文本日后漂移，
生成脚本把 Requirement 定义成一份数据结构，分别渲染出两种头部：

- `openspec/changes/archive/.../specs/<模块>/spec.md` → `# Delta for <模块>` + `## ADDED Requirements`
- `openspec/specs/<模块>/spec.md` → `# <模块> Specification` + `## Purpose` + `## Requirements`

正文逐字一致，归档动作因此天然可校验。

### 3. 三层文档分工，只有一层是真理源

| 层 | 位置 | 维护方式 |
|---|---|---|
| 行为约定（真理源） | `openspec/specs/` | 归档流程强制同步 |
| 决策理由（历史） | `openspec/changes/archive/*/design.md` | 写完即定型，**不维护** |
| 设计原文（快照） | `docs/设计规格书_v2.md` | **不维护**，头部显式标注冲突以 specs 为准 |

`docs/` 不写技术决策。`examples/生活手记.md` 记录过真实事故：
「文档写着不用 Room，而代码早就迁移到 Room」持续数个版本无人发现——
同一件事有两个地方可写，就没有任何机制保证同步。

### 4. 项目名

`offline-ledger`：直接对应「连 `INTERNET` 权限都不声明」这个核心卖点，
与 `lifelog` / `dayscounter` 等现有仓库的命名风格一致，不引入品牌感或理财平台感。

### 5. 推送方式

GitHub Contents API 逐文件写入，仓库名与本地源目录做成脚本参数（不硬编码）。
推送前 `echo` 目标仓库 + 本地源目录，推完用 Contents API 列远端根目录并比对 sha。
这对应 `踩坑清单.md` 的 4.6 / 4.7 / 4.5 三条：声称完成但产物不在、误推错误仓库、
本地副本落后导致整体覆盖。

## 考虑过的其他方案

| 方案 | 优点 | 为什么没选 |
|---|---|---|
| v2 原文直接丢仓库根目录当唯一文档 | 一步到位，零转换成本 | 叙述性规格没有 SHALL 级行为约定，也无法逐条验证；且会持续腐化 |
| 把设计塞进 README | 打开仓库就能看到 | README 只做 30 秒架构速览；塞满后没人看，且同样无法作为真理源 |
| 用 `/opsx:propose` 等斜杠命令建 change | 官方 OpenSpec 原生支持 | 元宝不支持斜杠命令，违反 `my-app-workflow` 的「工具无关」铁律 |
| 不建 change，直接写 `specs/` | 少 4 个文件 | 丢失「为什么否决伪支出校准 / 为什么弃用 Vico」等决策理由，半年后会被重新提一遍 |
| 顺手把编译 workflow 也推上 | 仓库看起来更完整 | 本轮只归档设计；且 fine-grained PAT 推 `.github/workflows/` 需独立 Workflows 权限位，会 403 |
| 建 public 仓库 | 便于分享 | 资产账本项目的设计文档含个人财务习惯与隐私取向，默认 private 更稳妥 |
| 现在就锁定 AGP / Kotlin / Compose BOM 版本号 | 规格看起来更「完整」 | 无本地编译环境，凭记忆写版本组合是 `踩坑清单.md` 1.2 明确记录过的坑；留到阶段 0 查证后锁定 |
| 引入 Hilt 之外的 DI 或干脆不用 DI | 对齐 `lifelog` 的极简取舍 | 本项目有加密 OpenHelper、Repository、备份序列化等多个构造依赖，收益大于成本 |

## 取舍

选择 **「7 模块 specs + 1 个已归档初始 change + 1 份不维护快照」**：

- **牺牲**：v2 原文的章节结构被打散，查某个表格不如原文顺手；49 条 Requirement 有维护成本
- **换取**：行为约定可逐条验证、决策理由不会蒸发、新会话能靠 `AGENTS.md` + `project.md` 续上

快照保留正是为了补回「查原文顺手」这一点，代价是必须接受它不维护、可能过时。

## 风险

**残余风险 1**：快照 `docs/设计规格书_v2.md` 与 specs 日后出现矛盾。
**缓解**：快照头部显式声明「非真理源，冲突以 `openspec/specs/` 为准」，
并在 `project.md` 的参考文档分工表里重复一次。

**残余风险 2**：46 条 Requirement 里有一部分（尤其 `reporting-ui`）暂无代码可验证，
变成纸面约定。
**缓解**：每条都写成可测 Scenario；实现阶段按 `tasks.md` 逐条对齐，
未实现的不在 `tasks.md` 里打勾；性能基线用 5 万条假数据实测。

**残余风险 3**：fine-grained PAT 可能无权新建仓库。
**缓解**：建仓失败时如实报告，不静默重试、不假报成功；
由使用者网页先建空仓或换凭据后，再用同一套参数化脚本推送。

**残余风险 4**：token 已出现在对话记录里。
**缓解**：本地凭据文件用完即清理；建议使用者归档完成后吊销该 token。

## 验证

- [x] 每个 Requirement 至少一个 GIVEN/WHEN/THEN 场景 + 「理由」
- [x] delta 与 specs 正文由单一数据源渲染，逐字一致
- [x] `project.md` 含三条铁律、已确认不做、分阶段计划、文档分工
- [x] `AGENTS.md` 含强制开场与「归档 = 合并 delta + 移入 archive + 校验」
- [x] 生成物中无 token 明文、无 `.github/workflows/`
- [ ] 推送后用远端 Contents API 逐个校验文件与 sha
