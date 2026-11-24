package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.ui.seat

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.base.BaseViewModel
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.RetrofitClient
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.exception.ApiException
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.exception.ErrorCode
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.model.Seat
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.model.SeatMap
import kotlinx.coroutines.launch

class SeatSelectViewModel : BaseViewModel() {

    companion object {
        const val MAX_SEATS = 4
    }

    private val _state = MutableLiveData(SeatUiState())
    val state: LiveData<SeatUiState> = _state

    private val _toast = MutableLiveData<String?>()
    val toast: LiveData<String?> = _toast

    private val _conflict = MutableLiveData<String?>()
    val conflict: LiveData<String?> = _conflict

    private val _navigate = MutableLiveData<SeatOrderPayload?>()
    val navigate: LiveData<SeatOrderPayload?> = _navigate

    private var scheduleId = 0
    private var fallbackPrice = 0.0

    fun load(scheduleId: Int, fallbackPrice: Double) {
        if (this.scheduleId != 0) return
        this.scheduleId = scheduleId
        this.fallbackPrice = fallbackPrice
        loadSeatMap()
    }

    fun retry() {
        if (scheduleId > 0) loadSeatMap()
    }

    fun toggleSeat(seat: Seat) {
        if (!seat.isAvailable) return
        val current = _state.value ?: return
        val keys = current.selectedKeys

        if (seat.key in keys) {
            _state.value = current.copy(selectedKeys = keys - seat.key)
        } else {
            if (keys.size >= MAX_SEATS) {
                _toast.value = "最多只能选择 $MAX_SEATS 个座位"
                return
            }
            _state.value = current.copy(selectedKeys = keys + seat.key)
        }
    }

    fun submit() {
        val current = _state.value ?: return
        if (current.submitting) return

        val selected = current.selectedSeats
        if (selected.isEmpty()) {
            _toast.value = "请先选择座位"
            return
        }

        _state.value = current.copy(submitting = true)

        viewModelScope.launch {
            try {
                val map = executeRequest {
                    RetrofitClient.apiService.getSeatMap(scheduleId)
                }
                val latestByKey = map.seats.associateBy { it.key }
                val conflicts = selected.filter { latestByKey[it.key]?.isAvailable != true }

                if (conflicts.isNotEmpty()) {
                    _state.value = buildLoadedState(map, current.copy(submitting = false), true)
                    _conflict.value = "座位 ${conflicts.joinToString("、") { it.label }} " +
                        "已被他人占用，请重新选择"
                    return@launch
                }

                val latest = _state.value ?: current
                val orderSeats = latest.selectedSeats
                if (orderSeats.isEmpty()) {
                    _state.value = latest.copy(submitting = false)
                    _toast.value = "请先选择座位"
                    return@launch
                }
                _state.value = latest.copy(submitting = false)
                _navigate.value = SeatOrderPayload(
                    scheduleId = scheduleId,
                    seatIds = orderSeats.map { it.seatId }.filter { it > 0 },
                    seatLabels = orderSeats.map { it.label },
                    totalPrice = latest.totalPrice
                )
            } catch (e: ApiException) {
                _state.value = (_state.value ?: current).copy(submitting = false)
                if (e.code == ErrorCode.SEAT_CONFLICT) {
                    _conflict.value = e.message ?: "座位已被其他用户锁定，请重新选择"
                } else {
                    _toast.value = e.message ?: "提交失败，请稍后重试"
                }
            }
        }
    }

    fun consumeToast() {
        _toast.value = null
    }

    fun consumeConflict() {
        _conflict.value = null
    }

    fun consumeNavigate() {
        _navigate.value = null
    }

    private fun loadSeatMap() {
        val current = _state.value ?: SeatUiState()
        _state.value = current.copy(loading = true, error = null)

        viewModelScope.launch {
            try {
                val map = executeRequest {
                    RetrofitClient.apiService.getSeatMap(scheduleId)
                }
                _state.value = buildLoadedState(map, _state.value ?: current, true)
                    .copy(loading = false)
            } catch (e: ApiException) {
                _state.value = (_state.value ?: current).copy(
                    loading = false,
                    loaded = true,
                    error = e.message ?: "座位图加载失败"
                )
            }
        }
    }

    private fun buildLoadedState(
        map: SeatMap,
        base: SeatUiState,
        keepSelection: Boolean
    ): SeatUiState {
        val seats = map.seats
        val rows = if (map.rows > 0) map.rows else (seats.maxOfOrNull { it.row } ?: 0)
        val cols = if (map.cols > 0) map.cols else (seats.maxOfOrNull { it.col } ?: 0)

        val availableKeys = seats.filter { it.isAvailable }.map { it.key }.toSet()
        val selectedKeys = if (keepSelection) {
            base.selectedKeys.intersect(availableKeys)
        } else {
            emptySet()
        }

        val seatPrice = seats.firstOrNull { it.price > 0 }?.price ?: 0.0
        val unitPrice = when {
            map.price > 0 -> map.price
            fallbackPrice > 0 -> fallbackPrice
            else -> seatPrice
        }

        return base.copy(
            seats = seats,
            rows = rows,
            cols = cols,
            selectedKeys = selectedKeys,
            unitPrice = unitPrice,
            loaded = true,
            error = null
        )
    }
}

data class SeatUiState(
    val seats: List<Seat> = emptyList(),
    val rows: Int = 0,
    val cols: Int = 0,
    val selectedKeys: Set<String> = emptySet(),
    val unitPrice: Double = 0.0,
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null
) {
    val selectedSeats: List<Seat>
        get() = seats.filter { it.key in selectedKeys }

    val totalPrice: Double
        get() = selectedSeats.sumOf { seat ->
            if (seat.price > 0) seat.price else unitPrice
        }
}

data class SeatOrderPayload(
    val scheduleId: Int,
    val seatIds: List<Int>,
    val seatLabels: List<String>,
    val totalPrice: Double
)
