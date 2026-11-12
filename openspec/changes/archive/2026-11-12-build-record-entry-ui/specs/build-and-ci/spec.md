# Delta for 构建与持续集成

## ADDED Requirements

### Requirement: 阶段 2 依赖锁定且零网络面

`app/build.gradle.kts` SHALL 显式声明
`androidx.compose.material:material-icons-core`（由 Compose BOM 管理，实际使用的图标均在 frozen core set 内）、
`navigation-compose:2.9.0`、`lifecycle-viewmodel-compose:2.9.1`、
`paging-common:3.3.6` 与 `paging-compose:3.3.6`。
系统 MUST NOT 引入 `room-paging`、`hilt-navigation-compose`、
Vico / Coil / Glide 或任何含网络行为的依赖。

#### Scenario: 图标 artifact 缺失

- **GIVEN** Compose BOM 2026.06 不再通过 `material3` 传递图标 artifact
- **WHEN** `assembleDebug` 编译
- **THEN** 因已显式声明 `material-icons-core` 而通过
- **AND** 依赖树中不含任何网络上报 SDK

**理由**：阶段 2 首次使用图标，漏声明会直接编译失败；
新增五个 artifact 时最容易顺手引入不需要的传递依赖。

---

### Requirement: 计算器单测独立于 UI

CI 的 `testDebugUnitTest` SHALL 覆盖计算器表达式优先级、除法舍入、
非法表达式与月份范围换算，且这些测试 SHALL 为纯 JVM 测试，不依赖模拟器。

#### Scenario: 表达式算错

- **GIVEN** `Calculator.evaluate` 把 `×` 当成 `+` 处理
- **WHEN** CI 运行单元测试
- **THEN** `12.5+3×2` 断言失败并使 job 失败
- **AND** 不产出一个「能编译但金额静默全错」的版本

**理由**：金额错误不崩溃、编译也能过，只能靠纯 JVM 单测在无模拟器环境里拦住。
