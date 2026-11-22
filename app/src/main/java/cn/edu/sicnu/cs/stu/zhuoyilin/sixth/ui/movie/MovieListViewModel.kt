package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.ui.movie

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.base.BaseViewModel
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.RetrofitClient
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.exception.ApiException
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.model.Movie
import kotlinx.coroutines.launch

class MovieListViewModel : BaseViewModel() {

    companion object {
        const val TYPE_SHOWING = 1 // 热映
        const val TYPE_COMING = 2  // 待映
        private const val PAGE_SIZE = 10
    }

    private val _state = MutableLiveData(MovieListUiState())
    val state: LiveData<MovieListUiState> = _state

    private val _toast = MutableLiveData<String?>()
    val toast: LiveData<String?> = _toast

    private var type = TYPE_SHOWING
    private var page = 0
    private var requestToken = 0

    fun start() {
        if (_state.value?.movies.isNullOrEmpty()) {
            load(reset = true, keepItemsOnError = false)
        }
    }

    fun switchType(newType: Int) {
        if (type == newType) return
        type = newType
        load(reset = true, keepItemsOnError = false)
    }

    fun refresh() {
        load(reset = true, keepItemsOnError = true)
    }

    fun loadMore() {
        val current = _state.value ?: return
        if (current.refreshing || current.loadingMore || !current.hasMore) return
        load(reset = false, keepItemsOnError = true)
    }

    fun consumeToast() {
        _toast.value = null
    }

    private fun load(reset: Boolean, keepItemsOnError: Boolean) {
        val token = ++requestToken
        val requestPage = if (reset) 1 else page + 1
        val old = _state.value ?: MovieListUiState()

        _state.value = if (reset) {
            old.copy(
                movies = emptyList(),
                refreshing = true,
                loadingMore = false,
                hasMore = true,
                firstLoadDone = false,
                error = null
            )
        } else {
            old.copy(loadingMore = true, error = null)
        }

        viewModelScope.launch {
            try {
                val pageData = executeRequest {
                    RetrofitClient.apiService.getMovieList(requestPage, type)
                }
                if (token != requestToken) return@launch

                page = requestPage
                val merged = (if (reset) emptyList() else old.movies) + pageData.list
                val hasMore = when {
                    pageData.list.isEmpty() -> false
                    pageData.total > 0 -> merged.size < pageData.total
                    else -> pageData.list.size >= PAGE_SIZE
                }

                _state.value = MovieListUiState(
                    movies = merged,
                    refreshing = false,
                    loadingMore = false,
                    hasMore = hasMore,
                    firstLoadDone = true,
                    error = null
                )
            } catch (e: ApiException) {
                if (token != requestToken) return@launch

                val keepItems = old.movies.isNotEmpty() && keepItemsOnError
                _state.value = old.copy(
                    movies = if (keepItems) old.movies else emptyList(),
                    refreshing = false,
                    loadingMore = false,
                    firstLoadDone = true,
                    error = e.message ?: "加载失败"
                )
                if (keepItems) {
                    _toast.value = e.message ?: "加载失败"
                }
            }
        }
    }
}

data class MovieListUiState(
    val movies: List<Movie> = emptyList(),
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val firstLoadDone: Boolean = false,
    val error: String? = null
)
