package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.ui.cinema

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.base.BaseActivity
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.databinding.ActivityCinemaSelectBinding
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.model.Cinema
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.model.Schedule
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.ui.seat.SeatSelectActivity
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.util.ToastUtil
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class CinemaSelectActivity : BaseActivity() {

    companion object {
        const val EXTRA_MOVIE_ID = "extra_movie_id"
        const val EXTRA_MOVIE_TITLE = "extra_movie_title"

        private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd")
        private val WEEK_NAMES = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    }

    private lateinit var binding: ActivityCinemaSelectBinding
    private val viewModel: CinemaSelectViewModel by viewModels()

    private lateinit var scheduleAdapter: ScheduleAdapter

    private val dateOptions: List<LocalDate> = (0L until 7L).map { LocalDate.now().plusDays(it) }
    private var cinemaOptions: List<Cinema> = emptyList()
    private var lastCinemaIds: List<Int> = emptyList()

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

        setupDropdowns()
        binding.errorView.onRetryClickListener = { viewModel.retry() }
        observeState()
        viewModel.start(movieId)
    }

    private fun setupDropdowns() {
        binding.etDate.setSimpleItems(dateOptions.map(::formatDateLabel).toTypedArray())
        binding.etDate.setText(formatDateLabel(dateOptions.first()), false)
        binding.etDate.setOnItemClickListener { _, _, position, _ ->
            dateOptions.getOrNull(position)?.let { viewModel.selectDate(it) }
        }

        binding.etCinema.setOnItemClickListener { _, _, position, _ ->
            cinemaOptions.getOrNull(position)?.let { viewModel.selectCinema(it.cinemaId) }
        }

        scheduleAdapter = ScheduleAdapter { schedule -> openSeatSelection(schedule) }
        binding.rvSchedules.layoutManager = LinearLayoutManager(this)
        binding.rvSchedules.isNestedScrollingEnabled = false
        binding.rvSchedules.adapter = scheduleAdapter
    }

    private fun observeState() {
        viewModel.state.observe(this) { state ->
            updateDateSelection(state.selectedDate)
            updateCinemaOptions(state.cinemas, state.selectedCinemaId)
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

    private fun updateDateSelection(date: LocalDate) {
        val label = formatDateLabel(date)
        if (binding.etDate.text?.toString() != label) {
            binding.etDate.setText(label, false)
        }
    }

    private fun updateCinemaOptions(cinemas: List<Cinema>, selectedCinemaId: Int) {
        val ids = cinemas.map { it.cinemaId }
        if (ids != lastCinemaIds) {
            cinemaOptions = cinemas
            lastCinemaIds = ids
            binding.etCinema.setSimpleItems(cinemas.map { it.name }.toTypedArray())
        }

        val selectedName = cinemas.firstOrNull { it.cinemaId == selectedCinemaId }?.name
        if (selectedName != null && binding.etCinema.text?.toString() != selectedName) {
            binding.etCinema.setText(selectedName, false)
        }
    }

    private fun formatDateLabel(date: LocalDate): String {
        val today = LocalDate.now()
        val prefix = when (date) {
            today -> "今天"
            today.plusDays(1) -> "明天"
            else -> WEEK_NAMES[date.dayOfWeek.value - 1]
        }
        return "$prefix ${date.format(DATE_FORMAT)}"
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
