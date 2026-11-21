package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.common.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.R

/**
 * 空数据占位页，可直接在布局里使用，也可在代码中控制显隐。
 */
class EmptyView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    init {
        LayoutInflater.from(context).inflate(R.layout.view_empty, this, true)
    }
}
