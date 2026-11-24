package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.cache.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 影片列表分页元数据，用于记录每种 type 已经缓存到多少页、总数是多少，
 * 方便下次离线浏览时知道有没有更多数据。
 */
@Entity(tableName = "movie_page_meta")
data class MoviePageEntity(
    /** 1=热映, 2=待映 */
    @PrimaryKey val type: Int,
    /** 服务端返回的总条数 */
    val total: Int,
    /** 当前已缓存到的最大页码 */
    val maxCachedPage: Int,
    val cachedAt: Long = System.currentTimeMillis()
)
