package com.bc.app.data.repository

import com.bc.app.data.dao.ExerciseDao
import com.bc.app.data.entity.Exercise
import kotlinx.coroutines.flow.Flow

/**
 * 动作数据仓库
 * 封装动作数据的读写逻辑
 */
class ExerciseRepository(private val exerciseDao: ExerciseDao) {

    /**
     * 所有动作数据流
     */
    val allExercises: Flow<List<Exercise>> = exerciseDao.getAll()

    /**
     * 插入动作
     */
    suspend fun insert(exercise: Exercise): Long {
        return exerciseDao.insert(exercise)
    }

    /**
     * 批量插入动作
     */
    suspend fun insertAll(exercises: List<Exercise>): List<Long> {
        return exerciseDao.insertAll(exercises)
    }

    /**
     * 更新动作
     */
    suspend fun update(exercise: Exercise) {
        exerciseDao.update(exercise)
    }

    /**
     * 删除动作
     */
    suspend fun delete(exercise: Exercise) {
        exerciseDao.delete(exercise)
    }

    /**
     * 根据id查询动作
     */
    suspend fun getById(id: Long): Exercise? {
        return exerciseDao.getById(id)
    }

    /**
     * 根据部位id查询动作
     */
    fun getByBodyPartId(bodyPartId: Long): Flow<List<Exercise>> {
        return exerciseDao.getByBodyPartId(bodyPartId)
    }

    /**
     * 根据部位id + 子分类查询动作
     */
    fun getByBodyPartAndSubcategory(bodyPartId: Long, subcategory: String): Flow<List<Exercise>> {
        return exerciseDao.getByBodyPartAndSubcategory(bodyPartId, subcategory)
    }

    /**
     * 根据部位id + 子分类查询动作（同步）
     */
    suspend fun getByBodyPartAndSubcategorySync(bodyPartId: Long, subcategory: String): List<Exercise> {
        return exerciseDao.getByBodyPartAndSubcategorySync(bodyPartId, subcategory)
    }

    /**
     * 同步根据部位id查询动作
     */
    suspend fun getByBodyPartIdSync(bodyPartId: Long): List<Exercise> {
        return exerciseDao.getByBodyPartIdSync(bodyPartId)
    }

    /**
     * 根据名称搜索动作
     */
    suspend fun searchByName(query: String): List<Exercise> {
        return exerciseDao.searchByName(query)
    }

    /**
     * 查询动作数量
     */
    suspend fun getCount(): Int {
        return exerciseDao.getCount()
    }
}
