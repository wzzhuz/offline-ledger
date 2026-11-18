# 任务（第 30 批）

- [x] 同步远端真实 HEAD 到本地作为唯一基线，不复用上一轮幻觉编号
- [x] 归档 `polish-stats-and-import-metadata`，4 份 delta 按标题整块合并进 specs
- [x] 删除 `SeedData.accounts` 预制行，新增 `AccountTemplates` 快捷模板（资产 / 信用负债分组）
- [x] `accounts.cardLast4` 可选字段，新增账户对话框可填后四位并拼进显示名
- [x] 分类清单挪 `assets/default_categories.json` + `SeedCatalog` 纯 JVM 解析
- [x] 按 A 方案重排清单：居住纯租房、代缴归人情/赡养·代缴水电、养车 / 宠物独立一级
- [x] `categories.enabled` + 分类管理启用 / 停用 + 四处录入侧过滤
- [x] `counterparty_aliases` 表与 DAO、`CounterpartyNormalizer`、CSV / Firefly 导入落库前归一
- [x] `AppDatabase.version = 3`，DatabaseModule 注册新 DAO，SeedCallback 传 Context
- [x] `SeedDataTest` / 新增 `SeedCatalogTest` / `CounterpartyNormalizerTest`
- [x] 六个静态检查本地全 PASS，版本 0.2.14
- [ ] push CI 与 release 实测回填、坚果云上传（执行后补编号）
- [ ] 真机复核：零账户首启、模板 3 击建账户 + 后四位、停用后记一笔隐藏但统计仍算、
      恢复内置、A 方案类目、别名归一命中、卸载重装后 FF3 重导幂等（由使用者装机确认）
