package com.bc.app.data.repository

import com.bc.app.data.dao.BodyPartDao
import com.bc.app.data.entity.BodyPart
import kotlinx.coroutines.flow.Flow

/**
 * 部位数据仓库
 * 封装部位数据的读写逻辑
 */
class BodyPartRepository(private val bodyPartDao: BodyPartDao) {

    /**
     * 所有部位数据流
     */
    val allBodyParts: Flow<List<BodyPart>> = bodyPartDao.getAll()

    /**
     * 插入部位
     */
    suspend fun insert(bodyPart: BodyPart): Long {
        return bodyPartDao.insert(bodyPart)
    }

    /**
     * 批量插入部位
     */
    suspend fun insertAll(bodyParts: List<BodyPart>): List<Long> {
        return bodyPartDao.insertAll(bodyParts)
    }

    /**
     * 更新部位
     */
    suspend fun update(bodyPart: BodyPart) {
        bodyPartDao.update(bodyPart)
    }

    /**
     * 删除部位
     */
    suspend fun delete(bodyPart: BodyPart) {
        bodyPartDao.delete(bodyPart)
    }

    /**
     * 根据id查询部位
     */
    suspend fun getById(id: Long): BodyPart? {
        return bodyPartDao.getById(id)
    }

    /**
     * 同步查询所有部位
     */
    suspend fun getAllSync(): List<BodyPart> {
        return bodyPartDao.getAllSync()
    }

    /**
     * 查询部位数量
     */
    suspend fun getCount(): Int {
        return bodyPartDao.getCount()
    }
}
