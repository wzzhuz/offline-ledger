# Delta for 隐私与安全基线

## ADDED Requirements

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

## MODIFIED Requirements

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
