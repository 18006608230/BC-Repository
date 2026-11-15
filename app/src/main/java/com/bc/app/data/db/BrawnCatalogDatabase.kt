package com.bc.app.data.db

import android.content.Context
import android.util.Log
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 数据库版本。**必须声明在类外面**：
 * 若写成 BrawnCatalogDatabase 伴生对象里的 const 并用于 @Database(version = ...)，
 * Room 的 KSP 处理器解析该类自身的伴生常量时会拿到 null，直接抛
 * `NullPointerException: Cannot invoke "java.lang.Integer.intValue()"`（Room 2.6 + KSP 的已知问题）。
 */
const val BRAWN_DB_VERSION = 10
const val BRAWN_DB_NAME = "brawn_catalog_db"

/**
 * Room数据库
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
    version = BRAWN_DB_VERSION,
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
        private const val TAG = "BrawnCatalogDB"

        /** 供迁移链测试与日志使用；真正的版本写在顶层常量 [BRAWN_DB_VERSION] */
        const val DATABASE_NAME = BRAWN_DB_NAME
        const val DATABASE_VERSION = BRAWN_DB_VERSION

        /**
         * 迁移链完整覆盖的最低版本：**v5 及之后每个相邻版本都有迁移**。
         *
         * v1→v2、v3→v4 是早期遗留缺口（当时没写迁移，仓库里也查不到那两版的 schema 变更，
         * 不敢凭空补写，补错反而会让后续 ALTER 打在错误的表结构上）。落到这两个版本上的库
         * 只能走破坏性重建，由 [emergencyBackupCallback] 在清库前抢出一份文件备份。
         *
         * **新增迁移时务必同步补上，否则会被 DatabaseMigrationTest 测出来。**
         */
        const val MIN_MIGRATABLE_VERSION = 5

        private const val EMERGENCY_BACKUP_DIR = "emergency_db_backup"
        private const val MAX_EMERGENCY_BACKUPS = 5

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

        /**
         * 迁移 v6 -> v7：动作库全量扩充（exercises-dataset 1285 条）
         * exercises 表新增数据集字段 + subcategory 索引，用户已有数据与自定义动作完整保留。
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercises ADD COLUMN nameEn TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE exercises ADD COLUMN target TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE exercises ADD COLUMN secondaryMuscles TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE exercises ADD COLUMN steps TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE exercises ADD COLUMN note TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE exercises ADD COLUMN sortWeight INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_exercises_subcategory ON exercises(subcategory)")
            }
        }

        /**
         * 迁移 v7 -> v8：动作库新增配图字段 imagePath（GIF 文件名，存于 assets/exercise_images/）
         * 用户已有数据与自定义动作完整保留，仅新增一列。
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercises ADD COLUMN imagePath TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * 迁移 v8 -> v9：动作库新增收藏字段 isFavorite（收藏动作在列表中置顶展示）
         * 用户已有数据与自定义动作完整保留，仅新增一列，默认 0（未收藏）。
         */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercises ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * 迁移 v9 -> v10：计划训练日新增休息日标记 isRestDay。
         * 用户已有数据与自定义计划完整保留，仅新增一列，默认 0（训练日）。
         */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plan_days ADD COLUMN isRestDay INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * 全部迁移。**不要改成 private** —— DatabaseMigrationTest 要读它做连续性校验。
         */
        val ALL_MIGRATIONS: List<Migration> = listOf(
            MIGRATION_2_3,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10
        )

        fun getDatabase(context: Context): BrawnCatalogDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BrawnCatalogDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(*ALL_MIGRATIONS.toTypedArray())
                    .fallbackToDestructiveMigration()
                    // 兜底：万一还是走到破坏性重建，先把数据抢出来，别让它静默消失
                    .addCallback(emergencyBackupCallback(context.applicationContext))
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * 破坏性重建前的紧急备份。
         *
         * `fallbackToDestructiveMigration()` 会在缺少迁移时**悄悄清库**，用户的训练记录、
         * 自定义动作、计划、身体数据全没了，且没有任何提示 —— 对一个纯本地存储的 App
         * 来说等同于灾难。
         *
         * 这里挂到 [RoomDatabase.Callback.onDestructiveMigration]：Room 在动手清库前会回调，
         * 我们趁这个时机把数据库文件复制一份到应用私有目录。
         *
         * 之所以复制**文件**而不是导出 JSON：
         *  - 不依赖表结构，哪怕 schema 已经对不上也能完整保住；
         *  - 导出 JSON 需要按列读取，而缺迁移恰恰意味着列可能已经变了，反而会失败。
         */
        private fun emergencyBackupCallback(appContext: Context): RoomDatabase.Callback =
            object : RoomDatabase.Callback() {
                override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                    val from = db.version
                    Log.e(
                        TAG,
                        "缺少 $from -> $DATABASE_VERSION 的迁移，数据库即将被清空重建！" +
                            "正在紧急备份，请尽快到设置里导出，避免备份被系统清理。"
                    )

                    // 先把 WAL 落盘，保证复制出来的主库文件包含最近写入
                    try {
                        db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { }
                    } catch (e: Exception) {
                        Log.w(TAG, "wal_checkpoint 失败，备份可能缺少最近写入", e)
                    }

                    try {
                        val src = appContext.getDatabasePath(DATABASE_NAME)
                        if (!src.exists()) {
                            Log.e(TAG, "找不到数据库文件，跳过紧急备份")
                            return
                        }
                        val dir = File(appContext.filesDir, EMERGENCY_BACKUP_DIR)
                        if (!dir.exists() && !dir.mkdirs()) {
                            Log.e(TAG, "无法创建紧急备份目录：${dir.absolutePath}")
                            return
                        }
                        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
                        val dst = File(dir, "$DATABASE_NAME-v$from-$stamp")
                        src.inputStream().use { input ->
                            dst.outputStream().use { output -> input.copyTo(output) }
                        }
                        Log.e(TAG, "已紧急备份到：${dst.absolutePath}（${dst.length()} 字节）")
                        pruneEmergencyBackups(dir)
                    } catch (e: Exception) {
                        Log.e(TAG, "紧急备份失败，数据将丢失！", e)
                    }
                }
            }

        /** 只保留最近 [MAX_EMERGENCY_BACKUPS] 份，避免长期占用存储空间 */
        private fun pruneEmergencyBackups(dir: File) {
            val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
            if (files.size <= MAX_EMERGENCY_BACKUPS) return
            files.drop(MAX_EMERGENCY_BACKUPS).forEach { f ->
                if (f.delete()) Log.d(TAG, "已删除较旧的紧急备份：${f.name}")
            }
        }
    }
}
