package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.activity.viewModels
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.MainActivity
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.base.BaseActivity
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.common.auth.AuthManager
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.databinding.ActivityLoginBinding
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.util.ToastUtil

class LoginActivity : BaseActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener { doLogin() }
        binding.etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                doLogin()
                true
            } else {
                false
            }
        }

        viewModel.uiState.observe(this) { state ->
            when (state) {
                LoginUiState.Idle -> Unit
                LoginUiState.Loading -> {
                    showLoading()
                    binding.btnLogin.isEnabled = false
                }
                is LoginUiState.Success -> {
                    dismissLoading()
                    AuthManager.saveToken(state.token)
                    startActivity(Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                    finish()
                }
                is LoginUiState.Error -> {
                    dismissLoading()
                    binding.btnLogin.isEnabled = true
                    ToastUtil.showShort(this, state.message)
                }
            }
        }
    }

    private fun doLogin() {
        val username = binding.etUsername.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()

        if (username.isEmpty()) {
            binding.tilUsername.error = "请输入账号"
            return
        }
        binding.tilUsername.error = null
        if (password.isEmpty()) {
            binding.tilPassword.error = "请输入密码"
            return
        }
        binding.tilPassword.error = null

        viewModel.login(username, password)
    }
}
