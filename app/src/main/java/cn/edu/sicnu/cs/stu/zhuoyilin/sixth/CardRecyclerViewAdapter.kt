package cn.edu.sicnu.cs.stu.zhuoyilin.sixth

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import androidx.recyclerview.widget.RecyclerView

class CardRecyclerViewAdapter(
    private val game: CardMatchingGame
): RecyclerView.Adapter<CardRecyclerViewAdapter.CardViewHolder>() {

    var onCardClickListener: ((Int)->Unit)? = null

    inner class CardViewHolder(item: android.view.View): RecyclerView.ViewHolder(item){
        val btn:Button = item.findViewById(R.id.cardBtn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_card,parent,false)
        return CardViewHolder(v)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val card = game.cards[position]
        if(card.isChosen || card.isMatched){
            holder.btn.text = card.getCardText()
        }else{
            holder.btn.text = "?"
        }
        holder.btn.setOnClickListener {
            onCardClickListener?.invoke(position)
        }
    }

    override fun getItemCount(): Int = game.cards.size
}
