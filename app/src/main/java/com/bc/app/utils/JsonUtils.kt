package com.bc.app.utils

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.bc.app.data.entity.SetDetail

/**
 * JSON工具类
 * 用于逐组明细的序列化和反序列化
 */
object JsonUtils {

    private val gson = Gson()

    /**
     * 将逐组明细列表转为JSON字符串
     */
    fun setDetailsToJson(setDetails: List<SetDetail>): String {
        return gson.toJson(setDetails)
    }

    /**
     * 将JSON字符串转为逐组明细列表
     */
    fun jsonToSetDetails(json: String): List<SetDetail> {
        return try {
            val type = object : TypeToken<List<SetDetail>>() {}.type
            gson.fromJson<List<SetDetail>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
