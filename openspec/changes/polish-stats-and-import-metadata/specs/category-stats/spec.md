# category-stats Delta

## MODIFIED Requirements

### Requirement: 下钻明细复用组合筛选且不全量加载
统计页点击分类查看流水时，系统 SHALL 跳转到明细 Tab 并带入 `categoryId`（或
`rootCategoryId`）、当前 `yearMonth` 与当前币种，复用既有 `applyFilter` /
`PagingData` / 官方下拉刷新，MUST NOT 在弹窗内维护第二套分页列表；明细页 MUST
显示可一键清除的筛选 chip；筛选状态 MUST 保持 Activity 作用域，切 Tab 返回不丢；
一级到二级构成的选择弹窗可保留，但点二级 MUST 跳明细而非在弹窗里列流水。

#### Scenario: 点二级分类跳明细
- **WHEN** 使用者在占比卡「按小类」视图点某个扇区
- **THEN** 切到明细 Tab，列表按该二级分类 + 当前币种筛选，顶部出现可清除的筛选 chip

#### Scenario: 清除筛选
- **WHEN** 使用者点明细页的筛选 chip
- **THEN** `clearFilter()` 被调用，列表回到未筛选状态，统计页返回时条件不残留
