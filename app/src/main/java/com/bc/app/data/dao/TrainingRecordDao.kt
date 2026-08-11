package com.bc.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bc.app.data.entity.TrainingRecord
import kotlinx.coroutines.flow.Flow

/**
 * 训练记录数据访问对象
 */
@Dao
interface TrainingRecordDao {

    /**
     * 插入训练记录
     * @return 插入的id
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: TrainingRecord): Long

    /**
     * 批量插入训练记录
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<TrainingRecord>): List<Long>

    /**
     * 更新训练记录
     */
    @Update
    suspend fun update(record: TrainingRecord)

    /**
     * 删除训练记录
     */
    @Delete
    suspend fun delete(record: TrainingRecord)

    /**
     * 根据id查询训练记录
     */
    @Query("SELECT * FROM training_records WHERE id = :id")
    suspend fun getById(id: Long): TrainingRecord?

    /**
     * 根据动作id查询所有训练记录，按日期倒序
     */
    @Query("SELECT * FROM training_records WHERE exerciseId = :exerciseId ORDER BY date DESC")
    fun getByExerciseId(exerciseId: Long): Flow<List<TrainingRecord>>

    /**
     * 根据动作id查询所有训练记录（同步）
     */
    @Query("SELECT * FROM training_records WHERE exerciseId = :exerciseId ORDER BY date DESC")
    suspend fun getByExerciseIdSync(exerciseId: Long): List<TrainingRecord>

    /**
     * 查询所有训练记录
     */
    @Query("SELECT * FROM training_records ORDER BY date DESC")
    fun getAll(): Flow<List<TrainingRecord>>

    /**
     * 查询某个动作的最新一条记录
     */
    @Query("SELECT * FROM training_records WHERE exerciseId = :exerciseId ORDER BY date DESC LIMIT 1")
    suspend fun getLatestByExerciseId(exerciseId: Long): TrainingRecord?

    /**
     * 查询某个动作的历史最高重量
     */
    @Query("SELECT MAX(weight) FROM training_records WHERE exerciseId = :exerciseId")
    suspend fun getMaxWeightByExerciseId(exerciseId: Long): Float?

    /**
     * 查询某个动作的历史最大单次容量（组数×重量×次数，用 avgReps 估算）
     */
    @Query("SELECT MAX(weight * sets * avgReps) FROM training_records WHERE exerciseId = :exerciseId")
    suspend fun getMaxVolumeByExerciseId(exerciseId: Long): Float?

    /**
     * 查询某个动作达到历史最高重量的一条记录（用于 PR 榜单）
     */
    @Query("SELECT * FROM training_records WHERE exerciseId = :exerciseId AND weight = (SELECT MAX(weight) FROM training_records WHERE exerciseId = :exerciseId) ORDER BY date DESC LIMIT 1")
    suspend fun getPrWeightRecord(exerciseId: Long): TrainingRecord?

    /**
     * 查询某个动作历史前 N 条记录（按重量降序，用于 PR 榜单）
     */
    @Query("SELECT * FROM training_records WHERE exerciseId = :exerciseId ORDER BY weight DESC, date DESC LIMIT :limit")
    suspend fun getTopByWeight(exerciseId: Long, limit: Int): List<TrainingRecord>

    /**
     * 查询某个动作达到历史最大 1RM 预估的一组（用专业模式 setDetails 计算）或最高重量记录
     */
    @Query("SELECT * FROM training_records WHERE exerciseId = :exerciseId AND weight > 0 ORDER BY date DESC")
    suspend fun getAllByExerciseIdForPr(exerciseId: Long): List<TrainingRecord>

    /**
     * 分页查询训练记录
     */
    @Query("SELECT * FROM training_records WHERE exerciseId = :exerciseId ORDER BY date DESC LIMIT :limit OFFSET :offset")
    suspend fun getByExerciseIdPaged(exerciseId: Long, limit: Int, offset: Int): List<TrainingRecord>

    /**
     * 查询训练记录数量
     */
    @Query("SELECT COUNT(*) FROM training_records")
    suspend fun getCount(): Int

    /**
     * 查询所有训练记录（同步，安全）
     */
    @Query("SELECT * FROM training_records ORDER BY date DESC")
    suspend fun getAllSyncSafe(): List<TrainingRecord>

    /**
     * 清空所有训练记录
     */
    @Query("DELETE FROM training_records")
    suspend fun deleteAll()
}
