package com.klynstudios.hapture.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * A screenshot of the person's real app that the experiment's stage shows behind the moving
 * object. Its own table (not columns on the experiment) so an editor holding an older copy of
 * the experiment can never overwrite it. [focusY] is which part of a tall screenshot the
 * stage window shows, 0 = top, 1 = bottom.
 */
@Entity(
    tableName = "experiment_backdrops",
    foreignKeys = [ForeignKey(entity = ExperimentEntity::class, parentColumns = ["id"], childColumns = ["experimentId"], onDelete = ForeignKey.CASCADE)],
)
data class BackdropEntity(
    @PrimaryKey val experimentId: Long,
    val path: String,
    val focusY: Float,
)

@Dao
interface BackdropDao {
    @Query("SELECT * FROM experiment_backdrops WHERE experimentId = :id")
    fun observe(id: Long): Flow<BackdropEntity?>

    @Query("SELECT * FROM experiment_backdrops WHERE experimentId = :id")
    suspend fun get(id: Long): BackdropEntity?

    /** Nothing references a backdrop row, so replacing it is safe. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(backdrop: BackdropEntity)

    @Query("UPDATE experiment_backdrops SET focusY = :focusY WHERE experimentId = :id")
    suspend fun setFocus(id: Long, focusY: Float)

    @Query("DELETE FROM experiment_backdrops WHERE experimentId = :id")
    suspend fun remove(id: Long)
}

/** Copies a picked screenshot into the app's own storage, so it survives the picker's access expiring. */
class BackdropRepository(private val context: Context, private val dao: BackdropDao) {
    fun observe(id: Long): Flow<BackdropEntity?> = dao.observe(id)

    /** Returns false if [uri] isn't a readable image. */
    suspend fun set(experimentId: Long, uri: Uri): Boolean {
        val bitmap = decode(uri) ?: return false
        val dir = File(context.filesDir, "backdrops").apply { mkdirs() }
        val file = File(dir, "$experimentId.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        dao.put(BackdropEntity(experimentId, file.absolutePath, dao.get(experimentId)?.focusY ?: 0.35f))
        return true
    }

    /** The backdrop row as it is now, taken just before its experiment is deleted so an Undo can put it back. */
    suspend fun snapshot(experimentId: Long): BackdropEntity? = dao.get(experimentId)

    /**
     * Puts back a row from [snapshot] after its experiment was restored. The image file outlives the cascade
     * (only [clear] deletes it), so this is just the row -- unless the file has gone, then there's nothing to show.
     */
    suspend fun restore(backdrop: BackdropEntity) {
        if (File(backdrop.path).exists()) dao.put(backdrop)
    }

    suspend fun setFocus(experimentId: Long, focusY: Float) =dao.setFocus(experimentId, focusY.coerceIn(0f, 1f))

    suspend fun clear(experimentId: Long) {
        dao.get(experimentId)?.let { File(it.path).delete() }
        dao.remove(experimentId)
    }

    /** Downscaled to at most [MAX_EDGE] px on the long side: plenty for a phone-sized stage, cheap to keep. */
    private fun decode(uri: Uri): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        if (longEdge <= 0) null else {
            val opts = BitmapFactory.Options().apply { inSampleSize = Integer.highestOneBit((longEdge / MAX_EDGE).coerceAtLeast(1)) }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }
    } catch (_: Exception) {
        null
    }

    fun load(path: String): Bitmap? = BitmapFactory.decodeFile(path)

    companion object {
        const val MAX_EDGE = 1600
    }
}
