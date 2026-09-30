package com.klynstudios.hapture.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** A named bundle of springs for one kind of app ("Fintech, restrained"). [archetype] is the tag it's filed under. */
@Entity(tableName = "token_sets")
data class TokenSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val archetype: String,
    val createdAt: Long,
)

/** One spring in a set, held by value so the set stays intact if a token of the same name is later edited or deleted. */
@Entity(
    tableName = "token_set_items",
    foreignKeys = [ForeignKey(entity = TokenSetEntity::class, parentColumns = ["id"], childColumns = ["setId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("setId")],
)
data class TokenSetItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val setId: Long,
    val name: String,
    val stiffness: Float,
    val dampingRatio: Float,
)

data class TokenSetWithItems(val set: TokenSetEntity, val items: List<TokenSetItemEntity>)

@Dao
interface TokenSetDao {
    @Query("SELECT * FROM token_sets ORDER BY createdAt DESC")
    fun observeSets(): Flow<List<TokenSetEntity>>

    @Query("SELECT * FROM token_set_items ORDER BY id ASC")
    fun observeItems(): Flow<List<TokenSetItemEntity>>

    @Query("SELECT * FROM token_set_items WHERE setId = :setId ORDER BY id ASC")
    suspend fun itemsOf(setId: Long): List<TokenSetItemEntity>

    /** Sets are only ever inserted new (never re-saved), so REPLACE can't cascade away any children. */
    @Insert
    suspend fun insertSet(set: TokenSetEntity): Long

    @Insert
    suspend fun insertItems(items: List<TokenSetItemEntity>)

    @Delete
    suspend fun delete(set: TokenSetEntity)
}

class TokenSetRepository(private val dao: TokenSetDao) {
    fun observeSets(): Flow<List<TokenSetEntity>> = dao.observeSets()
    fun observeItems(): Flow<List<TokenSetItemEntity>> = dao.observeItems()

    /** Files the given springs under a new set. Blank names are dropped; a set with nothing left isn't created (returns null). */
    suspend fun create(name: String, archetype: String, springs: List<Triple<String, Float, Float>>): Long? {
        val clean = springs.filter { it.first.isNotBlank() }
        if (clean.isEmpty()) return null
        val id = dao.insertSet(TokenSetEntity(name = name.trim().take(40).ifEmpty { "Set" }, archetype = archetype.trim().take(40), createdAt = System.currentTimeMillis()))
        dao.insertItems(clean.map { (n, k, z) -> TokenSetItemEntity(setId = id, name = n.trim().take(40), stiffness = k, dampingRatio = z) })
        return id
    }

    suspend fun delete(set: TokenSetEntity) = dao.delete(set)
}
