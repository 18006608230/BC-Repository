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
     * 根据部位id查询所有动作（精选/自定义置顶）
     */
    @Query("SELECT * FROM exercises WHERE bodyPartId = :bodyPartId ORDER BY isFavorite DESC, sortWeight ASC, name ASC")
    fun getByBodyPartId(bodyPartId: Long): Flow<List<Exercise>>

    /**
     * 根据部位id + 子分类查询动作（精选/自定义置顶）
     */
    @Query("SELECT * FROM exercises WHERE bodyPartId = :bodyPartId AND subcategory = :subcategory ORDER BY isFavorite DESC, sortWeight ASC, name ASC")
    fun getByBodyPartAndSubcategory(bodyPartId: Long, subcategory: String): Flow<List<Exercise>>

    /**
     * 根据部位id + 子分类查询动作（同步）
     */
    @Query("SELECT * FROM exercises WHERE bodyPartId = :bodyPartId AND subcategory = :subcategory ORDER BY isFavorite DESC, sortWeight ASC, name ASC")
    suspend fun getByBodyPartAndSubcategorySync(bodyPartId: Long, subcategory: String): List<Exercise>

    /**
     * 根据部位id查询所有动作（同步）
     */
    @Query("SELECT * FROM exercises WHERE bodyPartId = :bodyPartId ORDER BY isFavorite DESC, sortWeight ASC, name ASC")
    suspend fun getByBodyPartIdSync(bodyPartId: Long): List<Exercise>

    /**
     * 查询所有动作
     */
    @Query("SELECT * FROM exercises ORDER BY isFavorite DESC, sortWeight ASC, name ASC")
    fun getAll(): Flow<List<Exercise>>

    /**
     * 根据名称搜索动作（中文名 / 英文原名 / 目标肌群）
     */
    @Query(
        "SELECT * FROM exercises WHERE name LIKE '%' || :query || '%' " +
            "OR nameEn LIKE '%' || :query || '%' OR target LIKE '%' || :query || '%' " +
            "ORDER BY isFavorite DESC, sortWeight ASC, name ASC"
    )
    suspend fun searchByName(query: String): List<Exercise>

    /**
     * 全库搜索（Flow，动作列表页搜索框用）
     */
    @Query(
        "SELECT * FROM exercises WHERE name LIKE '%' || :query || '%' " +
            "OR nameEn LIKE '%' || :query || '%' OR target LIKE '%' || :query || '%' " +
            "OR secondaryMuscles LIKE '%' || :query || '%' " +
            "ORDER BY isFavorite DESC, sortWeight ASC, name ASC LIMIT 300"
    )
    fun searchAll(query: String): Flow<List<Exercise>>

    /**
     * 部位内搜索（Flow）
     */
    @Query(
        "SELECT * FROM exercises WHERE bodyPartId = :bodyPartId AND (" +
            "name LIKE '%' || :query || '%' OR nameEn LIKE '%' || :query || '%' " +
            "OR target LIKE '%' || :query || '%') ORDER BY isFavorite DESC, sortWeight ASC, name ASC"
    )
    fun searchInBodyPart(bodyPartId: Long, query: String): Flow<List<Exercise>>

    /**
     * 查询动作数量
     */
    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun getCount(): Int

    /**
     * 指定部位下各子分类的动作计数（数据驱动子卡片：只显示非空子分类）
     */
    @Query(
        "SELECT subcategory AS groupKey, COUNT(*) AS cnt FROM exercises " +
            "WHERE bodyPartId = :bodyPartId GROUP BY subcategory"
    )
    fun countBySubcategory(bodyPartId: Long): Flow<List<StringCount>>

    /**
     * 指定部位下各子分类的动作计数（同步）
     */
    @Query(
        "SELECT subcategory AS groupKey, COUNT(*) AS cnt FROM exercises " +
            "WHERE bodyPartId = :bodyPartId GROUP BY subcategory"
    )
    suspend fun countBySubcategorySync(bodyPartId: Long): List<StringCount>

    /**
     * 已存在的动作名集合（播种去重用，比查全表实体轻量得多）
     */
    @Query("SELECT name FROM exercises")
    suspend fun getAllNames(): List<String>

    /**
     * 根据名称查询单条动作（播种覆盖去重用）
     */
    @Query("SELECT * FROM exercises WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): Exercise?

    /**
     * 将内置(old-format)动作的 tips 迁移到 steps 并清空 tips，
     * 使旧预置动作与新数据集动作显示一致。
     * 仅作用于“非自定义、有 tips 且无 steps”的记录，不影响用户自定义动作。
     */
    @Query("UPDATE exercises SET steps = tips, tips = '' WHERE isCustom = 0 AND tips != '' AND steps = ''")
    suspend fun migrateBuiltInTipsToSteps()

    /**
     * 回填预置动作的英文名与目标肌群（v8 升级用）。
     * 仅更新“非自定义、且尚未写入英文名”的记录，不影响数据集动作与用户自定义动作。
     */
    @Query("UPDATE exercises SET nameEn = :nameEn, target = :target WHERE id = :id AND isCustom = 0 AND nameEn = ''")
    suspend fun updatePresetInfo(id: Long, nameEn: String, target: String)

    /**
     * 写入动作配图文件名（v8/v9 升级用）。
     * 仅更新“非自定义”的记录，不影响用户自定义动作。
     */
    @Query("UPDATE exercises SET imagePath = :imagePath WHERE id = :id AND isCustom = 0")
    suspend fun updateImagePath(id: Long, imagePath: String)

    /**
     * 切换收藏状态（v9 收藏功能）。
     * 仅更新单行的 isFavorite 列，不影响其它字段（含 imagePath、steps 等用户编辑内容）。
     */
    @Query("UPDATE exercises SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Int)

    /**
     * 查询所有动作（同步，安全）
     */
    @Query("SELECT * FROM exercises ORDER BY isFavorite DESC, sortWeight ASC, name ASC")
    suspend fun getAllSyncSafe(): List<Exercise>

    /**
     * 清空所有动作
     */
    @Query("DELETE FROM exercises")
    suspend fun deleteAll()

    /**
     * 清空数据集导入的动作（不影响精选与用户自定义）
     */
    @Query("DELETE FROM exercises WHERE sortWeight > 0 AND isCustom = 0")
    suspend fun deleteDatasetExercises()
}

/** 分组计数（key 为 String，如 subcategory） */
data class StringCount(val groupKey: String, val cnt: Int)
