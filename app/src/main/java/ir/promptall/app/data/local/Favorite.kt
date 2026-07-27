package ir.promptall.app.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "favorites", primaryKeys = ["id"])
data class Favorite(
    val id: Long,
    val title: String,
    val promptText: String,
    val imageUrl: String,
    val imageWidth: Int,
    val imageHeight: Int,
    val savedAt: Long = System.currentTimeMillis(),
)

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY savedAt DESC")
    fun observeAll(): Flow<List<Favorite>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(item: Favorite)

    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun remove(id: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE id = :id)")
    suspend fun contains(id: Long): Boolean
}

@Database(entities = [Favorite::class], version = 1, exportSchema = false)
abstract class PromptAllDatabase : RoomDatabase() {
    abstract fun favorites(): FavoriteDao
}
