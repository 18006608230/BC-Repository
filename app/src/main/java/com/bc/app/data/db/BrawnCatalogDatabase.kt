package com.bc.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bc.app.data.dao.BodyMetricDao
import com.bc.app.data.dao.BodyPartDao
import com.bc.app.data.dao.ExerciseDao
import com.bc.app.data.dao.PlanDayDao
import com.bc.app.data.dao.TrainingPlanDao
import com.bc.app.data.dao.TrainingRecordDao
import com.bc.app.data.dao.WorkoutScheduleDao
import com.bc.app.data.entity.BodyMetric
import com.bc.app.data.entity.BodyPart
import com.bc.app.data.entity.Exercise
import com.bc.app.data.entity.PlanDay
import com.bc.app.data.entity.TrainingPlan
import com.bc.app.data.entity.TrainingPlanItem
import com.bc.app.data.entity.TrainingRecord
import com.bc.app.data.entity.WorkoutSchedule

/**
 * Room数据库
 * 版本：6
 * 包含表：body_parts, exercises, training_records, training_plans, training_plan_items, plan_days, body_metrics, workout_schedules
 */
@Database(
    entities = [
        BodyPart::class,
        Exercise::class,
        TrainingRecord::class,
        TrainingPlan::class,
        TrainingPlanItem::class,
        PlanDay::class,
        BodyMetric::class,
        WorkoutSchedule::class
    ],
    version = 6,
    exportSchema = false
)
abstract class BrawnCatalogDatabase : RoomDatabase() {

    abstract fun bodyPartDao(): BodyPartDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun trainingRecordDao(): TrainingRecordDao
    abstract fun trainingPlanDao(): TrainingPlanDao
    abstract fun planDayDao(): PlanDayDao
    abstract fun bodyMetricDao(): BodyMetricDao
    abstract fun workoutScheduleDao(): WorkoutScheduleDao

    /**
     * 清空所有数据（备份导入前使用）
     * 先删子表再删父表，避免外键冲突
     */
    suspend fun clearAll() {
        withTransaction {
            trainingRecordDao().deleteAll()
            trainingPlanDao().deleteAllPlans()
            planDayDao().deleteAllDays()
            workoutScheduleDao().deleteAll()
            bodyMetricDao().deleteAll()
            exerciseDao().deleteAll()
            bodyPartDao().deleteAll()
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: BrawnCatalogDatabase? = null

        /**
         * 迁移 v2 -> v3：training_records 表新增 tags 列（训练标签）
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE training_records ADD COLUMN tags TEXT DEFAULT NULL")
            }
        }

        /**
         * 迁移 v4 -> v5：新增 plan_days 表（计划训练日分组）+ training_plan_items 新增 dayId 列
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS plan_days (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "planId INTEGER NOT NULL, " +
                        "dayIndex INTEGER NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "sortOrder INTEGER NOT NULL DEFAULT 0, " +
                        "FOREIGN KEY(planId) REFERENCES training_plans(id) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_plan_days_planId ON plan_days(planId)")
                db.execSQL("ALTER TABLE training_plan_items ADD COLUMN dayId INTEGER DEFAULT NULL")
            }
        }

        /**
         * 迁移 v5 -> v6：新增 workout_schedules 表（计划↔日历排课）
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS workout_schedules (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "date TEXT NOT NULL, " +
                        "planId INTEGER NOT NULL, " +
                        "dayId INTEGER, " +
                        "dayName TEXT, " +
                        "isCompleted INTEGER NOT NULL DEFAULT 0, " +
                        "FOREIGN KEY(planId) REFERENCES training_plans(id) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_schedules_date ON workout_schedules(date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_schedules_planId ON workout_schedules(planId)")
            }
        }

        fun getDatabase(context: Context): BrawnCatalogDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BrawnCatalogDatabase::class.java,
                    "brawn_catalog_db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
