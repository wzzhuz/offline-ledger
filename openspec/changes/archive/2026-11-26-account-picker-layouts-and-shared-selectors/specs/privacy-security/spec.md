# privacy-security · delta（第 83 批）

## ADDED Requirements

### Requirement: 账户布局偏好仅存本机且不进备份

账户选择器的「全局排序 / 按类型分组」布局 SHALL 存于
`LedgerPrefs.accountPickerLayout`（SharedPreferences，
`"offline_ledger_prefs"`，`"global"` / `"grouped"`）。
MUST NOT 写入 Room、MUST NOT 扩备份 / CSV / Firefly 导出 DTO、
MUST NOT 触发 Migration（`AppDatabase.version` 仍为 9、`Migrations.ALL` 仍空）。
该偏好 SHALL NOT 上传、SHALL NOT 申请 `android.permission.INTERNET`
或任何运行时权限、SHALL NOT 引入新依赖。
换设备 / 清数据后 SHALL 回退默认「全局排序」，MUST NOT 因缺键崩溃。

#### Scenario: 飞行模式切换布局

- **GIVEN** 设备飞行模式、无网络权限
- **WHEN** 在账户选择器切到「按类型分组」并重启 App
- **THEN** 布局仍为分组视图，全程零网络请求、零权限弹窗

#### Scenario: 备份不含偏好

- **GIVEN** 导出一份加密备份并在另一设备恢复
- **WHEN** 打开账户选择器
- **THEN** 布局为默认「全局排序」，账本数据完整
- **AND** 权限声明与 Gradle 依赖清单同 v0.2.61 逐项一致
