# 任务清单：optimize-cold-start-first-frame

> 被打断时从最后一个未勾选的断点继续。
> 动手前必读：`docs/页面优化经验与避坑指南.md` §1 + §6.1；改 UI 前再读 §2/§3/§6.2。
> 若用 Python 拼接 Kotlin 源码，MUST 跑 `scripts/check_kotlin_strings.py`（踩坑 1.1，已犯过两次）。
>
> **进度口径（第 49 批实现期）**：代码侧已落地、本地六个静态检查全绿；
> 标注「真机」的项沙盒无设备，MUST 由使用者在 R8 release 包上验证；
> 「CI」项待 Actions 结果；归档项等使用者明确说「可以归档了」才动。
> 实现与门禁描述的三处偏差已在 `design.md`「实现偏差登记」如实记录，不粉饰。

## 1. 先测后改：诊断打点（第一个 task，测完再定后续）

- [x] 1.1 在启动链路埋 5 个可关闭的时间戳（`core/StartupTrace.kt`）：`Application.onCreate` 入口 → `loadLibrary` 返回 → `getOrCreatePassphrase` 返回 → Room 构建完成（首次 DAO 查询前） → 首屏 Paging 返回；`ENABLED` 编译期常量默认 `false`，不持久化、不写账本文件、不进导出
- [ ] 1.2 **真机**：同一台机、同一 R8 包，杀进程冷启动 5 次，记录五段耗时与总时长（对照使用者已做的「基本都是 1~2s」）
- [ ] 1.3 **依赖 1.2**：按 `design.md`「判读」写结论：瓶颈落在加密链 / 非加密链 / 分散
- [ ] 1.4 **依赖 1.2**：按结论决定是否追加「自有 Baseline Profile」条件性任务；结论 MUST 写进 `design.md` 与本文件，不在对话里留着
- [ ] 1.5 **真机**：确认打点在 release 包默认关闭、不产生日志与文件残留（代码侧已默认关闭，仍需 R8 包复核）

## 2. 启动快照 DTO 与落盘

- [x] 2.1 新建独立 `@Serializable` DTO（`StartupSnapshot` / `SnapshotRow`，`data/startup/StartupSnapshot.kt`），只带渲染字段，MUST NOT 序列化 `RecordRow`；`dbVersion` 取 `AppDatabase.DB_VERSION` 编译期常量，不查库
- [x] 2.2 落盘到 `context.noBackupFilesDir`（`StartupSnapshotStore.kt`）；`.tmp` → 回读校验 → 改名，解析失败按判废回退，不留半截文件
- [x] 2.3 纯 JVM 单测（`StartupSnapshotTest.kt`）：判废 7 条逐条断言（含锁定 / 有筛选 / 非默认粒度 / 跨月 / dbVersion 不符 / 格式版本不符 / 空行或超 60 行），另覆盖编解码往返与坏 JSON 丢弃

## 3. 明细首屏双源状态机（C2 快照 + C1 骨架兜底）

- [x] 3.1 `DetailScreen` 拆出 `SNAPSHOT` / `SKELETON` / `LIVE` 三个独立内容分支，缓存态与真实态不共用 `LazyColumn` 数据源
- [x] 3.2 冷启动在解锁之后读快照并立刻渲染；`MainActivity` 只在 `unlocked == true` 分支渲染 `LedgerApp`，架构上保证快照不成为绕锁旁路；判废函数仍单独断言 `unlocked = false`
- [x] 3.3 真实 Paging 与快照态并行；复用现有 `pagingData`（`flatMapLatest + cachedIn`），顶部无条件 `collectAsLazyPagingItems()`，不新起取数流
- [x] 3.4 换源判定用 `snapshotFlow { loadState.refresh }` + `StartupPhaseMachine`：MUST 先见过一次 `Loading`，再等 `NotLoading` 才一次性原子切换；换源后用真数据前 60 行覆写快照文件，快照视图销毁
- [x] 3.5 单测锁死换源时序：刚 collect 的短暂 `NotLoading`、加载中均不得切；`NotLoading` 之前 MUST NOT 销毁快照态
- [x] 3.6 骨架兜底：判废 / 读取失败 / 解析失败 / 超 3s → `StartupSkeletonList` + `onStartupTimeout()`，MUST NOT 静默显示过期账目
- [x] 3.7 换源后 `_startupRows` 清空、进程内不再读第二次；之后切月 / 筛选 / 下拉 / 下钻全走既有 `revision + flatMapLatest`

## 4. 一致性与失效

- [x] 4.1 作废挂在 `LedgerRepository.init` 注册的 **Room `InvalidationTracker.Observer`**（数据库层，覆盖 11 张表 / 全部写事务与元数据写），MUST NOT 只挂 `DataRefreshBus` / `bumpRevision` / `refreshTick` 任一上层出口；实现偏差见 `design.md`
- [x] 4.2 覆盖写场景：记一笔 / 编辑 / 删除 / 报销 / 导入 / 恢复 / 批量规整 / 规则回溯 / 周期补记命中（由表失效通知统一覆盖，不逐入口挂）
- [x] 4.3 作废 = `StartupSnapshotBus.invalidate()` → Application 层注册钩子删磁盘文件 + ViewModel 清空快照行并回退骨架；单测覆盖「写后即失效」
- [x] 4.4 确认启动快照不进 `exportSnapshot()` / `SnapshotWriter` / CSV / `BackupPayload` / 统计聚合 / 余额重算 / `records` 写入；Room 仍是唯一权威

## 5. 启动期顺带减负（非主方案）

- [x] 5.1 `DetailViewModel.init` 与 `reload()` 重复跑的 `refreshMonthCounts` 去重（init 移除，只由 `reload` 跑一次）
- [x] 5.2 **实现收敛（如实登记）**：`allCounterparties()` 全表 DISTINCT 保留在 init、`monthCounts` 仍随 `reload` 取回，未改成首帧后预取。理由：`FilterSheet` 的对方联想与 `YearMonthPicker` 的 `disableEmptyMonths` 直接依赖这两份数据，延后会制造空态；2000 条下毫秒级且实测已证伪查询是瓶颈。见 `design.md`「实现偏差登记」
- [x] 5.3 `accountDao.all()` / `categoryDao.all()` 保留在 init（几十行，成本可忽略）
- [x] 5.4 `applyDueRules(today)` 由 `LedgerApp` 监听 `startupPhase == LIVE` 后触发，不在启动快照窗口内写库；命中补记的 invalidate 由最终 refresh 吸收

## 6. 隐私与构建配套

- [x] 6.1 `backup_rules.xml` / `data_extraction_rules.xml` 各补 `domain="file"` 排除（cloud-backup 与 device-transfer 两段都补），MUST NOT 写 `<include>`
- [x] 6.2 `scripts/check_manifest_privacy.py` 扩展校验 `domain="file"` 排除存在，PASS 文案同步更新；数据库三条 exclude / `allowBackup=false` / `hasFragileUserData` 一条不改
- [x] 6.3 `app/proguard-rules.pro` 补 `data.startup.**` 显式 keep（DTO 与字段名跨版本留在磁盘上，被 R8 改名会静默判废）
- [x] 6.4 **CI**：push CI `44977573597` success（六静态检查 + 单测 + debug APK + release lintVital 全绿）、release `44978260845` success（坚果云 success）。**CI 全绿不得替代真机观感 / 性能验证**（活文档 §1）

## 7. 真机验收与文档

- [ ] 7.1 **真机**：冷启动到缓存首帧 < 200ms（同一台机、同一 R8 包、前后对比）
- [ ] 7.2 **真机**：换源无空帧（录屏逐帧）；若有内容跳变，确认为「真数据覆盖旧快照」的正确行为并如实记录
- [ ] 7.3 **真机**：快照窗口内手快点「记一笔」→ 写后缓存立即退场、显示写后真数据
- [ ] 7.4 **真机**：开启应用锁冷启动 → 解锁前无任何账目；跨月 / 改筛选后不展示上月旧账
- [ ] 7.5 **真机**：R8 release 包冷启动反序列化正常；判废 / 失败 / 超时回退骨架
- [ ] 7.6 **真机**：回归活文档 §8 第 45–48 批待验项（切月即时刷新、表头单一周期源、统计下钻、清空筛选）
- [x] 7.7 更新 `docs/页面优化经验与避坑指南.md`：§7.31「症状 → 根因 → 解法 → 教训」四段、§8 第 49 批继承与新增待验已补；真机结论待使用者回填
- [x] 7.8 回填本批实测：`project.md` 第 49 批补录、活文档 §7.31 实测编号、`defaultVersionName = 0.2.31`；真实 commit `f9fca48ea153` / push CI `44977573597` / release `44978260845` / 制品 `offline-ledger-release-v0.2.31` 12,421,988 B；推送脚本按刚创建的 commit sha 逐文件比对 blob sha

## 8. 收尾

- [ ] 8.1 （等使用者说「可以归档了」）把三份 delta 合并回 `openspec/specs/reporting-ui/`、`privacy-security/`、`build-and-ci/`，校验 specs 已反映新行为
- [ ] 8.2 确认 `project.md` 未被本 change 改动；若将来按使用者新口径调整本地加密，另开 change
- [ ] 8.3 校验构建通过、静态检查通过、真机验收项逐条标注「已验 / 未验」
- [ ] 8.4 （等使用者明确说「可以归档了」）把 change 文件夹移到 `openspec/changes/archive/<日期>-optimize-cold-start-first-frame/`

> **归档 = 合并 delta + 挪目录 + 校验，缺一不可**（活文档 §4.2：前 6 次只挪目录，delta 全部悬空）。
> 本批 8.1 / 8.4 未动，change 仍保持「进行中」。
