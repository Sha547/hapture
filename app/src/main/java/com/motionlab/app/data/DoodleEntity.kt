package com.motionlab.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** A saved doodle: [strokes] is [com.motionlab.app.core.doodle.DoodleCodec] JSON, [background] a DoodleBackground name. */
@Entity(tableName = "doodles")
data class DoodleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val background: String,
    val strokes: String,
)

@Dao
interface DoodleDao {
    @Query("SELECT * FROM doodles ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DoodleEntity>>

    @Query("SELECT * FROM doodles WHERE id = :id")
    suspend fun getById(id: Long): DoodleEntity?

    @Query("SELECT COUNT(*) FROM doodles")
    suspend fun count(): Int

    /** No children reference a doodle, so REPLACE is safe here -- it also lets Undo re-insert under the same id. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DoodleEntity): Long

    @Update
    suspend fun update(entity: DoodleEntity)

    @Delete
    suspend fun delete(entity: DoodleEntity)
}

class DoodleRepository(private val dao: DoodleDao) {
    fun observeAll(): Flow<List<DoodleEntity>> = dao.observeAll()

    suspend fun get(id: Long): DoodleEntity? = dao.getById(id)

    suspend fun createDefault(): DoodleEntity {
        val count = dao.count()
        val now = System.currentTimeMillis()
        val e = DoodleEntity(
            name = if (count == 0) "Doodle" else "Doodle ${count + 1}",
            createdAt = now, updatedAt = now, background = "PAPER", strokes = "",
        )
        return e.copy(id = dao.insert(e))
    }

    suspend fun save(entity: DoodleEntity) = dao.update(entity.copy(updatedAt = System.currentTimeMillis()))

    suspend fun rename(entity: DoodleEntity, newName: String) = save(entity.copy(name = newName))

    suspend fun delete(entity: DoodleEntity) = dao.delete(entity)

    suspend fun restore(entity: DoodleEntity) {
        dao.insert(entity)
    }
}
