package com.bc.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bc.app.data.entity.Exercise
import kotlinx.coroutines.flow.Flow

/**
 * 动作数据访问对象
 */
@Dao
interface ExerciseDao {

    /**
     * 插入动作
     * @return 插入的id
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(exercise: Exercise): Long

    /**
     * 批量插入动作
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exercises: List<Exercise>): List<Long>

    /**
     * 更新动作
     */
    @Update
    suspend fun update(exercise: Exercise)

    /**
     * 删除动作
     */
    @Delete
    suspend fun delete(exercise: Exercise)

    /**
     * 根据id查询动作
     */
    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: Long): Exercise?

    /**
     * 根据部位id查询所有动作
     */
    @Query("SELECT * FROM exercises WHERE bodyPartId = :bodyPartId ORDER BY name ASC")
    fun getByBodyPartId(bodyPartId: Long): Flow<List<Exercise>>

    /**
     * 根据部位id + 子分类查询动作
     */
    @Query("SELECT * FROM exercises WHERE bodyPartId = :bodyPartId AND subcategory = :subcategory ORDER BY name ASC")
    fun getByBodyPartAndSubcategory(bodyPartId: Long, subcategory: String): Flow<List<Exercise>>

    /**
     * 根据部位id + 子分类查询动作（同步）
     */
    @Query("SELECT * FROM exercises WHERE bodyPartId = :bodyPartId AND subcategory = :subcategory ORDER BY name ASC")
    suspend fun getByBodyPartAndSubcategorySync(bodyPartId: Long, subcategory: String): List<Exercise>

    /**
     * 根据部位id查询所有动作（同步）
     */
    @Query("SELECT * FROM exercises WHERE bodyPartId = :bodyPartId ORDER BY name ASC")
    suspend fun getByBodyPartIdSync(bodyPartId: Long): List<Exercise>

    /**
     * 查询所有动作
     */
    @Query("SELECT * FROM exercises ORDER BY name ASC")
    fun getAll(): Flow<List<Exercise>>

    /**
     * 根据名称搜索动作
     */
    @Query("SELECT * FROM exercises WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    suspend fun searchByName(query: String): List<Exercise>

    /**
     * 查询动作数量
     */
    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun getCount(): Int

    /**
     * 查询所有动作（同步，安全）
     */
    @Query("SELECT * FROM exercises ORDER BY name ASC")
    suspend fun getAllSyncSafe(): List<Exercise>

    /**
     * 清空所有动作
     */
    @Query("DELETE FROM exercises")
    suspend fun deleteAll()
}
