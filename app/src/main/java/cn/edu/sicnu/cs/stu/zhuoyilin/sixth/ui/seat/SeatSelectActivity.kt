package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.ui.seat

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.databinding.ActivitySeatSelectBinding

/**
 * 选座入口页。B-4 只负责从可售场次进入此页，座位图由后续阶段补全。
 */
class SeatSelectActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SCHEDULE_ID = "extra_schedule_id"
        const val EXTRA_MOVIE_TITLE = "extra_movie_title"
        const val EXTRA_CINEMA_NAME = "extra_cinema_name"
        const val EXTRA_HALL_NAME = "extra_hall_name"
        const val EXTRA_START_TIME = "extra_start_time"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySeatSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val movieTitle = intent.getStringExtra(EXTRA_MOVIE_TITLE).orEmpty()
        val cinemaName = intent.getStringExtra(EXTRA_CINEMA_NAME).orEmpty()
        val hallName = intent.getStringExtra(EXTRA_HALL_NAME).orEmpty()
        val startTime = intent.getStringExtra(EXTRA_START_TIME).orEmpty()

        binding.tvScheduleInfo.text = buildString {
            append("影片：").append(movieTitle.ifBlank { "未知" })
            append("\n影院：").append(cinemaName.ifBlank { "未知" })
            append("\n场次：").append(startTime.ifBlank { "未知" })
            append("  ").append(hallName)
        }
        binding.toolbar.setNavigationOnClickListener { finish() }
    }
}
