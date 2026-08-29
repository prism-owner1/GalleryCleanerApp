package com.galleryclean.app.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.galleryclean.app.MainActivity
import com.galleryclean.app.R
import com.galleryclean.app.databinding.FragmentHomeBinding
import com.galleryclean.app.ui.MainViewModel
import com.galleryclean.app.ui.ScanState

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MainViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        viewModel.scanState.observe(viewLifecycleOwner) { state ->
            when (state) {
                ScanState.IDLE     -> showIdle()
                ScanState.SCANNING -> showScanning()
                ScanState.DONE     -> showResults()
                ScanState.ERROR    -> showError()
            }
        }

        viewModel.scanProgress.observe(viewLifecycleOwner) { (done, total) ->
            if (total > 0) {
                binding.progressBar.progress = (done * 100 / total)
                binding.tvProgress.text = "Analysing $done / $total photos…"
            }
        }

        viewModel.allPhotos.observe(viewLifecycleOwner) { photos ->
            binding.tvTotalPhotos.text = "${photos.size}"
            binding.tvTotalSize.text = viewModel.formatSize(photos.sumOf { it.size })
        }

        viewModel.similarGroups.observe(viewLifecycleOwner) { groups ->
            val dupes = groups.sumOf { it.photos.size - 1 }
            binding.tvDuplicates.text = "$dupes"
        }

        viewModel.promoPhotos.observe(viewLifecycleOwner) { promos ->
            binding.tvPromo.text = "${promos.size}"
        }

        viewModel.savedSize.observe(viewLifecycleOwner) { saved ->
            binding.tvSaved.text = viewModel.formatSize(saved)
        }

        binding.btnRescan.setOnClickListener { viewModel.startScan() }

        binding.cardSimilar.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_similar)
        }
        binding.cardPromo.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_promo)
        }
    }

    private fun showIdle() {
        binding.layoutScan.visibility = View.VISIBLE
        binding.layoutProgress.visibility = View.GONE
        binding.layoutResults.visibility = View.GONE
    }

    private fun showScanning() {
        binding.layoutScan.visibility = View.GONE
        binding.layoutProgress.visibility = View.VISIBLE
        binding.layoutResults.visibility = View.GONE
        binding.progressBar.progress = 0
        binding.tvProgress.text = "Preparing scan…"
    }

    private fun showResults() {
        binding.layoutScan.visibility = View.GONE
        binding.layoutProgress.visibility = View.GONE
        binding.layoutResults.visibility = View.VISIBLE
    }

    private fun showError() {
        binding.layoutScan.visibility = View.VISIBLE
        binding.layoutProgress.visibility = View.GONE
        binding.layoutResults.visibility = View.GONE
        binding.btnRescan.text = "Retry scan"
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
