# privacy-security · delta

## ADDED Requirements

### Requirement: 动态色与 edge-to-edge 不引入任何网络或权限

动态色 SHALL 仅通过系统 `dynamicLightColorScheme` 读取本机壁纸派生色板，
edge-to-edge SHALL 仅调整 Window insets，
MUST NOT 申请 `INTERNET`、MUST NOT 上传壁纸或色彩信息、
MUST NOT 新增运行时权限、MUST NOT 改动既有 `AndroidManifest.xml` 权限声明。

#### Scenario: 权限清单不变

- **GIVEN** 本批落地后
- **WHEN** 核对合并后的 Manifest
- **THEN** 权限声明与 v0.2.55 逐字节一致，零新增

#### Scenario: 离线可用

- **GIVEN** 设备处于飞行模式
- **WHEN** 冷启动并切换深浅壁纸
- **THEN** 主题仍正常渲染，无任何网络请求
