package com.bc.app.data.repository

import com.bc.app.data.dao.TrainingRecordDao
import com.bc.app.data.entity.TrainingRecord
import com.bc.app.utils.OneRmCalculator
import kotlinx.coroutines.flow.Flow

/**
 * 训练记录数据仓库
 * 封装训练记录的读写逻辑
 */
class TrainingRecordRepository(private val trainingRecordDao: TrainingRecordDao) {

    /**
     * 所有训练记录数据流
     */
    val allRecords: Flow<List<TrainingRecord>> = trainingRecordDao.getAll()

    /**
     * 插入训练记录
     */
    suspend fun insert(record: TrainingRecord): Long {
        return trainingRecordDao.insert(record)
    }

    /**
     * 批量插入训练记录
     */
    suspend fun insertAll(records: List<TrainingRecord>): List<Long> {
        return trainingRecordDao.insertAll(records)
    }

    /**
     * 更新训练记录
     */
    suspend fun update(record: TrainingRecord) {
        trainingRecordDao.update(record)
    }

    /**
     * 删除训练记录
     */
    suspend fun delete(record: TrainingRecord) {
        trainingRecordDao.delete(record)
    }

    /**
     * 根据id查询训练记录
     */
    suspend fun getById(id: Long): TrainingRecord? {
        return trainingRecordDao.getById(id)
    }

    /**
     * 根据动作id查询训练记录
     */
    fun getByExerciseId(exerciseId: Long): Flow<List<TrainingRecord>> {
        return trainingRecordDao.getByExerciseId(exerciseId)
    }

    /**
     * 同步根据动作id查询训练记录
     */
    suspend fun getByExerciseIdSync(exerciseId: Long): List<TrainingRecord> {
        return trainingRecordDao.getByExerciseIdSync(exerciseId)
    }

    /**
     * 查询某个动作的最新记录
     */
    suspend fun getLatestByExerciseId(exerciseId: Long): TrainingRecord? {
        return trainingRecordDao.getLatestByExerciseId(exerciseId)
    }

    /**
     * 查询某个动作的历史最高重量
     */
    suspend fun getMaxWeightByExerciseId(exerciseId: Long): Float? {
        return trainingRecordDao.getMaxWeightByExerciseId(exerciseId)
    }

    /**
     * 查询某个动作的历史最高单次容量
     */
    suspend fun getMaxVolumeByExerciseId(exerciseId: Long): Float? {
        return trainingRecordDao.getMaxVolumeByExerciseId(exerciseId)
    }

    /**
     * 查询某个动作的历史最大 1RM 估算
     */
    suspend fun getMaxOneRmByExerciseId(exerciseId: Long): Float? {
        val records = trainingRecordDao.getAllByExerciseIdForPr(exerciseId)
        return records.mapNotNull { OneRmCalculator.fromRecord(it) }.filter { it > 0 }.maxOrNull()
    }

    /**
     * 查询历史前 N 条按重量排序的记录（PR 榜单）
     */
    suspend fun getTopByWeight(exerciseId: Long, limit: Int): List<TrainingRecord> {
        return trainingRecordDao.getTopByWeight(exerciseId, limit)
    }

    /**
     * 分页查询训练记录
     */
    suspend fun getByExerciseIdPaged(exerciseId: Long, limit: Int, offset: Int): List<TrainingRecord> {
        return trainingRecordDao.getByExerciseIdPaged(exerciseId, limit, offset)
    }

    /**
     * 查询训练记录数量
     */
    suspend fun getCount(): Int {
        return trainingRecordDao.getCount()
    }
}
