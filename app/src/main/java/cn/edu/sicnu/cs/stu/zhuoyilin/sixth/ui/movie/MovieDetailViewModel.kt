package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.ui.movie

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.base.BaseViewModel
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.RetrofitClient
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.exception.ApiException
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.model.Movie
import kotlinx.coroutines.launch

class MovieDetailViewModel : BaseViewModel() {

    private val _movie = MutableLiveData<Movie?>()
    val movie: LiveData<Movie?> = _movie

    private val _toast = MutableLiveData<String?>()
    val toast: LiveData<String?> = _toast

    fun load(movieId: Int) {
        if (_movie.value != null) return
        viewModelScope.launch {
            try {
                _movie.value = executeRequest {
                    RetrofitClient.apiService.getMovieDetail(movieId)
                }
            } catch (e: ApiException) {
                _toast.value = e.message ?: "加载影片详情失败"
            }
        }
    }

    fun consumeToast() {
        _toast.value = null
    }
}
