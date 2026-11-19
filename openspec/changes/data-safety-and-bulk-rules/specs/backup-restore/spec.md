## ADDED Requirements

### Requirement: 自动快照自包含且可跨卸载重建
写入外部授权目录的快照 SHALL 使用 `SecureBackupCodec` 加密并带 SHA-256 校验和与 schema 版本；恢复时 SHALL 仅凭使用者自设备份口令解密，MUST NOT 依赖 AndroidKeystore 中的数据库口令。裸 `.db` 文件 MUST NOT 作为重建依据，主库 MUST 留在内部私有目录。

#### Scenario: 卸载重装后重建
- **WHEN** 使用者误卸载后重装并重新授权同一目录、选择最近快照
- **THEN** 校验通过、凭口令解密、单事务重建并完成余额重算
- **AND** 账户、分类、流水、模板、预算与统计合计与快照一致

#### Scenario: 原子写与保留策略
- **WHEN** 快照写入过程中被中断
- **THEN** 目录里 MUST NOT 出现被当成成品的半截文件
- **AND** 成品文件保留最近 7 份，临时文件优先清理

### Requirement: 卸载时给出保留数据确认
Manifest SHALL 声明 `android:hasFragileUserData="true"`；该声明 MUST NOT 被当作对「清除数据」的保障，也不得替代外部快照。

#### Scenario: 手滑卸载
- **WHEN** 使用者在卸载对话框操作
- **THEN** 系统提示是否保留此应用的数据

## MODIFIED Requirements

### Requirement: CSV 明文导出格式

CSV 明文导出 MUST 带 `importSource` 与 `sourceId` 列，导入缺列按空串处理；
加密备份 DTO 亦 MUST 携带这两个字段。系统 SHALL 按首行表头把文件区分为
「本 App 自有导出格式」与「第三方账单格式」，MUST NOT 靠文件名判定。
自有导出文件 SHALL 由「从 CSV 重建」入口处理，第三方账单 SHALL 由
「导入账单」处理；把自有文件投进「导入账单」SHALL 给出明确引导而
MUST NOT 静默兼容或静默丢弃。二次重建 MUST 继续按
`UNIQUE(importSource, sourceId)` 幂等（0 写入）。

**导出格式 ≠ 重建格式**：自有 CSV 只有 11 列流水，不含账户表与分类树。
「从 CSV 重建」SHALL 只定义为「在已有账户 / 分类体系上回灌
`EXPENSE` / `INCOME` 流水」；清库 / 换机后的完整重建 SHALL 走
「从加密备份恢复」或「自动快照与恢复」，MUST NOT 在按钮、说明或帮助文本里
引导使用者清库后用 CSV 重建。该入口 SHALL 使用 `rebuildFromOwnCsv` 专用
写路径，MUST NOT 复用 `importRecords` 的严格行级校验；MUST 与
`importBackup` 共用「单事务 + 末尾重算 + 失败整库回滚」骨架。
归档账户与停用分类 SHALL 视为已知（历史归属仍有效），MUST NOT 判成缺失。
缺账户 / 缺分类 MUST NOT 抛异常中断整批，SHALL 跳过受影响行、
在结果摘要里逐项列出缺失名称，并给出「先建立体系或改用加密备份 /
快照恢复」的可操作引导；分类清单换名导致的失配 SHALL 在摘要里说明，
MUST NOT 被表述成数据损坏。

#### Scenario: 导出文件被投进账单入口
- **WHEN** 使用者把本 App 导出的 CSV 选进「导入账单」
- **THEN** 页面提示改用「从 CSV 重建」（需已有账户与分类）或完整重建走加密备份 / 快照，账本不改动

#### Scenario: 自有文件重建
- **WHEN** 使用者把本 App 导出的 CSV 选进「从 CSV 重建」
- **THEN** 仅 `EXPENSE` / `INCOME` 且账户与一级分类齐备的行进入确认计划
- **AND** 转账、无账户、无一级分类的行以带行号的说明明确列出跳过
- **AND** 归档账户、停用分类不算缺失，历史归属仍可回灌
- **AND** 确认后单事务写入、末尾重算，来源字段原样保留

#### Scenario: 缺账户或分类时不整批失败
- **WHEN** 当前库无同名账户或分类（含刚卸载重装、v0.2.14 换过分类清单）
- **THEN** 不抛「账户不存在或已归档」，缺项名称逐项列出，受影响行跳过，其余行照常写入
- **AND** 结果摘要提示先建立上述账户 / 分类，或改用加密备份 / 快照恢复做完整重建
- **AND** 事务仍整体提交或整体回滚，账本不留半截数据

#### Scenario: 导出再重建同一份 CSV
- **WHEN** 在已有账户与分类体系上用同一份导出文件重建两次
- **THEN** 第二次全部行按来源唯一键跳过，写入 0 条

#### Scenario: 缺列兼容
- **WHEN** 重建一份没有 `importSource` / `sourceId` 列的旧 CSV
- **THEN** 按空串解析，不报错，手工流水仍不受唯一索引约束

### Requirement: 导入事务化且导入后强制重算

整包恢复 SHALL 在 `withTransaction` 内先全量替换再调用 `rebuildAllBalances()`，
解析或校验失败 SHALL 整体回滚。恢复 SHALL 处理 `categories` 自引用外键：
MUST 在清空前解除 `parentId` 引用或按拓扑序删除，
MUST 按父先子后的顺序插入分类，
MUST 在提交前用 `PRAGMA foreign_key_check` 兜底，
MUST NOT 用事务内 `PRAGMA foreign_keys = OFF`（该写法是 no-op），
MUST NOT 删除外键约束来绕过 1811。
备份 DTO SHALL 携带分类启用态、账户卡号后四位与商户别名规则，
恢复后这些字段 MUST NOT 回退成默认值。

#### Scenario: 导入中途失败
- **GIVEN** 备份文件结构非法或存在悬空外键
- **WHEN** 执行导入
- **THEN** 事务回滚，原账本完全不变，不留半套数据

#### Scenario: 分类含二级且父级排在子级之后
- **GIVEN** 备份里某个二级分类的数组位置早于它的一级父级
- **WHEN** 执行恢复
- **THEN** 不出现 `FOREIGN KEY constraint failed (code 1811)`，层级关系完整

#### Scenario: 启用态与别名随备份还原
- **GIVEN** 导出前停用了「养车」并保存了商户别名规则
- **WHEN** 恢复该备份
- **THEN** 「养车」仍为停用，别名规则仍在，统计仍读全量分类
