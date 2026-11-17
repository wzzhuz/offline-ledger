# 任务清单 · 第 22 批 Firefly III CSV 迁移

- [x] 查远端 HEAD / 最近 Actions / 归档状态（基线 `7f9ce4f89e96`，v0.2.5，进行中 change 为无）
- [x] 精读真实导出样本并跑纯 JVM 等价统计（1916 行、4 类、21 账户、28 分类键）
- [x] 写 change 四件套与 3 份 delta（backup-restore MODIFIED 1 + ADDED 1；ledger-transactions ADDED 1；reporting-ui ADDED 1）
- [x] `FireflyModels.kt`：行模型 / 计划 / 映射 / 结果
- [x] `FireflyCsvCodec.kt`：表头别名、全量校验、复式转单式、带符号金额、时间锚点、账户类型推断
- [x] `CsvCodec.parseRows` 提为 internal，复用引号状态机
- [x] `LedgerRepository.importFirefly`：单事务 + 按需建账户/分类 + 去重 + 末尾重算
- [x] `check_transaction_annotations.py` 写方法清单扩到 40
- [x] `SettingsViewModel`：解析 / 预览 / 映射修改 / 二次确认 / 回滚报告
- [x] `FireflyImportScreen.kt`：独立二级页、账户与分类映射、覆盖期初开关
- [x] `BackupSettingsScreen` 入口、`SettingsScreen` 摘要、`LedgerApp` 路由
- [x] `FireflyCsvCodecTest.kt`：9 个纯 JVM 单测（四类型 / 符号 / 幂等键 / 坏行 / 非 FF3 文件 / 类型推断）
- [x] 六个静态检查本地全 PASS；Kotlin 逐行引号与大括号自检
- [x] 版本 `0.2.5 → 0.2.6`
- [x] `docs/FireflyIII导入说明.md` 中文逐字段说明
- [x] 活文档 §7.8 / §8 与 `project.md` 阶段计划同步
- [x] 单次 commit 推送 + push CI + release + 坚果云上传
- [ ] 真机复核：用真实 1916 行样本导入、核对账户余额与统计（**由使用者装机确认**）
