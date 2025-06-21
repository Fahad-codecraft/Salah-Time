package com.example.salahtime

import androidx.room.*

@Entity
data class CachedSalahData(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,
    val latitude: String,
    val longitude: String,
    val dataJson: String,
    val timestamp: Long = System.currentTimeMillis()  // <-- ADD THIS
)

@Dao
interface SalahDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSalahData(data: CachedSalahData)

    @Query("SELECT * FROM CachedSalahData WHERE date = :date AND latitude = :lat AND longitude = :lon AND timestamp >= :validFrom LIMIT 1")
    suspend fun getValidSalahData(date: String, lat: String, lon: String, validFrom: Long): CachedSalahData?

    @Query("SELECT * FROM CachedSalahData WHERE date = :date AND latitude = :lat AND longitude = :lon LIMIT 1")
    suspend fun getSalahData(date: String, lat: String, lon: String): CachedSalahData?

    @Query("DELETE FROM CachedSalahData WHERE timestamp < :validFrom")
    suspend fun deleteOldCache(validFrom: Long)
}

@Database(entities = [CachedSalahData::class], version = 2, exportSchema = false)
abstract class SalahDatabase : RoomDatabase() {
    abstract fun salahDao(): SalahDao
}