package cn.edu.sicnu.cs.stu.zhuoyilin.sixth

import java.io.Serializable

data class Card(
    private val suit: String,
    private val rank: String,
    var isChosen: Boolean = false,
    var isMatched: Boolean = false
) : Serializable {
    fun getCardText(): String = "$rank$suit"
}
