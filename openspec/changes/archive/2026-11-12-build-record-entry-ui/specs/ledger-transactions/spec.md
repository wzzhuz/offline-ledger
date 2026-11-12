# Delta for 账务联动与一致性

## ADDED Requirements

### Requirement: 组合筛选走单条参数化查询

明细与核对视图 SHALL 由一条带可选参数的 `@Query` 覆盖时间 / 账户 / 类型 /
一级 / 二级分类 / 金额区间 / 备注全文的全部组合，
所有值 SHALL 通过参数绑定传入，MUST NOT 用字符串拼接 WHERE 值。
查询 SHALL 通过 `LEFT JOIN` 一次取回分类名、主题色与账户名。

#### Scenario: 组合筛选

- **GIVEN** 用户设置上月 + 微信零钱 + 一级「餐饮」+ 金额 20–100 元 + 备注含「午饭」
- **WHEN** 执行查询
- **THEN** 返回同时满足全部条件的流水
- **AND** 每行已带分类名与账户名，渲染时不再逐行查库

#### Scenario: 注入面

- **GIVEN** 备注搜索框输入 `' OR 1=1 --`
- **WHEN** 执行查询
- **THEN** 它被当作字面备注子串匹配
- **AND** 不返回全部流水

**理由**：动态组合筛选最容易写成拼 SQL；参数化绑定既零注入面，
又让一条 SQL 覆盖所有组合，避免 `@RawQuery` 或多份 `@Query` 的组合爆炸。

---

### Requirement: 分页查询不加载全表

明细列表 SHALL 由 `PagingSource<Int, RecordRow>` 以 `LIMIT :limit OFFSET :offset`
按 `localDate DESC, id DESC` 取页，MUST NOT 调用 `getAll()` 后在 Kotlin 里分组。
`nextKey` SHALL 按 `offset + rows.size` 推进，`getRefreshKey` SHALL 以
`anchorPosition` 为中心避免刷新跳回顶部。

#### Scenario: 5 万条首屏

- **GIVEN** `records` 含 5 万条流水
- **WHEN** 打开明细首页
- **THEN** 只查询首屏 60 条并命中 `(localDate, id)` 索引
- **AND** 内存占用不随总记录数线性增长

**理由**：行数不是瓶颈，几万对象 inflate 才是；分页键的推进方式写错
会在滚动时重复或漏掉记录。
