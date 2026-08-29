package com.galleryclean.app.ui.similar

import android.app.Activity
import android.app.AlertDialog
import android.content.IntentSender
import android.os.Bundle
import android.view.*
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.galleryclean.app.R
import com.galleryclean.app.databinding.FragmentSimilarBinding
import com.galleryclean.app.data.model.PhotoItem
import com.galleryclean.app.ui.MainViewModel

class SimilarFragment : Fragment() {

    private var _binding: FragmentSimilarBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: SimilarGroupAdapter

    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(requireContext(), "Deleted successfully", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSimilarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        adapter = SimilarGroupAdapter(
            onSelectionChanged = { updateDeleteButton() },
            formatSize = viewModel::formatSize
        )
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        viewModel.similarGroups.observe(viewLifecycleOwner) { groups ->
            adapter.submitGroups(groups)
            binding.tvEmpty.visibility = if (groups.isEmpty()) View.VISIBLE else View.GONE
            binding.tvGroupCount.text = "${groups.size} groups found"
        }

        // Similarity threshold slider
        binding.seekThreshold.progress = viewModel.similarityThreshold - 50
        binding.tvThreshold.text = "${viewModel.similarityThreshold}% match"
        binding.seekThreshold.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val threshold = 50 + progress
                viewModel.similarityThreshold = threshold
                binding.tvThreshold.text = "$threshold% match"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) { viewModel.startScan() }
        })

        binding.btnSelectAll.setOnClickListener { adapter.selectAllDuplicates() }
        binding.btnDeleteSelected.setOnClickListener { confirmDelete() }

        updateDeleteButton()
    }

    private fun updateDeleteButton() {
        val count = adapter.selectedCount()
        val size  = adapter.selectedSize()
        binding.btnDeleteSelected.isEnabled = count > 0
        binding.btnDeleteSelected.text = if (count > 0)
            "Delete $count photos (${viewModel.formatSize(size)})"
        else "Delete selected"
    }

    private fun confirmDelete() {
        val selected = adapter.getSelectedPhotos()
        if (selected.isEmpty()) return

        AlertDialog.Builder(requireContext())
            .setTitle("Delete ${selected.size} photos?")
            .setMessage("This will free ${viewModel.formatSize(selected.sumOf { it.size })}. This cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> doDelete(selected) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun doDelete(photos: List<PhotoItem>) {
        viewModel.deleteSelected(photos) { intentSender ->
            deleteLauncher.launch(
                IntentSenderRequest.Builder(intentSender as IntentSender).build()
            )
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
