package com.skyqlike.iptv.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.skyqlike.iptv.data.model.Programme

/** One TV show saved in the database, for one provider. */
@Entity(
    tableName = "programmes",
    indices = [Index(value = ["providerId", "start"])],
)
data class ProgrammeRow(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val providerId: String,
    val channelEpgId: String,
    val title: String,
    val description: String,
    val genre: String,
    val start: Long,
    val stop: Long,
    val iconUrl: String?,
)

/**
 * Notes about the saved guide for one provider: when it was downloaded, which hours it covers,
 * and which channels it was built for (so a changed channel list triggers a rebuild).
 */
@Entity(tableName = "epg_meta")
data class EpgMeta(
    @PrimaryKey val providerId: String,
    val fetchedAt: Long,
    val coverFrom: Long,
    val coverTo: Long,
    val wantedHash: Int,
)

fun ProgrammeRow.toProgramme() = Programme(
    channelEpgId = channelEpgId,
    title = title,
    description = description,
    genre = genre,
    start = start,
    stop = stop,
    iconUrl = iconUrl,
)

@Dao
abstract class EpgDao {

    @Query("SELECT * FROM epg_meta WHERE providerId = :providerId")
    abstract suspend fun meta(providerId: String): EpgMeta?

    @Query(
        "SELECT * FROM programmes WHERE providerId = :providerId AND stop > :from AND start < :to " +
            "ORDER BY channelEpgId, start"
    )
    abstract suspend fun programmes(providerId: String, from: Long, to: Long): List<ProgrammeRow>

    @Query("DELETE FROM programmes WHERE providerId = :providerId")
    abstract suspend fun deleteProgrammes(providerId: String)

    @Query("DELETE FROM epg_meta WHERE providerId = :providerId")
    abstract suspend fun deleteMeta(providerId: String)

    @Query("DELETE FROM programmes")
    abstract suspend fun clearProgrammes()

    @Query("DELETE FROM epg_meta")
    abstract suspend fun clearMeta()

    @Insert
    abstract suspend fun insertProgrammes(rows: List<ProgrammeRow>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putMeta(meta: EpgMeta)

    /** Swaps a provider's saved guide for a new one in one step, so a half-written guide is never left behind. */
    @Transaction
    open suspend fun replaceGuide(providerId: String, rows: List<ProgrammeRow>, meta: EpgMeta) {
        deleteProgrammes(providerId)
        rows.chunked(500).forEach { insertProgrammes(it) }
        putMeta(meta)
    }

    @Transaction
    open suspend fun forgetProvider(providerId: String) {
        deleteProgrammes(providerId)
        deleteMeta(providerId)
    }

    @Transaction
    open suspend fun clearAll() {
        clearProgrammes()
        clearMeta()
    }
}

@Database(entities = [ProgrammeRow::class, EpgMeta::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun epg(): EpgDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "iptv.db")
                    // The database only holds a copy of the guide, so rebuilding it is always safe.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
