package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.ui.movie

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.base.BaseViewModel
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.cache.CacheModule
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.cache.toDetailEntity
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.cache.toModel
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.RetrofitClient
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.exception.ApiException
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.exception.ErrorCode
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.model.Movie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MovieDetailViewModel : BaseViewModel() {

    private val _movie = MutableLiveData<Movie?>()
    val movie: LiveData<Movie?> = _movie

    private val _toast = MutableLiveData<String?>()
    val toast: LiveData<String?> = _toast

    fun load(movieId: Int) {
        if (_movie.value != null) return
        viewModelScope.launch {
            // 先查 Room 缓存，有则立即展示（让用户看到老数据）
            val cached = withContext(Dispatchers.IO) {
                CacheModule.movieDao.queryDetail(movieId)
            }
            cached?.let { _movie.value = it.toModel() }

            try {
                val detail = executeRequest {
                    RetrofitClient.apiService.getMovieDetail(movieId)
                }
                // 网络成功后写回 Room 并覆盖 UI
                withContext(Dispatchers.IO) {
                    CacheModule.movieDao.upsertDetail(detail.toDetailEntity())
                }
                _movie.value = detail
            } catch (e: ApiException) {
                val isNetworkDown = e.code == ErrorCode.NETWORK_ERROR
                val alreadyShowedCache = _movie.value != null
                when {
                    isNetworkDown && alreadyShowedCache ->
                        _toast.value = "网络不可用，已显示缓存数据"
                    isNetworkDown ->
                        _toast.value = "网络不可用，请稍后重试"
                    else ->
                        _toast.value = e.message ?: "加载影片详情失败"
                }
            }
        }
    }

    fun consumeToast() {
        _toast.value = null
    }
}
