# Delta for 隐私与安全基线

> 初始归档：本模块全部为新增要求（`ADDED`），无 `MODIFIED` / `REMOVED`。
> 归档时已原样合并至 `openspec/specs/privacy-security/spec.md`。

## ADDED Requirements

### Requirement: 不声明网络权限

AndroidManifest.xml MUST NOT 声明 `android.permission.INTERNET`。系统 SHALL NOT 包含任何账号体系、云同步、远程配置或服务端依赖。

#### Scenario: 安装前检查

- **GIVEN** 用户在系统设置查看应用权限列表
- **WHEN** 查看
- **THEN** 无任何网络相关权限
- **AND** 应用功能不依赖联网

**理由**：这是最硬的自我证明：用户装之前看一眼权限就放心，也从机制上阻断依赖库偷偷联网。

---

### Requirement: 禁止引入含网络行为的依赖

新增依赖 SHALL 经过评审，MUST NOT 引入广告、埋点、崩溃上报、网络统计 SDK，以及会传递引入网络栈的库（如为加载图片而引入 OkHttp 系依赖）。

#### Scenario: 依赖评审

- **GIVEN** 有人提议引入图片加载库以显示分类图标
- **WHEN** 评审
- **THEN** 改用 Material Icons / 本地资源实现
- **AND** 依赖树中不出现网络客户端

**理由**：零网络权限不等于零网络代码。传递依赖是「数据出去」最常见的暗门。

---

### Requirement: 关闭系统自动备份并排除数据库

系统 SHALL 设置 `android:allowBackup="false"`，并在 Android 12+ 通过 `dataExtractionRules` / `fullBackupContent` 显式排除数据库与备份文件。

#### Scenario: 换机场景

- **GIVEN** 用户更换手机
- **WHEN** 系统尝试自动备份应用数据
- **THEN** 账本数据库不被上传
- **AND** 用户需通过主动导出迁移数据

**理由**：系统 Auto Backup 默认把应用数据备份到云端，等于把最敏感的明文账本交给第三方，与「不信任第三方」直接冲突，且极易被忽略。

---

### Requirement: 本地数据库加密

账本数据库 SHALL 使用 SQLCipher（Room 经自定义 `SupportOpenHelperFactory` 接入）或同等强度方案加密，密钥 SHALL 由 Android Keystore 派生保管。

#### Scenario: 直接读取数据库文件

- **GIVEN** 有人取得应用数据目录下的 db 文件
- **WHEN** 用 SQLite 工具打开
- **THEN** 无法读到明文账目
- **AND** 提示文件已加密或无法解析

**理由**：设备丢失、备份残留、恶意应用读取文件是纯本地方案仅剩的风险面，加密是必要兜底。

---

### Requirement: 应用锁与界面防泄露

系统 SHALL 提供 BiometricPrompt / PIN 应用锁，在冷启动与从后台恢复时校验。系统 SHOULD 提供 `FLAG_SECURE` 开关，防止最近任务预览泄露资产页。

#### Scenario: 冷启动

- **GIVEN** 用户冷启动应用
- **WHEN** 进入首页前
- **THEN** 先校验生物识别或 PIN
- **AND** 校验失败不展示任何账目

#### Scenario: 最近任务

- **GIVEN** 用户打开多任务界面
- **WHEN** 系统截取应用预览
- **THEN** 资产页内容不显示（开启 FLAG_SECURE 时）

**理由**：资产总额是高度敏感数据，锁屏之外还应防「旁边的人瞥一眼」。

---

### Requirement: 导出文件默认加密

JSON 备份 SHALL 默认口令加密。CSV 导出 SHALL 默认关闭，开启时 MUST 在导出前二次确认并提示「明文文件，勿放公共目录」。

#### Scenario: 导出 CSV

- **GIVEN** 用户在设置页点击导出 CSV
- **WHEN** 确认前
- **THEN** 弹出明文风险提示与二次确认
- **AND** 取消则不生成文件

**理由**：资产数据以明文躺在网盘同步文件夹里，比存在厂商服务器上风险更高。

---

### Requirement: 仅使用 SAF 且不申请存储权限

导入导出 SHALL 通过 Android Storage Access Framework 由用户选择位置，系统 MUST NOT 申请 `READ/WRITE_EXTERNAL_STORAGE` 等存储权限。

#### Scenario: 导出备份

- **GIVEN** 用户点击一键导出
- **WHEN** 系统文件选择器打开
- **THEN** 用户自行选择手机本地或同步文件夹
- **AND** 应用权限列表仍为空

**理由**：既能把数据交给用户自己保管，又不多要一项权限，符合最小必要原则。

---
