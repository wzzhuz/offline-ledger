# category-stats Delta

## MODIFIED Requirements

### Requirement: 下钻明细复用组合筛选且不全量加载
统计页点击分类查看流水时，系统 SHALL 跳转到明细 Tab 并带入 `categoryId`（或
`rootCategoryId`）、当前 `yearMonth`、当前币种与**只读展示名**，复用既有
`applyFilter` / `PagingData` / 官方下拉刷新，MUST NOT 在弹窗内维护第二套分页列表；
明细页 MUST 显示带具体分类名、可一键清除的筛选 chip，MUST NOT 用维度名兜底；
筛选状态 MUST 保持 Activity 作用域，切 Tab 返回不丢。
组合筛选面板 SHALL 以一行「分类」显示已选的具体名称并提供带搜索的全量单选
弹层（一级 + 「一级 / 二级」），使已设的每一项可见、可改、可清；二级分类下钻
SHALL 同时带入父级 `rootCategoryId`。
分类占比卡的扇区与**图例** SHALL 都能触发同一份下钻；排行卡的三个维度
（分类 / 收款方 / 账户）SHALL 都能跳明细，收款方按包含匹配、账户按 id；
排行卡一级分类 SHALL 可内联展开二级（`rememberSaveable` 保存展开态），点二级
SHALL 直接跳明细，MUST NOT 回到弹窗内长列表。
一级到二级构成的**选择**弹窗可保留，但点二级 MUST 跳明细而非在弹窗里列流水。

#### Scenario: 点二级分类跳明细
- **WHEN** 使用者在占比卡「按小类」视图点某个扇区或对应图例
- **THEN** 切到明细 Tab，列表按该二级分类 + 当前币种筛选，顶部出现显示具体名称的可清除筛选 chip

#### Scenario: 筛选面板还原分类条件
- **WHEN** 下钻后打开组合筛选面板
- **THEN** 「分类」行显示具体名称（一级或「一级 / 二级」），可改可清，已设项计数与 chip 一致

#### Scenario: 排行三维下钻
- **WHEN** 排行卡切到收款方 / 账户并点某一行
- **THEN** 跳到明细 Tab 且分别按包含匹配 / 账户 id 过滤，chip 标明「包含」或显示账户名

#### Scenario: 排行二级展开
- **WHEN** 排行卡点一级行的 ⌄ 展开、再点某个二级
- **THEN** 二级列表内联展示且展开态切 Tab 返回后仍在，点二级跳明细带二级 id

#### Scenario: 清除筛选
- **WHEN** 使用者点明细页的筛选 chip
- **THEN** `clearFilter()` 被调用，列表回到未筛选状态，统计页返回时条件不残留
