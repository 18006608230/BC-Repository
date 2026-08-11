package com.bc.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bc.app.data.entity.TrainingPlan
import com.bc.app.data.entity.TrainingPlanItem
import kotlinx.coroutines.flow.Flow

/**
 * 训练计划数据访问对象
 */
@Dao
interface TrainingPlanDao {

    // ---- 计划 ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: TrainingPlan): Long

    @Update
    suspend fun updatePlan(plan: TrainingPlan)

    @Delete
    suspend fun deletePlan(plan: TrainingPlan)

    @Query("SELECT * FROM training_plans ORDER BY createTime DESC")
    fun getAllPlans(): Flow<List<TrainingPlan>>

    @Query("SELECT * FROM training_plans ORDER BY createTime DESC")
    suspend fun getAllPlansSync(): List<TrainingPlan>

    @Query("SELECT * FROM training_plans WHERE id = :id")
    suspend fun getPlanById(id: Long): TrainingPlan?

    /**
     * 将所有计划设为非启用
     */
    @Query("UPDATE training_plans SET isActive = 0")
    suspend fun deactivateAll()

    /**
     * 获取当前启用计划
     */
    @Query("SELECT * FROM training_plans WHERE isActive = 1 LIMIT 1")
    suspend fun getActivePlanSync(): TrainingPlan?

    @Query("SELECT * FROM training_plans WHERE isActive = 1 LIMIT 1")
    fun getActivePlan(): Flow<TrainingPlan?>

    /**
     * 设置指定计划为启用（先全部禁用，再启用指定）
     */
    @Query("UPDATE training_plans SET isActive = 0")
    suspend fun clearActive()

    @Query("UPDATE training_plans SET isActive = 1 WHERE id = :id")
    suspend fun setActive(id: Long)

    /**
     * 清空所有计划（备份导入用）
     */
    @Query("DELETE FROM training_plans")
    suspend fun deleteAllPlans()

    // ---- 计划条目 ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<TrainingPlanItem>)

    @Query("SELECT * FROM training_plan_items WHERE planId = :planId ORDER BY sortOrder ASC")
    suspend fun getItemsByPlanSync(planId: Long): List<TrainingPlanItem>

    @Query("SELECT * FROM training_plan_items WHERE planId = :planId ORDER BY sortOrder ASC")
    fun getItemsByPlan(planId: Long): Flow<List<TrainingPlanItem>>

    /**
     * 按训练日分组查询计划条目
     */
    @Query("SELECT * FROM training_plan_items WHERE dayId = :dayId ORDER BY sortOrder ASC")
    suspend fun getItemsByDaySync(dayId: Long): List<TrainingPlanItem>

    /**
     * 查询未分组的计划条目
     */
    @Query("SELECT * FROM training_plan_items WHERE planId = :planId AND dayId IS NULL ORDER BY sortOrder ASC")
    suspend fun getUngroupedItemsSync(planId: Long): List<TrainingPlanItem>

    @Update
    suspend fun updateItem(item: TrainingPlanItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: TrainingPlanItem): Long

    @Delete
    suspend fun deleteItems(items: List<TrainingPlanItem>)

    @Query("DELETE FROM training_plan_items WHERE planId = :planId")
    suspend fun deleteItemsByPlan(planId: Long)
}
