package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.cache.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cinemas")
data class CinemaEntity(
    @PrimaryKey val cinemaId: Int,
    val name: String,
    val address: String,
    val cachedAt: Long = System.currentTimeMillis()
)
