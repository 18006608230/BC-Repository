package com.bc.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bc.app.data.entity.BodyMetric
import kotlinx.coroutines.flow.Flow

/**
 * 身体数据记录访问对象
 */
@Dao
interface BodyMetricDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(metric: BodyMetric): Long

    @Delete
    suspend fun delete(metric: BodyMetric)

    @Query("SELECT * FROM body_metrics ORDER BY date DESC")
    fun getAll(): Flow<List<BodyMetric>>

    @Query("SELECT * FROM body_metrics ORDER BY date DESC")
    suspend fun getAllSync(): List<BodyMetric>

    @Query("SELECT * FROM body_metrics ORDER BY date DESC LIMIT 1")
    suspend fun getLatest(): BodyMetric?

    @Query("SELECT COUNT(*) FROM body_metrics")
    suspend fun getCount(): Int

    @Query("DELETE FROM body_metrics")
    suspend fun deleteAll()
}
