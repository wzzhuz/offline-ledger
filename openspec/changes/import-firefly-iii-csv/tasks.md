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
- [x] 单次 commit `71baf76b3576` 推送 + push CI `44109301622` success + release `44110502009` success + 坚果云上传 success
- [ ] 真机复核：用真实 1916 行样本导入、核对账户余额与统计（**由使用者装机确认**）


## 第 23 批（3-C 数据库级幂等 + 导入闭环 + 统计增强，v0.2.7）

- [x] 开场查远端 HEAD `8483ab81e15a`、305 blob 与本地零差异；复读活文档 §1 / §5 / §7.8 / §8
- [x] `design.md` §7 先写根因与三级回退顺序，再动代码
- [x] `RecordEntity` / `TrashEntryEntity` 增 `importSource` / `sourceId` + 复合唯一索引
- [x] `AppDatabase.version` 升 2、无 Migration；注释写明「必须卸载重装」
- [x] 备份 DTO / LedgerMappers / CSV 11 列 / `CsvSourceRow` 四处同步，
  新字段带默认值（按 B 方案不写 V1→V2 schema 兼容层）
- [x] `RecordDao.countByImportSource` 与 `importCandidates` 两条只读查询
- [x] `LedgerRepository`：来源落库、数据库级幂等、疑似匹配只读预览
- [x] `FireflyImportScreen` 结果摘要卡 + 「去看明细」；`SettingsViewModel` 状态与刷新事件
- [x] `DataRefreshBus` → `LedgerApp.refreshTick` → 明细 / 资产 / 统计立即重查
- [x] 明细页顶部 28dp 下滑刷新条；统计页接 refreshTick
- [x] `ChartData.chartArgb` 稳定低饱和色板；`DonutChart` 扇区与图例共用
- [x] 横屏全屏页 `WindowCompat` + `systemBarsPadding`
- [x] `TrendMetric.NET` 有符号净结余线；`StatsMode.MULTI_YEAR` 近 5 年 + 点年下钻
- [x] CSV 导入确认页疑似匹配逐笔勾选；字段说明补两列与重建口径
- [x] 3 份 delta 修订（未归档 change 不留矛盾文本）、活文档 §7.9 / §8、`project.md` 补录
- [x] `ImportIdentityTest`：旧 9 列兼容 / 11 列往返 / 色板稳定 / 有符号归一化 / 多年补全
- [x] 六个静态检查本地全 PASS；Kotlin 引号与括号自检
- [ ] push CI / release / 坚果云上传实测编号与制品字节回填（本批执行后填）
- [ ] 真机复核：卸载重装后重新导入真实 1916 行样本、核对余额 / 明细 / 统计（**由使用者装机确认**）
- [ ] 真机复核：结果摘要、当前月立即刷新、下拉刷新、环形图可辨、横屏不压状态栏、
  净结余含负值、多年下钻、疑似匹配逐笔确认（**由使用者装机确认**）
