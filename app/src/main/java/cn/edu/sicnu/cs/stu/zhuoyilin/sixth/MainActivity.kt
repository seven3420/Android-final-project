package cn.edu.sicnu.cs.stu.zhuoyilin.sixth

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    private val gameFragment = GameFragment.newInstance()
    private val helloFragment = HelloFragment.newInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val bottomNV = findViewById<BottomNavigationView>(R.id.bottomNV)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.frameLayout, gameFragment)
                .commit()
        }

        bottomNV.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.game -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.frameLayout, gameFragment).commit()
                }
                R.id.hello -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.frameLayout, helloFragment).commit()
                }
            }
            true
        }
    }
}
