# 任务清单：add-csv-export-and-import

## 1. 前置：闸门与新约束

- [x] 1.1 强制开场复述、五项对齐结论、分级判定并停下等确认
- [x] 1.2 使用者确认「按推荐来」（A + A′ + A）
- [x] 1.3 **使用者重申**：稳定版前不考虑升级场景 / 只用测试数据 /
  数据结构不需要考虑迁移，除非明确说明需要考虑
- [x] 1.4 6c-1 已归档（10 ADDED + 2 MODIFIED，基线 172 Req / 284 Scenario）
- [x] 1.5 `project.md`「稳定版前的版本策略」已落档（条件性暂缓 + 恢复条件）
- [x] 1.6 闸门放行 6c-2 三项；分批说明与计划表同步

## 2. change 四件套

- [x] 2.1 `proposal.md` —— 原始场景、隐私口径、新约束、完整对齐结论
- [x] 2.2 `design.md` —— CSV 格式 / 导入事务 / 口径一致性 /
  门禁降级 / 1 MODIFIED / 真机清单 / 否决方案表
- [x] 2.3 `tasks.md` —— 本文件
- [x] 2.4 7 份 delta：ADDED + **1 份 MODIFIED 完整替换文本**
- [x] 2.5 推送 change 文件并用远端 API 校验

## 3. CSV 编解码（纯 JVM）

- [x] 3.1 `CsvRow` / `CsvParseResult` / `CsvCodec` 新建
- [x] 3.2 `export()` 含 UTF-8 BOM、8 列表头、两位小数、引号转义
- [x] 3.3 `parseImport()` 支持 CSV/TSV、跳过 BOM 与空行、全量校验报行号
- [x] 3.4 金额走 `Cents.fromYuan`，禁止浮点参与

## 4. 设置页：导出与导入入口

- [x] 4.1 `SettingsScreen`：新增「导出 CSV」「导入账单」两个 SAF launcher
- [x] 4.2 删除「v1 不提供 CSV 导出（明文文件）」旧文案，改隐私边界说明
- [x] 4.3 `SettingsViewModel`：`exportCsvTo` / `readCsvPlan` /
  `confirmCsvImport` 三段，busy / message / error 复用
- [x] 4.4 导入前展示「将写入 N 条」常规确认 Dialog

## 5. 导入事务与去重

- [x] 5.1 `RecordDao` 加 `countDuplicate(...)` 参数化查询（不加字段）
- [x] 5.2 `LedgerRepository.importRecords(rows)`：
  单事务批量 insert + `withResolvedRoot`（含币种继承）+ 末尾重算
- [x] 5.3 `WRITE_METHODS` 30 → 31 并同步静态检查
- [x] 5.4 校验失败整体回滚；回报「写入 N / 跳过 M 重复」

## 6. 明细页与统计页币种口径一致性

- [x] 6.1 `pagedFiltered` / `dailyTotals` 加可选 `currencyCode` 参数
- [x] 6.2 `RecordFilter` 加字段并同步 `activeCount()` 清单
- [x] 6.3 `RecordPagingSource` 传参
- [x] 6.4 `FilterSheet` 加币种选择（默认「全部币种」）
- [x] 6.5 `DetailScreen` 概览显示当前筛选币种

## 7. 门禁降级与文档

- [x] 7.1 `check_transaction_annotations.py`：version / ALL / DEFAULT
  改 `warnings`，不 `return 1`；写方法事务与破坏性迁移禁令保留
- [x] 7.2 `README.md` 状态同步
- [x] 7.3 单测：`CsvCodecTest` / `CsvImportPlanTest` /
  `RecordFilterCurrencyTest` / `MigrationGateRelaxedTest`
- [x] 7.4 本地六个检查脚本全 PASS
- [x] 7.5 单次 commit 推送，查 Actions 确认触发并全绿
- [x] 7.6 远端逐文件比对 blob sha

## 8. 收尾

- [x] 8.1 同步 `design.md` / `tasks.md` 实测结果
- [ ] 8.2 使用者确认后归档（ADDED 合并 + 1 MODIFIED 按标题替换 + 移入 archive）

## 明确留到后续（不预先打勾）

> **使用者决定：真机实测整体推迟到完成度更高后统一装机体验。**
> 「三段 Migration 覆盖升级」按新约束**已移除**；
> 其余各项至今**全部未验证**。

- Room / SQLCipher 真机打开加密数据库、R8 包启动
- BiometricPrompt / SAF 文件选择器
- Excel 打开 CSV 是否乱码、中文 BOM 表现
- 5 万条切月 / 年报聚合耗时、滚动掉帧
- 模板 / 周期账 / 快捷方式 / 报销 / 预算 / 净支出手感
- 币种切换、分组小计、同币种转账提示
- 6c-2 新增：CSV 导出、账单导入确认流程、明细币种筛选手感
- **待决策**：自动记账
- **永久不做**：跨币种混算与汇率折算、小组件、通知栏磁贴、三级分类

> 归档需使用者明确指令，本轮不擅自归档。
