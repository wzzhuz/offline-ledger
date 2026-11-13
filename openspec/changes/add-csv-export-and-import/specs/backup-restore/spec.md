# Delta for 备份与恢复

## ADDED Requirements

### Requirement: CSV 明文导出格式

系统 SHALL 提供 CSV 明文导出，文件 SHALL 使用 UTF-8 with BOM，
表头 SHALL 固定为 `date,type,amount,account,categoryRoot,category,note,currencyCode`，
金额列 SHALL 以「元」为单位保留两位小数，日期 SHALL 为 `YYYY-MM-DD`。
导出 SHALL 通过 SAF 由用户选择保存位置，
MUST NOT 申请存储权限，MUST NOT 在应用私有目录留下副本。
加密 JSON 备份 SHALL 保持口令加密，MUST NOT 因提供 CSV 而倒退成明文。

#### Scenario: 两币种导出

- **GIVEN** 一笔 HKD 3850 分与一笔 CNY 2000 分支出
- **WHEN** 导出 CSV
- **THEN** 两行金额分别为 `38.50` 与 `20.00`
- **AND** `currencyCode` 列分别为 HKD 与 CNY
- **AND** 文件以 `EF BB BF` 开头，Excel 打开中文不乱码

#### Scenario: 导出不碰加密备份

- **GIVEN** 已实现口令加密的 JSON 备份
- **WHEN** 提供 CSV 导出入口
- **THEN** 「导出加密备份」仍生成密文
- **AND** 应用私有目录不新增明文 JSON

**理由**：使用者最初规格书就写了「方便用电脑 Excel 打开」；
6c-1 已澄清隐私边界是不上传第三方而非文件必须加密。
明文可接受不等于把已交付的加密能力拆掉。

---

### Requirement: 账单导入解析失败整体回滚

系统 SHALL 提供自定义极简 CSV / TSV 账单导入。
解析 MUST 为纯 JVM 且先全量校验，
任一行非法（金额、类型、日期、账户名、分类名）SHALL 返回带行号的错误报告，
且 MUST NOT 进入写入事务。
校验全部通过后 SHALL 展示「将写入 N 条」确认，
确认后 SHALL 在单个事务内批量写入并在末尾重算余额，
MUST NOT 留下半套数据。
导入 SHALL 只接受 `EXPENSE` / `INCOME`，MUST NOT 生成 TRANSFER 或 SETTLEMENT。
`currencyCode` 列 SHALL 在导入时被忽略，币种 MUST 从账户继承。

#### Scenario: 一行非法金额

- **GIVEN** 一份 10 行 CSV，其中第 7 行金额写成 `abc`
- **WHEN** 导入
- **THEN** 报告「第 7 行：金额格式非法」且账本零变化
- **AND** 不产生任何流水、余额不变

#### Scenario: 合法导入

- **GIVEN** 10 行合法 CSV，其中 2 行与已有流水重复
- **WHEN** 确认导入
- **THEN** 写入 8 条、报告「跳过 2 条重复」
- **AND** 余额重算后各账户一致、币种从账户继承

**理由**：导入是最容易写脏账本的路径；
「先全量校验 + 单事务 + 末尾重算」沿用 `importBackup` 已验证的范式，
且去重不加字段，符合稳定版前不背升级负担的新约束。
