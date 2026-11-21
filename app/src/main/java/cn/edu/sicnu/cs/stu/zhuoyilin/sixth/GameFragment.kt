package cn.edu.sicnu.cs.stu.zhuoyilin.sixth

import android.app.Activity
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class GameFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var reset: Button
    private lateinit var score: TextView
    private var game: CardMatchingGame? = null
    private lateinit var adapter: CardRecyclerViewAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_game, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.recyclerView)
        reset = view.findViewById(R.id.reset)
        score = view.findViewById(R.id.score)

        game = loadData()
        if(game == null){
            game = CardMatchingGame(count = 24)
        }

        adapter = CardRecyclerViewAdapter(game!!)
        recyclerView.adapter = adapter

        //横竖屏切换列数
        val cfg = resources.configuration
        if(cfg.orientation == Configuration.ORIENTATION_LANDSCAPE){
            recyclerView.layoutManager = GridLayoutManager(activity,6)
        }else{
            recyclerView.layoutManager = GridLayoutManager(activity,4)
        }

        updateUI()

        adapter.onCardClickListener = { pos ->
            game?.chooseCardAtIndex(pos)
            updateUI()
        }

        reset.setOnClickListener {
            game?.reset()
            updateUI()
        }
    }

    fun updateUI(){
        adapter.notifyDataSetChanged()
        score.text = String.format("Score:%d", game?.score)
    }

    override fun onStop() {
        super.onStop()
        saveData()
    }

    private fun saveData(){
        try {
            val outputStream: FileOutputStream? = activity?.openFileOutput("data", Activity.MODE_PRIVATE)
            val objOut = ObjectOutputStream(outputStream)
            objOut.writeObject(game)
            objOut.close()
            outputStream?.close()
        }catch (e:Exception){
            e.printStackTrace()
        }
    }

    private fun loadData():CardMatchingGame?{
        return try {
            val input: FileInputStream? = activity?.openFileInput("data")
            val objIn = ObjectInputStream(input)
            val g = objIn.readObject() as CardMatchingGame
            objIn.close()
            input?.close()
            g
        }catch (e:Exception){
            e.printStackTrace()
            null
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(): GameFragment = GameFragment()
    }
}
