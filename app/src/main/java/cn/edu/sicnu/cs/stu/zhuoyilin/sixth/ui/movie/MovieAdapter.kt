package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.ui.movie

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.R
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.databinding.ItemLoadMoreBinding
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.databinding.ItemMovieBinding
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.RetrofitClient
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.model.Movie
import coil.load
import java.util.Locale

class MovieAdapter(
    private val onClick: (Movie) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_MOVIE = 0
        private const val TYPE_FOOTER = 1
    }

    private val items = mutableListOf<Movie>()
    private var loadingMore = false

    fun submitList(movies: List<Movie>) {
        items.clear()
        items.addAll(movies)
        notifyDataSetChanged()
    }

    fun setLoadingMore(loading: Boolean) {
        if (loadingMore == loading) return
        loadingMore = loading
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size + if (loadingMore) 1 else 0

    override fun getItemViewType(position: Int): Int {
        return if (position == items.size) TYPE_FOOTER else TYPE_MOVIE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_FOOTER) {
            FooterViewHolder(ItemLoadMoreBinding.inflate(inflater, parent, false))
        } else {
            MovieViewHolder(ItemMovieBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is MovieViewHolder) {
            holder.bind(items[position])
        }
    }

    private inner class MovieViewHolder(
        private val binding: ItemMovieBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(movie: Movie) {
            binding.tvTitle.text = movie.title.ifBlank { "未命名影片" }
            binding.tvRating.text = String.format(Locale.CHINA, "%.1f分", movie.rating)
            binding.tvDuration.text = if (movie.duration > 0) {
                "时长：${movie.duration}分钟"
            } else {
                "时长未知"
            }
            binding.tvRelease.isVisible = movie.releaseDate.isNotBlank()
            binding.tvRelease.text = "上映：${movie.releaseDate}"

            binding.ivPoster.load(RetrofitClient.resolveUrl(movie.poster)) {
                crossfade(true)
                placeholder(R.color.purple_200)
                error(R.color.purple_200)
            }

            binding.root.setOnClickListener { onClick(movie) }
        }
    }

    private class FooterViewHolder(
        binding: ItemLoadMoreBinding
    ) : RecyclerView.ViewHolder(binding.root)
}
