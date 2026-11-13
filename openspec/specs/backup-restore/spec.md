# 备份与恢复 Specification

## Purpose

定义备份格式、兼容性、导入事务与导入后重算。备份是本项目的交付证明，必须在数据层阶段就最小可用。

---

## Requirements

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

若提供 CSV 导出，SHALL 使用 UTF-8 with BOM，
金额列以「元」为单位保留两位小数，并 SHALL 注明 `currencyCode` 列。
JSON 加密备份 SHALL 保持口令加密（已交付能力，MUST NOT 倒退成明文）。
新增任何导出格式之前 MUST 先显式更新 `project.md` 的 v1.1 分批计划。
**本地导出文件为明文不构成隐私基线违规**：
隐私边界是账本数据 MUST NOT 上传第三方服务器或托管给厂商，
文件留在用户自己的设备或自选位置即由用户自行掌控。

#### Scenario: 导出文件留在用户自选位置

- **GIVEN** 用户通过 SAF 导出一份账本文件
- **WHEN** 选择本机目录或自选同步文件夹
- **THEN** 文件只写入该位置，应用 MUST NOT 发起任何网络上传
- **AND** 是否加密由所用导出格式决定，不由隐私基线强制

#### Scenario: 有人以「明文风险」否决 CSV

- **GIVEN** 有人提议以「CSV 是明文、与导出默认加密冲突」否决 CSV
- **WHEN** 评审
- **THEN** 评审发现该理由已被使用者在 6c-1 正式推翻
- **AND** 只按批次顺序与闸门状态决策，不再做隐私评审

**理由**：6c-1 使用者明确「导出明文是可接受的，数据掌控在自己手里就可以，
不可接受的是市场上 app 将数据保存在他们服务器上」。
原 Requirement 的「默认关闭 / 导出前二次确认 / 注明导出即明文 /
明文文件勿放公共目录」都以「明文=风险」为前提，属于被推翻的论据；
同时原文本绑定「v1 SHALL NOT」阶段版本号，会随阶段推进反复失效。
本 MODIFIED 保留格式约定与闸门纪律，删除错误论据与阶段绑定，
不新增任何 CSV 入口（结论保留，仅理由改为批次顺序）。

---

### Requirement: 备份信封头部稳定且带版本号

系统 SHALL 提供 `BackupSchema.CURRENT` 与 `MIN_SUPPORTED` 常量，
并提供与 Room Entity 分离的 `@Serializable BackupEnvelope`。
信封头部 SHALL 包含 `schemaVersion`、`exportedAtEpochMilli`、`currencyCode`
与 `counts`，且头部字段 SHALL NOT 在后续阶段变更。

#### Scenario: 阶段 1 往备份里加数据 DTO

- **GIVEN** 阶段 1 定义了 `BackupAccount` / `BackupRecord` 等数据 DTO
- **WHEN** 把它们加入 `payload`
- **THEN** `schemaVersion`、`exportedAtEpochMilli`、`currencyCode` 三项字段名与含义不变
- **AND** 阶段 0 已导出的信封仍能被解析

#### Scenario: 序列化往返

- **GIVEN** 构造一个 `BackupEnvelope(exportedAtEpochMilli = 123456789L, currencyCode = "HKD")`
- **WHEN** 用 `kotlinx.serialization` 编码再解码
- **THEN** `schemaVersion` 为 1、`currencyCode` 为 `"HKD"`、时间戳不变

**理由**：备份格式必须在数据层阶段就定下来，否则开发期表结构频繁变动时
无法边写边导出验证。头部稳定是「旧备份可导入」的前提。

### Requirement: 备份头部不动且 payload 另起

系统 SHALL 新增顶层 `BackupFile(envelope, payload)`，
`BackupEnvelope` 的四项头部字段 SHALL NOT 变更。
`BackupPayload` SHALL 含 accounts / categories / records /
balanceAdjustments / trash 五类 DTO。

#### Scenario: 旧信封仍可解析

- **GIVEN** 阶段 0 导出的、只有头部字段的信封
- **WHEN** 用阶段 1 的解码器读取
- **THEN** 不抛异常
- **AND** 四项头部值保持不变

**理由**：Entity 加字段不该破坏旧备份解析；头部稳定是「旧备份可导入」的前提。

---

### Requirement: 编解码为纯 JVM 且可往返

`BackupExporter.encode` 与 `BackupImporter.decode` SHALL 不依赖 Android SDK，
以便纯 JVM 单测验证往返。

#### Scenario: 导出再导入余额一致

- **GIVEN** 一个含账户、两级分类、若干流水与一条校准的账本快照
- **WHEN** 编码为 JSON 再解码并交给 `LedgerEngine.computeBalances`
- **THEN** 各账户余额与原快照完全一致
- **AND** 净资产一致

**理由**：开发期表结构天天变，必须能边写边导出验证；纯 JVM 才能在没有模拟器的环境里测。

---

### Requirement: 导入事务化且导入后强制重算

整包恢复 SHALL 在 `withTransaction` 内先全量替换再调用 `rebuildAllBalances()`，
解析或校验失败 SHALL 整体回滚。系统 SHALL 把阶段 1 的明文导出标注为
「开发期用，勿放公共目录」。

#### Scenario: 导入中途失败

- **GIVEN** 备份文件结构非法
- **WHEN** 执行导入
- **THEN** 事务回滚，原账本完全不变
- **AND** 不留半套数据

**理由**：覆盖导入是最危险的操作，「修到一半」比「没导入」更难恢复。

### Requirement: 设置页提供临时明文导出入口

设置页 SHALL 提供「导出加密备份」与「从加密备份恢复」两个入口，
SHALL 通过 SAF 由用户选择文件位置，SHALL 要求输入至少 6 位口令，
并 SHALL 在覆盖导入前二次确认「将全量替换现有全部数据」。
导出前 SHALL 调用 `BackupExporter.encode` 生成 JSON 再经
`SecureBackupCodec` 压缩加密后流式写入 URI。
系统 MUST NOT 再提供「导出明文 JSON」按钮，
MUST NOT 为此申请任何存储权限，MUST NOT 把明文备份写入公共目录。

#### Scenario: 加密导出

- **GIVEN** 用户在设置页点「导出加密备份」
- **WHEN** 输入口令并选择保存位置
- **THEN** 生成加密文件且可用同一口令解密回原快照
- **AND** 界面不再出现「明文，勿外传」的临时导出按钮

#### Scenario: 导入后余额一致

- **GIVEN** 一份含账户、两级分类、流水与校准的加密备份
- **WHEN** 二次确认并导入
- **THEN** 事务提交后自动重算余额
- **AND** 净资产与流水聚合一致

**理由**：阶段 2 的临时明文导出只是为兑现「每阶段跑一遍导出备份」，
本阶段用 SAF + 口令加密把它替换掉，兑现第一条铁律「导出文件默认加密」。

---

### Requirement: 对账锚点随备份往返且不改信封

`BackupAccount` SHALL 保留 `lastReconciledAt` / `lastReconciledBalanceCents` 两个可空字段，
`LedgerMappers.toDto` / `toEntity` SHALL 双向映射它们。
导出 SHALL 自动带上锚点，SHALL NOT 为此修改 `BackupEnvelope` 头部或递增 `schemaVersion`。
阶段 3 MUST NOT 写 Room Migration。

#### Scenario: 锚点备份往返

- **GIVEN** 某账户锚点为 `at = 1762000000000`、`balance = 99999` 分
- **WHEN** 导出 JSON 再解析
- **THEN** 两个锚点字段值不变
- **AND** `schemaVersion` 仍为 1、信封四项头部不变

#### Scenario: 旧备份无锚点

- **GIVEN** 一份阶段 1 导出的、账户无锚点字段的备份
- **WHEN** 解析并导入
- **THEN** 两个锚点字段为 `null`
- **AND** 核对视图默认退回「全部流水」且不报错

**理由**：字段在阶段 1 就预留了，阶段 3 只是启用；
可空 + 默认 null 保证旧备份兼容，不需要递增版本号或写迁移。

### Requirement: 加密备份文件自带魔数与随机盐

加密备份 SHALL 以固定 MAGIC 开头，并 SHALL 携带每次随机生成的
16 字节 SALT 与 12 字节 IV。系统 MUST NOT 复用 IV、MUST NOT 把口令、
盐或密钥写进文件头之外的任何持久化位置。口令 SHALL 不落盘。

#### Scenario: 两次导出不同

- **GIVEN** 同一份账本、同一个口令
- **WHEN** 连续导出两次
- **THEN** 两份文件字节不完全相同
- **AND** 两份都能用同一口令解密

#### Scenario: 口令不落盘

- **GIVEN** 用户导出一份加密备份
- **WHEN** 检查应用私有目录与 SharedPreferences
- **THEN** 找不到口令明文
- **AND** 换设备后凭口令仍可解密

**理由**：GCM 复用 IV 会直接破坏机密性；把 Keystore 口令当备份口令
是「本机一切正常、换机永久打不开」的最隐蔽自毁。

---

### Requirement: 加密编解码为纯 JVM 且错口令不改账本

`SecureBackupCodec` SHALL 只用 `javax.crypto` / `java.util.zip` / `java.security`，
SHALL NOT 依赖 Android SDK。错口令、密文篡改、文件截断或 MAGIC 不匹配
SHALL 抛出明确异常，且系统 MUST NOT 调用 `importBackup()`、原账本 MUST 完全不变。

#### Scenario: 错口令

- **GIVEN** 用 `abc123` 导出的备份
- **WHEN** 用 `wrong` 解密
- **THEN** 提示「口令错误或备份文件已损坏」
- **AND** 不区分是口令错还是文件坏
- **AND** 账本数据一条未变

#### Scenario: 密文被篡改

- **GIVEN** 加密备份的某个密文字节被翻转
- **WHEN** 解密
- **THEN** GCM tag 校验失败并抛异常
- **AND** 不产生部分可读的 JSON

**理由**：GCM 自带完整性校验；不区分「口令错」与「文件坏」可避免口令探测。
纯 JVM 才能在无模拟器环境里把往返与失败路径都测掉。

---

### Requirement: SAF 导出导入且零存储权限

导出 SHALL 通过 `ActivityResultContracts.CreateDocument` 由用户选择位置，
导入 SHALL 通过 `OpenDocument` 选择文件，二者 SHALL 用
`ContentResolver.openOutputStream` / `openInputStream` 读写 URI。
系统 MUST NOT 申请 `READ/WRITE_EXTERNAL_STORAGE`，
MUST NOT 用 `File(path)` 写公共目录。
设置页 MUST NOT 再提供「导出明文 JSON」入口。

#### Scenario: 导出备份

- **GIVEN** 用户点击「导出加密备份」并输入口令
- **WHEN** 系统文件选择器打开
- **THEN** 用户自行选择手机本地或同步文件夹
- **AND** 应用权限列表仍无存储权限
- **AND** 写出的文件用文本编辑器打开看不到明文分类名与金额

#### Scenario: 覆盖导入二次确认

- **GIVEN** 用户选择一份备份进行恢复
- **WHEN** 确认前
- **THEN** 弹窗说明「将全量替换现有全部数据」
- **AND** 取消则不写任何数据
- **AND** 确认后走既有单事务 `importBackup()` 并自动重算余额

**理由**：Android 官方明确 SAF 由用户参与选择、无需任何存储权限；
覆盖导入是最危险的操作，二次确认不能省。

### Requirement: 备份 schemaVersion 升至 2 且兼容 v1

`BackupSchema.CURRENT` SHALL 为 2、`MIN_SUPPORTED` SHALL 保持 1。
`BackupEnvelope` 的四项头部字段 MUST NOT 改动。
系统 SHALL 能导入 `schemaVersion = 1` 的加密备份且解析成功。

#### Scenario: 导入 v1 备份

- **GIVEN** 一份 v1 导出的 `schemaVersion = 1` 加密备份
- **WHEN** 用 v1.1 包导入
- **THEN** 解码与落库成功、全量替换现有数据
- **AND** 模板与周期规则列表为空
- **AND** 余额重算后净资产与备份一致

#### Scenario: 拒绝过高版本

- **GIVEN** 一份 `schemaVersion = 99` 的备份
- **WHEN** 解码
- **THEN** 抛出「备份版本高于当前应用」
- **AND** 现有账本完全不变

**理由**：`ignoreUnknownKeys = true` + 新增字段带默认值，
v1 JSON 缺两个键时会解析成空列表，兼容不需要额外分支。

---

### Requirement: 模板与周期规则纳入备份且全量替换语义一致

`BackupPayload` / `LedgerSnapshot` SHALL 增加 `templates` 与
`recurringRules` 两个列表，均带 `emptyList()` 默认值。
`exportSnapshot` SHALL 导出两张新表的全量数据，
`importBackup` SHALL 先清空再插入，事务末尾 SHALL 重算余额。
`BackupCounts` SHALL 增加两项带默认值的计数。

#### Scenario: v2 往返

- **GIVEN** 账本里有 3 条模板与 2 条规则
- **WHEN** 导出再导入
- **THEN** 模板 / 规则的字段全量一致
- **AND** `BackupCounts` 报告 3 / 2

#### Scenario: v1 备份清空规则

- **GIVEN** 当前有模板与规则，导入一份 v1 备份
- **WHEN** 覆盖导入完成
- **THEN** 模板与规则被清空
- **AND** 这与「全量替换」语义一致，不算数据丢失

**理由**：规则存 SharedPreferences 就进不了备份，换设备会全丢；
进 Room 并纳入 payload 才能「数据随时可带走」。

### Requirement: 备份 schemaVersion 升至 3 且兼容 v1 与 v2

`BackupSchema.CURRENT` SHALL 为 3、`MIN_SUPPORTED` SHALL 保持 1。
`BackupEnvelope` 四项头部字段 MUST NOT 改动。
系统 SHALL 能导入 `schemaVersion = 1` 与 `= 2` 的加密备份。

#### Scenario: 导入 v1 备份

- **GIVEN** 一份 v1 加密备份（无 templates / recurringRules / budgets / linkedRecordId）
- **WHEN** 用 6b 包导入
- **THEN** 解码与落库成功、新增列表为空、linkedRecordId 为 null
- **AND** 余额重算后净资产一致

#### Scenario: 导入 v2 备份

- **GIVEN** 一份 v2 加密备份（含模板与规则、无 budgets / linkedRecordId）
- **WHEN** 用 6b 包导入
- **THEN** 模板与规则完整保留、budgets 为空
- **AND** 余额重算后净资产一致

**理由**：`ignoreUnknownKeys` + 新字段带默认值，
旧 JSON 缺键时自动补空列表 / null，兼容不需要额外分支。

---

### Requirement: 预算规则与 linkedRecordId 纳入备份

`BackupPayload` SHALL 增加带默认空列表的 `budgets`，
`BackupRecord` SHALL 增加带默认 null 的 `linkedRecordId`，
`BackupCounts` SHALL 增加 `budgets` 计数。
`exportSnapshot` SHALL 导出全部预算，
`importBackup` SHALL 先清空再插入，事务末尾 SHALL 重算余额。

#### Scenario: v3 往返

- **GIVEN** 3 条预算规则与一条带 linkedRecordId 的 SETTLEMENT
- **WHEN** 导出再导入
- **THEN** 预算字段全量一致、SETTLEMENT 关联不丢失
- **AND** `BackupCounts` 报告 budgets = 3

#### Scenario: v1 备份清空预算

- **GIVEN** 当前有预算规则，导入一份 v1 备份
- **WHEN** 覆盖导入完成
- **THEN** 预算被清空
- **AND** 这与「全量替换」语义一致

**理由**：预算塞 SharedPreferences 就进不了备份，换设备全丢；
进 Room 并纳入 payload 才能「数据随时可带走」。

### Requirement: 备份 schemaVersion 升至 4 且兼容 v1 至 v3

`BackupSchema.CURRENT` SHALL 为 4、`MIN_SUPPORTED` SHALL 保持 1，
`BackupEnvelope` 四项头部字段 MUST NOT 改动。
`BackupRecord` SHALL 增加带默认值的 `currencyCode`，
系统 SHALL 能导入 `schemaVersion` 为 1 / 2 / 3 的加密备份。

#### Scenario: v3 备份导入

- **GIVEN** 一份含 budgets 与 linkedRecordId、无 currencyCode 的 v3 备份
- **WHEN** 用 6c-1 包导入
- **THEN** 全量一致、旧流水币种默认 HKD、budgets 完整保留

#### Scenario: v4 往返

- **GIVEN** 一条 CNY 流水
- **WHEN** 导出再导入
- **THEN** 币种字段保持 CNY

**理由**：DTO 新字段带默认值 + `ignoreUnknownKeys` 让旧备份缺键自动补 HKD，
兼容不需要额外分支；信封头部不动保证既有解析器不返工。
