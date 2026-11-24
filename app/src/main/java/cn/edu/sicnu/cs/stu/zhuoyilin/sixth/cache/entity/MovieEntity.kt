package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.cache.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 影片列表缓存条目。按 type(热映=1 / 待映=2) 分组存储。
 */
@Entity(
    tableName = "movies",
    indices = [Index("type")]
)
data class MovieEntity(
    @PrimaryKey val movieId: Int,
    val type: Int,
    val title: String,
    val poster: String,
    val rating: Double,
    val duration: Int,
    val synopsis: String,
    /** 演员列表，Room 通过 TypeConverter 存 JSON 字符串 */
    val castJson: String,
    val releaseDate: String,
    val cachedAt: Long = System.currentTimeMillis()
)
