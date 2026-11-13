# Delta for 构建与持续集成

## ADDED Requirements

### Requirement: 事务静态检查清单随写方法同步扩展

`scripts/check_transaction_annotations.py` 的 `WRITE_METHODS` SHALL 覆盖
`reconcile` / `createAccount` / `updateAccountMeta` / `archiveAccount`，
连同原有 9 个共 13 个写方法。任一方法 SHALL 在其函数体内出现 `db.withTransaction`。
系统 MUST NOT 因新增写方法不在硬编码清单而漏检。

#### Scenario: 新写方法漏包事务

- **GIVEN** `reconcile` 被改成直接调 DAO 而不包事务
- **WHEN** CI 运行静态检查
- **THEN** `check_transaction_annotations` 失败并使 job 失败
- **AND** 不产出一个「能编译但对账可能半写」的版本

#### Scenario: 清单不同步

- **GIVEN** 又新增一个写方法但没加进清单
- **WHEN** 评审或检查
- **THEN** 被判定为规格与实现不同步
- **AND** 需同步更新清单与 build-and-ci spec

**理由**：硬编码清单最典型的失效方式就是「加了新方法忘了登记」；
阶段 1 只有 9 个，阶段 3 新增 4 个必须一起扩。

---

### Requirement: 对账引擎单测独立于 UI 与 Android SDK

CI 的 `testDebugUnitTest` SHALL 覆盖差额正负、尾差边界（100 / 101 分）、
可用额度、展示聚合值不被减法污染、三种金额反查区间与疑似标记解码，
且这些测试 SHALL 为纯 JVM 测试，不依赖模拟器或 Room。

#### Scenario: 尾差边界算错

- **GIVEN** `isTailDifference` 把 101 分误判为尾差
- **WHEN** CI 运行单元测试
- **THEN** 边界断言失败并使 job 失败
- **AND** 不产出一个「大额差额被一键抹平」的版本

#### Scenario: 净资产被减法倒推

- **GIVEN** 有人把 `displayTotals` 的净资产改成「总资产 − 总负债」
- **WHEN** 运行含 `includeInTotal = false` 账户的用例
- **THEN** 断言失败
- **AND** 拦住凭空虚高的净资产

**理由**：金额与差额错误不崩溃、编译也能过，只能靠纯 JVM 单测在无模拟器环境里拦住。
