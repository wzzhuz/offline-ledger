# Delta for privacy-security

## ADDED Requirements

### Requirement: 自动记账文本入口不新增权限与后台组件

系统 SHALL 通过 `TextIntakeActivity` 的两个 `intent-filter`
（`android.intent.action.PROCESS_TEXT` 与 `android.intent.action.SEND`，均为 `text/plain`）
接收使用者在系统文本选择菜单、分享选择器或设置页内部入口中主动提交的纯文本。
该入口 SHALL NOT 声明任何新增 `<uses-permission>`，SHALL NOT 声明
`android.permission.INTERNET`，SHALL NOT 引入任何 Gradle 依赖，
SHALL NOT 注册 `AccessibilityService`、`NotificationListenerService`、
前台服务、`WorkManager` 或 `SensorEventListener`，
SHALL NOT 产生相册文件、用户可见缓存文件或 SAF 导出。
规则覆盖层 / 自动开关 / 结果摘要 MAY 存 `LedgerPrefs`，但 MUST NOT 存原文，
且 SharedPreferences MUST 被备份规则排除。
`check_manifest_privacy.py` 与 `check_text_intake.py` MUST 在 CI 中断言上述边界。

#### Scenario: 从系统全选菜单分享文本

- **GIVEN** 使用者在支付宝账单详情页用系统「全选」选中整页文字
- **WHEN** 点「分享」并选择「离线账本」
- **THEN** `TextIntakeActivity` 收到 `EXTRA_TEXT` 并渲染识别页
- **AND** 应用权限列表仍只有 `USE_BIOMETRIC`
- **AND** 相册、用户可见缓存、外部存储均无新增文件

#### Scenario: 安装前查看权限

- **GIVEN** 使用者查看应用详情的权限列表
- **WHEN** 装有本自动记账入口的版本
- **THEN** 权限列表不含网络、存储、无障碍、通知、传感器相关项
- **AND** `check_manifest_privacy.py` PASS

**理由**：使用者核心顾虑是相册清理与后台耗电；本入口把「识屏」交给系统层文字聚合，
App 只在被主动调用时存在一次，不运行时对电量的贡献严格为零。

---

### Requirement: 自动记账原文只在内存且过应用锁

`TextIntakeActivity` SHALL 在 `AppLockScreen` 解锁后才渲染任何原文内容，
解锁前 MUST NOT 渲染账目或诊断内容；SHALL 尊重既有 `FLAG_SECURE` 与隐藏金额偏好。
收到的原文 SHALL 只保存在 `remember { mutableStateOf }` 内存态，
SHALL NOT 写入可保存状态容器、Room、日志或任何文件。
原文超过 20,000 字符 SHALL 被截断并显式标记。
预览页 SHALL 提供「一键清空」把内存字符串置空，
Activity `excludeFromRecents` SHALL 为 `true`。
规则覆盖层 / 自动开关 / 结果摘要 SHALL NOT 包含原文。

#### Scenario: 启用应用锁后从外部入口打开

- **GIVEN** 使用者已开启应用锁
- **WHEN** 从系统分享或设置页进入文本识别入口
- **THEN** 先展示 `AppLockScreen`
- **AND** 解锁失败时原文一个字符都不渲染

#### Scenario: 一键清空

- **GIVEN** 识别页正显示一段含商户全称与卡号尾号的原文
- **WHEN** 使用者点「一键清空」
- **THEN** 内存字符串被置空、页面显示「（已清空）」
- **AND** 退出 Activity 后无任何残留原文文件可供恢复

**理由**：整屏原文含商户全称、卡号尾号甚至余额，属「应用锁与界面防泄露」覆盖范围；
作为自动记账入口，没有理由让原文持久化。

---

### Requirement: 结果摘要留痕不含原文且不进备份

系统 SHALL 在自动落库、重复跳过、降级草稿、手工保存与撤销后追加结果摘要。
摘要 SHALL 只包含时间、金额、来源号尾号与结果类型，MUST NOT 包含原文、
商户全称、完整订单号或备注。
摘要与规则覆盖层 SHALL 存 `LedgerPrefs`，
`backup_rules.xml` 与 `data_extraction_rules.xml` SHALL 排除
`sharedpref/offline_ledger_prefs.xml`。
系统 SHALL 提供「清除留痕」一键清空，并 SHALL 限制摘要最多 20 行。

#### Scenario: 自动落库后查看留痕

- **GIVEN** 一笔 `-12.00` 自动落库成功
- **WHEN** 使用者查看「最近结果摘要」
- **THEN** 只看到时间、金额、来源号尾号与「已记账」
- **AND** 看不到商户全称、完整订单号或商品说明

#### Scenario: 清除留痕

- **GIVEN** 最近结果摘要有若干行
- **WHEN** 使用者点「清除留痕」
- **THEN** 摘要变为空
- **AND** 备份与设备转移不携带该 SharedPreferences 文件

**理由**：留痕用于自查「有没有被自动记过」，不是账本原文副本；可清除且不含原文才不会成为新的隐私面。

---

## MODIFIED Requirements

### Requirement: 应用锁与界面防泄露

系统 SHALL 提供 BiometricPrompt / PIN 应用锁，在冷启动、从后台恢复
以及**从外部 Intent 入口（App Shortcut、`PROCESS_TEXT`、`ACTION_SEND`）或设置页内部文本入口启动**时校验。
系统 SHOULD 提供 `FLAG_SECURE` 开关，防止最近任务预览泄露资产页与文本识别原文。
外部文本识别 Activity SHALL 设置 `android:excludeFromRecents="true"`。

#### Scenario: 冷启动

- **GIVEN** 用户冷启动应用
- **WHEN** 进入首页前
- **THEN** 先校验生物识别或 PIN
- **AND** 校验失败不展示任何账目

#### Scenario: 最近任务

- **GIVEN** 用户打开多任务界面
- **WHEN** 系统截取应用预览
- **THEN** 资产页内容与文本识别原文都不显示
- **AND** 开启 FLAG_SECURE 时系统不生成可见快照

#### Scenario: 外部文本入口

- **GIVEN** 使用者从系统文本选择菜单或分享选择器点「离线账本 · 自动记账」
- **WHEN** Activity 被创建
- **THEN** 复用同一套 `AppLockScreen` 校验，不提供绕过锁的路径
- **AND** 该 Activity 不出现在最近任务列表


---

## ADDED Requirements（第 88 批）

### Requirement: 规则生成提示词可复制且文档化

系统 SHALL 在规则增补卡片提供「复制规则生成提示词」按钮，
复制内容 SHALL 与 `docs/自动记账规则生成提示词.md` 的完整提示词一致。
该文档 SHALL 包含：可复制的完整 Prompt、四种指令格式、使用步骤、
「结构变化不能靠覆盖层解决」的判定、以及「贴给外部 AI 前先删订单号 / 卡号尾号」的隐私提醒。
`check_text_intake.py` MUST 断言文档与 Activity 常量中的关键句一致。

#### Scenario: 离开本会话后修复新格式

- **GIVEN** 使用者在另一个 AI 会话里，手上有一笔未自动记账的原文
- **WHEN** 复制 App 内提示词 + 处理后的原文一起发给 AI
- **THEN** AI 只返回 1~3 行四种指令之一的规则增补行
- **AND** 使用者粘贴到规则增补框、试解析即时生效，不需要等新版本

#### Scenario: 结构变化

- **GIVEN** 原文字段位置变了，不是某个词没覆盖
- **WHEN** AI 按提示词判断
- **THEN** 只输出 `# 结构变化，需要改代码并发版`
- **AND** 使用者不会把无效规则粘进覆盖层反复试


---

### Requirement: 三分区不扩大隐私面（第 89 批）

页面拆分后，原文 SHALL 仍只存在 Activity 内存态；
「本次结果」页 MUST NOT 渲染完整原文，
完整原文 SHALL 只在「记录」页以 `SelectionContainer` 只读展示。
「本次识别摘要」与结果摘要 MUST NOT 含完整订单号，只显示来源号尾号。

#### Scenario: 结果页不含原文

- **GIVEN** 一笔自动落库失败并显示 `REJECTED`
- **WHEN** 使用者停留在「本次结果」页
- **THEN** 页面显示失败原因、金额、账户、来源号尾号，但不显示整屏原文
- **AND** 切到「记录」页才可用 `SelectionContainer` 选择原文
