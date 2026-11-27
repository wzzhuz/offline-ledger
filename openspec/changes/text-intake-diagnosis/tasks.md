# text-intake-diagnosis · Tasks

## 1. 四件套

- [x] 1.1 proposal.md（含「对齐结论」节、使用者原始诉求、三项拍板、分级判定）
- [x] 1.2 design.md（8 条决策、被否决方案表、架构、置信度门槛、隐私护栏、脚本影响、真机待验）
- [x] 1.3 tasks.md
- [x] 1.4 delta：`specs/privacy-security/spec.md`、`specs/reporting-ui/spec.md`

## 2. 代码

- [x] 2.1 `core/intake/TextIntakeInspector.kt`：纯 Kotlin 只读分析，20,000 字符截断，疑似金额全量列出，四项命中，门槛只读预演
- [x] 2.2 `ui/intake/TextIntakeActivity.kt`：`FragmentActivity` + 应用锁 + `FLAG_SECURE` / edge-to-edge + 原文预览页
- [x] 2.3 预览页：来源 / 字符数 / 截断标记 / 金额候选 / 四项命中 / 门槛标签 / 一键复制 / 一键清空 / 关闭 / 隐私提示
- [x] 2.4 `AndroidManifest.xml`：`PROCESS_TEXT` + `ACTION_SEND` 两个 intent-filter，`exported=true`、`excludeFromRecents=true`
- [x] 2.5 `strings.xml` 两个标签；入口 label 刻意不叫「记一笔」
- [x] 2.6 `app/src/test/java/.../TextIntakeInspectorTest.kt`：支付宝样例 / 多金额 / 截断 / 空文本 / SEND 来源

## 3. 校验与文档

- [x] 3.1 版本号 0.2.64 → 0.2.65
- [x] 3.2 `check_record_layout.py` 版本断言同步
- [x] 3.3 新增 `check_text_intake.py` 并挂进 `build.yml`
- [x] 3.4 `project.md`：自动记账 6c-2 状态改为进行中 + 第 86 批登记
- [x] 3.5 活文档补 §7.68（含「AOSP 口径外推」认账）
- [x] 3.6 12 个静态检查 + 新增脚本全 PASS（本地与 CI 双验）

## 4. 出包

- [ ] 4.1 编译与校验 CI 全绿
- [ ] 4.2 正式出包 CI 全绿、坚果云 job step success
- [ ] 4.3 fast-forward 合并 main
- [ ] 4.4 真机七项待验（见 design.md）回填

## 5. 后续批（本 change 保持活跃，不归档）

- [ ] 5.1 依真机原文写规则表（金额 / 方向 / 时间 / 商户 / 账户尾号 / 分类候选）
- [ ] 5.2 置信度达标自动落库 + 撤销 + 去重
- [ ] 5.3 缺项降级草稿与手填
- [ ] 5.4 delta 合并回主 spec 后整体归档

## 流程登记

本批按阶段 0 五项对齐 → 使用者确认 → 四件套 → 推送 → 实现 → 同步 spec 执行，
无跳流程、无归档不合并 delta（本 change 刻意保持活跃）。
