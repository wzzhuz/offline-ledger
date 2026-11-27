# text-intake-diagnosis

## 对齐结论（阶段 0）

| # | 项 | 结论 |
|---|---|---|
| 1 | 要解决什么 | 出**诊断小包**，验两件事：HyperOS 3.0 文本选择「竖向三点 → 更多」是否对第三方 `ACTION_PROCESS_TEXT` 开放，以及「全选 → 分享」能否把整页原文送到本 App；并把收到的原文质量（节点聚合 vs 系统 OCR、噪声多少、四项字段命中率）摊给使用者人工检查。无障碍整套一行不写，相册垃圾 / CSV 延迟 / 后台耗电 / 服务保活四个副作用同时消失 |
| 2 | 改什么 / 不改什么 | 改：`AndroidManifest.xml` 新增 `TextIntakeActivity`（两个 intent-filter）、`TextIntakeInspector` 纯函数 + JVM 单测、原文预览页、版本号 0.2.64→0.2.65、静态脚本、`project.md`、活文档、delta spec。**不改**：`MainActivity` / `LedgerApp` / `RecordEditorDialog` / `LedgerRepository` / `AppDatabase`（version 仍 9）/ 备份 DTO / 4 Tab / 分类与账户数据路径。**本批 MUST NOT 落库** |
| 3 | 关键约束 | 零 `uses-permission`、零新增 Gradle 依赖、零 `INTERNET`、零改表、零 SAF 导出（原文只在内存、退出即清）；诊断页 MUST 先过应用锁、`excludeFromRecents=true`；原文截断上限 20,000 字符；置信度门槛写进 spec 作后续批约束；沙盒不编译 APK，走 Actions |
| 4 | 验收标准 | 12 个静态检查 + 单测 + Debug + Release lintVital 全绿；真机上「全选 → 更多」出现入口**或**「全选 → 分享」选择器里出现「离线账本」，至少一条通即算成功；原文预览可贴回；应用锁生效、退出后无残留文件；坚果云 job step success |
| 5 | 被否决方案 | 完整表见 `design.md`「考虑过的其他方案」 |

分级判定：**需要建 change**（新增系统入口、改变 Manifest 行为），不是 bug 修复。
本 change **保持活跃、不归档**：真机原文回来后还要在同一 change 里写规则表 + 自动落库，最后一起归档。

## 为什么改（使用者原始诉求）

> 「提高自动记账能力，目标是可以识屏自动填充内容，减少手工输入」「最核心诉求是减少手输，减少人工操作」。

排查过程中逐条被否掉的旧路：

- 下拉磁贴：平台层不能主动弹出，Android 12+ 要手动拖进快捷设置，手感不尽人意
- 系统截屏 → 分享：截图自动进相册，**要定期清理，使用不方便**
- 支付宝定期导出 CSV：**不能及时记录，导出也麻烦**
- 无障碍读节点树 + 摇一摇：一次系统级广泛授权、进程常驻内存、厂商可能为省电主动关服务；使用者手机用了 2 年，**对电量敏感**

转折来自真机反馈（HyperOS 3.0，小米）：

> 「系统全选可以整个页面内容选中。选中之后我看到有『复制』『分享』『网页搜索』『朗读』，竖向 3 个点的更多选项，里面有『百度地图』『百度搜索』『百度翻译』『问AI』『问问DeepSeek』『浏览器搜索』等选项」。

这条信息同时给出两个关键事实：**系统层做了整屏文字聚合**（绕过 App 自身是否 `textIsSelectable`，也绕过支付宝/微信改版），以及**「分享」是一级菜单项**。于是零电量文本入口成立：没有图片、没有文件、没有后台、没有权限。我此前按 AOSP 口径说「Android 没有全选整页文字」属凭印象外推（§7.51），在此更正。

## 使用者拍板

| # | 问题 | 拍板 |
|---|---|---|
| 1 | 无障碍整套是否搁置、改走零电量文本入口 | **同意** |
| 2 | 是否先出诊断小包 | **先出诊断小包** |
| 3 | 置信度门槛按「四项齐全 + 金额精确匹配才自动落库，否则降级草稿」写死 | **可以** |

## 改什么

- 新增 `ui/intake/TextIntakeActivity.kt`：`FragmentActivity` + `setContent`，先过 `AppLockScreen` 再渲染只读预览页；读取 `EXTRA_PROCESS_TEXT` / `EXTRA_TEXT`
- 新增 `core/intake/TextIntakeInspector.kt`：纯 Kotlin 只读分析（截断、疑似金额串、方向 / 时间 / 商户命中、门槛预演），**不解析入库、不猜值**
- 新增 JVM 单测 `TextIntakeInspectorTest.kt`
- `AndroidManifest.xml` 注册两个 intent-filter（`PROCESS_TEXT` + `ACTION_SEND`，均 `text/plain` + `CATEGORY_DEFAULT`），`exported=true`、`excludeFromRecents=true`
- 新增 `scripts/check_text_intake.py` 并挂进 `build.yml`，把「零权限 / 不落库 / 应用锁 / 版本号」变成可执行断言
- 版本号 0.2.65；`check_record_layout.py` 硬编码版本断言同步
- `project.md` 把「自动记账」从 6c-2 待决策改为进行中；活文档补 §7.68

## 不改什么

无障碍服务、摇一摇 / 音量键触发、传感器、`takeScreenshot`、节点树 dump、OCR 依赖、云端 / 端侧大模型、通知与前台服务、悬浮窗、任何 Room 写路径、任何自动落库。

## 影响

- Manifest 多一个 exported Activity，但 `check_manifest_privacy.py` 精确匹配 `<uses-permission>`，不误报
- `MainActivity` 一字不动，桌面快捷方式与应用锁零回归
- 零改表，可直接覆盖 v0.2.64
- 隐私面扩大可控：原文含商户全称与卡号尾号，故应用锁 + excludeFromRecents + 内存态 + 一键清空四道护栏

## 验收

12 个静态检查 + 单测 + Debug + Release lintVital 全绿；真机三项见对齐结论第 4 项。
