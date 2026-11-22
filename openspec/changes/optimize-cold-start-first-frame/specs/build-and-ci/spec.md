# Delta for build-and-ci

## ADDED Requirements

### Requirement: 启动快照 DTO 的 R8 兼容与静态检查

启动快照 SHALL 使用独立的 `@Serializable` DTO，MUST NOT 直接序列化 DAO 行类 `RecordRow`；
`app/proguard-rules.pro` SHALL 覆盖该 DTO 与其生成的 serializer，保证 R8 混淆后仍能反序列化。
`scripts/check_manifest_privacy.py` SHALL 校验 `backup_rules.xml` 与 `data_extraction_rules.xml`
均含 `domain="file"` 排除且不含 `<include>`；六个静态检查与纯 JVM 单测 SHALL 在 CI 编译前执行。
系统 SHALL 用 **R8 release 包**真机验证冷启动快照能正常读取、判废与回退，
MUST NOT 以 debug 包构建通过或 CI 全绿替代该项验证。

#### Scenario: R8 包冷启动

- **GIVEN** 已产出开启 `minifyRelease` 的 release 包
- **WHEN** 杀进程后冷启动，磁盘上已有启动快照
- **THEN** DTO 反序列化成功，明细首屏按快照渲染或按判废回退骨架
- **AND** 不因混淆丢失 serializer 而崩溃或静默丢弃快照

#### Scenario: 静态检查拦截

- **GIVEN** 有人删掉 `domain="file"` 排除，或给备份规则加了 `<include>`
- **WHEN** CI 执行静态检查与 release `lintVital`
- **THEN** 检查失败 / 构建失败，不产出该版本

#### Scenario: 不用 debug 包冒充验证

- **GIVEN** debug 包构建与单测全绿
- **WHEN** 汇报启动快照验收状态
- **THEN** 明确区分「R8 包真机已验」与「仍未验」，MUST NOT 写成已全部验证

**理由**：debug 不跑 R8 也不跑 `lintVital`，备份规则与序列化 keep 的问题只在 release 暴露
（活文档 §1、踩坑 2.3 已两次记过）；沙盒无模拟器，CI 全绿不得替代真机观感 / 性能验证是本项目固定规则。
DTO 与 `RecordRow` 分离也延续项目「备份 DTO 与 Entity 分离」的既有约定。

---

### Requirement: 启动期改动不扩大 CI 与推送风险面

本批涉及 UI / 备份规则 / 静态脚本的改动 SHALL 沿用既有推送流程：
推送前 MUST 回显目标仓库名与本地源目录并等使用者确认，推完 MUST 用 Trees / Contents API
按刚创建的 commit sha 逐文件比对 blob sha、校验零删除且 `.github/` 与 `.gitignore` 存在。
`build.yml` 的 push 触发 MUST 继续保持 `branches: ['**']`；
诊断打点 SHALL 可关闭、release 默认关闭或可移除，MUST NOT 常驻写文件或进账本数据。
交付前 MUST 给出可点开的真实 commit sha + run id，并把实测编号回填进 `project.md` / 活文档。

#### Scenario: 推送前确认

- **GIVEN** change 文件已写完、准备推送到远端
- **WHEN** 执行推送
- **THEN** 先回显「目标仓库 + 本地源目录」并等确认
- **AND** 推完列出远端根目录与逐文件 blob sha 校验结果

#### Scenario: 打点不残留

- **GIVEN** 诊断打点已用于定位瓶颈
- **WHEN** 产出交付的 R8 release 包
- **THEN** 打点默认关闭或已移除，不写账本文件、不进导出

**理由**：本项目发生过「没做说做了」「做了说没做」「没查当通过」「增量推送误删 `.github/`」
多次状态事故，活文档 §1 已把固定规则写死；本批沿用，不新造流程。
