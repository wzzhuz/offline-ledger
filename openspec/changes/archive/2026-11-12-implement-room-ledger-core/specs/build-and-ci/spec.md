# Delta for 构建与持续集成

## ADDED Requirements

### Requirement: 阶段 1 单测独立于 APK 构建执行

`build.yml` SHALL 继续执行 `gradle testDebugUnitTest`，
且阶段 1 新增的纯 JVM 单测 SHALL 全部通过。

#### Scenario: 余额算法单测失败

- **GIVEN** `LedgerEngine` 的净变动算错一个符号
- **WHEN** CI 运行单元测试
- **THEN** `testDebugUnitTest` 失败并使 job 失败
- **AND** 不出现「APK 编译通过但算法错误被放过」

**理由**：账本里最难查的是「编译能过、不崩溃、数字静默全错」，
纯 JVM 单测是唯一能在无模拟器环境里拦住它的手段。

---

### Requirement: 静态检查覆盖事务与统计口径

`build.yml` SHALL 在编译前执行 `check_transaction_annotations.py` 与
`check_stat_rules.py`，任一失败 SHALL 中断构建。

#### Scenario: 有人新增写方法但忘了包事务

- **GIVEN** Repository 新增一个直接调用 DAO 写操作的方法
- **WHEN** CI 执行静态检查
- **THEN** 脚本报错并使 job 失败
- **AND** 不会产出一个「流水已记但余额未变」的版本

**理由**：Room 在 Repository 方法上的 `@Transaction` 不生效，
这类错误编译期发现不了，只能靠静态检查兜底。
