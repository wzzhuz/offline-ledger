package com.zwz.offlineledger.data.startup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 冷启动「感知优先」的一次性渲染快照。
 *
 * 这是**独立于 Entity / DAO 行类的 DTO**：
 * `RecordRow` 是 Room 查询结果，字段随表结构变化；快照文件在磁盘上跨版本存在，
 * 两者 MUST 分离（沿用项目「备份 DTO 与 Entity 分离」的既有约定）。
 *
 * 快照不是第二数据源：只在冷启动到真实首屏到达之间的 1~2s 窗口里渲染，
 * 换源后即销毁；任一写事务提交即作废（见 `StartupSnapshotBus`）。
 * MUST NOT 进入 exportSnapshot / SnapshotWriter / CSV / 加密备份 / 统计聚合。
 */
@Serializable
data class StartupSnapshot(
    val schemaVersion: Int,
    /** 落盘时的 AppDatabase.version，编译期常量比对，不查库。 */
    val dbVersion: Int,
    val yearMonth: Int,
    /** "MONTH" / "YEAR"，只记录写入时的默认粒度。 */
    val granularity: String,
    val savedAtEpochMilli: Long,
    val rows: List<SnapshotRow> = emptyList()
)

/** 只带明细行渲染所需字段；字段一律带默认值以兼容旧格式。 */
@Serializable
data class SnapshotRow(
    val id: Long = 0L,
    val type: String = "",
    val amountCents: Long = 0L,
    val timestamp: Long = 0L,
    val localDate: Int = 0,
    val yearMonth: Int = 0,
    val note: String = "",
    val excludeFromStats: Boolean = false,
    val categoryName: String? = null,
    val categoryColorHex: String? = null,
    val fromAccountName: String? = null,
    val toAccountName: String? = null,
    val counterparty: String = "",
    val description: String = ""
)

/** 首屏内容源的三个阶段，彼此是独立内容分支，不共用一个 LazyColumn 数据源。 */
enum class StartupPhase { SNAPSHOT, SKELETON, LIVE }

object StartupSnapshotCodec {

    const val SCHEMA_VERSION = 1
    const val MAX_ROWS = 60
    const val FILE_NAME = "startup_snapshot.json"

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    fun encode(snapshot: StartupSnapshot): String =
        json.encodeToString(StartupSnapshot.serializer(), snapshot)

    /** 解析失败 / 格式不对一律返回 null，调用方按判废回退骨架。 */
    fun decode(text: String): StartupSnapshot? = runCatching {
        json.decodeFromString(StartupSnapshot.serializer(), text)
    }.getOrNull()

    /**
     * 七条判废条件，纯 JVM 可单测。
     * 「文件不可读 / 解析失败」由 Store.read() 返回 null 处理，不在这里。
     *
     * MUST NOT 在此查库：冷启动校验快照若等开库，感知优先就白做了。
     */
    fun isUsable(
        snapshot: StartupSnapshot,
        currentYearMonth: Int,
        expectedDbVersion: Int,
        expectedSchemaVersion: Int = SCHEMA_VERSION,
        unlocked: Boolean,
        filterIsDefault: Boolean,
        isDefaultGranularity: Boolean
    ): Boolean =
        unlocked &&
            filterIsDefault &&
            isDefaultGranularity &&
            snapshot.yearMonth == currentYearMonth &&
            snapshot.dbVersion == expectedDbVersion &&
            snapshot.schemaVersion == expectedSchemaVersion &&
            snapshot.rows.size in 1..MAX_ROWS

    /** 只保留前 60 行；空列表不落盘（避免下次冷启动读到空快照）。 */
    fun trim(rows: List<SnapshotRow>): List<SnapshotRow> = rows.take(MAX_ROWS)
}

/**
 * 换源时序判定，纯 JVM 可单测。
 *
 * Paging 3 的 refresh 会先清空现有 items；`LazyPagingItems` 刚 collect 时
 * `loadState.refresh` 可能短暂是 NotLoading（还没真正开始加载）。
 * 因此 MUST 先见过一次 Loading，再等 refresh 回到 NotLoading 才允许换源，
 * 否则会立刻切到 LIVE 并把「列表空着」复现一遍。
 */
object StartupPhaseMachine {

    /** 记录是否已经真的开始过加载。 */
    fun sawLoading(previouslySaw: Boolean, currentIsLoading: Boolean): Boolean =
        previouslySaw || currentIsLoading

    /** 只有「确实加载过」且「本次 refresh 已结束」才允许原子换源。 */
    fun shouldSwitch(previouslySaw: Boolean, currentIsLoading: Boolean): Boolean =
        previouslySaw && !currentIsLoading
}
