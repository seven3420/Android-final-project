package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.cache.entity

import androidx.room.Entity

@Entity(tableName = "cinemas", primaryKeys = ["movieId", "cinemaId"])
data class CinemaEntity(
    val movieId: Int,
    val cinemaId: Int,
    val name: String,
    val address: String,
    val cachedAt: Long = System.currentTimeMillis()
)
