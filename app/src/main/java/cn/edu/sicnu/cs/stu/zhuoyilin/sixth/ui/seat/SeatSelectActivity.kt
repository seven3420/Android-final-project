package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.ui.seat

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.core.view.isVisible
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.base.BaseActivity
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.databinding.ActivitySeatSelectBinding
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.ui.order.OrderConfirmActivity
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.ui.order.OrderConfirmContract
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.util.ToastUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

class SeatSelectActivity : BaseActivity() {

    companion object {
        const val EXTRA_SCHEDULE_ID = "extra_schedule_id"
        const val EXTRA_MOVIE_TITLE = "extra_movie_title"
        const val EXTRA_CINEMA_NAME = "extra_cinema_name"
        const val EXTRA_HALL_NAME = "extra_hall_name"
        const val EXTRA_START_TIME = "extra_start_time"
        const val EXTRA_TICKET_PRICE = "extra_ticket_price"
    }

    private lateinit var binding: ActivitySeatSelectBinding
    private val viewModel: SeatSelectViewModel by viewModels()

    private var movieTitle: String = ""
    private var cinemaName: String = ""
    private var hallName: String = ""
    private var startTime: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySeatSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        val scheduleId = intent.getIntExtra(EXTRA_SCHEDULE_ID, 0)
        movieTitle = intent.getStringExtra(EXTRA_MOVIE_TITLE).orEmpty()
        cinemaName = intent.getStringExtra(EXTRA_CINEMA_NAME).orEmpty()
        hallName = intent.getStringExtra(EXTRA_HALL_NAME).orEmpty()
        startTime = intent.getStringExtra(EXTRA_START_TIME).orEmpty()
        val ticketPrice = intent.getDoubleExtra(EXTRA_TICKET_PRICE, 0.0)

        if (scheduleId <= 0) {
            ToastUtil.showShort(this, "场次信息不完整")
            finish()
            return
        }

        binding.tvMovieInfo.text = buildString {
            append(movieTitle.ifBlank { "未知影片" })
            append(" · ").append(cinemaName.ifBlank { "未知影院" })
            append("\n").append(startTime.ifBlank { "场次待定" })
            append("  ").append(hallName)
        }

        binding.seatMapView.onSeatClickListener = { seat -> viewModel.toggleSeat(seat) }

        binding.errorView.onRetryClickListener = { viewModel.retry() }
        binding.btnSubmit.setOnClickListener { viewModel.submit() }

        observeState()
        viewModel.load(scheduleId, ticketPrice)
    }

    private fun observeState() {
        viewModel.state.observe(this) { state ->
            binding.seatMapView.submit(
                state.seats,
                state.rows,
                state.cols,
                state.selectedKeys
            )

            val loading = state.loading || state.submitting
            binding.progress.isVisible = loading
            binding.seatMapView.isVisible =
                !loading && state.error == null && state.seats.isNotEmpty()
            binding.emptyView.isVisible =
                !loading && state.error == null && state.loaded && state.seats.isEmpty()
            binding.errorView.isVisible = !loading && state.error != null

            binding.tvSelectedCount.text =
                "已选 ${state.selectedKeys.size}/${SeatSelectViewModel.MAX_SEATS}"
            binding.tvSelectedSeats.text = if (state.selectedSeats.isEmpty()) {
                "座位：未选择"
            } else {
                "座位：" + state.selectedSeats.joinToString("、") { it.label }
            }
            binding.tvTotalPrice.text =
                String.format(Locale.CHINA, "合计 ¥%.1f", state.totalPrice)

            binding.btnSubmit.isEnabled = !state.submitting
            binding.btnSubmit.text = if (state.submitting) "提交中..." else "提交选座"
        }

        viewModel.toast.observe(this) { message ->
            if (!message.isNullOrEmpty()) {
                ToastUtil.showShort(this, message)
                viewModel.consumeToast()
            }
        }

        viewModel.conflict.observe(this) { message ->
            if (message.isNullOrEmpty()) return@observe
            viewModel.consumeConflict()
            MaterialAlertDialogBuilder(this)
                .setTitle("选座冲突")
                .setMessage(message)
                .setPositiveButton("重新选座", null)
                .show()
        }

        viewModel.navigate.observe(this) { payload ->
            if (payload == null) return@observe
            viewModel.consumeNavigate()
            startActivity(
                Intent(this, OrderConfirmActivity::class.java).apply {
                    putExtra(OrderConfirmContract.EXTRA_SCHEDULE_ID, payload.scheduleId)
                    putExtra(OrderConfirmContract.EXTRA_SEAT_IDS, payload.seatIds.toIntArray())
                    putStringArrayListExtra(
                        OrderConfirmContract.EXTRA_SEAT_LABELS,
                        ArrayList(payload.seatLabels)
                    )
                    putExtra(OrderConfirmContract.EXTRA_TOTAL_PRICE, payload.totalPrice)
                    putExtra(OrderConfirmContract.EXTRA_MOVIE_TITLE, movieTitle)
                    putExtra(OrderConfirmContract.EXTRA_CINEMA_NAME, cinemaName)
                    putExtra(OrderConfirmContract.EXTRA_HALL_NAME, hallName)
                    putExtra(OrderConfirmContract.EXTRA_START_TIME, startTime)
                }
            )
        }
    }
}
