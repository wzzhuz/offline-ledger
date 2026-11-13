# 任务清单：add-multi-currency-basics

## 1. 前置：闸门、口径与归档状态

- [x] 1.1 强制开场复述、五项对齐结论、分级判定并停下等确认
- [x] 1.2 使用者确认「按推荐来」（拆批 / 严格不混算 / 真机继续推迟）
- [x] 1.3 **使用者澄清隐私观**：导出明文可接受，不可接受的是数据存厂商服务器
- [x] 1.4 **使用者质疑迁移**：design.md 第 0 节正面回答并修正过重风险措辞
- [x] 1.5 阶段 6b 已归档（13 条 ADDED 合并，基线 162 Req / 264 Scenario）
- [x] 1.6 闸门：6c 拆 6c-1 / 6c-2；放行多币种基础；
  小组件、通知栏磁贴、跨币种混算移永久不做
- [x] 1.7 铁律 1 补隐私边界；CSV 决策理由改写（结论保留）

## 2. change 四件套

- [x] 2.1 `proposal.md` —— 原始场景、隐私口径、完整对齐结论
- [x] 2.2 `design.md` —— 迁移答疑 / 字段偏差 / A′ 语义 / 明文论据修正 /
  真机累积清单 / 否决方案表
- [x] 2.3 `tasks.md` —— 本文件
- [x] 2.4 7 份 delta：ADDED + **2 份 MODIFIED 完整替换文本**
- [ ] 2.5 推送 change 文件并用远端 API 校验

## 3. 数据层

- [ ] 3.1 `RecordEntity` / `TrashEntryEntity` 加 `currencyCode`
- [ ] 3.2 `Migrations.kt`：`MIGRATION_3_4` 两条 ALTER（带 DEFAULT 'HKD'），
  `ALL` 扩到三段
- [ ] 3.3 `AppDatabase version = 4`
- [ ] 3.4 `BackupSchema.CURRENT = 4`；`BackupRecord` 加带默认值币种
- [ ] 3.5 `LedgerMappers` 补齐 4 处 + trash 往返的 `currencyCode`

## 4. 写入侧币种继承

- [ ] 4.1 `withCurrency(...)` 统一补齐：从账户 / 原流水继承
- [ ] 4.2 `insertRecord` / `updateRecord` / `recordFromTemplate` /
  `recordSettlement` / `importBackup` 走统一路径
- [ ] 4.3 转账同币种校验：不一致 MUST 拒绝
- [ ] 4.4 `aggregateAccountDeltas` 与余额重算 **保持不过滤币种**

## 5. 查询与引擎

- [ ] 5.1 `RecordDao` 新增 12 条 `...ForCurrency` 查询，
  既有 12 条 SQL 一行不动
- [ ] 5.2 `check_stat_rules.py` marker 12 → 24
- [ ] 5.3 `check_transaction_annotations.py` 扩到 `version = 4` / 三段迁移

## 6. UI

- [ ] 6.1 `AssetsViewModel` 按币种分组小计；**移除单一净资产总数**
- [ ] 6.2 `AssetsScreen` 分组列表 + 每行币种标签
- [ ] 6.3 `StatsViewModel` 加币种切换，全程走 ForCurrency 查询
- [ ] 6.4 `StatsScreen` 顶部币种 chips + 口径标注
- [ ] 6.5 记一笔显示账户币种；跨币种转账错误提示
- [ ] 6.6 明细页标注「全部币种」（6c-2 再收口一致性）

## 7. 测试与文档

- [ ] 7.1 `CurrencyInheritTest` / `BackupV3CompatTest`
- [ ] 7.2 `MigrationContractTest` 扩 3→4 契约
- [ ] 7.3 `README.md` / `project.md` 状态同步
- [ ] 7.4 本地六个检查脚本全 PASS
- [ ] 7.5 单次 commit 推送，查 Actions 确认触发并全绿
- [ ] 7.6 远端逐文件比对 blob sha

## 8. 收尾

- [ ] 8.1 同步 `design.md` / `tasks.md` 实测结果
- [ ] 8.2 使用者确认后归档（ADDED 合并 + 2 MODIFIED 按标题替换 + 移入 archive）

## 明确留到后续（不预先打勾）

> **使用者决定：真机实测整体推迟到完成度更高后统一装机体验。**
> 以下各项至今**全部未验证**；风险口径已修正为「未验证」，不是「升级会丢数据」。

- Room / SQLCipher 在真机打开加密数据库（全新安装走 `onCreate`）
- 三段 Migration 在真机执行（按当前用法首次装机不触发）
- R8 包启动、BiometricPrompt、SAF、5 万条切月耗时
- 模板 / 周期账 / 快捷方式 / 报销 / 预算 / 净支出手感
- 6c-1 新增：币种切换、分组小计、同币种转账提示
- **6c-2**：CSV 导出（不再需要隐私评审，但 MUST 显式决策）、
  账单导入、明细页与统计页币种口径一致性收尾
- **永久不做候选**：跨币种混算与汇率折算、小组件、通知栏磁贴

> 归档需使用者明确指令，本轮不擅自归档。
