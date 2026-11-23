# Design：预制分类 v2

## 1. 分类表（最终锁定）

一级 15 个（支出 10 / 收入 5），默认启用 7 个支出一级；二级 91 个，启用 35 / 停用扩展 56。
完整清单见 `assets/default_categories.json`（唯一来源）。

| 决策 | 结论 |
|---|---|
| 一级命名 | 餐饮 / 交通 / 养车 / 居住 / 购物 / 医疗 / 学习娱乐 / 宠物 / 人情社交 / 其他 |
| 养车 | **保留独立一级且默认启用**（使用者要求看清成本）；与交通划清：人走的归交通、车耗的归养车 |
| 三餐 | 降为 `餐饮 / 正餐`，与外卖 / 聚餐 / 买菜做饭并列；不再有三级 |
| 学习娱乐 | 改名不拆（拆成两个一级会变细，违背本轮目标） |
| 扩展二级 | 零食、水果、饮品奶茶、咖啡、夜宵、烟酒、话费、美妆护肤、长途交通… 一律 `enabled=false` |
| 停用一级 | 学习娱乐 / 宠物 / 人情社交，其下二级同步停用，避免父子可见性不一致 |
| 收入 | 5 个一级整组保留，仅重编 ID |

`enabled=false` 无需改 UI：`CategoryDao.setEnabled` / `enabledOnly()` 不限层级，
`FilterSheet` L135/147 已按 `it.enabled` 过滤二级，`CategoryManagerDialog` 已有一级/二级各自的启用菜单。
代价是停用项不在「记一笔」下拉，碰到时走「设置 → 分类管理 → 启用」，使用者已接受。

## 2. ID 规则与「无语义」原则

- 一级：支出 `1..10`、收入 `30..34`；预留 `11..29`（支出）、`35..39`（收入）、`40..99`（通用）。
- 二级：`parentId × 1000 + 序号(1..999)` → `1001..99999`。本版实际范围 `1001..34004`。
- 二级最小 id MUST > 一级最大 id；全局唯一；每父可挂 999 个二级；一级预留 89 个。
- **ID 只是主键**：树关系唯一由 `parentId` 表达；`CategoryTree.resolveRootId` 查 HashMap 取 parentId，
  从不解析数字；统计靠 `records.rootCategoryId` 冗余列 GROUP BY。
- 手工新建 / CSV / Firefly 导入新建：`CategoryEntity` 不传 id → AUTOINCREMENT 从
  `sqlite_sequence` 历史 max+1（本版 34005 起），**不按段位**，ID 彻底无语义。
- 效率：主键 B-tree 比较、HashMap 查找、GROUP BY 都与 ID 数值无关，分类表百行级一页装得下；
  抬高 AUTOINCREMENT 起点无实质开销。**本方案不是性能优化。**

## 3. 备份与旧数据

- `BackupPayload.categories` 自带完整分类表，流水带 `categoryId` / `rootCategoryId` → ID 自洽闭环。
- `importBackup` 全量替换：defer_foreign_keys → 断自引用 → `categoryDao.clear()` → 父先子后整表插回
  → 插流水。代码只整表搬运，不认任何具体数字 → **ID 重编不用重写备份代码**。
- 「ID 不动引用才不破」只适用于「在已有数据的库上原地改清单」；本次全新空库，约束消失。
- 但**导入旧备份会把旧分类表整体覆盖回来，新清单不生效** → 装包说明 MUST 写明
  「恢复标准清单 = 全新安装且不导入旧备份」。
- CSV / Firefly 按名称路径匹配、批量规则按名称存分类：旧 CSV / 旧规则改名后对不上，
  因不导旧数据而不触发，登记为已知限制。
- 未来隐患：旧备份手工分类 ID 从 1502 起，与新二级段 `1001..` 数值重叠。
  只要保持整表替换就没事；故 spec 钉死「MUST NOT 按 ID 增量合并」。

## 4. 删除策略（B 方案）

| 判定 | 行为 |
|---|---|
| `linkedRecordCount > 0` | 归档（既有规则，保历史 `rootCategoryId`） |
| `isPreset = true`（即使 0 流水） | **归档**（本批新增） |
| 其余（手工新建且无流水） | 物理删 |

落点（零 Context、零 DI、零改表）：

- 新文件 `engine/CategoryDeletionPolicy.kt`：`shouldArchive(linkedRecordCount, isPreset)`，纯 JVM 可测。
- `SeedData.presetIds = categories.map{id}.toSet()` + `isPresetCategoryId(id)`。
  由 `SeedCatalogTest.catalog matches seed fallback exactly` 保证与随包 JSON 同步，
  因此**不需要在运行时读 assets、不需要注入 Context**。这是「清单 id 集合」数据比对，
  不是解析数值段位，不违反「ID 无语义」。
- `LedgerRepository.deleteCategory` 改为调用策略；`restoreCategory` 无需改（对归档行生效）。
- 弹窗补警告；归档区 `text` 改经 `CategoryDisplay.resolve(parentName, c.name)` 带父级前缀
  （`categories` 全量列表已在该 composable 作用域内）。

**空洞**：AUTOINCREMENT 永不重用 rowid。空洞不占位、不加页、不拖慢查询；
回填反而会让旧备份里的旧 ID 被新分类顶上，造成静默关联错乱 → **明确不回填，写进 spec**。
外部导入的不合理分类大多挂流水，本来就只归档，实际几乎不产生空洞。

**登记未修**（独立议题，本轮不动）：归档列表平铺累积；导入查重排除归档行，
重复导入会造同名重复项。正确整理姿势写进文档：全新安装 / 清库重导，或改名 + 停用，别靠「删」。

## 5. 被否决方案

| 方案 | 否决理由 |
|---|---|
| 直接把「三餐」降级 | 其下已有 4 个二级 → 变三级，违反「最多两级」 |
| 加三级分类 | `CategoryTree.validate` 与 spec 都禁止 level≥2 |
| A 只写文档警告、不改删除逻辑 | 误点仍不可逆 |
| C 加「恢复缺失预制分类」按钮 | 要定义「改名过的算不算缺」，本轮范围外 |
| D 在 onCreate 之外自动补种 | 会覆盖用户主动删除 / 改名的意图 |
| 回填 ID 空洞 | 静默关联错乱风险 |
| 手工分类也按父段编号 | 要查段内空位 + 处理并发，收益为零，保持 ID 无语义 |

## 6. 三处清单一致性

`assets/default_categories.json`（唯一可替换来源）↔ `SeedData.categories`（fallback）
↔ `app/src/test/resources/default_categories.json`。`SeedCatalogTest` 逐行断言三者一致；
新增 `SeedDataTest.id layout has no collision` 断言全局唯一 / 段位 / 二级最小 > 一级最大。
`SeedCatalog.parse` 的正则只匹配纯字母字段名与 JSON 基础值，本版字段名未变、名字里无单引号，兼容。

## 7. 版本与出包

`defaultVersionName` → `0.2.34`（已核实未被占用）。零改表 → 但对**已有数据的库无效**，
MUST 全新安装；不导旧备份；测试数据重造。出 R8 包、打 tag、传坚果云。
