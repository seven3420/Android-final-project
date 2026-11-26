package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.ui.cinema

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.base.BaseActivity
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.databinding.ActivityCinemaSelectBinding
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.model.Schedule
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.ui.seat.SeatSelectActivity
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.util.ToastUtil

class CinemaSelectActivity : BaseActivity() {

    companion object {
        const val EXTRA_MOVIE_ID = "extra_movie_id"
        const val EXTRA_MOVIE_TITLE = "extra_movie_title"
    }

    private lateinit var binding: ActivityCinemaSelectBinding
    private val viewModel: CinemaSelectViewModel by viewModels()

    private lateinit var dateAdapter: DateAdapter
    private lateinit var cinemaAdapter: CinemaAdapter
    private lateinit var scheduleAdapter: ScheduleAdapter

    private var movieTitle: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCinemaSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        val movieId = intent.getIntExtra(EXTRA_MOVIE_ID, 0)
        movieTitle = intent.getStringExtra(EXTRA_MOVIE_TITLE).orEmpty()

        if (movieId <= 0) {
            ToastUtil.showShort(this, "影片信息不完整")
            finish()
            return
        }

        binding.tvMovieTitle.text = if (movieTitle.isBlank()) "影片信息" else "影片：$movieTitle"

        setupLists()
        binding.errorView.onRetryClickListener = { viewModel.retry() }
        observeState()
        viewModel.start(movieId)
    }

    private fun setupLists() {
        binding.rvDates.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        dateAdapter = DateAdapter { date -> viewModel.selectDate(date) }
        binding.rvDates.adapter = dateAdapter

        binding.rvCinemas.layoutManager = LinearLayoutManager(this)
        binding.rvCinemas.isNestedScrollingEnabled = false
        cinemaAdapter = CinemaAdapter { cinema -> viewModel.selectCinema(cinema.cinemaId) }
        binding.rvCinemas.adapter = cinemaAdapter

        binding.rvSchedules.layoutManager = LinearLayoutManager(this)
        binding.rvSchedules.isNestedScrollingEnabled = false
        scheduleAdapter = ScheduleAdapter { schedule -> openSeatSelection(schedule) }
        binding.rvSchedules.adapter = scheduleAdapter
    }

    private fun observeState() {
        viewModel.state.observe(this) { state ->
            dateAdapter.setSelected(state.selectedDate)
            cinemaAdapter.submit(state.cinemas, state.selectedCinemaId)
            scheduleAdapter.submit(state.schedules)

            val loading = state.loadingCinemas || state.loadingSchedules
            binding.progress.isVisible = loading
            binding.rvSchedules.isVisible = !loading && state.schedules.isNotEmpty()
            binding.errorView.isVisible = !loading && state.error != null

            val noCinema = state.cinemasLoaded && state.cinemas.isEmpty()
            val noSchedule = state.schedulesLoaded && state.schedules.isEmpty()
            binding.emptyView.isVisible =
                !loading && state.error == null && (noCinema || noSchedule)
        }

        viewModel.toast.observe(this) { message ->
            if (!message.isNullOrEmpty()) {
                ToastUtil.showShort(this, message)
                viewModel.consumeToast()
            }
        }
    }

    private fun openSeatSelection(schedule: Schedule) {
        val state = viewModel.state.value
        val cinemaName = state?.cinemas
            ?.firstOrNull { it.cinemaId == state.selectedCinemaId }
            ?.name
            .orEmpty()

        startActivity(
            Intent(this, SeatSelectActivity::class.java)
                .putExtra(SeatSelectActivity.EXTRA_SCHEDULE_ID, schedule.scheduleId)
                .putExtra(SeatSelectActivity.EXTRA_MOVIE_TITLE, movieTitle)
                .putExtra(SeatSelectActivity.EXTRA_CINEMA_NAME, cinemaName)
                .putExtra(SeatSelectActivity.EXTRA_HALL_NAME, schedule.hallName)
                .putExtra(SeatSelectActivity.EXTRA_START_TIME, schedule.startTime)
                .putExtra(SeatSelectActivity.EXTRA_TICKET_PRICE, schedule.price)
        )
    }
}
