package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.cache.entity.MovieDetailEntity
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.cache.entity.MovieEntity
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.cache.entity.MoviePageEntity

@Dao
interface MovieDao {

    /** 按 type 批量 upsert（id 已存在则覆盖） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(movies: List<MovieEntity>)

    /** 按 type 清空再批量插入（刷新时用） */
    suspend fun replaceAllByType(type: Int, movies: List<MovieEntity>) {
        deleteByType(type)
        if (movies.isNotEmpty()) upsertAll(movies)
    }

    @Query("SELECT * FROM movies WHERE type = :type ORDER BY movieId ASC")
    suspend fun queryByType(type: Int): List<MovieEntity>

    @Query("DELETE FROM movies WHERE type = :type")
    suspend fun deleteByType(type: Int)

    // ---- 分页元数据 ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPageMeta(meta: MoviePageEntity)

    @Query("SELECT * FROM movie_page_meta WHERE type = :type LIMIT 1")
    suspend fun queryPageMeta(type: Int): MoviePageEntity?

    @Query("DELETE FROM movie_page_meta WHERE type = :type")
    suspend fun deletePageMetaByType(type: Int)

    // ---- 影片详情 ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDetail(detail: MovieDetailEntity)

    @Query("SELECT * FROM movie_details WHERE movieId = :movieId LIMIT 1")
    suspend fun queryDetail(movieId: Int): MovieDetailEntity?
}
