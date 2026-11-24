package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.cache.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 影片详情缓存。key = movieId。
 */
@Entity(tableName = "movie_details")
data class MovieDetailEntity(
    @PrimaryKey val movieId: Int,
    val title: String,
    val poster: String,
    val rating: Double,
    val duration: Int,
    val synopsis: String,
    val castJson: String,
    val releaseDate: String,
    val cachedAt: Long = System.currentTimeMillis()
)
