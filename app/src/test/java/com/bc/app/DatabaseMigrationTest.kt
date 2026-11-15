package com.bc.app

import com.bc.app.data.db.BrawnCatalogDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 迁移链完整性测试（纯 JVM，不需要模拟器或真机）
 *
 * 为什么需要它：项目开了 `fallbackToDestructiveMigration()`。一旦某个版本忘了写迁移，
 * Room 不会报错，而是**直接把库清掉重建** —— 训练记录、自定义动作、计划、身体数据
 * 全部消失，用户毫不知情。对一个纯本地存储、数据只在手机上的 App 来说，这是最坏的故障。
 *
 * 这个测试把「漏写迁移」挡在开发阶段：只要迁移链断了，测试立刻红。
 *
 * 覆盖范围从 [BrawnCatalogDatabase.MIN_MIGRATABLE_VERSION] 起（更早的版本是历史遗留缺口，
 * 由 BrawnCatalogDatabase 的紧急备份回调兜底，详见那里的注释）。
 */
class DatabaseMigrationTest {

    private val migrations get() = BrawnCatalogDatabase.ALL_MIGRATIONS
    private val dbVersion get() = BrawnCatalogDatabase.DATABASE_VERSION

    @Test
    fun everyAdjacentVersionHasMigration() {
        val missing = (BrawnCatalogDatabase.MIN_MIGRATABLE_VERSION until dbVersion)
            .filter { v -> migrations.none { it.startVersion == v && it.endVersion == v + 1 } }

        assertTrue(
            "缺少这些相邻版本的迁移：${missing.joinToString { "$it -> ${it + 1}" }}。" +
                "缺迁移会触发 fallbackToDestructiveMigration，导致用户数据被静默清空。",
            missing.isEmpty()
        )
    }

    @Test
    fun noMigrationExceedsCurrentDatabaseVersion() {
        migrations.forEach { m ->
            assertTrue(
                "迁移 ${m.startVersion} -> ${m.endVersion} 超出了当前数据库版本 $dbVersion，" +
                    "说明 DATABASE_VERSION 忘了跟着涨",
                m.endVersion <= dbVersion
            )
        }
    }

    @Test
    fun migrationsAreForwardOnly() {
        migrations.forEach { m ->
            assertTrue(
                "迁移方向不对：${m.startVersion} -> ${m.endVersion}",
                m.startVersion < m.endVersion
            )
        }
    }

    @Test
    fun noDuplicateMigrations() {
        val pairs = migrations.map { it.startVersion to it.endVersion }
        assertEquals(
            "存在重复的迁移：${pairs.groupBy { it }.filter { it.value.size > 1 }.keys}",
            pairs.size,
            pairs.toSet().size
        )
    }
}
