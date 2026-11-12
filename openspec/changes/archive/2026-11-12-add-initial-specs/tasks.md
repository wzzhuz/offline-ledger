# 任务清单：add-initial-specs

> 本 change 的范围是**设计归档**，不含任何代码实现。
> 代码相关的任务由阶段 0～6 各自的 change 承担。

## 1. 阶段 0 对齐

- [x] 1.1 读取 `my-app-workflow` 的流程文档与 lifelog 的 openspec 落地结构
- [x] 1.2 产出五项对齐结论并停下等待确认
- [x] 1.3 回显目标仓库与本地源目录，获得使用者确认

## 2. 建立项目仓库

- [x] 2.1 新建 `wzzhuz/offline-ledger`（private）

## 3. 写 openspec 基线

- [x] 3.1 `openspec/AGENTS.md` —— 强制开场、目录含义、改动流程、归档定义
- [x] 3.2 `openspec/project.md` —— 三条铁律、技术栈、已确认不做、编码约定、分阶段计划
- [x] 3.3 `openspec/specs/money-precision/spec.md`
- [x] 3.4 `openspec/specs/ledger-transactions/spec.md`
- [x] 3.5 `openspec/specs/reconciliation/spec.md`
- [x] 3.6 `openspec/specs/category-stats/spec.md`
- [x] 3.7 `openspec/specs/privacy-security/spec.md`
- [x] 3.8 `openspec/specs/backup-restore/spec.md`
- [x] 3.9 `openspec/specs/reporting-ui/spec.md`

## 4. 写初始归档 change

- [x] 4.1 `proposal.md` —— 含原始场景与完整的「对齐结论」节
- [x] 4.2 `design.md` —— 含「考虑过的其他方案」表与取舍论证
- [x] 4.3 `tasks.md` —— 本文件
- [x] 4.4 `specs/<7 模块>/spec.md` —— 7 份 Delta（全部 ADDED）
- [x] 4.5 **把 delta 合并回 `openspec/specs/`**（归档不是只挪目录）
- [x] 4.6 移入 `openspec/changes/archive/2026-11-12-add-initial-specs/`

## 5. 快照与速览

- [x] 5.1 `docs/设计规格书_v2.md` —— 原文 + 「非真理源」头部声明
- [x] 5.2 `README.md` —— 30 秒架构速览、目录结构、当前状态、下一步

## 6. 推送与校验

- [x] 6.1 推送前回显目标仓库 + 本地源目录
- [x] 6.2 用参数化脚本逐文件推送（仓库名 / 本地路径不硬编码）
- [x] 6.3 推完用远端 Contents API 列根目录并逐个比对 sha
- [x] 6.4 校验仓库中无 token 明文、无 `.github/workflows/`
- [x] 6.5 清理本地凭据文件

## 后续（不在本 change 范围）

- [ ] 阶段 0 change：工程骨架 + 隐私基线 + 版本组合查证 + build.yml
- [ ] 阶段 1 change：Room 数据层 + 余额重算 + 最小 JSON 导出 + 单元测试
