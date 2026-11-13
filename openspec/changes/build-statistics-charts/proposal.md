# 实现统计报表与图表

> **本文件是 change 的上下文入口。**
> 新会话只读这一份文件，就应当能还原本次改动的全貌，无需重新讨论。

## 为什么改

**背景（使用者原话与项目现状）**：

- 使用者最初的诉求是三件事：「**知道自己的资产情况**」「**每个月看下哪类支出比较多**」
  「**年终报表看下收入情况、支出情况、曲线变化等**」
- 使用者还明确要求：「分类考虑一下多级分类，例如食品分类，二级分类是食材、正餐、水果等等。
  **统计时候支持按一级分类统计，也支持按二级分类的进行统计**」
- 阶段 0 / 1 / 2 / 3 均已完成并归档：8 个模块 / 107 条 Requirement / 145 个 Scenario，
  CI 全绿（run `43226535584` / `f8caf837a7`），已产出约 22.6 MB debug APK。
  现在能记一笔、翻明细、组合筛选、看净资产、按「尾差一键抹平 / 大额先核对」处理账实不符
- **但统计页仍是标注「阶段 4」的占位页**。三个原始诉求里，
  资产与对账已解决「钱有多少、差了怎么找」，**「钱花在哪、怎么变的」还没落地**
- 阶段 3 归档时记录了一条**规格债（同坑第二次复发）**：
  阶段 3 修掉了「资产页显示占位说明」，却在同一条 Requirement
  「四 Tab 骨架一次成型」里换上了「统计页在阶段 4 落地前 SHALL 显示占位内容」，
  Scenario 还写着「**GIVEN** 阶段 3 版本」。阶段 4 一落地又会与实际行为相反。
  更微妙的是 delta 的「理由」段自称「不再绑定具体阶段版本号」，但正文其实仍绑定了

**问题**：统计所需的数据出口在阶段 1 / 2 已全部备好（`totalExpense` / `totalIncome` /
`expenseByRoot` / `expenseByCategory` / `monthlyTrend` / `dailyTotals` / `pagedFiltered`），
缺的只是把它们画出来并让用户能一层层点进去。阶段 4 是三个原始诉求的最后一块。

## 做什么

1. **统计页替换占位**：月度 / 年度切换、所选周期总收入 / 总支出 / 结余概览
2. **环形图**：一级（`rootCategoryId`）与二级（`categoryId`）两种口径切换，
   按金额降序展示占比与具体金额；点击一级扇区 → 展开该大类下二级构成 →
   再点二级进入该分类明细（三级下钻路径）
3. **月度每日支出折线**：当月 1 日至月末，无支出的日子补 0 而非断线
4. **年度收支双折线**：1–12 月两条线，空月补 0，一眼看出全年走势
5. **合计一致性校验**：一级合计 = 二级合计 = 总支出，UI 显示警告条 + 纯 JVM 单测双保险
6. **原生 Canvas 自绘**三种图，零新增 Gradle 依赖，不引入 Vico / Coil / Glide
7. **1 份 MODIFIED**：把「四 Tab 骨架一次成型」改成**不含阶段版本号**的长期表述，
   彻底收口统计页占位，避免阶段 5 再次复发
8. 纯 JVM `ChartData` 数据准备层与单测，`check_stat_rules.py` 扩展覆盖新增统计查询

## 不做什么

- ❌ **不改表结构 / 备份格式 / `schemaVersion`**：`AppDatabase` 仍 `version = 1`，
  **无 Room Migration**；本阶段只新增**读**查询，无写方法
- ❌ 不改 4 Tab 导航结构、中央 `+` FAB
- ❌ 不改阶段 2 明细 / 记一笔 / 回收站、阶段 3 资产 / 对账
- ❌ 不新增任何 Gradle 依赖（Compose / Room / Hilt / Paging 均已具备）
- ❌ 不引入 Vico 等第三方图表库、Coil / Glide、任何网络或上报 SDK
- ❌ 阶段 5 功能：SAF 加密导入导出、CSV、应用锁、分类管理（增删改排序）、
  账本健康页、阈值 / 配色偏好设置
- ❌ v1.1 清单：模板 / 周期账、快捷方式与小组件、预算与超支提醒、
  退款报销、多币种混算、三级及以上分类、账单导入
- ❌ 不改 `wzzhuz/my-app-workflow`、不改 `docs/设计规格书_v2.md` 历史快照
- ❌ 不在沙盒内编译 APK 或真机测性能（无 SDK / 模拟器）

## 对齐结论

> 阶段 0 在对话里产出，五项齐全才进入写作。

1. **要解决什么问题**
   把最开始那句「每个月看下哪类支出比较多，年终报表看收入情况、支出情况、
   曲线变化」真正落地。资产与对账已解决「钱有多少、差了怎么找」，
   统计页解决「钱花在哪、怎么变的」——三个原始诉求的最后一块。

2. **范围边界**
   - 改：统计页真内容、三种 Canvas 图表、下钻明细、纯 JVM 数据准备层与单测、
     一条新读查询、1 份 MODIFIED、README / 检查脚本
   - 不改：表结构、备份格式、导航结构、阶段 2 / 3 UI、阶段 5 功能、v1.1 清单

3. **关键约束**
   **本轮查证的关键发现：DAO 聚合已在阶段 1 / 2 全部备好**（见 `design.md` 第 1 节），
   所以阶段 4 像阶段 3 一样是**零表结构变更、零新增依赖、零 Migration**，
   主要是 UI + Canvas + 纯 JVM 数据整理，最多补一条
   `WHERE rootCategoryId = :rootId GROUP BY categoryId`。
   统计口径 MUST 复用 `StatRules.SQL_STAT_RELEVANT` 唯一出口，
   所有聚合 MUST 带 `excludeFromStats = 0` 且 `type IN ('EXPENSE','INCOME')`
   （分类占比查询为 `type = 'EXPENSE'`），禁止各页面手写裸 SQL 各自过滤；
   一级 `GROUP BY rootCategoryId`、二级 `GROUP BY categoryId`，不递归；
   按 `yearMonth` / `localDate` 冗余列分组，MUST NOT 对 `timestamp` 做函数运算；
   面向用户的统计 MUST 走 SQL 聚合，MUST NOT 用 `records.size`，年度只返回 12 行。
   **一级合计 = 二级合计 = 总支出** MUST 校验，不一致给提示而非静默显示；
   空日期 / 空月份补 0，不断线不缺月；校准记录与 `excludeFromStats` 流水
   MUST NOT 进任何图。
   金额 `Long` 分、`BigDecimal` 只用于展示；红 = 支出 / 绿 = 收入；
   Canvas 自绘；下钻复用阶段 2 组合筛选 + 分页参数化 SQL，MUST NOT 全量加载；
   4 Tab 结构不变。
   沙盒无 SDK / 模拟器，编译与单测由 Actions 验证；推送用单次 commit；
   Python 拼 Kotlin 后必跑五个检查脚本；不声明 `INTERNET`。

4. **验收标准**（可测）
   - **GIVEN** CI 跑完 **THEN** 五个静态检查 / 单测 / debug / release(lintVital) 全绿，
     且 Actions 列表能查到该次运行
   - **GIVEN** 一级 / 二级各记若干笔 **THEN** 一级合计 = 二级合计 = 总支出，
     不一致时 UI 显示警告且单测失败
   - **GIVEN** 点「餐饮」扇区 **THEN** 展开食材 / 正餐 / 外卖二级构成，
     再点「食材」进入该分类明细
   - **GIVEN** 月度视图 **THEN** 当日无支出的日子折线值为 0 而非断线
   - **GIVEN** 年度视图 **THEN** 1–12 月收入 / 支出两条线齐全、空月为 0
   - **GIVEN** 切月 / 切年 **THEN** 只返回聚合行（月 ≤31 行、年 12 行），不全表加载
   - **GIVEN** 一条 `excludeFromStats` 流水与一条校准 **THEN** 二者不出现在任何图
   - **GIVEN** 归档阶段 4 **WHEN** 校验 specs **THEN**「统计页占位」已被 MODIFIED
     替换为长期表述、无阶段版本号
   - **GIVEN** 推送完成 **WHEN** 用 API 校验 **THEN** sha 一致、无 token 明文

5. **被否决的方案**
   一句话结论：**所有「跳 change / 引入 Vico / 把 `PagingData` 塞进 `UiState` /
   一次性做掉阶段 5 / `records.size` 或内存分组 / 对 `timestamp` 做函数分组 /
   下钻全量加载明细 / 三级分类 / 新增 Gradle 依赖 / Canvas 里查数据库 /
   只追加 ADDED 不改统计页占位」的方案均被否决**，
   采用「零结构变更 + 复用既有 DAO 聚合 + 原生 Canvas + 纯 JVM 数据准备层 +
   1 份 MODIFIED 收口」。完整表格见 `design.md`「考虑过的其他方案」。

## 影响范围

**触及的 spec 模块**：`category-stats`（主）、`reporting-ui`（MODIFIED + 统计页性能）、
`money-precision`（金额展示）、`ledger-transactions`（新 SQL 口径）、
`build-and-ci`（单测与检查清单扩展）。

**是否需要重新安装包**：否（签名未变，数据库结构未变，无 Migration）

**是否会丢数据**：否（本阶段只读 + UI，不写账本）

**行为变化**：统计页从占位说明变为真实月 / 年报表；其余页面行为不变。

## 与项目铁律是否冲突

- [x] 不违反三条铁律（本轮兑现「知道哪类支出多、年终看曲线变化」）
- [x] 不涉及「已确认不做」清单中的功能
- [x] 无冲突

**补充说明**：阶段 3 归档的 `design.md` 明确要求本 change 用
`## MODIFIED Requirements` 把「四 Tab 骨架一次成型」改成**不含阶段版本号**的长期表述，
并写成完整替换文本。本 change 的 `specs/reporting-ui/spec.md` 已含这份 MODIFIED，
标题与原 Requirement 一致，归档阶段 4 时按标题第二次替换。
这是同一条 Requirement 的**第二次 MODIFIED**，目的就是终结「每阶段都要改占位表述」的循环。
