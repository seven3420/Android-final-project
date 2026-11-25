package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.cache.entity.CinemaEntity

@Dao
interface CinemaDao {

    /** 覆盖单部影片的影院，避免不同影片的缓存互相污染。 */
    suspend fun replaceByMovie(movieId: Int, cinemas: List<CinemaEntity>) {
        deleteByMovie(movieId)
        if (cinemas.isNotEmpty()) insertAll(cinemas)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cinemas: List<CinemaEntity>)

    @Query("SELECT * FROM cinemas WHERE movieId = :movieId ORDER BY cinemaId ASC")
    suspend fun queryByMovie(movieId: Int): List<CinemaEntity>

    @Query("SELECT * FROM cinemas WHERE movieId = :movieId AND cinemaId = :cinemaId LIMIT 1")
    suspend fun queryOne(movieId: Int, cinemaId: Int): CinemaEntity?

    @Query("DELETE FROM cinemas WHERE movieId = :movieId")
    suspend fun deleteByMovie(movieId: Int)
}
