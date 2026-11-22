package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.ui.cinema

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.databinding.ActivityCinemaSelectBinding

/**
 * 选择影院入口页。B-3 只负责提供跳转入口，具体影院列表由后续阶段补全。
 */
class CinemaSelectActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MOVIE_ID = "extra_movie_id"
        const val EXTRA_MOVIE_TITLE = "extra_movie_title"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityCinemaSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val movieTitle = intent.getStringExtra(EXTRA_MOVIE_TITLE).orEmpty()
        binding.tvMovieTitle.text = if (movieTitle.isBlank()) "影片信息" else "影片：$movieTitle"
        binding.toolbar.setNavigationOnClickListener { finish() }
    }
}
