package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.cache

import android.content.Context
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.cache.dao.CinemaDao
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.cache.dao.MovieDao
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.cache.entity.CinemaEntity
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.cache.entity.MovieDetailEntity
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.cache.entity.MovieEntity
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.cache.entity.MoviePageEntity
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.model.Cinema
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.model.Movie

private val converters = Converters()

/** Room 缓存单例门面。持有 DB 实例 + DAO。 */
object CacheModule {

    private lateinit var db: AppDatabase

    fun init(context: Context) {
        if (!::db.isInitialized) db = AppDatabase.get(context.applicationContext)
    }

    val movieDao: MovieDao get() = db.movieDao()
    val cinemaDao: CinemaDao get() = db.cinemaDao()

    // ---- 辅助 ----
    fun pageMeta(type: Int, total: Int, page: Int) = MoviePageEntity(
        type = type, total = total, maxCachedPage = page
    )
}

// ---- Movie 转换（顶层扩展函数，方便直接 import 调用）----
fun Movie.toEntity(type: Int): MovieEntity = MovieEntity(
    movieId = movieId,
    type = type,
    title = title,
    poster = poster,
    rating = rating,
    duration = duration,
    synopsis = synopsis,
    castJson = converters.fromStringList(cast),
    releaseDate = releaseDate
)

fun MovieEntity.toModel(): Movie = Movie(
    movieId = movieId,
    title = title,
    poster = poster,
    rating = rating,
    duration = duration,
    synopsis = synopsis,
    cast = converters.toStringList(castJson),
    releaseDate = releaseDate
)

fun MovieDetailEntity.toModel(): Movie = Movie(
    movieId = movieId,
    title = title,
    poster = poster,
    rating = rating,
    duration = duration,
    synopsis = synopsis,
    cast = converters.toStringList(castJson),
    releaseDate = releaseDate
)

fun Movie.toDetailEntity(): MovieDetailEntity = MovieDetailEntity(
    movieId = movieId,
    title = title,
    poster = poster,
    rating = rating,
    duration = duration,
    synopsis = synopsis,
    castJson = converters.fromStringList(cast),
    releaseDate = releaseDate
)

// ---- Cinema 转换 ----
fun Cinema.toEntity(movieId: Int): CinemaEntity = CinemaEntity(
    movieId = movieId,
    cinemaId = cinemaId,
    name = name,
    address = address
)

fun CinemaEntity.toModel(): Cinema = Cinema(
    cinemaId = cinemaId,
    name = name,
    address = address
)
