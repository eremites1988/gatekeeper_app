package com.gatekeeper.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedAppDao {
    @Query("SELECT * FROM blocked_apps ORDER BY label")
    fun observeAll(): Flow<List<BlockedApp>>

    @Query("SELECT * FROM blocked_apps WHERE packageName = :pkg")
    suspend fun get(pkg: String): BlockedApp?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(app: BlockedApp)

    @Query("DELETE FROM blocked_apps WHERE packageName = :pkg")
    suspend fun delete(pkg: String)
}

@Dao
interface GateConfigDao {
    @Query("SELECT * FROM gate_configs WHERE isGlobalDefault = 1 LIMIT 1")
    suspend fun getGlobalDefault(): GateConfig?

    @Query("SELECT * FROM gate_configs WHERE isGlobalDefault = 1 LIMIT 1")
    fun observeGlobalDefault(): Flow<GateConfig?>

    @Query("SELECT * FROM gate_configs WHERE id = :id")
    suspend fun get(id: Long): GateConfig?

    @Insert
    suspend fun insert(config: GateConfig): Long

    @Update
    suspend fun update(config: GateConfig)

    @Query("DELETE FROM gate_configs WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE isDone = 0 ORDER BY priority DESC, createdAt")
    fun observeIncomplete(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE isDone = 1 ORDER BY completedAt DESC")
    fun observeCompleted(): Flow<List<Task>>

    @Query("SELECT COUNT(*) FROM tasks WHERE isDone = 0")
    suspend fun incompleteCount(): Int

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): Task?

    @Insert
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    @Delete
    suspend fun delete(task: Task)

    /** Daily reset of recurring tasks (R-5.4), run by WorkManager. */
    @Query("UPDATE tasks SET isDone = 0, completedAt = NULL WHERE isRecurring = 1 AND isDone = 1")
    suspend fun resetRecurring()
}

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY name")
    fun observeAll(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun get(id: Long): Exercise?

    @Insert
    suspend fun insert(exercise: Exercise): Long

    @Update
    suspend fun update(exercise: Exercise)

    @Delete
    suspend fun delete(exercise: Exercise)
}

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: Long): Book?

    @Insert
    suspend fun insert(book: Book): Long

    @Update
    suspend fun update(book: Book)

    @Delete
    suspend fun delete(book: Book)
}

@Dao
interface SessionGrantDao {
    @Query("SELECT * FROM session_grants WHERE expiresAt > :now")
    suspend fun active(now: Long): List<SessionGrant>

    @Query("SELECT COUNT(*) FROM session_grants WHERE packageName = :pkg AND grantedAt >= :since")
    suspend fun countSince(pkg: String, since: Long): Int

    @Query("SELECT MAX(grantedAt) FROM session_grants WHERE packageName = :pkg")
    suspend fun lastGrantedAt(pkg: String): Long?

    @Query("SELECT COUNT(*) FROM session_grants WHERE grantedAt >= :since")
    suspend fun totalCountSince(since: Long): Int

    @Insert
    suspend fun insert(grant: SessionGrant): Long

    @Query("UPDATE session_grants SET expiresAt = :now WHERE expiresAt > :now")
    suspend fun expireAll(now: Long)

    @Query("UPDATE session_grants SET expiresAt = :now WHERE packageName = :pkg AND expiresAt > :now")
    suspend fun expireFor(pkg: String, now: Long)
}

@Dao
interface CompletionLogDao {
    @Insert
    suspend fun insert(log: CompletionLog)

    @Query("SELECT COALESCE(SUM(amount), 0) FROM completion_logs WHERE type = :type AND timestamp >= :since")
    suspend fun sumSince(type: LogType, since: Long): Int

    @Query("SELECT * FROM completion_logs ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<CompletionLog>>
}
