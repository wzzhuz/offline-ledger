# Delta for 金额精度与净资产

## ADDED Requirements

### Requirement: 图表展示金额仍由整数分换算且浮点不回写

统计页所有金额的**存储、求和、比较、差额** SHALL 保持 `Long` 分，
`BigDecimal` MAY 仅用于展示格式化。系统 MAY 用 `Float` 做
「金额比例 → Canvas 绘制角度 / 归一化坐标」的几何换算，
但该结果 SHALL 只进 `DrawScope`，MUST NOT 回写任何金额、
MUST NOT 参与统计或余额计算。合计差额 SHALL 以分为单位展示。

#### Scenario: 扇区角度换算

- **GIVEN** 三个分类支出分别为 1000 / 2000 / 3000 分
- **WHEN** 计算扇区角度
- **THEN** 三个 sweepAngle 之和为 360°
- **AND** 三个金额的和仍由 `Long` 累加、不经过浮点

#### Scenario: 合计差额展示

- **GIVEN** 一级合计与总支出相差 100 分
- **WHEN** 显示警告条
- **THEN** 文案为「差额 100 分」
- **AND** 不用浮点元值做比较

**理由**：把「绝对禁止浮点数」的边界写清楚，避免后来者
误以为金额铁律被 Canvas 破了，或反过来用 Float 去累加金额。
