package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.common.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.google.android.material.button.MaterialButton
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.R

/**
 * 网络错误重试页，点击重试按钮回调 [onRetryClickListener]。
 */
class NetworkErrorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var onRetryClickListener: (() -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.view_network_error, this, true)
        findViewById<MaterialButton>(R.id.btn_retry).setOnClickListener {
            onRetryClickListener?.invoke()
        }
    }
}
