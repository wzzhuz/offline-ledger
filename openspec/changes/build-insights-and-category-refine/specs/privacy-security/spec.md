# privacy-security · Delta

## ADDED Requirements

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

## MODIFIED Requirements

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

