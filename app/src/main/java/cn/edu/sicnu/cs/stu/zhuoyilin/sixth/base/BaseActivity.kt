package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.base

import androidx.appcompat.app.AppCompatActivity
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.common.dialog.LoadingDialog

open class BaseActivity : AppCompatActivity() {

    private var loadingDialog: LoadingDialog? = null

    protected fun showLoading() {
        if (isFinishing || isDestroyed) return
        val dialog = loadingDialog ?: LoadingDialog(this).also { loadingDialog = it }
        if (!dialog.isShowing) {
            dialog.show()
        }
    }

    protected fun dismissLoading() {
        loadingDialog?.takeIf { it.isShowing }?.dismiss()
    }

    override fun onDestroy() {
        dismissLoading()
        loadingDialog = null
        super.onDestroy()
    }
}
