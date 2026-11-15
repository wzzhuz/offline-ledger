## ADDED Requirements

### Requirement: 真机反馈批次的静态检查与单测

本批次 SHALL 保持六个静态检查全 PASS，且 SHALL 新增纯 JVM 单测覆盖
默认币种 / 时区、`counterparty` 在备份与 CSV 的往返、以及 8 列旧 CSV 兼容。
CI 编译通过 MUST NOT 被表述为「可覆盖升级」或「真机已验证」。

#### Scenario: 检查全绿

- **GIVEN** 推送本批次代码
- **WHEN** Actions 跑完
- **THEN** 六个静态检查、单测、debug / release 构建全绿
- **AND** 能在 Actions 列表查到该次运行

#### Scenario: 迁移门禁不误伤

- **GIVEN** `AppDatabase version = 1` 且 `Migrations.ALL` 为空
- **WHEN** 跑 `check_transaction_annotations.py`
- **THEN** 只产生记录性 warnings，job 仍 success
- **AND** 写方法漏事务仍使 job 失败

**理由**：稳定版前迁移检查已降级为 warnings（6c-2），本批次要确认
重置版本不会意外让 CI 变红。

### Requirement: 横屏趋势页与图表刻度门禁
系统 SHALL 提供横屏全屏趋势页（Manifest 固定 landscape、`exported=false`、零新增权限），
SHALL 在竖屏图表显示竖轴具体数值刻度，单线趋势 SHALL 按该系列自身最大值缩放。
新增写方法 MUST 全部登记进 `check_transaction_annotations.py` 的写方法清单，
且 MUST 全部包在 `db.withTransaction` 内。

**理由**：使用者反馈「最大值是不是和收入用了同一个、横轴只显示 3 个标签、看不到数值、
是否提供放大然后横屏显示」。

#### Scenario: 单线独立缩放
- **GIVEN** 月支出最大 320 元、月收入 5000 元
- **WHEN** 查看支出单线
- **THEN** 支出线按 320 元缩放至画布顶部，竖轴显示 0.00 / 80.00 / 160.00 / 240.00 / 320.00

#### Scenario: 横屏放大
- **GIVEN** 用户在统计页点「横屏放大」
- **WHEN** 全屏页打开
- **THEN** 横轴标签全量铺开、双指可缩放、点击数据点显示具体金额，且不申请任何权限


### Requirement: 发版产物可辨别新旧
版本号 SHALL 随每次发版递增；release 产物文件名 SHALL 带 8 位短 commit sha，
MUST NOT 用固定同名文件覆盖，以免使用者下载到旧包却无法分辨。

**理由**：第 4 批事件里，旧代码重跑 release 生成的同名包与前一版仅差压缩包时间戳，
使用者下载后无法判断是否更新，直到质疑「确定提交了吗」才被发现。

#### Scenario: 两次不同提交
- **GIVEN** 两个不同 commit 各触发一次 release
- **WHEN** 比较两者 APK 文件名
- **THEN** 短 sha 不同，文件名不同，坚果云上不会互相覆盖
