package com.klynstudios.hapture.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** A named spring ("snappy") kept in physical terms so it means the same on every platform. */
@Entity(tableName = "motion_tokens")
data class MotionTokenEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val stiffness: Float,
    val dampingRatio: Float,
    val createdAt: Long,
)

@Dao
interface MotionTokenDao {
    @Query("SELECT * FROM motion_tokens ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<MotionTokenEntity>>

    @Query("SELECT * FROM motion_tokens WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun byName(name: String): MotionTokenEntity?

    /** Nothing references a token, so REPLACE is safe. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(token: MotionTokenEntity): Long

    @Delete
    suspend fun delete(token: MotionTokenEntity)
}

class MotionTokenRepository(private val dao: MotionTokenDao) {
    fun observeAll(): Flow<List<MotionTokenEntity>> = dao.observeAll()

    /** Saving under an existing name (any case) overwrites that token. */
    suspend fun save(name: String, stiffness: Float, dampingRatio: Float) {
        val clean = name.trim().take(40).ifEmpty { return }
        val existing = dao.byName(clean)
        dao.insert(MotionTokenEntity(existing?.id ?: 0, clean, stiffness, dampingRatio, existing?.createdAt ?: System.currentTimeMillis()))
    }

    suspend fun delete(token: MotionTokenEntity) = dao.delete(token)
}
