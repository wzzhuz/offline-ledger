package com.zwz.offlineledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.zwz.offlineledger.data.local.entity.RecordTagCrossRef
import com.zwz.offlineledger.data.local.entity.TagEntity

/** 标签统计行：命中金额与笔数，刻意不提供百分比（多标签合计可超总支出）。 */
data class TagStatRow(
    val tagId: Long,
    val name: String,
    val hitCents: Long?,
    val hitCount: Int?
)

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY isArchived ASC, sortOrder ASC, id ASC")
    suspend fun all(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE isArchived = 0 ORDER BY sortOrder ASC, id ASC")
    suspend fun active(): List<TagEntity>

    @Query("SELECT id FROM tags WHERE name = :name LIMIT 1")
    suspend fun idByName(name: String): Long?

    @Insert
    suspend fun insertTag(tag: TagEntity): Long

    @Query("UPDATE tags SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE tags SET isArchived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("UPDATE tags SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int)

    @Query(
        "SELECT t.name FROM record_tags rt JOIN tags t ON t.id = rt.tagId " +
            "WHERE rt.recordId = :recordId ORDER BY t.sortOrder ASC, t.id ASC"
    )
    suspend fun namesForRecord(recordId: Long): List<String>

    @Query("DELETE FROM record_tags WHERE recordId = :recordId")
    suspend fun clearForRecord(recordId: Long)

    @Insert
    suspend fun insertRefs(refs: List<RecordTagCrossRef>)

    @Query("SELECT COUNT(*) FROM record_tags WHERE tagId = :tagId")
    suspend fun usageCount(tagId: Long): Int

    @Query(
        """
        SELECT t.id AS tagId, t.name AS name,
               COALESCE(SUM(r.amountCents), 0) AS hitCents,
               COUNT(r.id) AS hitCount
          FROM tags t
          JOIN record_tags rt ON rt.tagId = t.id
          JOIN records r ON r.id = rt.recordId
         WHERE t.isArchived = 0
           AND r.yearMonth BETWEEN :fromYm AND :toYm
           AND r.currencyCode = :currencyCode
           AND r.type IN ('EXPENSE', 'INCOME')
           AND r.excludeFromStats = 0
         GROUP BY t.id, t.name
         ORDER BY hitCents DESC
        """
    )
    suspend fun tagStats(fromYm: Int, toYm: Int, currencyCode: String): List<TagStatRow>
}
