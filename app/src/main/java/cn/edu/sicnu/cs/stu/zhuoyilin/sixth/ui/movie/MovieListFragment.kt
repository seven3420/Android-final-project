package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.ui.movie

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.R
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.databinding.FragmentMovieListBinding
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.util.ToastUtil
import com.google.android.material.tabs.TabLayout

class MovieListFragment : Fragment(R.layout.fragment_movie_list) {

    private var _binding: FragmentMovieListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MovieListViewModel by viewModels()
    private lateinit var adapter: MovieAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentMovieListBinding.bind(view)
        applyTopInset()

        setupList()
        setupTabs()
        observeState()
        viewModel.start()
    }

    private fun applyTopInset() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(view.paddingStart, top, view.paddingEnd, view.paddingBottom)
            insets
        }
    }

    private fun setupList() {
        adapter = MovieAdapter { movie ->
            if (movie.movieId <= 0) {
                ToastUtil.showShort(requireContext(), "影片信息不完整")
                return@MovieAdapter
            }
            startActivity(
                Intent(requireContext(), MovieDetailActivity::class.java)
                    .putExtra(MovieDetailActivity.EXTRA_MOVIE_ID, movie.movieId)
            )
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (lastVisible >= adapter.itemCount - 3) {
                    viewModel.loadMore()
                }
            }
        })

        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
        binding.errorView.onRetryClickListener = { viewModel.refresh() }
    }

    private fun setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("热映"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("待映"))
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val type = if (tab.position == 0) {
                    MovieListViewModel.TYPE_SHOWING
                } else {
                    MovieListViewModel.TYPE_COMING
                }
                viewModel.switchType(type)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    private fun observeState() {
        viewModel.state.observe(viewLifecycleOwner) { state ->
            binding.swipeRefresh.isRefreshing = state.refreshing
            adapter.submitList(state.movies)
            adapter.setLoadingMore(state.loadingMore)

            binding.emptyView.isVisible =
                state.firstLoadDone && state.movies.isEmpty() && state.error == null
            binding.errorView.isVisible = state.movies.isEmpty() && state.error != null
        }

        viewModel.toast.observe(viewLifecycleOwner) { message ->
            if (!message.isNullOrEmpty()) {
                ToastUtil.showShort(requireContext(), message)
                viewModel.consumeToast()
            }
        }
    }

    override fun onDestroyView() {
        binding.recyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
