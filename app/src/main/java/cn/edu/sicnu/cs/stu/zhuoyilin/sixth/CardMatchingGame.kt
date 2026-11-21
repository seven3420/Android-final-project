package cn.edu.sicnu.cs.stu.zhuoyilin.sixth

import java.io.Serializable

class CardMatchingGame(val count: Int) : Serializable {
    var score = 0
    val cards: MutableList<Card> = mutableListOf()
    private var firstSelectedIndex: Int? = null

    init {
        // 生成卡片
        val suits = listOf("♠","♥","♣","♦")
        val ranks = listOf("A","2","3","4","5","6")
        for(i in 0 until count/2){
            val s = suits[i%suits.size]
            val r = ranks[i%ranks.size]
            cards.add(Card(s,r))
            cards.add(Card(s,r))
        }
        cards.shuffle()
    }

    fun chooseCardAtIndex(index: Int){
        val card = cards[index]
        if(card.isMatched) return
        card.isChosen = !card.isChosen

        if(firstSelectedIndex == null){
            firstSelectedIndex = index
        }else{
            if(firstSelectedIndex == index){
                card.isChosen = false
                return
            }
            val first = cards[firstSelectedIndex!!]
            if(first.getCardText() == card.getCardText()){
                //匹配成功
                first.isMatched = true
                card.isMatched = true
                score += 10
            }else{
                //匹配失败
                first.isChosen = false
                card.isChosen = false
                score -=2
            }
            firstSelectedIndex = null
        }
    }

    fun reset(){
        score = 0
        firstSelectedIndex = null
        cards.forEach {
            it.isChosen = false
            it.isMatched = false
        }
        cards.shuffle()
    }
}
