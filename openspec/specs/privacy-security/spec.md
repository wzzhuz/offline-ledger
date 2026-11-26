# 隐私与安全基线 Specification

## Purpose

把「不信任第三方、资产数据高度敏感」从动机落成可核查的硬约束。本模块三条核心约束违反任意一条即视为项目失败。

---

## Requirements

### Requirement: 不声明网络权限

**当前状态**：AndroidManifest.xml 不声明 `android.permission.INTERNET`，
因为现有功能无需联网、且尚无使用者授权；**这不是形式禁令**。
系统 SHALL NOT 包含账号体系、云同步、远程配置或「默认回传账本数据且用户不可控」的服务端依赖。
红线（2026-11-17 使用者澄清）是「默认把数据传到厂商服务器且用户无法控制」，
与「Manifest 是否出现 INTERNET」「是否新增 Gradle 依赖」无关：
弃用钱迹 / 随手记 / 鲨鱼记账正是因为前者。

#### Scenario: 安装前检查

- **GIVEN** 用户在系统设置查看应用权限列表
- **WHEN** 查看
- **THEN** 当前无网络相关权限
- **AND** 应用功能不依赖联网

#### Scenario: 使用者显式授权联网能力

- **GIVEN** 使用者明确要求某个需要联网的自用能力
- **WHEN** 在 `openspec/project.md` 写入「权限显式授权」，
  说明用途、数据流向与用户如何控制
- **THEN** 允许声明 `INTERNET` 并引入对应依赖
- **AND** 静态检查按授权放行，不以「零网络权限」为由否决
- **AND** 未经该授权的任何联网行为仍 MUST NOT 出现

**理由**：原 Requirement 把「无 INTERNET」写成不可违反的自我证明，
混淆了**数据流向**与**权限形式**。自用 App 的控制权在使用者手里，
由使用者自己决定不主动往不可信三方上传；再拿权限字面当铁律，
会拦掉纯 UI 库与将来可能的自用能力。

---

### Requirement: 禁止引入含网络行为的依赖

新增依赖 SHALL 记录坐标、许可证与传递依赖审计。
系统 MUST NOT 引入「默认回传数据且用户不可控」的 SDK——
广告、统计埋点、崩溃上报、远程配置、账号体系、用户行为分析等，
**这类依赖不因授权而放行**。
纯 UI / 本地能力库（如 `sh.calvin.reorderable:reorderable:3.1.0`，Apache-2.0，
传递依赖仅 kotlin-stdlib-common 与 compose runtime/animation/foundation）允许引入，
不构成违规：Gradle 依赖只在构建期解析，不产生 App 运行时联网。

#### Scenario: 依赖评审

- **GIVEN** 有人提议引入一个第三方库
- **WHEN** 评审
- **THEN** 按「是否默认回传厂商数据 / 用户是否可控 / 传递依赖有无网络客户端」判断
- **AND** 记录坐标与许可证
- **AND** 依赖树中不出现默认回传类 SDK
- **AND** MUST NOT 以「零新增依赖」为由否决纯本地能力库

#### Scenario: 引入列表拖拽库

- **GIVEN** 第 19 批引入 `reorderable:3.1.0` 替换自研拖拽手势层
- **WHEN** CI 执行静态检查
- **THEN** 检查通过
- **AND** Manifest 仍无 `INTERNET`
- **AND** 依赖树中不出现网络客户端

**理由**：原 Requirement 用「不出现网络客户端 + 零新增依赖」表达边界，
把手段当成了目的。真正的暗门是默认回传 SDK，不是依赖数量。

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

### Requirement: 本地数据库为明文，边界由数据流向界定

账本数据库 SHALL 以平台默认 SQLiteOpenHelper 打开（明文），
系统 MUST NOT 再引入 SQLCipher / `SupportOpenHelperFactory` 或任何数据库文件级加密方案。
保护边界 SHALL 由「不上传第三方、关闭系统自动备份并排除数据库、应用沙箱隔离、应用锁防瞥视」共同构成。
系统 MUST NOT 以「本地文件为明文」为由要求联网或引入回传类 SDK；
导出 JSON 备份的口令加密 MUST NOT 因本条倒退。

#### Scenario: 直接读取数据库文件

- **GIVEN** 有人取得应用数据目录下的 db 文件
- **WHEN** 用普通 SQLite 工具打开
- **THEN** 能读到明文账目——这是使用者已明确接受的对价，不算违规
- **AND** 该风险由应用沙箱 / 关闭自动备份 / 应用锁承担，威胁模型不含设备丢失与物理取证

#### Scenario: 误引入加密依赖

- **GIVEN** 有人重新加入 `sqlcipher-android` 或数据库口令派生逻辑
- **WHEN** 构建与静态检查执行
- **THEN** 该改动 MUST 被拒绝，除非使用者再次显式授权并同步修订本文件

**理由**：2026-11-22 使用者显式授权——自用 App 装在个人可信手机，威胁模型不含设备丢失 / 取证 / 物理接触；
实测加密开库链（so 加载 + Keystore + 密钥派生）导致冷启动 1~2s，去掉后基本无感。
隐私红线是「默认把数据传到厂商服务器且用户不可控」，不是本地文件是否加密（见本文件「隐私边界是数据不离开设备而非文件必须加密」）。

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

JSON 备份 SHALL 默认口令加密（已实现并 MUST NOT 倒退），
SHALL 通过 SAF 由用户选择保存位置，
系统 MUST NOT 在应用私有目录留下明文账本备份。
**明文导出不构成隐私基线违规**：账本数据 MUST NOT 离开用户设备、
MUST NOT 上传第三方服务器；本地文件是否加密、放在哪里由用户决定。
若将来提供 CSV 等其它导出格式，SHALL 通过 SAF 写入用户自选位置，
MUST NOT 引入网络上传。

#### Scenario: 导出加密备份

- **GIVEN** 用户在设置页点击「导出加密备份」
- **WHEN** 输入口令并选择位置
- **THEN** 生成的文件用文本编辑器打开看不到明文分类名与金额
- **AND** 应用私有目录不新增明文 JSON

#### Scenario: 不以明文为由拦截本地导出

- **GIVEN** 有人提议以「明文文件有风险」为由禁用某种本地导出格式
- **WHEN** 评审
- **THEN** 评审改按「是否上传第三方」判断
- **AND** 不把文件是否加密当作隐私合规条件

**理由**：原 Requirement 把「JSON 加密」与「CSV 默认关闭 + 强制二次确认 +
明文文件勿放公共目录」绑在一起，后半段的前提是「明文=风险」。
6c-1 使用者正式推翻该前提。本 MODIFIED 保留已交付的 JSON 加密能力与
「私有目录不留明文备份」，删除以明文风险为前提的约束与
「v1 SHALL NOT 提供 CSV 导出入口」的阶段版本号绑定，
消除理由与实现、理由与使用者意图的双重不一致。

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

### Requirement: 数据库以平台默认 SQLiteOpenHelper 打开

系统 SHALL 用 Room 默认 `SQLiteOpenHelper` 打开 `offline_ledger.db`，
MUST NOT 注入自定义 `SupportOpenHelperFactory`、MUST NOT 加载 `sqlcipher` native 库、
MUST NOT 为打开数据库派生或保管任何口令。
库名与 `AppDatabase.version` SHALL 保持不变，以免备份排除规则失效。

#### Scenario: 冷启动开库

- **GIVEN** 杀进程后冷启动
- **WHEN** `DatabaseModule.provideDatabase` 执行
- **THEN** 只做平台 Room 构建，无 so 加载、无 Keystore IPC、无密钥派生
- **AND** 明细列表、顶部汇总与每日合计不经额外等待即可显示

#### Scenario: 旧加密库残留

- **GIVEN** 设备上残留第 49 批及之前的加密 `offline_ledger.db`
- **WHEN** 明文 Room 尝试打开
- **THEN** 必须走「导出加密备份 → 卸载 → 安装 → 导入重建」，MUST NOT 宣称可覆盖安装

**理由**：第 50 批去加密后，第一条铁律靠 Manifest / 备份排除 / 应用沙箱落地，不再靠文件加密；
`allowBackup=false` 与两个 section 的 `domain="database"` 排除 MUST NOT 因去加密而回退。

---

### Requirement: 不再保留数据库口令与 Keystore 数据库密钥

系统 MUST NOT 再为数据库生成 / 保管口令，
MUST NOT 存在 `DatabaseKeyManager` 或等价的 Keystore 数据库密钥逻辑。
Android Keystore MAY 继续用于应用锁 PIN 相关的既有能力，
但 MUST NOT 与本地数据库打开路径耦合。
导出备份的口令仍由使用者自设、不落盘，与数据库完全无关。

#### Scenario: 应用重启后打开同一数据库

- **GIVEN** 应用进程被杀死后重新启动
- **WHEN** Room 打开 `offline_ledger.db`
- **THEN** 无需任何口令即可打开同一明文库
- **AND** 账目与重启前完全一致

**理由**：口令派生与 Keystore 解密正是冷启动 1~2s 的来源；
去加密后不再需要数据库口令，`SecureBackupCodec` 的备份口令是独立且唯一保留的加密边界。

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
MUST NOT 宣称等价强认证——本地账本文件为明文，应用锁只防瞥视，不等价设备级强认证。

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

`check_manifest_privacy.py` SHALL 把「权限 / 需评审依赖」从无条件 FAIL
改为 **显式授权闸门**：`openspec/project.md` 含「权限显式授权」标记时记提示并放行，
不含时 FAIL 并提示先写授权（用途 / 数据流向 / 用户控制方式）。
「默认回传类 SDK」清单 SHALL 始终硬禁止，不受授权影响。
白名单检查 MUST 精确匹配 `<uses-permission>` 标签并先剥除 XML 注释，
MUST NOT 用宽泛子串匹配（避免阶段 1 归档那两次自误报）。

#### Scenario: 有人加回网络权限

- **GIVEN** 有人在 Manifest 里加 `android.permission.INTERNET`
- **WHEN** CI 执行静态检查且 `project.md` 无显式授权
- **THEN** 检查失败并使 job 失败
- **AND** 报错指出「需先在 project.md 写权限显式授权」
- **AND** 不产出含未授权网络权限的版本

#### Scenario: 已授权时放行

- **GIVEN** `project.md` 已按使用者要求写明权限显式授权
- **WHEN** CI 执行静态检查
- **THEN** 记录「已授权」提示并 PASS

#### Scenario: 注释里提到 USE_BIOMETRIC

- **GIVEN** Manifest 注释解释了为什么需要该权限
- **WHEN** 执行静态检查
- **THEN** 不因注释里的权限名误报
- **AND** 五个静态检查仍全部在编译前执行

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

### Requirement: 预算与报销不新增权限或网络依赖

阶段 6b 的预算管理、SETTLEMENT 流水、净支出统计
SHALL NOT 新增任何 `uses-permission`，
SHALL NOT 引入含网络行为的依赖或任何新 Gradle 依赖，
SHALL NOT 改变 `allowBackup=false` 与数据库备份排除规则。
超支提醒 SHALL 只在应用内呈现，MUST NOT 申请 `POST_NOTIFICATIONS`。
`check_manifest_privacy.py` MUST 在阶段 6b 之后仍 PASS。

#### Scenario: 检查 Manifest

- **GIVEN** 阶段 6b 已落地
- **WHEN** 运行 `check_manifest_privacy.py`
- **THEN** 仍只有 `USE_BIOMETRIC` 一个 normal 级权限
- **AND** 无 `INTERNET`、无存储权限、无 `POST_NOTIFICATIONS`

#### Scenario: 依赖清单

- **GIVEN** `app/build.gradle.kts`
- **WHEN** 与阶段 6a 对比
- **THEN** 新增依赖数为 0

**理由**：「不信任第三方」这条铁律要在每个阶段重新确认；
小组件与通知栏提醒正因为这条约束被移入 6c。

### Requirement: 隐私边界是数据不离开设备而非文件必须加密

系统的隐私边界 SHALL 是：账本数据 MUST NOT 上传第三方服务器、
MUST NOT 托管给厂商、MUST NOT 通过任何网络通道外发。
本地导出的文件由用户自行保管，
**明文导出 SHALL NOT 被判定为违反隐私基线**。
JSON 备份 SHALL 保持已实现的口令加密能力，MUST NOT 倒退成明文。
6c-1 SHALL NOT 新增任何 `uses-permission`、Gradle 依赖或网络行为。

#### Scenario: 导出明文文件

- **GIVEN** 用户导出一份本地账本文件
- **WHEN** 检查应用行为
- **THEN** 文件只写入用户通过 SAF 选择的位置
- **AND** 应用没有 `INTERNET` 权限、没有发起任何上传

#### Scenario: 检查权限清单

- **GIVEN** 6c-1 已落地
- **WHEN** 运行 `check_manifest_privacy.py`
- **THEN** 仍只有 `USE_BIOMETRIC` 一个 normal 级权限
- **AND** 新增依赖数为 0

**理由**：使用者在 6c-1 明确澄清了最初「不信任第三方」的所指——
不可接受的是厂商服务器托管，不是本地文件明文。
把边界写清楚可避免以后用错误理由拦功能，也防止有人反向把
「明文可接受」误解成「可以加网络同步」。

### Requirement: 明文导出只走用户自选位置且不外发

CSV 明文导出 SHALL 通过 SAF 写入用户自选位置，
系统 MUST NOT 声明 `android.permission.INTERNET`、
MUST NOT 申请任何存储权限、MUST NOT 向任何网络地址上传数据、
MUST NOT 在应用私有目录留下明文账本副本。
系统 MUST NOT 以「明文文件有风险」为由提供默认关闭、
强制二次确认或「勿放公共目录」提示等拦截；
导出入口 MAY 提供一次避免误触覆盖的常规确认。
`check_manifest_privacy.py` SHALL 继续 PASS。

#### Scenario: 导出 CSV 后检查应用行为

- **GIVEN** 用户导出一份 CSV 到自选目录
- **WHEN** 检查 Manifest 与网络行为
- **THEN** 无 `INTERNET` 权限、无网络上传、私有目录无副本
- **AND** 设置页说明「明文文件由你自行保管；应用不会上传任何数据」

#### Scenario: 有人再次以明文风险拦截 CSV

- **GIVEN** 有人提议禁用 CSV 或加「默认关闭 + 强制二次确认」
- **WHEN** 评审
- **THEN** 评审改按「是否上传第三方」判断
- **AND** 不把文件是否加密当作隐私合规条件

**理由**：6c-1 使用者已正式推翻「明文=风险」的前提；
本批把落地行为写死，避免错误论据以 UI 文案形式悄悄回来。

### Requirement: 收款方与默认币种不引入任何网络行为

新增 `counterparty` 自由文本与默认币种偏好 SHALL 全部留在本地，
系统 MUST NOT 声明 `INTERNET`、MUST NOT 引入网络依赖、
MUST NOT 把收款方或账户信息发往任何第三方。
收款方常用值 SHALL 由本地 SQL `DISTINCT` 得出，MUST NOT 走在线词库。

#### Scenario: 权限复核

- **GIVEN** 本批次完成
- **WHEN** 检查 Manifest 与依赖
- **THEN** 无 `INTERNET`、无新增权限、无新增依赖
- **AND** `check_manifest_privacy.py` PASS

**理由**：使用者澄清隐私边界是「数据不能存在厂商服务器」；
本地明文文件由自己掌控可以接受，但网络行为仍是不可越过的红线。

---

### Requirement: 交易对方字段不扩大数据外泄面
系统 SHALL 把交易对方作为本地流水字段保存，MUST NOT 因联想功能引入网络请求、
MUST NOT 声明 `INTERNET`；联想 SHALL 由本地 `DISTINCT ... LIKE` 查询实现。

**理由**：交易对方常含商户名，属敏感文本；隐私边界仍是「不上传第三方」。

#### Scenario: 联想离线
- **GIVEN** 用户输入「盒」
- **WHEN** 联想列表出现「盒马」
- **THEN** 结果来自本地 Room 查询，应用未发起任何网络请求

### Requirement: 隐藏金额的隐私模式 SHALL 只遮显示

系统 SHALL 提供「隐藏金额」偏好开关，开启后统计页、明细页与资产页的金额文本
SHALL 显示为 `****`；图表点的数值标签与竖轴刻度 SHALL 同步遮蔽，
MUST NOT 出现「数字藏了但图上标签仍显示真实金额」。
该偏好 MUST 存于 SharedPreferences，MUST NOT 进 Room、MUST NOT 进备份 DTO、
MUST NOT 触发 Migration、MUST NOT 修改任何流水或余额数据。
偏好改变 SHALL 通过可观察状态让已打开的页面即时重组，MUST NOT 依赖重启生效。
隐藏金额 MUST NOT 被解释为「数据已加密」或「明文导出受限」；
App MUST 继续不声明 `android.permission.INTERNET`，
已交付的口令加密 JSON 备份 MUST NOT 因本功能倒退为明文。

#### Scenario: 公共场合查看

- **GIVEN** 用户开启「隐藏金额」
- **WHEN** 在明细、统计、资产三个 Tab 之间切换
- **THEN** 所有金额文本与图表数值标签显示 `****`
- **AND** 流水笔数、日期、分类与账户名称仍正常显示

#### Scenario: 数据不变

- **GIVEN** 开启隐藏金额后记一笔 38.50 元
- **WHEN** 关闭隐藏金额并导出加密备份
- **THEN** 该笔金额仍为 3850 分，备份格式与 `schemaVersion` 不变
- **AND** 偏好本身不出现在备份文件里

#### Scenario: 重启保留

- **GIVEN** 用户开启隐藏金额后杀掉进程
- **WHEN** 冷启动重新打开
- **THEN** 隐藏金额仍生效，直到用户手动关闭

**理由**：吸收 BeeCount `hideAmountsProvider` 的公共场合可读性，但明确边界——
它只是显示层遮挡，不是加密能力、不改变数据流向，也不与「明文本地导出可接受」的
使用者口径冲突（见 `project.md` 三条铁律第 1 条隐私边界）。

---

### Requirement: 账户品牌图标资源逐项登记且缺项回退

账户图标 SHALL 由 `AccountIconCatalog` 以**编译期显式常量表**
（`when(iconKey)`）提供，MUST NOT 用运行时字符串反射 / 查表映射。
`AccountTemplate.iconKey` SHALL 为资源键位预留，
资源 SHALL 为**随包本地图形**（第 64 批为 `drawable-nodpi/` 下
128×128 白圆底 PNG），MUST NOT 引入在线下载、
MUST NOT 因此申请 `INTERNET` 权限、MUST NOT 新增 Gradle 依赖。
每一份引入的品牌图形 SHALL 在 `assets/NOTICE` 逐项登记
**作者 / 来源链接 / 授权类型 / 传递依赖**，
MUST NOT 用「来自某图标平台」代替逐项审计
（平台图标由不同作者上传、授权状态逐个不同）。
**第 64 批资源已补齐**：27 个 `iconKey` SHALL 全部映射到本地图形
（26 个 PNG，`alipay` 与 `huabei` 复用同一份）；
**未登记的 `iconKey` 仍 SHALL 回退** `CategoryIcon` 的中性圆 + 中文首字。
MUST NOT 凭空绘制或临摹银行行徽、MUST NOT 复制一羽记账的视觉资产。
Q7 的使用者判断（「指示账户归属、记账类、无授权风险」）
与公开权利声明的差异 SHALL 在 `assets/NOTICE` [C] 与 design.md
如实并列并标注**由使用者决策并承担**，
MUST NOT 写成「已解决」或「无风险」的事实结论。

#### Scenario: 未登记键位仍回退

- **GIVEN** 一个不在 27 个模板键位内的自定义账户（`iconKey = null`）
- **WHEN** 打开资产页 / 模板 sheet / 记一笔
- **THEN** 显示中性圆 + 中文首字，页面正常渲染、零网络请求

#### Scenario: 替换资源不动 UI

- **GIVEN** 后续拿到逐项授权登记后的图标文件
- **WHEN** 覆盖同名 PNG（或新增键位时补 `when` 分支与 NOTICE 条目）
- **THEN** 三页 UI 代码零改动即可显示对应图标
- **AND** 未登记的 `iconKey` 仍回退中性圆

---

---

### Requirement: 账户品牌图标来源与授权口径逐项登记

**第 64 批**：实际随包分发的每一份账户品牌图形 SHALL 有可追溯来源，
并在 `assets/NOTICE` 逐项登记 `iconKey / 品牌 / 来源文件 / 授权类型 / 商标归属`。
本批 SHALL 只采用带明确开源许可的源：
`icongo/bank-logos`（MIT，21 家中国银行 SVG）与
`Simple Icons`（CC0-1.0，微信 / 支付宝 / 美团 / 抖音月付）；
无 CC0 / 明确授权图形的京东白条 SHALL 用**本作品原创**占位图形，
MUST NOT 临摹京东商标。
**IconFont 未被采用**：UGC 图标授权逐个不同、部分图库需打赏才商用、
且需登录态导出；平台聚合页 MUST NOT 当作「可随包分发」的依据。
若后续改用 IconFont，SHALL 逐个打开详情页核验授权、留存凭证、
替换同名 PNG、并**逐行改写** NOTICE [B] 表，MUST NOT 只写平台名。
`assets/NOTICE` [C] SHALL 如实并列：使用者判断、
2026 年修订《商标法》第 73 条第 3 款的指示性使用、
MIT / CC0 **不覆盖商标权**、Simple Icons 官方商标免责、
以及未取得逐家书面授权这一事实；
MUST NOT 写成「已获授权 / 无风险 / 已解决」。
转换流程 SHALL 可由 `tools/build_account_icons.py` 复现，
该脚本为构建期工具，App 运行时 MUST NOT 执行、MUST NOT 联网。

#### Scenario: 27 个键位全部有本地图形

- **GIVEN** 模板清单的 27 个 `iconKey`
- **WHEN** 查询 `AccountIconCatalog.drawableFor()`
- **THEN** 全部返回非 null 的 `R.drawable.ic_acct_*`，共 26 个 PNG 文件
- **AND** `alipay` 与 `huabei` 指向同一份支付宝母品牌图形
- **AND** 打开资产页 / 模板 sheet / 记一笔三页均显示图形、零网络请求

#### Scenario: 换成 IconFont 必须逐项改写登记

- **GIVEN** 使用者从 IconFont 导出了某家银行图标
- **WHEN** 核验该图标详情页授权并覆盖同名 PNG
- **THEN** NOTICE [B] 表对应行 SHALL 改写为新来源与授权类型
- **AND** MUST NOT 保留「MIT / CC0」旧值、MUST NOT 只写「来自 IconFont」

---

---

### Requirement: 银行图标使用纯图形徽记且不带中文行名

**第 65 批**：随包分发的 21 家中国银行图标 SHALL 使用
`icongo/bank-logos` 的 `logos/<slug>-rect.svg` **方形纯徽记版**，
MUST NOT 使用横版 `logos/<slug>.svg`（其内容为「徽记 + 中文/英文行名」，
在账户图标位缩成细条导致文字不可辨）。
转换时 SHALL 删除 `-rect.svg` 自带的 1024 圆角纯白底 path，
使 `getbbox()` 只框住彩色徽记并放大撑满圆，
MUST NOT 保留白底（否则徽记无法放大）。
`cebbank-rect.svg` SHALL 剔除紫色 `#6B1785` 的 `ank` 字样 path，
只保留黄色徽记。
`pingan-rect.svg` 为橙色方块内「中国平安」文字标、京东白条无纯图形
官方徽记，两者 SHALL 用**本作品原创纯几何符**占位（零文字、
MUST NOT 临摹商标），并在 `assets/NOTICE` 登记为原创、非官方 logo。
4 个 Simple Icons 图形（wechat / alipay / meituan / douyin）SHALL 保持不变：
它们是满幅方块标而非横版行名细条；`alipay`「支」与 `meituan`「美团」
为官方方块文字标，本批范围按使用者原话限定为「银行 logo」，
MUST 在 NOTICE 如实说明该保留，MUST NOT 悄悄扩张范围。
`AccountIconCatalog.kt` 的 `when` 分支与 `R.drawable` 引用 SHALL 零改动，
27 个键位 / 26 个 PNG 数量 SHALL 不变，`huabei` SHALL 继续复用 `alipay`。
授权与商标口径 SHALL 与第 64 批 [C] 完全一致，
MUST NOT 因换文件而写成「已解决授权问题」。

#### Scenario: 21 家银行图标无行名文字

- **GIVEN** 资产页 / 模板 sheet / 记一笔显示 21 家银行账户图标
- **WHEN** 在 28/34dp 下查看
- **THEN** 每家只显示纯图形徽记，MUST NOT 出现「中国工商银行」等行名细条
- **AND** 徽记放大撑满白圆底，肉眼可辨

#### Scenario: 替换资源不动 Kotlin

- **GIVEN** 第 65 批只替换 `drawable-nodpi/ic_acct_*.png` 内容
- **WHEN** 编译并运行
- **THEN** `AccountIconCatalog.kt` 与三页 UI 代码零改动
- **AND** 27 个键位仍全部返回非 null，PNG 仍为 26 个

---

### Requirement: 动态色与 edge-to-edge 不引入任何网络或权限

动态色 SHALL 仅通过系统 `dynamicLightColorScheme` 读取本机壁纸派生色板，
edge-to-edge SHALL 仅调整 Window insets，
MUST NOT 申请 `INTERNET`、MUST NOT 上传壁纸或色彩信息、
MUST NOT 新增运行时权限、MUST NOT 改动既有 `AndroidManifest.xml` 权限声明。

#### Scenario: 权限清单不变

- **GIVEN** 本批落地后
- **WHEN** 核对合并后的 Manifest
- **THEN** 权限声明与 v0.2.55 逐字节一致，零新增

#### Scenario: 离线可用

- **GIVEN** 设备处于飞行模式
- **WHEN** 冷启动并切换深浅壁纸
- **THEN** 主题仍正常渲染，无任何网络请求

---

### Requirement: 本机图标导入不引入网络与权限

账户自定义图标导入 SHALL 仅通过系统文件选择器（SAF `OpenDocument()`）
读取使用者本机选中的文件，写入应用私有 `filesDir/account-icons/`。
MUST NOT 申请 `android.permission.INTERNET`、MUST NOT 新增任何运行时权限、
MUST NOT 上传图标或账户信息、MUST NOT 引入 Coil / Glide / SVG 解析库
或任何会传递引入网络栈的依赖。
导入文件 MUST NOT 随 APK 分发、MUST NOT 写进备份 / CSV / Firefly 导出，
`assets/NOTICE` SHALL 以独立段落说明「仅本机使用、许可由使用者自行确认」，
且 MUST NOT 把导入资源并入随包许可清单或改写成「已解决授权」。
预置图标资源仍按既有 [A]/[B]/[C] 段落逐项登记，零改动。

#### Scenario: 飞行模式可用

- **GIVEN** 设备处于飞行模式且无网络权限
- **WHEN** 在账户编辑页导入一张透明底 PNG 并保存
- **THEN** 图标正常显示，全程无任何网络请求、无权限弹窗

#### Scenario: 权限清单不变

- **GIVEN** 本批落地后
- **WHEN** 核对 `AndroidManifest.xml` 与 Gradle 依赖清单
- **THEN** 权限声明与 v0.2.60 逐项一致、依赖清单无新增项
