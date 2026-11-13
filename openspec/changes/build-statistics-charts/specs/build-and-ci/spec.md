# Delta for 构建与持续集成

## ADDED Requirements

### Requirement: 统计口径静态检查覆盖阶段 4 新查询

`scripts/check_stat_rules.py` 的统计查询清单 SHALL 覆盖
`subcategoryTotalsUnderRoot`，并要求其 `@Query` 带 `excludeFromStats = 0`。
`check_transaction_annotations.py` 的 `WRITE_METHODS` SHALL 保持 13 个不变
（阶段 4 不新增写方法）。

#### Scenario: 新查询漏了口径

- **GIVEN** `subcategoryTotalsUnderRoot` 的 `@Query` 未带 `excludeFromStats = 0`
- **WHEN** CI 运行静态检查
- **THEN** `check_stat_rules` 失败并使 job 失败
- **AND** 五个静态检查仍全部在编译前执行

#### Scenario: 写方法清单未被误扩

- **GIVEN** 阶段 4 只新增只读查询
- **WHEN** 查看 `WRITE_METHODS`
- **THEN** 仍为 13 个
- **AND** 不因「新增了 DAO 方法」就顺手把只读方法算进事务清单

**理由**：硬编码清单最典型的失效方式是「加了新方法忘了登记」；
反过来，把只读方法误登记进写清单也是一处规格与实现不同步。

---

### Requirement: 图表数据准备层为纯 JVM 且可单测

CI 的 `testDebugUnitTest` SHALL 覆盖合计一致 / 不一致、
闰年 2 月与大小月补 0、空月补 0、全 0 与单系列归一化、
扇区角度与跨 0° 点击命中、颜色解析合法 / 非法，
且这些测试 SHALL 为纯 JVM 测试，不依赖模拟器、Room 或 `android.graphics`。

#### Scenario: 闰年补零

- **GIVEN** 2028 年 2 月（29 天）只有 2 月 3 日一笔流水
- **WHEN** 生成每日序列
- **THEN** 返回 29 个点
- **AND** 其余 28 天支出均为 0

#### Scenario: 合计不一致被拦住

- **GIVEN** 有人把 `checkTotals` 改成只比两个口径
- **WHEN** 运行单元测试
- **THEN** 三口径断言失败并使 job 失败
- **AND** 不产出一个「静默显示错数」的版本

**理由**：统计口径与补 0 的错误不崩溃、编译也能过，
只能靠纯 JVM 单测在无模拟器环境里拦住。
