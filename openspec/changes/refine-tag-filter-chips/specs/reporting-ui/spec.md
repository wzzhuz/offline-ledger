# reporting-ui · delta

## MODIFIED Requirements

### Requirement: 筛选面板统一网格与紧凑输入框

筛选面板 SHALL 用统一的「固定标签列 + 值 + 指示器」行网格组织全部维度，
同区域 MUST NOT 混用 filled 与 outlined 输入框；行内容高度 SHALL 落在同一量级，
可点区域 SHALL ≥ 40dp。选项数量较多的维度（账户 / 一级 / 二级分类 / **标签**）
SHALL 通过整行点击打开带搜索的全量单选弹层，MUST NOT 用横向滚动或多列纵向排列
把面板高度做成随选项数增长，也 MUST NOT 为该维度单独留一个手输文本框。
输入框 SHALL 用 `placeholder` 而非浮动 `label`，
placeholder 与输入文字 SHALL 同字号；输入框高度 SHALL 用 `heightIn(min)` 调整，
MUST NOT 用固定 `height` 导致占位文字被裁切。
日期条件 SHALL 通过日期选择器点选，MUST NOT 要求用户手工输入 `YYYYMMDD`；
日期读写 SHALL 用 UTC 锚点往返，MUST NOT 因设备时区串一天。

#### Scenario: 维度一屏可见

- **GIVEN** 6.73 寸竖屏、账户 10 个以上、二级分类 20 个、标签 12 个
- **WHEN** 打开筛选面板
- **THEN** 全部维度一屏可见、无需上滑
- **AND** 各维度左边界对齐、行高量级一致，标签维度也是「标签 › 取值」整行

#### Scenario: 输入框聚焦

- **GIVEN** 用户点击金额区间 / 备注 / 交易对方输入框
- **WHEN** 进入编辑态
- **THEN** 边框不被浮动标签撑高，placeholder 完整不裁切
- **AND** 字号与网格内其他文字一致

#### Scenario: 标签不再手输

- **GIVEN** 账本里有标签「家用」「给老婆」「旅行」
- **WHEN** 打开筛选面板点「标签」行
- **THEN** 弹出带搜索的全量单选弹层，首项为「全部标签」，其余为现有启用标签
- **AND** 选中「家用」后该行显示「家用」，应用后按该标签精确筛选
- **AND** 面板内不存在独立的标签名文本输入框

#### Scenario: 筛选值回填

- **GIVEN** 用户已设置金额区间 20–100 元、标签为「家用」
- **WHEN** 关闭面板后再次打开并点「应用」
- **THEN** 输入框仍显示 20.00 / 100.00，标签行仍显示「家用」
- **AND** 条件不被静默清空

## ADDED Requirements

### Requirement: 明细行标签采用轻量行内标记

明细列表与流水详情页的账单标签 SHALL 用轻量只读行内标记呈现，
MUST NOT 使用 `AssistChip` / `FilterChip` 等操作型组件，
MUST NOT 按标签数量换行撑高明细行。明细行 SHALL 单行展示最多 2 个标记，
超出部分 SHALL 以「+N」计数收尾；详情页 SHALL 可展示全量标记。
两处 MUST 共用同一组件与同一配色，MUST NOT 出现两套视觉重量。

#### Scenario: 三个标签的明细行

- **GIVEN** 一笔流水挂了「家用」「给老婆」「旅行」三个标签
- **WHEN** 查看明细列表
- **THEN** 该行只显示两枚小标记 + 「+1」，单行不换行，主标题与金额不被挤压

#### Scenario: 详情页全量

- **GIVEN** 同一笔流水
- **WHEN** 打开流水详情
- **THEN** 三个标签全部可见，配色与列表一致

### Requirement: 生效筛选条件统一为可独立清除的 chips

明细页顶部 SHALL 把**每一个**生效筛选条件各呈现为一枚「条件名：取值」chip，
MUST NOT 用互斥 `when` 只展示分类 / 交易对方 / 账户三类。
每枚 chip SHALL 带独立的清除图标，点该图标 SHALL 只清除该条件，
MUST NOT 清空其余筛选条件；点 chip 文字区 SHALL 打开筛选面板。
chips 行 SHALL 横向滚动，MUST NOT 挤压列表高度；末尾 SHALL 保留「清空」全清入口。
来自统计的只读周期展示名 SHALL 也呈现为一枚可清除 chip。

#### Scenario: 多条件并存

- **GIVEN** 同时设置了分类「餐饮 / 早餐」、标签「家用」、币种 CNY、备注含「周末」
- **WHEN** 查看明细页顶部
- **THEN** 四枚 chip 同时可见，各自显示条件名与取值，互不相斥

#### Scenario: 只清自己

- **GIVEN** 上述四枚 chip
- **WHEN** 点「标签：#家用」那枚的 ✕
- **THEN** 仅 `tagName` 被清空并重建 Pager，其余三枚 chip 与条件保持不变
- **AND** 列表刷新为按剩余三个条件筛选的结果

#### Scenario: 分类联动

- **GIVEN** 选中了二级分类（同时带 `rootCategoryId` / `categoryId` / 展示名）
- **WHEN** 点分类 chip 的 ✕
- **THEN** 三个字段一并清空，MUST NOT 残留只按一级过滤的隐式条件

### Requirement: 筛选字段清除走单一联动出口

`RecordFilter` SHALL 提供 `FilterField` 枚举与 `clearField()` 作为清除联动的**唯一**
实现，UI 侧 MUST NOT 各自 `copy()` 拼字段。清除后 SHALL 按 `resolveAllMonths()`
同一口径重算 `allMonths` 并递增 `revision` 触发 Pager 重建。
`activeCount()` SHALL 包含 `tagName`，且 MUST NOT 包含三条只读展示名、
`allMonths`、`granularity` 等视图模式字段。

#### Scenario: 标签计入筛选角标

- **GIVEN** 只按标签「家用」筛选
- **WHEN** 查看筛选入口角标
- **THEN** 显示「筛选 (1)」，且月概览按既有「有筛选」口径隐藏

#### Scenario: 清掉最后一个条件

- **GIVEN** 只有「标签：家用」一个条件
- **WHEN** 点该 chip 的 ✕
- **THEN** `activeCount()` 归零、`allMonths` 回落到当前月视图口径、`revision` 递增
- **AND** 与直接点「清空筛选」的结果在筛选条件上等价

#### Scenario: 时间范围联动

- **GIVEN** 显式日期范围 + 来自统计的周期展示名
- **WHEN** 清除时间范围 chip
- **THEN** `fromDate` / `toDate` / `periodDisplayName` 一并清空，
  MUST NOT 残留跨月过滤
