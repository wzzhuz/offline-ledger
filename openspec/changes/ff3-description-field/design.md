# 设计：description 独立列（第 43 批 / v0.2.25）

## 根因

v0.2.19 与截图对照时把 FF3 `description` 误映射为「交易对方」、`notes` 映射为「备注」；FF3 CSV roles 里 description / note / opposing-name 是三个独立列角色，description 必填、notes 只在详情页可见。本项目流水只有 `counterparty` + `note` 两个自由文本，丢的是整个维度而非映射不佳。

## 回退顺序

1. 先加 Entity 列与 `version = 5`，确认无迁移策略（测试期卸载重装）
2. 打通数据链路：FF3 计划 → 落库；自有 CSV 导出/导入/重建；备份 DTO 与快照
3. 接 UI：记一笔输入 → 明细行 `displayTitle` 动态回退
4. 接筛选与规则：单条 SQL 参数、字段目录、联想 DAO、动作回写
5. 纯 JVM 单测锁死：FF3 三值分离、CSV 往返与旧文件兼容、备份往返与旧备份兼容、规则条件与动作

## 取舍（如实记录）

- **改表**：`version = 5`、`Migrations.ALL` 仍为空（稳定版前冻结），覆盖安装会开库失败 → 必须卸载重装、装之前先导出。
- **备份兼容**：`BackupRecord.description` 带默认值 `""`，`MIN_SUPPORTED` 保持 1；旧备份缺键解析成空串，不新增拒绝分支。
- **CSV 兼容**：`description` 列入 `optional`，旧 8~11 列 CSV 仍可导入；`detectFormat` 的 required 集合同步排除它。
- **统计口径**：Top5 对方排行里「虚拟-不记录」仍聚成一坨，是数据源占位习惯，本批不改统计口径；描述只影响展示与筛选/规则。
- **描述非必填**：记一笔 placeholder-only，不强制；`displayTitle` 单一出口在 `RecordRowCard` 内实现，列表与副行不各自拼串。
- **规则动作**：`SET_DESCRIPTION` 追加在枚举末尾，旧规则 JSON 按名反序列化不破；`BulkTarget.description` 带默认值，既有构造全部兼容。
