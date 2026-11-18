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


## ADDED Requirements

### Requirement: 环形图图例可点与相邻扇区可辨
分类占比卡的图例每项 SHALL 与对应扇区共用同一个下钻回调，整行可点且触摸区
≥40dp；图表色板 SHALL 按分类 id 稳定分配、低饱和但**色相分明**，相邻扇区在
真机上 MUST 可区分；MUST NOT 用同色系明度递进让相邻扇区糊成一片，
MUST NOT 回到第 4 批否掉的高饱和 6~7 跳色，MUST NOT 把 `colorHex` 开放给用户
可配；分类列表 / chip 的中性单色标识 MUST NOT 因此改变。

#### Scenario: 点图例跳明细
- **WHEN** 使用者在「按小类」视图点图例里某一项的名称或色块
- **THEN** 与点对应扇区行为一致，跳到明细 Tab 并带该分类筛选条件

#### Scenario: 相邻颜色可辨
- **WHEN** 存在 5 个以上分类扇区
- **THEN** 任意相邻两扇区与图例色块目视可区分，且同一分类多次打开颜色不变
