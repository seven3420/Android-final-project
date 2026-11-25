package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.cache

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.cache.dao.CinemaDao
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.cache.dao.MovieDao
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.cache.entity.CinemaEntity
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.cache.entity.MovieDetailEntity
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.cache.entity.MovieEntity
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.cache.entity.MoviePageEntity

@Database(
    entities = [
        MovieEntity::class,
        MovieDetailEntity::class,
        MoviePageEntity::class,
        CinemaEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun movieDao(): MovieDao
    abstract fun cinemaDao(): CinemaDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "sixth_cache.db"
            ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}
