# Tasks：预制分类 v2（第 51 批）

> 状态：代码与文档全部完成；真机验收（§5）待使用者实测。CI 与制品编号见末尾回填。

## 1. 清单与 ID 规则

- [x] 1.1 重写 `assets/default_categories.json` 为 v4（一级 15 / 二级 91，ID 按 `父×1000`）
- [x] 1.2 同步 `app/src/test/resources/default_categories.json` 逐字一致
- [x] 1.3 重写 `SeedData.categories` fallback，新增 `presetIds` 与 `isPresetCategoryId`
- [x] 1.4 断言：全局唯一 / 二级最小 > 一级最大 / 二级落在父段 / 一级在 1..99 / 三者一致

## 2. 删除策略与 UI

- [x] 2.1 新增纯 JVM `engine/CategoryDeletionPolicy.kt`
- [x] 2.2 `LedgerRepository.deleteCategory` 接入策略（有流水或预制 → 归档）
- [x] 2.3 删除确认弹窗补「预制不会自动补回 / 恢复标准清单需全新安装」警告
- [x] 2.4 归档区显示改经 `CategoryDisplay.resolve` 带父级前缀
- [x] 2.5 新增 `CategoryDeletionPolicyTest`（预制 / 有流水 / 手工未用三路径）

## 3. 规格与文档

- [x] 3.1 `category-stats`：新增 ID 规则、空洞不回填与删除策略、导入整表替换三条 Requirement（含 Scenario 与理由）
- [x] 3.2 `category-stats`：修「预置资产账户」→ 零账户、停用示例养车 → 宠物、A 方案人情 → 人情社交
- [x] 3.3 `ledger-transactions`：`version SHALL 为 3` → 5，补「onCreate 只对全新库生效」
- [x] 3.4 `backup-restore`：明确 MUST NOT 按 ID 增量合并 / 按名称重映射
- [x] 3.5 `project.md` 第 51 批、活文档 §7.33、`README` 无需改（未提分类名）
- [x] 3.6 `SeedCatalogTest` / `SeedDataTest` / `DescriptionRoundTripTest` 随清单改名

## 4. 版本与构建

- [x] 4.1 核实 `0.2.34` 未被 tag / release / artifact 占用
- [x] 4.2 `defaultVersionName` → `0.2.34`
- [x] 4.3 本地六个静态检查 + Kotlin 括号 + JSON 一致性 + ID 规则自检
- [x] 4.4 推送（单次 commit，parent 保持远端 main HEAD）并按新 commit sha 逐文件校验
- [x] 4.5 push CI 六静态检查 / 单测 / debug APK / release lintVital 全绿（仅 artifact 上传失败，见回填）
- [x] 4.6 出 R8 正式包 v0.2.34（打 tag + 坚果云 + GitHub Release 附件），取制品名与体积
- [x] 4.7 回填实测编号到 project.md 与活文档，第二个 commit

## 5. 真机验收（沙盒不能验，CI 全绿不得替代）

- [ ] 5.1 全新安装首启：一级 15 个、默认可见 7 个支出一级，名字与清单一致
- [ ] 5.2 「餐饮」下有「正餐 / 外卖 / 聚餐 / 买菜做饭」四个可见二级，无三级入口
- [ ] 5.3 停用扩展项（零食 / 水果 / 话费）不在记一笔下拉；分类管理里可一键启用
- [ ] 5.4 分类管理展开「已归档」区，归档项显示「一级 / 二级」全名
- [ ] 5.5 误删一个无流水预制二级 → 转归档且可「恢复」，不出现「恢复无反应」
- [ ] 5.6 删除弹窗文案可见
- [ ] 5.7 统计 / 筛选 / 记一笔的分类显示名带父级前缀一致，无空项
- [ ] 5.8 不导旧备份；若误导入 v0.2.33 备份，确认「旧分类表整体覆盖、新清单不生效」符合文档

## 实测回填

代码 `ca4c80f0db29` / push CI `45109206812`（代码门禁全绿，artifact 上传因配额失败）/ release `45109513641` success（tag `v0.2.34`，坚果云 success）/ GitHub Release 附件 `offline-ledger-v0.2.34-release-ca4c80f0.apk` 3,039,702 B。详见 `openspec/project.md` 第 51 批实测。
