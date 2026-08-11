package com.bc.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bc.app.data.entity.PlanDay
import kotlinx.coroutines.flow.Flow

/**
 * 计划训练日分组数据访问对象
 */
@Dao
interface PlanDayDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDay(day: PlanDay): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDays(days: List<PlanDay>)

    @Update
    suspend fun updateDay(day: PlanDay)

    @Delete
    suspend fun deleteDay(day: PlanDay)

    @Query("SELECT * FROM plan_days WHERE id = :id")
    suspend fun getDayById(id: Long): PlanDay?

    @Query("SELECT * FROM plan_days WHERE planId = :planId ORDER BY sortOrder ASC")
    suspend fun getDaysByPlanSync(planId: Long): List<PlanDay>

    @Query("SELECT * FROM plan_days WHERE planId = :planId ORDER BY sortOrder ASC")
    fun getDaysByPlan(planId: Long): Flow<List<PlanDay>>

    @Query("DELETE FROM plan_days WHERE planId = :planId")
    suspend fun deleteDaysByPlan(planId: Long)

    @Query("DELETE FROM plan_days")
    suspend fun deleteAllDays()

    @Query("SELECT MAX(dayIndex) FROM plan_days WHERE planId = :planId")
    suspend fun getMaxDayIndex(planId: Long): Int?
}
