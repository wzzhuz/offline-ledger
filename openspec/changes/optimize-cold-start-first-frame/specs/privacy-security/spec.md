# Delta for privacy-security

## ADDED Requirements

### Requirement: 启动快照文件的本地边界

启动快照 SHALL 落在 `context.noBackupFilesDir`（或同等不被系统备份的私有位置），
并在 `backup_rules.xml` 与 `data_extraction_rules.xml` 中通过 `domain="file"` 显式排除；
排除规则 MUST NOT 使用 `<include>` 白名单模式。
启动快照 SHALL 只作冷启动渲染副本，MUST NOT 进入 `exportSnapshot()`、`SnapshotWriter`、
CSV 导出、加密备份、`BackupPayload`、统计聚合、余额重算或 `records` 写入。
启动快照 MUST 在应用锁解锁之后才可渲染，MUST NOT 成为绕过应用锁的旁路。
任一写库成功或读取 / 解析失败时，启动快照 SHALL 作废或回退骨架，MUST NOT 被当作最新账目长期展示。
系统 MUST NOT 以「启动快照是明文账目」为由要求默认上传、联网或引入回传类 SDK。

#### Scenario: 换机 / 设备间转移

- **GIVEN** 磁盘上存在启动快照文件
- **WHEN** 系统触发 Auto Backup 或设备间直接转移
- **THEN** 该文件与数据库一同不被上传或转移
- **AND** `domain="file"` 排除在 `cloud-backup` 与 `device-transfer` 两个 section 均生效

#### Scenario: 导出与快照

- **GIVEN** 用户导出加密备份 / CSV，或触发每日自动快照
- **WHEN** 系统读取账本数据
- **THEN** 内容全部来自 Room，启动快照不参与、不被复制、不进备份 DTO
- **AND** 已交付的 JSON 口令加密能力不倒退

#### Scenario: 解锁前

- **GIVEN** 用户启用应用锁且尚未解锁
- **WHEN** 冷启动进入应用
- **THEN** 不渲染任何账目（含启动快照），只见 `AppLockScreen`

#### Scenario: 静态检查

- **GIVEN** CI 执行 `scripts/check_manifest_privacy.py`
- **WHEN** 规则文件缺少 `domain="file"` 排除或误用了 `<include>`
- **THEN** 检查失败并使 job 失败
- **AND** 不产出会把明文账目快照交给系统备份的版本

**理由**：使用者澄清隐私边界是「账本数据不默认上传厂商服务器且用户可控」，
明文本地文件由自己掌控可接受（6c-1 已推翻「明文=风险」前提），因此本条**不禁止明文快照**；
但 `noBackupFilesDir` 与 `domain="file"` 排除仍必须做——否则等于把账目副本交给系统云端，
恰好踩中最原始的红线。启动快照是本次「感知优先」新增的唯一一份账目副本，边界必须单独写死。
同时它是显示层副本，MUST NOT 被解释成「数据已加密」或「导出受限」。

---

## MODIFIED Requirements

### Requirement: 关闭系统自动备份并排除数据库与启动快照

系统 SHALL 设置 `android:allowBackup="false"`，并在 Android 12+ 通过
`dataExtractionRules` / `fullBackupContent` 显式排除数据库、数据库的 WAL / SHM 文件与启动快照文件；
排除规则 MUST NOT 使用 `<include>` 白名单模式。
启动快照 SHALL 落在 `context.noBackupFilesDir` 或同等不被系统备份的私有位置，
`cloud-backup` 与 `device-transfer` 两个 section SHALL 都包含 `domain="file"` 排除。
`android:hasFragileUserData="true"`、数据库三条 `domain="database"` 排除、
`check_manifest_privacy.py` 对权限与依赖的既有检查 MUST NOT 因本改动回退。

#### Scenario: 换机场景

- **GIVEN** 用户更换手机
- **WHEN** 系统尝试自动备份应用数据
- **THEN** 账本数据库、WAL / SHM 与启动快照均不被上传
- **AND** 用户需通过主动导出迁移数据

#### Scenario: 设备间直接转移

- **GIVEN** 用户在 Android 12+ 上用数据线把应用数据转移到新机
- **WHEN** `device-transfer` 规则生效
- **THEN** 数据库与启动快照同样被排除
- **AND** `allowBackup=false` 不会被误当成已覆盖这条路径

#### Scenario: 检查脚本

- **GIVEN** CI 编译前执行 `scripts/check_manifest_privacy.py`
- **WHEN** 检查备份规则
- **THEN** `domain="database"` 排除、两个 section、`domain="file"` 排除、无 `<include>` 全部通过
- **AND** 仍只有 `USE_BIOMETRIC` 一个白名单权限、无未授权网络权限

**理由**：系统 Auto Backup 默认把应用数据备份到云端，等于把最敏感的账本交给第三方，
与「不信任第三方」直接冲突且极易被忽略；`allowBackup=false` 不阻止设备间直接转移，
所以两个 section 都要写。本次新增启动快照文件，原 Requirement 只覆盖 database，
不补齐就会出现「数据库排除了、账目副本却跟着备份走」的缺口。

**口径说明**：本条不把「文件必须加密」写成隐私合规条件——按 6c-1 与使用者本次对铁律 1 的重读，
红线是数据流向与用户控制方式，不是本地文件是否明文。
