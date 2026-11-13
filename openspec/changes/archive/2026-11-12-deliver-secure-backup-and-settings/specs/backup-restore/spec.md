# Delta for 备份与恢复

## ADDED Requirements

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

## MODIFIED Requirements

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

### Requirement: CSV 格式约定

**v1 SHALL NOT 提供 CSV 导出入口。** 下列格式约定 SHALL 作为 **v1.1 预留**：
若将来实现 CSV 导出，SHALL 使用 UTF-8 with BOM，金额列以「元」为单位
保留两位小数，并 SHALL 注明「导出即明文」、默认关闭、导出前二次确认。
v1 提供 CSV 之前 MUST 先显式更新 `project.md`「已确认不做」清单。

#### Scenario: v1 打开设置页

- **GIVEN** v1 版本的应用
- **WHEN** 查看设置页备份与导出区
- **THEN** 只有「导出加密备份」与「从加密备份恢复」
- **AND** 没有 CSV 导出入口

#### Scenario: 有人提议顺手做 CSV

- **GIVEN** 有人提议 v1 顺手加一个 CSV 按钮方便 Excel
- **WHEN** 提交 change
- **THEN** 评审发现 `reporting-ui`「v1 范围收敛」与 `project.md` 都把 CSV 列在 v1.1
- **AND** 要求先显式更新「已确认不做」清单

**理由**：阶段 4 归档发现 CSV 在 `reporting-ui`「v1 范围收敛」被列为 v1.1 禁止项，
却在 `backup-restore` / `privacy-security` / `project.md` 阶段 5 里被视为 v1 功能，
两处长期约束互相矛盾。使用者拍板「v1 不做」，本 MODIFIED 把格式约定
降级为 v1.1 预留，保留将来要用时的约定而不留一个做了一半的入口。
