package com.casper.dalilak.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tools", indices = [Index(value = ["toolKey"], unique = true)])
data class ToolEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val toolKey: String,
    val name: String,
    val type: String,
    val pricing: String,
    val reason: String,
    val limitation: String,
    val url: String,
    val details: String,
    val verified: String,
    val unverified: String,
    val sources: String,
    val verifiedAt: Long,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "preferences")
data class PreferenceEntity(
    @PrimaryKey val prefKey: String,
    val value: String,
    val updatedAt: Long
)

@Entity(tableName = "choices")
data class ChoiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val toolName: String,
    val toolUrl: String,
    val task: String,
    val action: String, // CHOSE | REJECTED
    val reason: String,
    val worked: Int,    // 1 yes, 0 no, -1 unknown
    val useAgain: Int,  // 1 yes, 0 no, -1 unknown
    val createdAt: Long
)

@Entity(tableName = "search_history")
data class SearchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val task: String,
    val resultJson: String,
    val toolCount: Int,
    val createdAt: Long
)

@Dao
interface ToolDao {
    @Query("SELECT * FROM tools WHERE toolKey = :k LIMIT 1")
    suspend fun byKey(k: String): ToolEntity?

    @Insert
    suspend fun insert(t: ToolEntity): Long

    @Update
    suspend fun update(t: ToolEntity)

    @Query("SELECT * FROM tools ORDER BY updatedAt DESC LIMIT :n")
    suspend fun recent(n: Int): List<ToolEntity>

    @Query("SELECT * FROM tools ORDER BY updatedAt DESC")
    suspend fun all(): List<ToolEntity>
}

@Dao
interface PreferenceDao {
    @Query("SELECT * FROM preferences")
    fun observeAll(): Flow<List<PreferenceEntity>>

    @Query("SELECT * FROM preferences")
    suspend fun all(): List<PreferenceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(p: PreferenceEntity)

    @Query("DELETE FROM preferences")
    suspend fun deleteAll()
}

@Dao
interface ChoiceDao {
    @Query("SELECT * FROM choices ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ChoiceEntity>>

    @Query("SELECT * FROM choices")
    suspend fun all(): List<ChoiceEntity>

    @Insert
    suspend fun insert(c: ChoiceEntity): Long

    @Update
    suspend fun update(c: ChoiceEntity)

    @Delete
    suspend fun delete(c: ChoiceEntity)
}

@Dao
interface SearchDao {
    @Query("SELECT * FROM search_history ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SearchEntity>>

    @Query("SELECT * FROM search_history")
    suspend fun all(): List<SearchEntity>

    @Insert
    suspend fun insert(s: SearchEntity): Long

    @Delete
    suspend fun delete(s: SearchEntity)
}

@Database(
    entities = [ToolEntity::class, PreferenceEntity::class, ChoiceEntity::class, SearchEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tools(): ToolDao
    abstract fun prefs(): PreferenceDao
    abstract fun choices(): ChoiceDao
    abstract fun searches(): SearchDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(ctx: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    ctx.applicationContext,
                    AppDatabase::class.java,
                    "dalilak_db"
                ).addMigrations(*Migrations.ALL).build().also { instance = it }
            }
    }
}

/** أي نسخة قاعدة بيانات جديدة = migration هنا (مفيش fallbackToDestructiveMigration). */
object Migrations {
    val ALL: Array<Migration> = arrayOf()
}
