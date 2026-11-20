package vn.pvc.hub.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Số mục chưa đọc theo từng nguồn. */
data class SourceCount(val source: String, val cnt: Int)

@Dao
interface HubDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: HubItem): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<HubItem>): List<Long>

    @Query("SELECT * FROM hub_item WHERE hidden = 0 AND snoozedUntil <= :now ORDER BY time DESC LIMIT 500")
    fun observeAll(now: Long): Flow<List<HubItem>>

    @Query("SELECT * FROM hub_item WHERE hidden = 0 AND source = :source AND snoozedUntil <= :now ORDER BY time DESC LIMIT 500")
    fun observeBySource(source: String, now: Long): Flow<List<HubItem>>

    @Query("SELECT source AS source, COUNT(*) AS cnt FROM hub_item WHERE hidden = 0 AND read = 0 AND snoozedUntil <= :now GROUP BY source")
    fun observeUnreadCounts(now: Long): Flow<List<SourceCount>>

    /** Hoãn một mục tới thời điểm `until`; tới giờ nó hiện lại như tin chưa đọc. */
    @Query("UPDATE hub_item SET snoozedUntil = :until, read = 0 WHERE id = :id")
    suspend fun snooze(id: Long, until: Long)

    @Query("UPDATE hub_item SET replyTo = :addr WHERE `key` = :key AND replyTo = ''")
    suspend fun setReplyTo(key: String, addr: String)

    @Query("SELECT DISTINCT source FROM hub_item")
    fun observePresentSources(): Flow<List<String>>

    @Query("UPDATE hub_item SET read = 1 WHERE id = :id")
    suspend fun markRead(id: Long)

    @Query("UPDATE hub_item SET read = 1")
    suspend fun markAllRead()

    @Query("UPDATE hub_item SET read = 1 WHERE `key` IN (:keys)")
    suspend fun markReadByKeys(keys: List<String>)

    @Query("SELECT * FROM hub_item WHERE hidden = 0 AND source = 'EMAIL' AND account = :account AND snoozedUntil <= :now ORDER BY time DESC LIMIT 500")
    fun observeByAccount(account: String, now: Long): Flow<List<HubItem>>

    @Query("SELECT * FROM hub_item WHERE hidden = 0 AND snoozedUntil > :now ORDER BY snoozedUntil ASC LIMIT 500")
    fun observeSnoozed(now: Long): Flow<List<HubItem>>

    @Query("SELECT COUNT(*) FROM hub_item WHERE hidden = 0 AND snoozedUntil > :now")
    fun observeSnoozedCount(now: Long): Flow<Int>

    @Query("SELECT account AS source, COUNT(*) AS cnt FROM hub_item WHERE hidden = 0 AND source = 'EMAIL' AND read = 0 AND snoozedUntil <= :now GROUP BY account")
    fun observeUnreadByAccount(now: Long): Flow<List<SourceCount>>

    @Query("UPDATE hub_item SET hidden = 1, read = 1 WHERE id IN (:ids)")
    suspend fun hideByIds(ids: List<Long>)

    @Query("UPDATE hub_item SET hidden = 1, read = 1 WHERE hidden = 0 AND snoozedUntil > :now")
    suspend fun hideAllSnoozed(now: Long)

    /** Tự dọn: ẩn tin cũ hơn mốc `cutoff` (hidden = 2, giữ bản ghi để lần đồng bộ sau không hiện lại). */
    @Query("UPDATE hub_item SET hidden = 2 WHERE hidden = 0 AND time < :cutoff AND snoozedUntil <= :now")
    suspend fun autoClean(cutoff: Long, now: Long)

    @Query("SELECT * FROM hub_item WHERE hidden = 0 AND snoozedUntil > :now")
    suspend fun snoozedNow(now: Long): List<HubItem>

    @Query("UPDATE hub_item SET hidden = 1, read = 1 WHERE hidden = 0 AND source != 'EMAIL' AND snoozedUntil > :now")
    suspend fun hideNonEmailSnoozed(now: Long)

    @Query("UPDATE hub_item SET snoozedUntil = 0 WHERE snoozedUntil > :now")
    suspend fun unsnoozeAll(now: Long)

    @Query("UPDATE hub_item SET hidden = 0 WHERE hidden = 1")
    suspend fun unhideAll()

    @Query("SELECT COUNT(*) FROM hub_item WHERE hidden = 1")
    suspend fun hiddenCount(): Int

    /** Số tin hoãn vừa tới giờ trong khoảng (since, now]. */
    @Query("SELECT COUNT(*) FROM hub_item WHERE hidden = 0 AND snoozedUntil > 0 AND snoozedUntil > :since AND snoozedUntil <= :now")
    suspend fun dueCount(since: Long, now: Long): Int

    @Query("SELECT * FROM hub_item WHERE id = :id")
    suspend fun byId(id: Long): HubItem?

    @Query("UPDATE hub_item SET read = 1 WHERE id IN (:ids)")
    suspend fun markReadByIds(ids: List<Long>)

    @Query("SELECT * FROM hub_item WHERE hidden = 0 AND snoozedUntil <= :now ORDER BY time DESC LIMIT :limit")
    suspend fun latest(now: Long, limit: Int): List<HubItem>

    @Query("SELECT COUNT(*) FROM hub_item WHERE hidden = 0 AND read = 0 AND snoozedUntil <= :now")
    suspend fun unreadTotal(now: Long): Int

    @Query("SELECT * FROM hub_item ORDER BY time DESC")
    suspend fun all(): List<HubItem>

    @Query("DELETE FROM hub_item")
    suspend fun deleteAll()
}
