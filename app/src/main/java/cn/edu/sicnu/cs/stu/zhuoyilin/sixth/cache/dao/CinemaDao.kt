package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.cache.entity.CinemaEntity

@Dao
interface CinemaDao {

    /** 全量覆盖：先清再插，避免旧数据残留 */
    suspend fun replaceAll(cinemas: List<CinemaEntity>) {
        deleteAll()
        if (cinemas.isNotEmpty()) insertAll(cinemas)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cinemas: List<CinemaEntity>)

    @Query("SELECT * FROM cinemas ORDER BY cinemaId ASC")
    suspend fun queryAll(): List<CinemaEntity>

    @Query("SELECT * FROM cinemas WHERE cinemaId = :cinemaId LIMIT 1")
    suspend fun queryOne(cinemaId: Int): CinemaEntity?

    @Query("DELETE FROM cinemas")
    suspend fun deleteAll()
}
