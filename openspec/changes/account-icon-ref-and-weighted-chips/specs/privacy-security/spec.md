# privacy-security · delta（第 82 批）

## ADDED Requirements

### Requirement: 本机图标导入不引入网络与权限

账户自定义图标导入 SHALL 仅通过系统文件选择器（SAF `OpenDocument()`）
读取使用者本机选中的文件，写入应用私有 `filesDir/account-icons/`。
MUST NOT 申请 `android.permission.INTERNET`、MUST NOT 新增任何运行时权限、
MUST NOT 上传图标或账户信息、MUST NOT 引入 Coil / Glide / SVG 解析库
或任何会传递引入网络栈的依赖。
导入文件 MUST NOT 随 APK 分发、MUST NOT 写进备份 / CSV / Firefly 导出，
`assets/NOTICE` SHALL 以独立段落说明「仅本机使用、许可由使用者自行确认」，
且 MUST NOT 把导入资源并入随包许可清单或改写成「已解决授权」。
预置图标资源仍按既有 [A]/[B]/[C] 段落逐项登记，零改动。

#### Scenario: 飞行模式可用

- **GIVEN** 设备处于飞行模式且无网络权限
- **WHEN** 在账户编辑页导入一张透明底 PNG 并保存
- **THEN** 图标正常显示，全程无任何网络请求、无权限弹窗

#### Scenario: 权限清单不变

- **GIVEN** 本批落地后
- **WHEN** 核对 `AndroidManifest.xml` 与 Gradle 依赖清单
- **THEN** 权限声明与 v0.2.60 逐项一致、依赖清单无新增项
