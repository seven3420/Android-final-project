package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.ui.cinema

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.base.BaseViewModel
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.cache.CacheModule
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.cache.toEntity
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.cache.toModel
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.RetrofitClient
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.exception.ApiException
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.exception.ErrorCode
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.model.Cinema
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.model.Schedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class CinemaSelectViewModel : BaseViewModel() {

    private val _state = MutableLiveData(CinemaSelectUiState())
    val state: LiveData<CinemaSelectUiState> = _state

    private val _toast = MutableLiveData<String?>()
    val toast: LiveData<String?> = _toast

    private var movieId = 0
    private var scheduleToken = 0

    fun start(movieId: Int) {
        if (this.movieId != 0) return
        this.movieId = movieId
        loadCinemas()
    }

    fun selectDate(date: LocalDate) {
        val current = _state.value ?: return
        if (current.selectedDate == date) return
        _state.value = current.copy(selectedDate = date)
        loadSchedules()
    }

    fun selectCinema(cinemaId: Int) {
        val current = _state.value ?: return
        if (current.selectedCinemaId == cinemaId) return
        _state.value = current.copy(selectedCinemaId = cinemaId)
        loadSchedules()
    }

    fun retry() {
        val current = _state.value ?: return
        if (current.cinemas.isEmpty()) {
            loadCinemas()
        } else {
            loadSchedules()
        }
    }

    fun consumeToast() {
        _toast.value = null
    }

    private fun loadCinemas() {
        val current = _state.value ?: CinemaSelectUiState()
        _state.value = current.copy(loadingCinemas = true, error = null)

        viewModelScope.launch {
            // 先查 Room 缓存，有则立即展示
            val cached = withContext(Dispatchers.IO) { CacheModule.cinemaDao.queryByMovie(movieId) }
            if (cached.isNotEmpty()) {
                val firstId = cached.first().cinemaId
                val selectedId = if (current.selectedCinemaId == 0) firstId else current.selectedCinemaId
                _state.value = (_state.value ?: current).copy(
                    cinemas = cached.map { it.toModel() },
                    selectedCinemaId = selectedId,
                    loadingCinemas = true
                )
            }

            try {
                val cinemaPage = executeRequest {
                    RetrofitClient.apiService.getCinemaList(movieId, pageNum = 1, pageSize = 20)
                }
                val cinemas = cinemaPage.list

                // 覆盖当前影片的 Room 缓存
                withContext(Dispatchers.IO) {
                    CacheModule.cinemaDao.replaceByMovie(
                        movieId,
                        cinemas.map { it.toEntity(movieId) }
                    )
                }

                val first = cinemas.firstOrNull()
                _state.value = (_state.value ?: current).copy(
                    cinemas = cinemas,
                    selectedCinemaId = first?.cinemaId ?: 0,
                    loadingCinemas = false,
                    cinemasLoaded = true,
                    error = null
                )
                if (first == null) {
                    _state.value = (_state.value ?: current).copy(
                        loadingSchedules = false,
                        schedulesLoaded = true
                    )
                } else {
                    loadSchedules()
                }
            } catch (e: ApiException) {
                val fallback = (_state.value?.cinemas?.takeIf { it.isNotEmpty() })
                    ?: withContext(Dispatchers.IO) { CacheModule.cinemaDao.queryByMovie(movieId).map { it.toModel() } }
                val isNetworkDown = e.code == ErrorCode.NETWORK_ERROR

                val first = fallback.firstOrNull()
                val selectedId = if (current.selectedCinemaId == 0) first?.cinemaId ?: 0 else current.selectedCinemaId
                _state.value = (_state.value ?: current).copy(
                    cinemas = fallback,
                    selectedCinemaId = selectedId,
                    loadingCinemas = false,
                    cinemasLoaded = true,
                    error = if (fallback.isNotEmpty()) null else (e.message ?: "影院加载失败")
                )

                if (fallback.isNotEmpty() && isNetworkDown) {
                    _toast.value = "网络不可用，已显示缓存数据"
                }

                if (first == null) {
                    _state.value = (_state.value ?: current).copy(
                        loadingSchedules = false,
                        schedulesLoaded = true
                    )
                } else {
                    loadSchedules()
                }
            }
        }
    }

    private fun loadSchedules() {
        val current = _state.value ?: return
        if (movieId <= 0 || current.selectedCinemaId <= 0) return

        val token = ++scheduleToken
        val cinemaId = current.selectedCinemaId
        val date = current.selectedDate.toString()

        _state.value = current.copy(
            schedules = emptyList(),
            loadingSchedules = true,
            schedulesLoaded = false,
            error = null
        )

        viewModelScope.launch {
            try {
                val schedules = executeRequest {
                    RetrofitClient.apiService.getScheduleList(cinemaId, movieId, date)
                }
                if (token != scheduleToken) return@launch
                _state.value = (_state.value ?: current).copy(
                    schedules = schedules,
                    loadingSchedules = false,
                    schedulesLoaded = true,
                    error = null
                )
            } catch (e: ApiException) {
                if (token != scheduleToken) return@launch
                _state.value = (_state.value ?: current).copy(
                    schedules = emptyList(),
                    loadingSchedules = false,
                    schedulesLoaded = true,
                    error = e.message ?: "场次加载失败"
                )
            }
        }
    }
}

data class CinemaSelectUiState(
    val cinemas: List<Cinema> = emptyList(),
    val schedules: List<Schedule> = emptyList(),
    val selectedCinemaId: Int = 0,
    val selectedDate: LocalDate = LocalDate.now(),
    val loadingCinemas: Boolean = false,
    val loadingSchedules: Boolean = false,
    val cinemasLoaded: Boolean = false,
    val schedulesLoaded: Boolean = false,
    val error: String? = null
)
