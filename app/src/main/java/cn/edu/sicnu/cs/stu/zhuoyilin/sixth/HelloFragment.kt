package cn.edu.sicnu.cs.stu.zhuoyilin.sixth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment

class HelloFragment : Fragment() {

    private lateinit var button: Button
    private lateinit var textView: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_hello, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        button = view.findViewById(R.id.button)
        textView = view.findViewById(R.id.textView)

        button.setOnClickListener {
            textView.text = "I am clicked!"
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(): HelloFragment = HelloFragment()
    }
}
