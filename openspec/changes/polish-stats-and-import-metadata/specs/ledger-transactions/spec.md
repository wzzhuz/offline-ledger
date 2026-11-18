# ledger-transactions Delta

## MODIFIED Requirements

### Requirement: 导入来源唯一标记
导入流水的来源 MUST 完全由 `importSource` + `sourceId` 字段承载，MUST NOT 写入
备注；备注 MUST 逐字保留来源文件的原文（Firefly III 为 `notes` 列）；流水详情
MUST 显示来源信息；无对应字段的元数据（description / tags / budget / 分组）
MUST NOT 拼接进备注，且 MUST 在导入结果中明示未迁移。

#### Scenario: Firefly III 导入不改备注
- **WHEN** 导入含 `notes` 列的 Firefly III 导出
- **THEN** 落库备注与 `notes` 原文一致，不含 `ff3:` 前缀或「分组：」等拼接文本

#### Scenario: 详情看来源
- **WHEN** 使用者点开一条导入流水
- **THEN** 详情显示「来源：firefly-iii · journal xxx」，去重仍走唯一索引

### Requirement: 分类显示名单一出口
分类显示名 MUST 由单一纯 JVM 出口构造：二级分类 MUST 显示为
「一级 / 二级」，一级分类 MUST 保持不含父级前缀的裸名。占比图例、占比扇区
下钻、筛选面板与明细筛选 chip MUST 全部经该出口渲染，MUST NOT 各自拼接
分隔符；只用于展示，MUST NOT 参与 SQL、MUST NOT 计入 `activeCount()`。

#### Scenario: 同名二级分类可分辨
- **WHEN** 统计页切「按小类」且两个不同一级下均存在名为「其他」的二级分类
- **THEN** 图例分别显示「餐饮 / 其他」与「交通 / 其他」，扇区与图例点击下钻
  后明细筛选 chip 显示同样的带父级全名

#### Scenario: 统计跳转与手工选一致
- **WHEN** 分别由统计页二级下钻、筛选面板手工选、明细 chip 兜底反查三种
  入口渲染同一个二级分类
- **THEN** 三者输出完全相同；一级下钻与手工选一级时均只显示一级名
