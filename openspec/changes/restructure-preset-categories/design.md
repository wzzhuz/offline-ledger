# Design：预制分类 v2

## 1. 分类表（第 90 批修订为 v3，最终锁定）

> 本节在**第 90 批**被整体修订：v2 的「一级 15 / 停用宠物 / 话费挂居住」已作废，
> 保留下文决策表仅作历史追溯。权威口径以本节的 v3 表与
> `openspec/specs/category-stats/spec.md`「第 90 批：预制分类清单 v3」为准。

**v3**：`default_categories.json` version 5，114 行 = 一级 16（支出 11 / 收入 5）+ 二级 98，
启用 61 / 停用 53；默认停用一级只剩「学习娱乐」「人情社交」。

| 决策 | 结论 |
|---|---|
| 新增一级「日常」(11) | 收纳绑在「人 / 生活运转」上的周期性服务：话费、理发美容、快递邮寄、家政保洁、洗衣护理、上门维修、生活杂费、绿植鲜花 |
| 三段边界 | 居住 = 房子成本；日常 = 人的周期性服务；购物 = 买到的实物 |
| 搬迁 | 话费 4007→11002、保洁搬家 4011→11004「家政保洁」、家具家电 4009→5009；三条旧 id 全部删除 |
| 5004 | 「家居」改名「家居家纺」，与「家具家电」(5009) 两条并存，MUST NOT 合并 |
| 宠物 (8) | 与 7 个二级一起由默认停用改为**默认启用** |
| 补齐 | 医疗 / 商业保险 (6009) 等停用扩展；不新增「颜值管理 / 母婴育儿 / 小孩支出」等更多一级 |
| 图标 | 复用既有 Tabler key 为主；新增 `scissors` / `flower` 两个（同 v3.34.0 MIT），合计 92 个 drawable |

**v2 原表（历史，已作废）**

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


---

## 6. 第 90 批修订记录（v2 → v3）

**触发**：使用者核查 v2 后认为「日常生成场景覆盖不足，比如理发；家具家电应归购物；
是否应增加『日常』大类，话费也归它；宠物改默认启用」。

**清点发现的规格冲突**：主 spec 早就写死「居住 SHALL 只包含租房 / 自住成本」，
而 v2 清单同时挂着话费 / 家具家电 / 保洁搬家。当年只补了「代缴水电」一条 Scenario，
没回头扫整张表 —— 规格与清单从落地那天就没完全对齐。

**决策**：

1. 新增一级「日常」(id 11, sortOrder 5)，按「绑定对象」划三段边界（房子 / 人 / 实物），
   不按词面归类。一级 15 → 16。
2. 搬迁 MUST 换新 id 到新父段，MUST NOT 只改 parentId；三条旧 id 从清单、
   `CategoryIconKeys`、`CategoryIconKeysTest` 一并移除。
3. 宠物改默认启用（7 个二级同步），默认停用一级只剩学习娱乐 / 人情社交；
   `SeedCatalogTest` 的断言与 `category-stats` 的停用 Scenario 同步改写。
4. 图标先判断能否复用现有 key；确无对应的新增 `scissors`(理发美容) /
   `flower`(绿植鲜花)，同步 Catalog / Tints / Keys / NOTICE 四处。`5004` 换 `bed`
   与 `5009` 的 `sofa` 区分近义项。
5. 新增一级 MUST 同步所有写死清单的地方：主 spec 的「N 个一级分类」Scenario、
   `project.md`、`docs/自动记账规则生成提示词.md` 与 `TextIntakeActivity.RULE_PROMPT`。

**生效口径**：`SeedCallback.onCreate` 只对全新库执行 → **必须卸载重装、不导旧备份**。
`AppDatabase.version` 仍 9、`Migrations.ALL` 仍空、表结构零改动。
旧备份会整表覆盖回 v2 分类，且旧 `categoryId=4007/4009/4011` 在 v3 无对应行，
恢复可能失败 —— 登记为已知限制，装包说明 MUST 写明「恢复前先导出 CSV」。

**被否决方案**：不新增「日常」把话费塞「其他」（污染统计）；保留旧 id 只改 parentId
（违反 ID 单测）；为覆盖安装做一次性同步逻辑（会复活用户手动停用的分类，另起一轮）；
新增更多一级（颜值管理 / 母婴育儿 / 小孩支出，稀释一级）；从网络拉分类扩展包（撞离线口径）。

**真机待验（仍 `[ ]`，本 change 不提前归档）**：卸载重装后快捷栏出现「日常 / 宠物」、
家具家电在购物下与家居家纺并存、话费在日常下、居住只剩 6 个启用项、
分类搜索 / 统计 / CSV 往返零回归、92 个图标在 40dp 圆底内的辨识度。
