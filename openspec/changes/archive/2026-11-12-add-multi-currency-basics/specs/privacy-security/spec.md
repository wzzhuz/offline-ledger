# Delta for 隐私与安全基线

## ADDED Requirements

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

## MODIFIED Requirements

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
