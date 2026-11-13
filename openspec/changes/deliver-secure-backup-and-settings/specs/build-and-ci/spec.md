# Delta for 构建与持续集成

## ADDED Requirements

### Requirement: 阶段 5 新增依赖与权限进入检查清单

`app/build.gradle.kts` SHALL 声明 `androidx.biometric:biometric:1.1.0`
（官方最新稳定版，1.4.0 属 alpha 线不用于 v1），
`check_manifest_privacy.py` SHALL 把 `USE_BIOMETRIC` 列为唯一允许的
`<uses-permission>`，`check_transaction_annotations.py` 的
`WRITE_METHODS` SHALL 为 17 个。
`MainActivity` SHALL 继承 `FragmentActivity` 以支持 `BiometricPrompt`。
系统 MUST NOT 为应用锁引入含网络行为的依赖。

#### Scenario: 误用 alpha 版

- **GIVEN** 有人把 biometric 依赖改成 1.4.0-alpha07
- **WHEN** 评审或 CI 检查
- **THEN** 发现与 `design.md` 锁定的稳定版不符
- **AND** 要求改回 1.1.0 或显式提 change 复核

#### Scenario: 权限白名单漏登记

- **GIVEN** 新增 `USE_BIOMETRIC` 却没扩 `ALLOWED_PERMISSIONS`
- **WHEN** CI 执行隐私检查
- **THEN** 检查失败并使 job 失败
- **AND** 不产出一个「检查器与实际 Manifest 不同步」的版本

**理由**：阶段 5 是首个同时新增依赖、权限、Activity 基类的阶段，
版本与白名单必须在 change 里锁定，不能靠记忆。

---

### Requirement: 加密层单测为纯 JVM 且覆盖失败路径

CI 的 `testDebugUnitTest` SHALL 覆盖加密往返、错口令、密文篡改、
MAGIC 不匹配、文件截断、短口令拒绝、密文不含明文分类名与金额、
同明文两次导出密文不同，且这些测试 SHALL 为纯 JVM 测试，
不依赖模拟器、Room、Android SDK 或 `android.graphics`。

#### Scenario: 错口令被拦住

- **GIVEN** 有人把解密异常吞掉并返回空字符串
- **WHEN** 运行单元测试
- **THEN** 错口令断言失败并使 job 失败
- **AND** 不产出一个「静默导入空账本」的版本

**理由**：加密代码写错不会崩溃，只会「看起来能用但换设备打不开」；
纯 JVM 单测是沙盒无模拟器环境里唯一能验证的环节。
