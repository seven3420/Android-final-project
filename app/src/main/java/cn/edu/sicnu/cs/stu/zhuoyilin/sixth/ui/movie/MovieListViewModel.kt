package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.ui.movie

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
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.model.Movie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            load(reset = true, keepItemsOnError = false, readCacheFirst = true)
        }
    }

    fun switchType(newType: Int) {
        if (type == newType) return
        type = newType
        load(reset = true, keepItemsOnError = false, readCacheFirst = true)
    }

    fun refresh() {
        load(reset = true, keepItemsOnError = true, readCacheFirst = true)
    }

    fun loadMore() {
        val current = _state.value ?: return
        if (current.refreshing || current.loadingMore || !current.hasMore) return
        load(reset = false, keepItemsOnError = true, readCacheFirst = false)
    }

    fun consumeToast() {
        _toast.value = null
    }

    /**
     * @param reset true 表示刷新/切换类型；false 表示 loadMore
     * @param keepItemsOnError true 表示网络失败时保留已有数据并 toast；false 表示直接置空
     * @param readCacheFirst reset 模式下是否先读 Room 缓存展示（loadMore 永远 false）
     */
    private fun load(
        reset: Boolean,
        keepItemsOnError: Boolean,
        readCacheFirst: Boolean
    ) {
        val token = ++requestToken
        val requestPage = if (reset) 1 else page + 1
        val old = _state.value ?: MovieListUiState()

        // reset 模式先清 movies；loadMore 不清
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
            // reset 模式先读 Room 缓存，让 UI 立即有内容（有老数据总比白屏好）
            if (reset && readCacheFirst) {
                val cached = withContext(Dispatchers.IO) {
                    CacheModule.movieDao.queryByType(type)
                }
                if (token != requestToken) return@launch
                if (cached.isNotEmpty()) {
                    // 推一份缓存状态，UI 立刻展示老数据，同时网络请求继续
                    _state.value = MovieListUiState(
                        movies = cached.map { it.toModel() },
                        refreshing = true,  // 保持 refreshing 让 swiperefresh 转圈
                        loadingMore = false,
                        hasMore = true,
                        firstLoadDone = true,
                        error = null,
                        fromCache = true
                    )
                }
            }

            try {
                val pageData = executeRequest {
                    RetrofitClient.apiService.getMovieList(requestPage, type)
                }
                if (token != requestToken) return@launch

                page = requestPage
                val merged = (if (reset) emptyList() else (_state.value?.movies ?: old.movies)) + pageData.list
                val hasMore = when {
                    pageData.list.isEmpty() -> false
                    pageData.total > 0 -> merged.size < pageData.total
                    else -> pageData.list.size >= PAGE_SIZE
                }

                // 写入 Room 缓存
                withContext(Dispatchers.IO) {
                    if (reset) {
                        CacheModule.movieDao.deleteByType(type)
                        CacheModule.movieDao.deletePageMetaByType(type)
                    }
                    // 网络返回的这批（requestPage 这一页）插入 Room；reset 之前已经 delete 过了所以不会重复
                    CacheModule.movieDao.upsertAll(pageData.list.map { it.toEntity(type) })
                    CacheModule.movieDao.upsertPageMeta(
                        CacheModule.pageMeta(type, pageData.total, requestPage)
                    )
                }

                _state.value = MovieListUiState(
                    movies = merged,
                    refreshing = false,
                    loadingMore = false,
                    hasMore = hasMore,
                    firstLoadDone = true,
                    error = null,
                    fromCache = false
                )
            } catch (e: ApiException) {
                if (token != requestToken) return@launch

                // 网络失败兜底：如果 movies 为空，最后再从 Room 捞一次
                val fallbackMovies = if (_state.value?.movies.isNullOrEmpty()) {
                    withContext(Dispatchers.IO) {
                        CacheModule.movieDao.queryByType(type).map { it.toModel() }
                    }
                } else {
                    _state.value?.movies ?: emptyList()
                }

                val isNetworkDown = e.code == ErrorCode.NETWORK_ERROR
                val keepItems = fallbackMovies.isNotEmpty()

                _state.value = MovieListUiState(
                    movies = if (keepItems) fallbackMovies else emptyList(),
                    refreshing = false,
                    loadingMore = false,
                    hasMore = old.hasMore,
                    firstLoadDone = true,
                    error = if (keepItems) null else (e.message ?: "加载失败"),
                    fromCache = keepItems
                )

                if (keepItems && isNetworkDown) {
                    _toast.value = "网络不可用，已显示缓存数据"
                } else if (keepItems && keepItemsOnError) {
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
    val error: String? = null,
    /** 当前展示的是不是本地缓存数据（断网时为 true） */
    val fromCache: Boolean = false
)
