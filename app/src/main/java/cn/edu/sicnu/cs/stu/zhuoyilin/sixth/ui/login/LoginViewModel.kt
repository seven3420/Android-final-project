package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.base.BaseViewModel
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.RetrofitClient
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.exception.ApiException
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.exception.ErrorCode
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.model.LoginRequest
import kotlinx.coroutines.launch

class LoginViewModel : BaseViewModel() {

    // 登录失败返回 401 时不能触发“跳转登录页”，否则会一直重定向
    override val autoHandleTokenExpired: Boolean = false

    private val _uiState = MutableLiveData<LoginUiState>(LoginUiState.Idle)
    val uiState: LiveData<LoginUiState> = _uiState

    fun login(username: String, password: String) {
        if (_uiState.value == LoginUiState.Loading) return
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            try {
                val result = executeRequest {
                    RetrofitClient.apiService.login(LoginRequest(username, password))
                }
                val token = result.token?.takeIf { it.isNotBlank() }
                    ?: throw ApiException(ErrorCode.PARSE_ERROR, "登录成功但未返回 token")
                _uiState.value = LoginUiState.Success(token)
            } catch (e: ApiException) {
                _uiState.value = LoginUiState.Error(e.message ?: "登录失败")
            }
        }
    }
}

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data class Success(val token: String) : LoginUiState
    data class Error(val message: String) : LoginUiState
}
