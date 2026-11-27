# text-intake-diagnosis · Tasks

## 1. 四件套

- [x] 1.1 proposal.md（对齐结论、原始诉求、7 项拍板、6 笔样本范围）
- [x] 1.2 design.md（14 条决策、被否决方案、架构、严格充分条件、规则覆盖层、隐私与备份）
- [x] 1.3 tasks.md
- [x] 1.4 delta：`specs/privacy-security/spec.md`、`specs/reporting-ui/spec.md`

## 2. 第 86 批：零权限诊断入口

- [x] 2.1 Manifest 注册 `PROCESS_TEXT` / `ACTION_SEND`，`exported=true`、`excludeFromRecents=true`
- [x] 2.2 只读原文预览页、应用锁、内存态、一键清空
- [x] 2.3 v0.2.65 出包与真机入口验证
- [x] 2.4 回填：HyperOS 全选为节点聚合，原文质量足以做规则解析；6 笔均走 App 入口

## 3. 第 87 批：B 方案与自动落库

- [x] 3.1 `TextIntakeModels` / `TextIntakeRules` / `TextIntakeInspector`：规则集、覆盖层、状态机、字段抽取、算术校验
- [x] 3.2 6 笔原文 + 未知格式 + 规则覆盖层 + 金额解析 JVM 单测
- [x] 3.3 `LedgerRepository.commitTextIntake`：复用事务 / 去重 / 账户复合匹配 / ExtraAmountRules / 余额重算
- [x] 3.4 `TextIntakeActivity`：规则增补框、试解析、自动/仅草稿开关、预填草稿、手工保存、撤销、结果摘要
- [x] 3.5 `LedgerPrefs` 三个键；`backup_rules.xml` / `data_extraction_rules.xml` 排除 prefs
- [x] 3.6 `SettingsScreen` 内部入口「自动记账 / 文本诊断」
- [x] 3.7 版本号 0.2.65 → 0.2.66；`check_record_layout.py` / `check_text_intake.py` 同步
- [x] 3.8 `project.md` 第 87 批、活文档 §7.69、proposal/design/tasks/delta 同步
- [x] 3.9 13 个静态检查本地全 PASS

## 3b. 第 88 批：入口链路修复 + 提示词文档

- [x] 3b.1 `extract()` 增加 `Intent.clipData` 兜底；`external` 改为非空文本判定
- [x] 3b.2 入口标签改「离线账本 · 自动记账」；Manifest 过期注释同步
- [x] 3b.3 页面新增「本次入口状态」醒目卡片（字符数 / 来源 / 是否写库 / 空文本引导）
- [x] 3b.4 新增 `docs/自动记账规则生成提示词.md`：完整 Prompt、四种指令、步骤、结构变化判定、隐私提醒
- [x] 3b.5 App 内「复制规则生成提示词」按钮（`RULE_PROMPT` 常量）
- [x] 3b.6 delta / proposal / design / project.md / 活文档 §7.70 同步；版本号 0.2.67
- [x] 3b.7 `check_text_intake.py` 增加 clipData / 标签 / 提示词文档断言；13 个静态检查本地全 PASS

## 4. 出包

- [ ] 4.1 编译与校验 CI 全绿（含 `testDebugUnitTest`）
- [ ] 4.2 正式出包 CI 全绿、坚果云 job step success
- [ ] 4.3 fast-forward 合并 main
- [ ] 4.4 真机十项待验（见 design.md）回填

## 5. 后续轮（本 change 保持活跃，不归档）

- [ ] 5.1 采集微信支付成功页原文，判是否有分享/文本入口，另写规则表或不覆盖
- [ ] 5.2 真机反馈的失败样例：AI 给一行规则 → 用户粘贴覆盖层即时验，必要时合入默认基线
- [ ] 5.3 全部真机项通过后，delta 合并回主 spec 并整体归档

## 流程登记

第 86 批与第 87 批均按阶段 0 五项对齐 → 使用者确认 → 推送 / 实现 / 同步 spec 执行。
第 87 批因「规则外置怎么用」追加了一轮范围澄清，用户选 B 后才动手，无跳流程。
