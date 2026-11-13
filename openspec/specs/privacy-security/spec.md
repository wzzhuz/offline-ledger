# 隐私与安全基线 Specification

## Purpose

把「不信任第三方、资产数据高度敏感」从动机落成可核查的硬约束。本模块三条核心约束违反任意一条即视为项目失败。

---

## Requirements

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

JSON 备份 SHALL 默认口令加密，SHALL 通过 SAF 由用户选择保存位置，
MUST NOT 生成明文 JSON 文件。**v1 SHALL NOT 提供 CSV 导出入口**；
CSV 若列入 v1.1 实现，SHALL 默认关闭、开启时 MUST 在导出前二次确认
并提示「明文文件，勿放公共目录」。
系统 MUST NOT 在应用私有目录留下明文账本备份。

#### Scenario: 导出加密备份

- **GIVEN** 用户在设置页点击「导出加密备份」
- **WHEN** 输入口令并选择位置
- **THEN** 生成的文件用文本编辑器打开看不到明文分类名与金额
- **AND** 应用私有目录不新增明文 JSON

#### Scenario: v1 没有 CSV 入口

- **GIVEN** v1 版本
- **WHEN** 查看设置页
- **THEN** 没有 CSV 导出按钮
- **AND** 不存在「默认关闭但点得到」的 CSV 入口

**理由**：原 Requirement 同时写了「JSON 加密」与「CSV 默认关闭」两件事，
阶段 4 归档发现后半句与 `reporting-ui`「v1 范围收敛」把 CSV 列为 v1.1
互相矛盾。使用者拍板 v1 不做 CSV；本 MODIFIED 保留 JSON 加密的硬要求，
把 CSV 明确降级为 v1.1 预留，消除两处相反约束。

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

### Requirement: Manifest 隐私基线可逐条核查

`AndroidManifest.xml` SHALL 满足：不声明 `android.permission.INTERNET`、
`android:allowBackup="false"`、通过 `android:fullBackupContent` 与
`android:dataExtractionRules` 引用排除数据库的规则文件。
系统 MUST NOT 声明 `READ/WRITE_EXTERNAL_STORAGE`。

#### Scenario: 安装前查看权限

- **GIVEN** 用户在系统设置中查看应用权限
- **WHEN** 打开该应用详情
- **THEN** 权限列表为空或仅含无害权限
- **AND** 不含任何网络与存储权限

#### Scenario: 系统尝试自动备份

- **GIVEN** 用户换机或系统触发 Auto Backup
- **WHEN** 系统读取应用数据
- **THEN** 账本数据库不被上传
- **AND** 迁移数据须通过用户主动导出

**理由**：系统 Auto Backup 默认把应用数据备份到云端，
等于把最敏感的账本交给第三方，与「不信任第三方」直接冲突且极易被忽略。

---

### Requirement: 隐私基线由自动化脚本检查

系统 SHALL 提供 `scripts/check_manifest_privacy.py`，
对 Manifest、两个备份规则 XML 与依赖声明做静态检查，
并 SHALL 在 CI 编译前执行，失败即中断。

#### Scenario: 备份规则漏写 database 排除

- **GIVEN** 有人删掉 `data_extraction_rules.xml` 里的 `<exclude domain="database" ...>`
- **WHEN** CI 执行检查脚本
- **THEN** 脚本报错、job 失败
- **AND** 不会产出一个可被系统备份数据库的版本

**理由**：文字约定会被忘记，可执行检查不会。
且备份规则只在 release 的 `lintVital` 才校验，静态检查能在更早一步拦住。

### Requirement: 数据库以 SQLCipher 加密打开

系统 SHALL 通过 `SupportOpenHelperFactory` 打开 Room 数据库，
口令 SHALL 由 `DatabaseKeyManager` 生成，
且 SHALL 在打开任何数据库之前调用 `System.loadLibrary("sqlcipher")`。

#### Scenario: 直接读取数据库文件

- **GIVEN** 有人取得应用数据目录下的 `offline_ledger.db`
- **WHEN** 用普通 SQLite 工具打开
- **THEN** 无法读到明文账目

**理由**：零网络权限之外，设备丢失与文件残留是纯本地方案仅剩的风险面，
加密是必要兜底；第一条铁律不能只停在 Manifest 上。

---

### Requirement: 数据库口令由 Android Keystore 保护

系统 SHALL 用 Android Keystore 的 AES-256/GCM 密钥加密数据库口令，
密文与 IV MAY 存于 `SharedPreferences`，
密钥材料 MUST NOT 离开 Keystore，口令 MUST NOT 以明文持久化。

#### Scenario: 应用重启后仍可打开同一数据库

- **GIVEN** 应用进程被杀死后重新启动
- **WHEN** `DatabaseKeyManager.getOrCreatePassphrase` 执行
- **THEN** 解密得到与首次完全相同的 32 字节口令
- **AND** 无需用户重新输入

**理由**：口令一旦每次随机生成，重启后数据库就打不开，等于数据全丢。

### Requirement: 应用锁在冷启动与后台恢复校验

启用应用锁后，系统 SHALL 在冷启动与从后台恢复时先校验生物识别或 PIN，
校验通过前 MUST NOT 渲染任何账目界面。生物识别不可用时 SHALL 提供
自建 PIN 兜底，且启用应用锁 SHALL 要求先设置 PIN。
系统 MUST NOT 把应用锁做成第 5 个 Tab 或只在设置页放开关而不拦主界面。

#### Scenario: 冷启动

- **GIVEN** 用户启用应用锁并冷启动应用
- **WHEN** 进入首页前
- **THEN** 先显示解锁界面，不出现明细 / 资产 / 统计 / 设置任何账目
- **AND** 校验失败不进主界面

#### Scenario: 后台恢复

- **GIVEN** 应用已解锁并被切到后台
- **WHEN** 从最近任务切回
- **THEN** 重新要求校验
- **AND** 最近任务预览在开启 FLAG_SECURE 时不显示资产内容

#### Scenario: 生物识别不可用

- **GIVEN** 设备没有生物识别硬件或未录入
- **WHEN** 打开解锁界面
- **THEN** 直接显示 PIN 输入
- **AND** 不卡在无按钮的空白页

**理由**：资产总额是高度敏感数据；只拦冷启动不拦后台恢复，
等于切出去再回来账目直接可见。

---

### Requirement: FLAG_SECURE 与 PIN hash 边界明确

系统 SHALL 提供 `FLAG_SECURE` 开关，开启时 SHALL 在 Activity 上设置
`WindowManager.LayoutParams.FLAG_SECURE`。PIN SHALL 以加盐 SHA-256 存储，
MUST NOT 明文持久化。UI SHALL 说明该锁防的是「旁边的人瞥一眼 / 丢手机随手翻」，
MUST NOT 宣称等价强认证——账本本体由 SQLCipher + Keystore 保护。

#### Scenario: 开启防泄露

- **GIVEN** 用户开启「最近任务防泄露」
- **WHEN** 打开多任务界面
- **THEN** 系统截取的预览不显示应用内容

#### Scenario: PIN 存储

- **GIVEN** 用户设置 PIN `135790`
- **WHEN** 检查 SharedPreferences
- **THEN** 存的是随机盐与加盐 hash，不含 PIN 明文

**理由**：把加盐 SHA-256 说成强认证会误导用户；明确边界才不会让人误以为
它能对抗有物理接触的攻击者。

---

### Requirement: 新增权限与依赖进入静态检查白名单

`check_manifest_privacy.py` SHALL 增加 `ALLOWED_PERMISSIONS` 检查：
除 `android.permission.USE_BIOMETRIC`（normal 级）外，
任何 `<uses-permission>` 声明 SHALL 使检查失败。
系统 SHALL 继续禁止 `INTERNET` 与 `READ/WRITE_EXTERNAL_STORAGE`，
并 SHALL 继续禁止含网络行为的依赖。
白名单检查 MUST 精确匹配 `<uses-permission>` 标签并先剥除 XML 注释，
MUST NOT 用宽泛子串匹配（避免阶段 1 归档那两次自误报）。

#### Scenario: 有人加回网络权限

- **GIVEN** 有人在 Manifest 里加 `android.permission.INTERNET`
- **WHEN** CI 执行静态检查
- **THEN** 检查失败并使 job 失败
- **AND** 不产出含网络权限的版本

#### Scenario: 注释里提到 USE_BIOMETRIC

- **GIVEN** Manifest 注释解释了为什么需要该权限
- **WHEN** 执行静态检查
- **THEN** 不因注释里的权限名误报
- **AND** 五个静态检查仍全部在编译前执行

**理由**：阶段 5 是首个新增权限的阶段，白名单要跟着扩；
硬编码清单最典型的失效方式是「加了新权限忘了登记」或反过来「检查器自误报」。

### Requirement: CI 网络请求与 App 权限边界明确

出包流水线在 GitHub runner 上访问坚果云 WebDAV 的行为 SHALL 仅用于
上传**安装包（APK）**，MUST NOT 传输任何账本数据、备份文件或用户口令。
系统 SHALL 继续满足「App 不声明 `android.permission.INTERNET`」，
`check_manifest_privacy.py` MUST 在出包阶段之后仍 PASS。
坚果云账号与应用密码、正式签名密钥 SHALL 取自 GitHub Secrets，
MUST NOT 写进仓库、日志或 Job Summary。

#### Scenario: 出包后检查 App 权限

- **GIVEN** release 工作流已成功上传 APK 到坚果云
- **WHEN** 检查 `AndroidManifest.xml` 与 CI 静态检查结果
- **THEN** 仍无 `INTERNET`、无存储权限
- **AND** `USE_BIOMETRIC` 仍是白名单里唯一的权限

#### Scenario: 日志里找凭据

- **GIVEN** 一次完整的 release 构建日志与 Job Summary
- **WHEN** 搜索 `JGY_USER` / `JGY_APP_PASSWORD` / keystore 口令
- **THEN** 只能看到 Secret 名与补齐路径，看不到取值
- **AND** `check_workflow_syntax.py` 未发现硬编码凭据字面量

**理由**：「不信任第三方」指账本数据不托管；runner 上传的是编译产物。
把两者混为一谈会做出错误设计，但反过来也不能因为「CI 能联网」
就放松 App 的零网络权限铁律——明确写死边界才不会在下个阶段被误放宽。

### Requirement: 快捷方式与周期补记不新增权限或网络依赖

阶段 6a 的静态快捷方式、冷启动补记、模板与规则落库
SHALL NOT 新增任何 `uses-permission`，SHALL NOT 引入
含网络行为的依赖，SHALL NOT 改变 `allowBackup=false` 与数据库备份排除规则。
`check_manifest_privacy.py` MUST 在阶段 6a 之后仍 PASS。

#### Scenario: 检查 Manifest

- **GIVEN** 阶段 6a 已落地
- **WHEN** 运行 `check_manifest_privacy.py`
- **THEN** 仍只有 `USE_BIOMETRIC` 一个 normal 级权限
- **AND** 无 `INTERNET`、无存储权限、无 `SCHEDULE_EXACT_ALARM`

#### Scenario: 依赖清单

- **GIVEN** `app/build.gradle.kts`
- **WHEN** 与阶段 5 对比
- **THEN** 新增依赖数为 0
- **AND** 未出现 WorkManager / AlarmManager 相关库

**理由**：低摩擦功能最容易顺手引入后台权限与上报 SDK；
「不信任第三方」这条铁律要在每个阶段重新确认一次。
