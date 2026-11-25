# 任务 · refine-firefly-mapping（v0.2.53）

## 实现（全部 `[x]`）

- [x] 1.1 `FireflyCategoryChoice` 加 `targetCategoryId` / `targetDisplayName`
- [x] 1.2 新增 `enum FireflyOpeningPolicy { KEEP, OVERWRITE, ADJUST }`，
      `FireflyMapping` 去掉布尔 `overwriteOpeningForExisting`
- [x] 1.3 `importFirefly` 分类段按 id 精确命中，类型/目标校验后整批回滚
- [x] 1.4 `importFirefly` 期初段改三态，修掉 `targetName == name`
- [x] 1.5 `ADJUST` 走 `BalanceAdjustmentEntity`（`FIREFLY_OPENING`）并做幂等
- [x] 1.6 `FireflyImportState` 改 `openingPolicy` + 加 `openingBySource`
- [x] 1.7 `SettingsViewModel` 同名一级自动预填改按 id；补三态 setter
- [x] 1.8 `FireflyImportScreen` 期初 Switch → 三态 FilterChip + 逐态说明
- [x] 1.9 分类行接 `CategorySelectDialog`，`⋯` 下拉只留两个动作
- [x] 1.10 账户行显示「当前期初 / FF3 期初」两值
- [x] 1.11 补 9 处 import / 删 `Switch` import / 版本号 0.2.53
- [x] 1.12 新建 `scripts/check_firefly_mapping.py`（第 9 个静态检查）
- [x] 1.13 四件套 + `docs/FireflyIII导入说明.md` + 活文档 §7.56 + project.md

## 验证与交付

- [x] 2.1 九个静态检查全 PASS（新增 `check_firefly_mapping.py`）
- [x] 2.2 首轮 push CI `45586904827` 挂在 `Cents` 包名，临时诊断两次后
      `ab7d9f78d20a` → `45588221397` **success**；`build.yml` 逐字节恢复
- [x] 2.3 触发与轮询拆独立调用，出包 CI `45589137394` success /
      tag `v0.2.53` / SHA256 `55b983cabeb26070eeeb597a3f2e3b6fe500589b98f30b86ec931f79c2502ee7`
- [x] 2.4 实测回填（活文档 §7.56 与 project.md）；正式归档校验仍保持 `[ ]`

## 真机待验（§8，全部 `[ ]`）

- [ ] 3.1 点「选择分类」弹全量选择器，搜索 / 左导航 / 彩色网格可用
- [ ] 3.2 选预制一级与已有二级，副行显示「映射到 · 餐饮 / 早餐」
- [ ] 3.3 `⋯` 下拉只有两个动作，没有任何分类项
- [ ] 3.4 选已有二级后流水 `categoryId` / `rootCategoryId` 正确，无新子类
- [ ] 3.5 类型不一致 / 目标不存在报错并整批回滚
- [ ] 3.6 三态 chip 可切且说明随策略变化；账户行两值正确
- [ ] 3.7 `KEEP` 默认跳过；`OVERWRITE` 改名映射也生效
- [ ] 3.8 `ADJUST` 不改期初、终值与覆盖一致、已对账余额不变
- [ ] 3.9 第二次导入同一份文件，`ADJUST` 不重复累加
- [ ] 3.10 无 OPENING 行三种策略都不动；新建账户仍用导出值
- [ ] 3.11 v0.2.52 直接覆盖安装数据不丢、不升表
- [ ] 3.12 §8 第 45–73 批既有真机待验项整体继承
