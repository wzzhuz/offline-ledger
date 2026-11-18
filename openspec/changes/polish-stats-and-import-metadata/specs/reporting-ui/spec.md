# reporting-ui Delta

## MODIFIED Requirements

### Requirement: 图表点数值常驻显示
年度与多年趋势在竖屏 MUST 用简化值（万 / k）常驻标注关键点（峰值、末点、选中点），
奇偶上下交错避免重叠；MUST NOT 12 个点全量常驻完整金额，MUST NOT 画 12 根虚线；
点按 MUST 出现图下随选更新的文字汇总与**一条**瞬时竖向引导线，MUST NOT 用
AlertDialog 遮挡图表；横屏放大页的完整数值表 MUST 保留；跨币种混算仍 MUST NOT。

#### Scenario: 年度竖屏读数值
- **WHEN** 统计页切到年度、竖屏查看趋势卡
- **THEN** 峰值 / 末点 / 选中点显示简化值且上下错开，互不重叠

#### Scenario: 点按某月
- **WHEN** 使用者点趋势图某个月的数据点
- **THEN** 该点出现一条竖向虚线，图下显示该月收入 / 支出 / 净结余，可清除选中
