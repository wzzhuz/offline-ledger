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
