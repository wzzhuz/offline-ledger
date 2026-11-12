# 任务清单：implement-assets-and-reconciliation

## 1. 阶段 0 对齐与前置归档

- [x] 1.1 读取 `AGENTS.md` / `project.md` 与 `money-precision` / `reconciliation` /
  `money-precision` / `backup-restore` spec，核查 `AccountEntity` 与
  `BackupAccount` 已预留锚点字段，确认**无需 Migration**
- [x] 1.2 强制开场复述、产出五项对齐结论并停下等待确认
- [x] 1.3 判定「需要建 change」并告知，等确认
- [x] 1.4 使用者确认「按推荐来」：账户管理一步到位 + 阈值常量化
- [x] 1.5 使用者说明 token 在项目完成前不吊销，本轮起不再反复提醒
- [x] 1.6 归档阶段 2：15 条 ADDED 合并进 `specs/`、change 移入
  `archive/2026-11-12-build-record-entry-ui/`、逐模块校验通过
- [x] 1.7 归档阶段 2 时识别的两条「阶段限定 Requirement」规格债纳入本 change

## 2. 写 change 四件套

- [x] 2.1 `proposal.md` —— 原始场景与完整「对齐结论」节
- [x] 2.2 `design.md` —— 零 Migration 论证 / 对账引擎 / 事务 / 两条 MODIFIED / 回退顺序 / 否决方案表
- [x] 2.3 `tasks.md` —— 本文件
- [x] 2.4 `specs/reconciliation/spec.md` —— ADDED 6 条 + **MODIFIED「阶段边界明确」**
- [x] 2.5 `specs/money-precision/spec.md` —— ADDED 2 条
- [x] 2.6 `specs/ledger-transactions/spec.md` —— ADDED 3 条
- [x] 2.7 `specs/reporting-ui/spec.md` —— **MODIFIED「四 Tab 骨架一次成型」**
- [x] 2.8 `specs/backup-restore/spec.md` —— ADDED 1 条
- [x] 2.9 `specs/build-and-ci/spec.md` —— ADDED 2 条
- [x] 2.10 推送 change 文件到远端并用 API 校验

## 3. 对账引擎与常量（纯 JVM）

- [x] 3.1 `engine/Reconciliation.kt`：差额 / 尾差 / 可用额度 / 展示聚合值 /
  三种金额反查区间 / 疑似标记位 / 三个常量
- [x] 3.2 `engine/ReconciliationTest.kt`：边界 100 与 101 分、负差额、
  展示值不被减法污染、三种模式 clamp、位解码

## 4. DAO 追加（零新增表、零 Migration）

- [x] 4.1 `AccountDao`：Flow 观察 / 总资产 / 总负债 / 锚点更新 / 账户属性更新 / 归档 / 关联流水计数
- [x] 4.2 `BalanceAdjustmentDao`：`byAccountId` / `byId`
- [x] 4.3 `RecordDao`：`ReviewRow` POJO + `accountRecords` / `reviewRows` / `countExactAmount`
- [x] 4.4 `LedgerRepository`：`reconcile` / `createAccount` / `updateAccountMeta` / `archiveAccount`
  四个方法全部 `withTransaction`
- [x] 4.5 `scripts/check_transaction_annotations.py` 的 `WRITE_METHODS` 从 9 扩到 13

## 5. 资产页

- [x] 5.1 `ui/assets/AssetsViewModel.kt` + `AssetsUiState`
- [x] 5.2 `ui/assets/AssetsScreen.kt`：净资产看板 / 总资产 / 总负债 / 分组 / 可用额度 / 新增账户
- [x] 5.3 `ui/assets/AccountDetailDialog.kt`：改名 / 计入净资产 / 归档 / 专属流水 /
  余额调整记录折叠 + 撤销
- [x] 5.4 `ui/LedgerApp.kt` 把 assets 占位换成 `AssetsScreen()`，4 Tab 结构不动

## 6. 对账与核对视图

- [x] 6.1 `ui/reconcile/ReconcileViewModel.kt`：账面 / 实际 / 差额 / 阈值分流 / 备注必填
- [x] 6.2 `ui/reconcile/ReconcileDialog.kt`：一键尾差校准 / 先核对 / 仍要校准
- [x] 6.3 `ui/reconcile/ReviewViewModel.kt`：锚点后切片 / 7·30·90 天放宽 /
  三种金额反查 / 疑似标记补算 / 只看疑似项
- [x] 6.4 `ui/reconcile/ReviewDialog.kt`：范围切换 / 反查输入 / 标记 chips / 无匹配提示

## 7. 测试与静态检查

- [x] 7.1 对账引擎单测全部 PASS
- [x] 7.2 备份锚点往返单测
- [x] 7.3 本地五个检查脚本全部 PASS（含扩展后的事务清单）
- [x] 7.4 单次 commit 推送后用 GitHub API 查 Actions 列表，确认**确实触发**
- [ ] 7.5 轮询 Actions；失败按 `design.md` 第 7 节回退顺序单次修复后重推
- [ ] 7.6 确认 job 结论为 success、制品产出

## 8. 同步与收尾

- [x] 8.1 更新 `README.md` 阶段状态、目录结构、资产与对账说明、测试边界
- [x] 8.2 推完用远端 API 逐文件比对 sha
- [x] 8.3 token 按使用者说明保留至项目完成，仅清理临时诊断产物
- [ ] 8.4 使用者确认后归档本 change（2 份 MODIFIED 按标题替换 + ADDED 合并）

> 7.5 / 7.6（CI 轮询与结论）与 8.4（归档）未完成，不预先打勾。
> 归档需使用者明确指令，本轮不擅自归档。
