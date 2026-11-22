# 任务清单：optimize-cold-start-first-frame

> 被打断时从最后一个未勾选的断点继续。
> 动手前必读：`docs/页面优化经验与避坑指南.md` §1 + §6.1；改 UI 前再读 §2/§3/§6.2。
> 若用 Python 拼接 Kotlin 源码，MUST 跑 `scripts/check_kotlin_strings.py`（踩坑 1.1，已犯过两次）。

## 1. 先测后改：诊断打点（第一个 task，测完再定后续）

- [ ] 1.1 在启动链路埋 5 个可关闭的时间戳：`Application.onCreate` 入口 → `loadLibrary` 返回 → `getOrCreatePassphrase` 返回 → 首次 DAO 查询前（Room open 完成） → 首屏 Paging 返回；不持久化、不写账本文件、release 默认关闭
- [ ] 1.2 同一台机、同一 R8 包，杀进程冷启动 5 次，记录各段耗时与总时长（对照使用者已做的「基本都是 1~2s」）
- [ ] 1.3 按 design.md「判读」写结论：瓶颈落在加密链 / 非加密链 / 分散
- [ ] 1.4 **按结论决定**：落在非加密链 → 在 tasks 追加「自有 Baseline Profile（Macrobenchmark 覆盖冷启动→明细首屏）」条件性任务；落在加密链 → 只登记，本轮不加 B/A；分散 → 不扩大改动面。结论写进 `design.md` 与本文件，不在对话里留着
- [ ] 1.5 交付前确认打点可从 release 包移除或默认关闭

## 2. 启动快照 DTO 与落盘

- [ ] 2.1 新建独立 `@Serializable` DTO（`StartupSnapshot` / `SnapshotRow`），只带渲染字段，MUST NOT 序列化 `RecordRow`；`dbVersion` 取编译期 `AppDatabase.version` 常量，不查库
- [ ] 2.2 落盘到 `context.noBackupFilesDir`；`.tmp` → 校验 → 原子 rename，解析失败按判废回退，不留半截文件
- [ ] 2.3 纯 JVM 单测：判废 7 条（无筛选 / 默认月 / 月份一致 / dbVersion 一致 / 格式版本一致 / 已解锁 / 文件可读且 1..60 行）逐条断言

## 3. 明细首屏双源状态机（C2 快照 + C1 骨架兜底）

- [ ] 3.1 `DetailScreen` 拆出「快照态 / 真实态 / 骨架态」三个独立内容分支，缓存态与真实态 MUST NOT 共用一个 `LazyColumn` 数据源
- [ ] 3.2 冷启动在解锁之后读快照并立刻渲染；`unlocked == false` 时只渲染 `AppLockScreen`，MUST NOT 出现任何账目
- [ ] 3.3 真实 Paging 与快照态并行；复用现有 `pagingData`（`flatMapLatest + cachedIn`），不新起一条取数流
- [ ] 3.4 换源判定等 `loadState.refresh` 为 `NotLoading` 且首屏结果确定，一次性原子切换；换源后用真数据覆写快照文件，快照视图销毁
- [ ] 3.5 单测锁死换源时序：`NotLoading` 之前 MUST NOT 销毁快照态；验收「无空帧」
- [ ] 3.6 骨架兜底：判废 / 读取失败 / 解析失败 / refresh 失败 / 超 3s → 骨架态 + 提示，MUST NOT 静默显示过期账目（「失败也 MUST 出结果卡」）
- [ ] 3.7 换源后缓存视图在进程内不再读取第二次；之后切月 / 筛选 / 下拉 / 下钻全走既有 `revision + flatMapLatest`，行为零回归

## 4. 一致性与失效

- [ ] 4.1 作废动作挂在 `LedgerRepository` 最底层写事务出口（所有写路径都过 `db.withTransaction { ... rebuildAllBalancesInternal() }`），MUST NOT 只挂 `DataRefreshBus` / `bumpRevision` / `refreshTick` 任一上层出口
- [ ] 4.2 覆盖写场景逐个核：记一笔 / 编辑 / 删除 / 报销 / 导入 / 恢复 / 批量规整 / 规则回溯 / 周期补记命中
- [ ] 4.3 作废 = 标记快照不可用 + 立即切真实态或骨架态 + 递增既有 revision；单测覆盖「窗口内写一笔后旧账不残留」
- [ ] 4.4 确认启动快照不进 `exportSnapshot()` / `SnapshotWriter` / CSV / `BackupPayload` / 统计聚合 / 余额重算 / `records` 写入；Room 仍是唯一权威

## 5. 启动期顺带减负（非主方案）

- [ ] 5.1 `DetailViewModel.init` 与 `reload()` 重复跑的 `refreshMonthCounts` 去重
- [ ] 5.2 `allCounterparties()` 全表 DISTINCT、`monthCounts` 整年 GROUP BY 改首帧完成后低优先级预取；点开 `FilterSheet` / `YearMonthPickerDialog` 时数据 MUST 已就绪，不制造空态
- [ ] 5.3 `accountDao.all()` / `categoryDao.all()` 保留在 init（几十行，成本可忽略）
- [ ] 5.4 `LedgerApp.applyDueRules(today)` 挪到首屏稳定后的空闲窗口，或保证最终那次 refresh 吸收补记结果；真机构造一条到期规则冷启动验证无二次重建

## 6. 隐私与构建配套

- [ ] 6.1 `backup_rules.xml` / `data_extraction_rules.xml` 各补一条 `domain="file"` exclude；MUST NOT 写 `<include>`（踩坑 2.3：`lintVital` fatal）
- [ ] 6.2 `scripts/check_manifest_privacy.py` 扩展校验 `domain="file"` 排除存在，PASS 文案同步更新；数据库三条 exclude / `allowBackup=false` / `hasFragileUserData` 一条不改
- [ ] 6.3 `app/proguard-rules.pro` 补 kotlinx.serialization DTO 与 serializer 的 keep；确认 release `lintVital` PASS
- [ ] 6.4 六个静态检查 + 单测在 CI 全绿；**CI 全绿不得替代真机观感 / 性能验证**（活文档 §1）

## 7. 真机验收与文档

- [ ] 7.1 冷启动到缓存首帧 < 200ms（同一台机、同一 R8 包、前后对比）
- [ ] 7.2 换源无空帧（录屏逐帧）；若有内容跳变，确认为「真数据覆盖旧快照」的正确行为并如实记录
- [ ] 7.3 快照窗口内手快点「记一笔」→ 写后缓存立即退场、显示写后真数据
- [ ] 7.4 开启应用锁冷启动 → 解锁前无任何账目；跨月 / 改筛选后不展示上月旧账
- [ ] 7.5 R8 release 包冷启动反序列化正常；判废 / 失败 / 超时回退骨架
- [ ] 7.6 回归活文档 §8 第 45–48 批待验项（切月即时刷新、表头单一周期源、统计下钻、清空筛选）；既有明细行为零回归
- [ ] 7.7 更新 `docs/页面优化经验与避坑指南.md`：把「release 冷启动与 SQLCipher 开库」按实测结果结项或改写；本批新踩坑按「症状 → 根因 → 解法 → 防范」补记；新增真机未验项如实列出
- [ ] 7.8 回填本批实测：真实 commit sha + push CI run id + release run id + 制品名与体积；按刚创建的 commit sha 用 Trees/Contents API 逐文件比对 blob sha（AGENTS.md 硬性要求）

## 8. 收尾

- [ ] 8.1 把三份 delta 合并回 `openspec/specs/reporting-ui/`、`privacy-security/`、`build-and-ci/`，校验 specs 已反映新行为（**归档 = 合并 + 挪目录 + 校验，缺一不可**）
- [ ] 8.2 确认 `project.md` 未被本 change 改动；若将来按使用者新口径调整本地加密，另开 change
- [ ] 8.3 校验构建通过、静态检查通过、真机验收项逐条标注「已验 / 未验」
- [ ] 8.4 （等使用者明确说「可以归档了」）把 change 文件夹移到 `openspec/changes/archive/<日期>-optimize-cold-start-first-frame/`
