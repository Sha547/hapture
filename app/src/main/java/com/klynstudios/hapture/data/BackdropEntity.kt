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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A screenshot of the person's real app that the experiment's stage shows behind the moving
 * object. Its own table (not columns on the experiment) so an editor holding an older copy of
 * the experiment can never overwrite it. [focusY] is which part of a tall screenshot the
 * stage window shows, 0 = top, 1 = bottom. [path] is the image's file name inside filesDir/backdrops,
 * not an absolute path, so it still resolves if the app's data directory moves (a restore onto another phone).
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
        // Decoding, compressing and writing a full screenshot takes long enough to drop frames on Main.
        val file = withContext(Dispatchers.IO) {
            val bitmap = decode(uri) ?: return@withContext null
            dir.mkdirs()
            File(dir, "$experimentId.jpg").also { file ->
                try {
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                } finally {
                    bitmap.recycle()
                }
            }
        } ?: return false
        dao.put(BackdropEntity(experimentId, file.name, dao.get(experimentId)?.focusY ?: 0.35f))
        return true
    }

    /** The backdrop row as it is now, taken just before its experiment is deleted so an Undo can put it back. */
    suspend fun snapshot(experimentId: Long): BackdropEntity? = dao.get(experimentId)

    /**
     * Puts back a row from [snapshot] after its experiment was restored. The image file outlives the cascade
     * (until [clear] or [discard] deletes it), so this is just the row -- unless the file has gone, then there's
     * nothing to show.
     */
    suspend fun restore(backdrop: BackdropEntity) {
        if (fileOf(backdrop.path).exists()) dao.put(backdrop)
    }

    /** Deletes a [snapshot]'s image once its experiment's delete can no longer be undone. The row is already gone. */
    fun discard(backdrop: BackdropEntity) {
        fileOf(backdrop.path).delete()
    }

    suspend fun setFocus(experimentId: Long, focusY: Float) =dao.setFocus(experimentId, focusY.coerceIn(0f, 1f))

    suspend fun clear(experimentId: Long) {
        dao.get(experimentId)?.let { fileOf(it.path).delete() }
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

    fun load(path: String): Bitmap? = BitmapFactory.decodeFile(fileOf(path).path)

    private val dir get() = File(context.filesDir, "backdrops")

    /** Only the last segment is used, so a stored name can never point outside the backdrops folder. */
    fun fileOf(path: String): File = File(dir, File(path).name)

    companion object {
        const val MAX_EDGE = 1600
    }
}
