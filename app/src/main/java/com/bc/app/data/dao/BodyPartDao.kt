package com.bc.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bc.app.data.entity.BodyPart
import kotlinx.coroutines.flow.Flow

/**
 * 部位数据访问对象
 */
@Dao
interface BodyPartDao {

    /**
     * 插入部位
     * @return 插入的id
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bodyPart: BodyPart): Long

    /**
     * 批量插入部位
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(bodyParts: List<BodyPart>): List<Long>

    /**
     * 更新部位
     */
    @Update
    suspend fun update(bodyPart: BodyPart)

    /**
     * 删除部位
     */
    @Delete
    suspend fun delete(bodyPart: BodyPart)

    /**
     * 根据id查询部位
     */
    @Query("SELECT * FROM body_parts WHERE id = :id")
    suspend fun getById(id: Long): BodyPart?

    /**
     * 查询所有部位，按sortOrder排序
     */
    @Query("SELECT * FROM body_parts ORDER BY sortOrder ASC")
    fun getAll(): Flow<List<BodyPart>>

    /**
     * 查询所有部位（同步）
     */
    @Query("SELECT * FROM body_parts ORDER BY sortOrder ASC")
    suspend fun getAllSync(): List<BodyPart>

    /**
     * 查询部位数量
     */
    @Query("SELECT COUNT(*) FROM body_parts")
    suspend fun getCount(): Int

    /**
     * 清空所有部位
     */
    @Query("DELETE FROM body_parts")
    suspend fun deleteAll()
}
