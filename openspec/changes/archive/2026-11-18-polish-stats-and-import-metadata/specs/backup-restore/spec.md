# backup-restore Delta

## MODIFIED Requirements

### Requirement: CSV 明文导出格式
CSV 明文导出 MUST 带 `importSource` 与 `sourceId` 列，导入缺列按空串处理；
导出再导入同一份文件 MUST 继续幂等（0 写入），MUST NOT 因导出丢来源字段而破坏
`UNIQUE(importSource, sourceId)`；加密备份 DTO 亦 MUST 携带这两个字段。

#### Scenario: 导出再导入同一份 CSV
- **WHEN** 使用者导出明文 CSV 后对同一账本再导入该文件
- **THEN** 全部行按来源唯一键跳过，写入 0 条

#### Scenario: 缺列兼容
- **WHEN** 导入一份没有 `importSource` / `sourceId` 列的旧 CSV
- **THEN** 按空串解析，不报错，手工流水仍不受唯一索引约束
