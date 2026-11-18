# 导入 Firefly III 导出的 CSV

## Why

使用者此前用 Firefly III 6.6.6 记账，现要把历史账本一次性搬进本项目。
这是**一次性历史迁移**，不是持续同步。Firefly III 是复式记账（source / destination
两个账户，其中一方常是外部 Expense / Revenue account），本项目是单式流水
（账户只装自己的资产 / 负债 / 充值 / 投资，`counterparty` 是流水级自由文本）。
两者的语义差必须在导入层收敛，不能把复式模型带进本项目。

## What Changes

- 设置 → 数据与备份新增「导入 Firefly III 导出（CSV）」入口，进入独立二级页
- 纯 JVM 解析 6.6.x `transactions.csv`：表头按列名别名映射、引号状态机复用 `CsvCodec`
- 生成映射预览：账户逐项对齐（同名建议 + 可改 + 可一键新建）、分类逐项对齐、报告各类行数
- 二次确认后单事务写入：按需新建账户 / 两级分类 → 批量写流水 → 末尾余额重算
- 零表结构变更、零新增依赖、零新增权限；不引入网络行为

## Capabilities

- `backup-restore`（MODIFIED 1 条 + ADDED 1 条）：两种导入入口并存，互不冲突
- `ledger-transactions`（ADDED 1 条）：复式 → 单式的字段映射与期初余额语义
- `reporting-ui`（ADDED 1 条）：迁移二级页与映射预览

## Impact

- 代码：`backup/csv/` 新增 2 个纯 JVM 文件、`ui/settings/` 新增 1 页、
  `LedgerRepository` 新增 1 个写方法、`SettingsViewModel` 新增状态机、
  `LedgerApp` 新增 1 个路由
- 静态检查：`check_transaction_annotations.py` 的写方法清单扩到 40
- 版本 `0.2.5 → 0.2.6`；覆盖安装即可，不用卸载
- 不碰：DAO 查询、Entity、`AppDatabase.version`、备份 DTO、统计口径、Manifest、release.yml


## 第 23 批扩写（使用者「选 3-C，其余按推荐来」）

导入功能真机试用后的收尾批，**同一个 change 内修订本文件前面写死的
「零表结构变更 / 覆盖安装即可」结论**：

- **3-C（使用者明确授权）**：`records` / `trash` 增 `importSource` + `sourceId`
  与复合唯一索引，`AppDatabase.version` 升到 2、仍不注册 Migration。
  **必须卸载重装**；卸载前 MUST 先导出 CSV 或加密备份带走真实迁移数据。
- 导入结束给出常驻结果摘要 + 「去看明细」；写操作后由 `DataRefreshBus`
  通知明细 / 资产 / 统计立即重查，明细页另加顶部下滑刷新兜底。
- 环形图扇区改稳定低饱和色板（自动生成分类 `colorHex` 恒 `#888888` 导致全同色）；
  横屏全屏页吃 `systemBarsPadding` 不再压状态栏；趋势卡加「收入 − 支出」净结余线
  与「近 5 年」多年粒度，点年份可下钻回年度视图。
- 跨来源对账（手工流水 vs 支付宝账单）新增疑似匹配预览：
  金额分 + 账户 + localDate ±1 天，逐笔勾选「跳过 / 仍导入」，MUST NOT 自动合并。
- 版本 `0.2.6 → 0.2.7`。不碰统计口径唯一出口、金额铁律、不混算、Manifest、release.yml。
