# 第 49 批：下拉快捷磁贴一键识屏记账（分支开发）

## 根因与时序

用户希望：在第三方 App（支付宝 / 微信）的交易记录页，下拉系统快捷设置点磁贴，
把「刚看的那笔」自动带进账本。

关键时序约束：**点磁贴那一刻才去识屏是做不到的**（QS 收起、前台变成自己 App）。
但用户给出的方案解决了这一点：`TileService.onClick()` 用
`startActivityAndCollapse(PendingIntent)` 拉起一个**透明 Activity**，
底下那个第三方 Activity 是 PAUSED 而非 STOPPED，画面仍在 SurfaceFlinger 合成帧里，
所以 `AccessibilityService.takeScreenshot()` 当场截单帧是可行的。
断掉的只是 `rootInActiveWindow` 节点树（active window 变成自己的透明页），
因此节点文本必须提前在白名单窗口事件里被动缓存。

## 决策

- **A′ 融合**：节点文本优先（省一次截图、不受 FLAG_SECURE 影响）→ 截图 OCR 兜底
  → 安全窗口 / 无金额明确降级到「手动记账」。
- **不常驻后台**：AccessibilityService 由系统按需绑定，不挂前台服务、无常驻通知；
  节点缓存只在内存、TTL 60s、默认开、白名单之外不解析。
- **砍掉 MediaProjection**：Android 14 起每 session 都要重新弹用户同意且需前台服务，
  与「一键」冲突。只用 API 30+ `takeScreenshot`（minSdk 26，低版本明确降级）。
- **OCR**：ML Kit Text Recognition v2 中文 bundled `16.0.1`，
  必须同时声明 `text-recognition:16.0.1`（`ChineseTextRecognizerOptions` 在基础包）。
  带坐标文本块只交 `ScreenTextParser`，金额走 BigDecimal 转分，MUST NOT 用浮点。
- **落库**：确认页用户核对后才写；`importSource="screen"` + 结构化指纹幂等；
  走 `LedgerRepository.insertScreenRecord` 单事务 + 末尾一次重算，
  不新开写入口、不动规则引擎求值、不改表。
- **分支**：`feature/quick-tile-screen-capture`，main 冻结在 v0.2.30；
  稳定后由使用者发话才合回。

## 与上一轮方案相比纠正的两处

1. 上轮称「起自己 Activity 后截图只能拿到自己界面」——对**像素合成帧**是错的，
   只对节点树成立。透明页正好绕开。
2. 上轮称「识别必须提前常驻完成」——对节点树成立，对截图不成立；
   本批两者并用，服务仍无需常驻。

## 隐私边界（写进 project.md 显式授权）

- 用途：把使用者正在看的账单页转成一条候选流水
- 数据流向：全屏文本 / 单帧 Bitmap 只在本机内存，TTL 后丢弃；
  不落盘、不进相册、不上传、不记全屏日志、不读密码与验证码
- 用户控制：白名单逐个开关、一键停用、系统无障碍开关、确认页才落库

## 如实取舍

- 确认页**只读展示**将写入的默认账户 / 一级分类，不在一期做下拉选择；
  要改账户或分类点「手动记账」。二期再补选择器。
- `startActivityAndCollapse` 的 400ms 死等改为系统回调 + 窗口稳定后由
  `TakeScreenshotCallback` 自身节流错误兜底，不自造延时。
- `FLAG_SECURE` 页面截图直接失败、无官方绕过，只能明确降级。
- 系统截图错误码原样透传给 `QuickCaptureResolver`，可读原因按码映射；
  真机复核映射是否准确。
- 引导页入口放在确认页「设置」，不改设置页一级导航，避免动 4 Tab 骨架。

## 回退顺序

1. 关闭无障碍权限 / 停用磁贴即可完全停用，数据链路不受影响
2. 删 Manifest 四个组件 + a11y 配置 + 透明主题
3. 删 ML Kit 两行依赖与 QuickOcr
4. 删 `quick/` 与 `insertScreenRecord` / `quickCaptureDefaults`


## 第 50 批修订：把静默的黑管拆成可观测的链

使用者真机反馈三点：① 有时候点磁贴没反应；② 问「必须开无障碍权限吗」；
③ 识别内容不准，分不清 OCR 读错还是提取选错块。三点同根：整条链
（点击 → 截图 → OCR → 字段提取 → 预填）没有中间状态与失败反馈。

### 决策

- **点击解耦**：`onClick()` 只做同步 `startActivityAndCollapse`；透明页立刻显示
  「正在识别…」与手动 / 设置出口。截图、OCR、解析全在页内异步。
  移除 `noHistory="true"` 与 `taskAffinity=""`（透明页被 QS 收起动画短暂失焦即销毁，
  是「有时候没反应」的高概率真因）；PendingIntent 抛异常时退回 `startActivity`。
- **权限分层**：识屏仍 MUST 开无障碍（`takeScreenshot` 与节点树都是 a11y 能力，
  MediaProjection 在 Android 14 起每 session 重弹授权且要前台服务，已排除）；
  但**磁贴零权限也可用**：未连接时明确提示 + 一键手动 + 权限引导。
- **节流重试**：截图错误码 1（AOSP 间隔过短）退避 900ms 重试一次；
  其余失败（含安全窗口）一次性落到「明确原因 + 手动记账」，不靠用户连点。
- **识别详情**：OCR 改回带坐标的文本块；确认页折叠面板展示字段来源
  （块号 / 坐标 / 命中规则）、全部原始块、以及金额 / 对方 / 描述的改选按钮。
  **ML Kit v2 不公开逐行置信度，所以不编造数值**，只展示「来自哪块 + 哪条规则」。
- **解析收紧**：金额候选按「强标签 > 货币符号 > 两位小数 > 普通数字」排序，
  日期/时间、余额/优惠/单号、负号一律标 `rejected` 默认不取但保留供改选；
  商户与描述继续分开落，不并进备注。
- **单测**：脱敏账单页 fixture 把「选错块」变成可回归的纯 JVM 断言。

### 如实边界

- 诊断信息只在确认页内存，不落盘、不上传、不加列改表（留样本要 `version = 6`，不做）。
- 改选金额 MUST 保留原 fingerprint，否则连点两次磁贴的幂等会被人工纠偏破坏。
- 截图错误码到可读文案的映射以真机复核为准；当前仅对码 1 做退避重试，
  其余按「错误码 N + 建议手动」呈现，不硬猜含义。
- 厂商 ROM 对 TileService 绑定 / QS 收起时序的差异，沙盒验不了。


### §7.32 实测编号（第 50 批，分支开发）

- 首轮代码 `c3b91b3f8c8058ab2751cda2062b6687c454a072`（1 新增 / 11 修改），push CI `44914954456` **failure**（17 步中「运行单元测试」红）。
- 根因：`afterLabel` 同命中多标签时取了第一个——「商品说明：牛奶鸡蛋」被「商品」抢先切成「说明：牛奶鸡蛋」，fixture 断言 `牛奶鸡蛋` 失败。修复 commit `5f41d72a91914420b382f96f463973a284a67715` 改为取最长匹配标签，重跑 CI `44915458086` **success** 17/17。
- release `44916130971` **success**，28 步 = 23 success / 5 skipped，**「上传到坚果云」completed success**；无并发 cancelled run。
- 制品：`offline-ledger-release-v0.2.32` 32,159,121 B、`offline-ledger-mapping-v0.2.32` 4,390,719 B、`offline-ledger-metrics-v0.2.32-p-m-nsfalse` 269 B。`nsfalse` 表示认证 / 建目录 / PUT 全程走完，未走「跳过」。
- 按 release.yml 命名规则，坚果云目标为 `/my-apps/offline-ledger-releases/offline-ledger-v0.2.32-release-5f41d72a.apk`。WebDAV 目录与 job logs 在当前凭据下不可读（403），**目录以使用者实际所见为准**。
- **main 仍冻结在 v0.2.30**（`6f85c8318bda`），未 merge、未 bump main 版本号。
- 体积 32M 含 ML Kit 中文 bundled 模型，与 v0.2.31 同量级，不是另一个构建变体。
