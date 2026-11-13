# Delta for 多级分类与统计

## ADDED Requirements

### Requirement: 环形图支持一级与二级切换并落地三级下钻

统计页 SHALL 提供「按大类 / 按小类」切换，一级口径 SHALL 走
`GROUP BY rootCategoryId`，二级口径 SHALL 走 `GROUP BY categoryId`，
两者 SHALL 按金额降序展示占比与具体金额。
点击一级扇区 SHALL 用 `WHERE rootCategoryId = :rootId GROUP BY categoryId`
展开该大类下二级构成；点击二级项 SHALL 进入该分类明细列表。
系统 MUST NOT 用递归或闭包表实现层级。

#### Scenario: 一级扇区下钻

- **GIVEN** 月报中「餐饮」为最大支出扇区
- **WHEN** 点击「餐饮」
- **THEN** 展开食材 / 正餐 / 外卖等二级构成及各自金额
- **AND** 金额的合计等于「餐饮」扇区金额

#### Scenario: 二级进入明细

- **GIVEN** 已展开「餐饮」的二级构成
- **WHEN** 点击「食材」
- **THEN** 显示该分类的流水明细列表
- **AND** 明细走参数化 SQL 与 LIMIT，不全量加载

#### Scenario: 切换统计口径

- **GIVEN** 用户切到「按小类」
- **WHEN** 查看环形图
- **THEN** 扇区按 `categoryId` 聚合而非 `rootCategoryId`
- **AND** 合计与一级口径相等

**理由**：使用者明确要求「统计时候支持按一级分类统计，也支持按二级分类的进行统计」。
`rootCategoryId` 冗余列让两条 `GROUP BY` 都不需要递归；三级下钻路径
让「看完图还得重新翻账」不再发生。

---

### Requirement: 合计一致性在 UI 与单测双保险

系统 SHALL 校验「一级合计 = 二级合计 = 总支出」，
不一致时 SHALL 在统计页显示警告条与差额（单位分），MUST NOT 静默显示图表。
校验逻辑 SHALL 为纯 JVM 函数并 SHALL 有对应单元测试。

#### Scenario: 合计一致

- **GIVEN** 若干笔分布在「餐饮 → 食材 / 正餐」与「交通 → 打车」的支出
- **WHEN** 生成月报
- **THEN** 三个口径合计相等
- **AND** 不显示警告条

#### Scenario: 合计不一致

- **GIVEN** 一级合计与总支出相差 100 分
- **WHEN** 生成月报
- **THEN** 显示红色警告条与「差额 100 分」
- **AND** 单测中该构造使断言失败

**理由**：合计能对得上是「账要算得准」在统计维度的延伸；
统计口径写错不会崩溃、只会算错数，必须靠 UI 提示 + 单测两条线拦住。

---

### Requirement: 下钻明细复用组合筛选且不全量加载

二级下钻的明细列表 SHALL 复用 `pagedFiltered(...)` 的参数化 SQL，
SHALL 带 `LIMIT`，MUST NOT 一次性加载该分类的全部流水。
明细 SHALL 复用唯一统计口径，已移入 `trash` 的流水 MUST NOT 出现。

#### Scenario: 大类下的大量流水

- **GIVEN** 「餐饮」下有 3000 笔流水
- **WHEN** 下钻查看「食材」明细
- **THEN** SQL 只返回 LIMIT 指定的行数
- **AND** 内存不随该分类总记录数增长

#### Scenario: 口径一致

- **GIVEN** 某笔 `excludeFromStats = 1` 的流水
- **WHEN** 下钻查看明细
- **THEN** 该笔不出现在列表中
- **AND** 与统计页合计口径一致

**理由**：下钻是「月度哪类支出多 → 逐笔核对」的连续路径，
不能因为多一层交互就绕开阶段 2 定下的分页与口径约束。
