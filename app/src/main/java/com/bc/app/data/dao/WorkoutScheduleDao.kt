package com.bc.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bc.app.data.entity.WorkoutSchedule

/**
 * 计划↔日历排课数据访问对象
 */
@Dao
interface WorkoutScheduleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(schedule: WorkoutSchedule): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(schedules: List<WorkoutSchedule>)

    /**
     * 查询某个日期范围内的排课
     */
    @Query("SELECT * FROM workout_schedules WHERE date BETWEEN :start AND :end ORDER BY date ASC")
    suspend fun getByDateRange(start: String, end: String): List<WorkoutSchedule>

    /**
     * 查询某天的排课
     */
    @Query("SELECT * FROM workout_schedules WHERE date = :date")
    suspend fun getByDate(date: String): List<WorkoutSchedule>

    /**
     * 查询某计划的所有排课
     */
    @Query("SELECT * FROM workout_schedules WHERE planId = :planId ORDER BY date ASC")
    suspend fun getByPlan(planId: Long): List<WorkoutSchedule>

    /**
     * 删除某天的排课
     */
    @Query("DELETE FROM workout_schedules WHERE date = :date")
    suspend fun deleteByDate(date: String)

    /**
     * 删除某计划在指定日期范围内的排课
     */
    @Query("DELETE FROM workout_schedules WHERE planId = :planId AND date BETWEEN :start AND :end")
    suspend fun deleteByPlanBetween(planId: Long, start: String, end: String)

    @Query("DELETE FROM workout_schedules WHERE id = :scheduleId")
    suspend fun deleteById(scheduleId: Long)

    @Query("DELETE FROM workout_schedules")
    suspend fun deleteAll()
}
