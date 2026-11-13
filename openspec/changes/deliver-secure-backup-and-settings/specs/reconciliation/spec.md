# Delta for 对账与核对

## ADDED Requirements

### Requirement: 尾差阈值可由偏好配置且三层同步

设置页 SHALL 提供尾差阈值输入（单位元，默认 1.00 元、范围 0–1000 元），
偏好 SHALL 存于 SharedPreferences。**UI 分流、ViewModel 判定与
`LedgerRepository.reconcile` 的备注必填校验 SHALL 使用同一个阈值**，
MUST NOT 出现「UI 说可一键抹平、Repository 按旧默认值拒绝」。
`Reconciliation.TAIL_DIFFERENCE_THRESHOLD_CENTS` SHALL 保留为默认值常量。

#### Scenario: 调高阈值

- **GIVEN** 用户把阈值设为 5.00 元，账面与实际差 3.00 元
- **WHEN** 打开对账
- **THEN** 出现「忽略尾差并校准」一键操作
- **AND** 不要求填写备注
- **AND** 校准成功并推进锚点

#### Scenario: 三层不一致被拦住

- **GIVEN** 只改了 UI 判定、Repository 仍用 100 分
- **WHEN** 对一笔 300 分差额执行一键校准
- **THEN** 会抛「非尾差校准必须填写备注」
- **AND** 单测或评审要求三层同步后再合入

**理由**：阶段 3 把阈值写成常量时就在 spec 里注明「阶段 5 再做可配置 UI」；
真正落地时最容易只改界面，留下 UI 与 Repository 两个真相源。
