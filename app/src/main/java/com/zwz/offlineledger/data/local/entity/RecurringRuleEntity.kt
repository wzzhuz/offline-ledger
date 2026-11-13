package com.zwz.offlineledger.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.zwz.offlineledger.data.model.Cadence
import com.zwz.offlineledger.data.model.RecordType

/**
 * 周期规则。**自包含**流水字段，不外键引用模板表：
 * 模板归档后规则仍可独立存在，新增模板/规则也能复用同一表单组件。
 *
 * lastGeneratedLocalDate 是幂等锚点：只在确实生成了流水时推进，
 * 起始日晚于今天（未来 anchor）时保持为 null，否则规则会永远不生效。
 */
@Entity(tableName = "recurring_rules")
data class RecurringRuleEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0L,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "type") val type: RecordType,
    @ColumnInfo(name = "amountCents") val amountCents: Long,
    @ColumnInfo(name = "categoryId") val categoryId: Long? = null,
    @ColumnInfo(name = "rootCategoryId") val rootCategoryId: Long? = null,
    @ColumnInfo(name = "fromAccountId") val fromAccountId: Long? = null,
    @ColumnInfo(name = "toAccountId") val toAccountId: Long? = null,
    @ColumnInfo(name = "note") val note: String = "",
    @ColumnInfo(name = "excludeFromStats") val excludeFromStats: Boolean = false,
    @ColumnInfo(name = "cadence") val cadence: Cadence,
    /** MONTHLY 用：1..31，当月无该日则取当月最后一天。 */
    @ColumnInfo(name = "dayOfMonth") val dayOfMonth: Int = 1,
    /** WEEKLY 用：ISO 1=Mon .. 7=Sun。 */
    @ColumnInfo(name = "dayOfWeek") val dayOfWeek: Int = 1,
    /** YYYYMMDD，按 Asia/Hong_Kong 口径。 */
    @ColumnInfo(name = "anchorLocalDate") val anchorLocalDate: Int,
    @ColumnInfo(name = "lastGeneratedLocalDate") val lastGeneratedLocalDate: Int? = null,
    @ColumnInfo(name = "isEnabled") val isEnabled: Boolean = true,
    @ColumnInfo(name = "createdAt") val createdAt: Long = 0L
)
