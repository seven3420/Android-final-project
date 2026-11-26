package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.ui.cinema

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.R
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.databinding.ItemCinemaBinding
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.model.Cinema

class CinemaAdapter(
    private val onClick: (Cinema) -> Unit
) : RecyclerView.Adapter<CinemaAdapter.Holder>() {

    private val items = mutableListOf<Cinema>()
    private var selectedId = 0

    fun submit(cinemas: List<Cinema>, selectedCinemaId: Int) {
        if (items == cinemas && selectedId == selectedCinemaId) return
        items.clear()
        items.addAll(cinemas)
        selectedId = selectedCinemaId
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemCinemaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position], items[position].cinemaId == selectedId)
    }

    inner class Holder(
        private val binding: ItemCinemaBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(cinema: Cinema, selected: Boolean) {
            binding.tvCinemaName.text = cinema.name.ifBlank { "未命名影院" }
            binding.tvCinemaAddress.text = cinema.address.ifBlank { "地址待定" }

            val density = binding.root.resources.displayMetrics.density
            binding.root.strokeWidth = ((if (selected) 2 else 1) * density).toInt()
            binding.root.strokeColor = ContextCompat.getColor(
                binding.root.context,
                if (selected) R.color.primary else R.color.outline
            )

            binding.root.setOnClickListener { onClick(cinema) }
        }
    }
}
