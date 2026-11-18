## ADDED Requirements

### Requirement: 导出与重建保留启用态
CSV 导出与加密备份 DTO SHALL 包含 `categories.enabled`，重建后停用状态 MUST NOT 丢失；导出 MUST 读取全量分类，MUST NOT 因停用而漏掉历史归属。

#### Scenario: 重建后状态一致
- **WHEN** 导出后清库再导入
- **THEN** 各分类的启用 / 停用状态与导出前一致
