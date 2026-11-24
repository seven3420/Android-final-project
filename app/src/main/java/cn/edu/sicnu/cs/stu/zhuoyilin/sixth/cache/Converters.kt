package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.cache

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {

    private val gson = Gson()

    @TypeConverter
    fun fromStringList(list: List<String>): String {
        return gson.toJson(list)
    }

    @TypeConverter
    fun toStringList(json: String): List<String> {
        return runCatching {
            gson.fromJson<List<String>>(json, object : TypeToken<List<String>>() {}.type)
        }.getOrDefault(emptyList())
    }
}
