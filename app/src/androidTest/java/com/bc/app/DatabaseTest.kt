package com.bc.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bc.app.data.db.BrawnCatalogDatabase
import com.bc.app.data.entity.BodyPart
import com.bc.app.data.entity.Exercise
import com.bc.app.data.entity.TrainingRecord
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Room数据库测试
 * 验证数据库基础能力
 */
@RunWith(AndroidJUnit4::class)
class DatabaseTest {

    private lateinit var db: BrawnCatalogDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BrawnCatalogDatabase::class.java)
            .build()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun testInsertAndRetrieveBodyPart() = runBlocking {
        // 插入部位
        val bodyPart = BodyPart(name = "胸部", sortOrder = 1)
        val id = db.bodyPartDao().insert(bodyPart)

        // 查询验证
        val retrieved = db.bodyPartDao().getById(id)
        assertNotNull(retrieved)
        assertEquals("胸部", retrieved?.name)
        assertEquals(1, retrieved?.sortOrder)
    }

    @Test
    @Throws(Exception::class)
    fun testInsertAndRetrieveExercise() = runBlocking {
        // 先插入部位
        val bodyPart = BodyPart(name = "胸部", sortOrder = 1)
        val bodyPartId = db.bodyPartDao().insert(bodyPart)

        // 插入动作
        val exercise = Exercise(bodyPartId = bodyPartId, name = "杠铃卧推", tips = "核心收紧")
        val id = db.exerciseDao().insert(exercise)

        // 查询验证
        val retrieved = db.exerciseDao().getById(id)
        assertNotNull(retrieved)
        assertEquals("杠铃卧推", retrieved?.name)
        assertEquals(bodyPartId, retrieved?.bodyPartId)
    }

    @Test
    @Throws(Exception::class)
    fun testInsertAndRetrieveTrainingRecord() = runBlocking {
        // 先插入部位和动作
        val bodyPart = BodyPart(name = "胸部", sortOrder = 1)
        val bodyPartId = db.bodyPartDao().insert(bodyPart)
        
        val exercise = Exercise(bodyPartId = bodyPartId, name = "杠铃卧推")
        val exerciseId = db.exerciseDao().insert(exercise)

        // 插入训练记录
        val record = TrainingRecord(
            exerciseId = exerciseId,
            date = System.currentTimeMillis(),
            weight = 60.0f,
            sets = 4,
            avgReps = 10,
            remark = "感觉不错"
        )
        val id = db.trainingRecordDao().insert(record)

        // 查询验证
        val retrieved = db.trainingRecordDao().getById(id)
        assertNotNull(retrieved)
        assertEquals(60.0f, retrieved?.weight)
        assertEquals(4, retrieved?.sets)
        assertEquals(10, retrieved?.avgReps)
    }

    @Test
    @Throws(Exception::class)
    fun testGetLatestRecordByExerciseId() = runBlocking {
        // 准备数据
        val bodyPart = BodyPart(name = "胸部", sortOrder = 1)
        val bodyPartId = db.bodyPartDao().insert(bodyPart)
        
        val exercise = Exercise(bodyPartId = bodyPartId, name = "杠铃卧推")
        val exerciseId = db.exerciseDao().insert(exercise)

        // 插入两条记录
        val now = System.currentTimeMillis()
        val record1 = TrainingRecord(exerciseId = exerciseId, date = now - 86400000, weight = 50.0f, sets = 3, avgReps = 12)
        val record2 = TrainingRecord(exerciseId = exerciseId, date = now, weight = 60.0f, sets = 4, avgReps = 10)
        
        db.trainingRecordDao().insert(record1)
        db.trainingRecordDao().insert(record2)

        // 查询最新记录
        val latest = db.trainingRecordDao().getLatestByExerciseId(exerciseId)
        assertNotNull(latest)
        assertEquals(60.0f, latest?.weight)
    }

    @Test
    @Throws(Exception::class)
    fun testGetMaxWeightByExerciseId() = runBlocking {
        // 准备数据
        val bodyPart = BodyPart(name = "胸部", sortOrder = 1)
        val bodyPartId = db.bodyPartDao().insert(bodyPart)
        
        val exercise = Exercise(bodyPartId = bodyPartId, name = "杠铃卧推")
        val exerciseId = db.exerciseDao().insert(exercise)

        // 插入多条记录
        val record1 = TrainingRecord(exerciseId = exerciseId, date = System.currentTimeMillis(), weight = 50.0f, sets = 3, avgReps = 12)
        val record2 = TrainingRecord(exerciseId = exerciseId, date = System.currentTimeMillis(), weight = 70.0f, sets = 3, avgReps = 8)
        val record3 = TrainingRecord(exerciseId = exerciseId, date = System.currentTimeMillis(), weight = 60.0f, sets = 4, avgReps = 10)
        
        db.trainingRecordDao().insert(record1)
        db.trainingRecordDao().insert(record2)
        db.trainingRecordDao().insert(record3)

        // 查询最高重量
        val maxWeight = db.trainingRecordDao().getMaxWeightByExerciseId(exerciseId)
        assertEquals(70.0f, maxWeight)
    }

    @Test
    @Throws(Exception::class)
    fun testPredefinedData() = runBlocking {
        // 验证预置数据（早期是 6 个部位，后来补了「有氧」；「颈部」已下线，现在是 7 个）
        val bodyParts = BodyPart.getPredefinedParts()
        assertEquals(7, bodyParts.size)
        assertEquals("胸部", bodyParts[0].name)
        assertEquals("背部", bodyParts[1].name)
        assertEquals("肩部", bodyParts[2].name)
        assertEquals("手臂", bodyParts[3].name)
        assertEquals("腿部", bodyParts[4].name)
        assertEquals("核心", bodyParts[5].name)
        assertEquals("有氧", bodyParts[6].name)
        assertFalse("「颈部」应已下线", bodyParts.any { it.name == "颈部" })

        val exercises = Exercise.getPredefinedExercises()
        assertTrue(exercises.isNotEmpty())
        
        // 验证每个部位都有动作
        val chestExercises = exercises.filter { it.bodyPartId == 1L }
        assertTrue(chestExercises.isNotEmpty())
    }
}
