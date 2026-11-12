# Delta for 备份与恢复

> 初始归档：本模块全部为新增要求（`ADDED`），无 `MODIFIED` / `REMOVED`。
> 归档时已原样合并至 `openspec/specs/backup-restore/spec.md`。

## ADDED Requirements

### Requirement: 备份 DTO 与 Entity 分离并带版本号

备份 SHALL 使用与 Room Entity 分离的 DTO，并 SHALL 包含 `schemaVersion`。系统 SHALL 支持旧版本备份导入，导入时 SHALL 补全新增字段的默认值。

#### Scenario: v1 备份导入 v2

- **GIVEN** 导入一份无 `rootCategoryId` / `parentId` 的旧备份
- **WHEN** 解析
- **THEN** 所有分类 `parentId = null`、`level = 0`、`rootCategoryId = 自身 id`
- **AND** 历史流水 `rootCategoryId = categoryId`，统计结果正确

**理由**：Entity 加字段不该破坏旧备份解析；版本号让未来迁移有明确入口。

---

### Requirement: 导出流式写入且默认加密压缩

导出 SHALL 采用协程 + 流式序列化写入，SHALL 经 Gzip 压缩并默认口令加密。系统 MUST NOT 把全部记录拼成一个超大 String 再写出。

#### Scenario: 大数据量导出

- **GIVEN** 账本含 5 万条流水
- **WHEN** 执行导出
- **THEN** 逐条写入并生成文件
- **AND** 内存占用平稳，不发生 OOM

**理由**：10 年账本约 2–5 万条，全量字符串拼接是最容易在老数据上炸掉的写法。

---

### Requirement: 导入事务化且失败整体回滚

整包恢复 SHALL 在单个事务内完成，SHALL 先校验 `schemaVersion` 与外键完整性；校验或解析失败 SHALL 整体回滚，MUST NOT 留下半套数据。

#### Scenario: 导入中途失败

- **GIVEN** 备份文件在中途损坏
- **WHEN** 解析到第 N 条报错
- **THEN** 事务回滚
- **AND** 原账本数据完全不变

**理由**：覆盖导入是最危险的操作，「修到一半」比「没导入」更难恢复。

---

### Requirement: 导入后强制重算与自检

导入完成后 SHALL 执行 `rebuildAllBalances()` 与账本健康自检，检查孤儿流水、`rootCategoryId` 一致性、调整记录合法性。

#### Scenario: 恢复备份

- **GIVEN** 从加密 JSON 全量恢复
- **WHEN** 事务提交后
- **THEN** 自动重算余额
- **AND** 净资产与流水聚合一致，异常写入审计日志

**理由**：导入等价于一次性写入上万条流水，必须走与日常写操作相同的一致性保障。

---

### Requirement: 覆盖导入前二次确认与口径明确

覆盖导入前 SHALL 二次确认并明确提示「将全量替换现有全部数据」。v2 SHALL 把预置账户/分类的去重规则定义为**全量替换**。

#### Scenario: 覆盖导入

- **GIVEN** 用户选择一份备份进行恢复
- **WHEN** 确认前
- **THEN** 弹窗说明将替换全部数据
- **AND** 确认后按全量替换执行，不产生重复预置分类

**理由**：「合并还是替换」不写死，实现时必然摇摆，且会产生重复的默认账户。

---

### Requirement: 最小可用导出在第一阶段落地

分阶段计划 SHALL 把「最小可用 JSON 导出」安排在数据层阶段，早于任何页面实现。

#### Scenario: 数据层阶段验收

- **GIVEN** 第 1 阶段完成
- **WHEN** 验收
- **THEN** 能导出并重新导入一份含流水与账户的备份
- **AND** 每个阶段结束都跑一遍导出

**理由**：开发期表结构天天变，没有导出等于边写边丢数据；备份也是「数据是你的」这一铁律的可验证证据。

---

### Requirement: CSV 格式约定

CSV 导出 SHALL 使用 UTF-8 with BOM，金额列以「元」为单位保留两位小数，并 SHALL 注明「导出即明文」。

#### Scenario: Excel 打开

- **GIVEN** 用户在电脑用 Excel 打开导出的 CSV
- **WHEN** 打开
- **THEN** 中文分类名与备注不乱码
- **AND** 金额显示为两位小数

**理由**：方便电脑核对是 CSV 的唯一目的，编码与单位不写清楚就会变成「打开了但看不懂」。

---
