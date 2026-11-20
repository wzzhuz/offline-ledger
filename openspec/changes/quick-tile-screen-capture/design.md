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
