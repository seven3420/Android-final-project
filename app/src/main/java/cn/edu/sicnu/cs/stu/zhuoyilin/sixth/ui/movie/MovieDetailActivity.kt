package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.ui.movie

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.base.BaseActivity
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.databinding.ActivityMovieDetailBinding
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.RetrofitClient
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.model.Movie
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.ui.cinema.CinemaSelectActivity
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.util.ToastUtil
import coil.load
import java.util.Locale

class MovieDetailActivity : BaseActivity() {

    companion object {
        const val EXTRA_MOVIE_ID = "extra_movie_id"
    }

    private lateinit var binding: ActivityMovieDetailBinding
    private val viewModel: MovieDetailViewModel by viewModels()

    private var movieId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMovieDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnChooseCinema.isEnabled = false

        movieId = intent.getIntExtra(EXTRA_MOVIE_ID, 0)
        if (movieId <= 0) {
            ToastUtil.showShort(this, "影片信息不完整")
            finish()
            return
        }

        observe()
        showLoading()
        viewModel.load(movieId)
    }

    private fun observe() {
        viewModel.movie.observe(this) { movie ->
            if (movie == null) return@observe
            dismissLoading()
            render(movie)
        }

        viewModel.toast.observe(this) { message ->
            if (!message.isNullOrEmpty()) {
                dismissLoading()
                ToastUtil.showShort(this, message)
                viewModel.consumeToast()
            }
        }
    }

    private fun render(movie: Movie) {
        binding.tvTitle.text = movie.title.ifBlank { "未命名影片" }
        binding.tvRating.text = String.format(Locale.CHINA, "%.1f分", movie.rating)
        binding.tvDuration.text = if (movie.duration > 0) "${movie.duration}分钟" else "时长未知"
        binding.tvRelease.text = movie.releaseDate
        binding.tvSynopsis.text = movie.synopsis.ifBlank { "暂无简介" }
        binding.tvCast.text = if (movie.cast.isEmpty()) {
            "暂无演职人员信息"
        } else {
            movie.cast.joinToString(" / ")
        }

        binding.ivPoster.load(RetrofitClient.resolveUrl(movie.poster)) {
            crossfade(true)
            placeholder(cn.edu.sicnu.cs.stu.zhuoyilin.sixth.R.color.purple_200)
            error(cn.edu.sicnu.cs.stu.zhuoyilin.sixth.R.color.purple_200)
        }

        binding.btnChooseCinema.isEnabled = true
        binding.btnChooseCinema.setOnClickListener {
            startActivity(
                Intent(this, CinemaSelectActivity::class.java)
                    .putExtra(CinemaSelectActivity.EXTRA_MOVIE_ID, movie.movieId)
                    .putExtra(CinemaSelectActivity.EXTRA_MOVIE_TITLE, movie.title)
            )
        }
    }
}
