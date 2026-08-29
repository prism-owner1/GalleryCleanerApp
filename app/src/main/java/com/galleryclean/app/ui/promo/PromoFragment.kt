package com.galleryclean.app.ui.promo

import android.app.Activity
import android.app.AlertDialog
import android.content.IntentSender
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.galleryclean.app.databinding.FragmentPromoBinding
import com.galleryclean.app.data.model.PhotoItem
import com.galleryclean.app.ui.MainViewModel

class PromoFragment : Fragment() {

    private var _binding: FragmentPromoBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: PromoAdapter

    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK)
            Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPromoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        adapter = PromoAdapter(viewModel::formatSize) { updateDeleteButton() }
        binding.recycler.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.recycler.adapter = adapter

        viewModel.promoPhotos.observe(viewLifecycleOwner) { photos ->
            adapter.submitList(photos)
            binding.tvEmpty.visibility = if (photos.isEmpty()) View.VISIBLE else View.GONE
            binding.tvCount.text = "${photos.size} unwanted photos found"
            binding.tvEstSize.text = viewModel.formatSize(photos.sumOf { it.size })
        }

        binding.btnSelectAll.setOnClickListener   { adapter.selectAll(); updateDeleteButton() }
        binding.btnDeleteSelected.setOnClickListener { confirmDelete() }

        updateDeleteButton()
    }

    private fun updateDeleteButton() {
        val count = adapter.selectedCount()
        binding.btnDeleteSelected.isEnabled = count > 0
        binding.btnDeleteSelected.text = if (count > 0)
            "Delete $count photos"
        else "Delete selected"
    }

    private fun confirmDelete() {
        val selected = adapter.getSelected()
        if (selected.isEmpty()) return
        AlertDialog.Builder(requireContext())
            .setTitle("Delete ${selected.size} photos?")
            .setMessage("Frees ${viewModel.formatSize(selected.sumOf { it.size })}. Cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteSelected(selected) { intentSender ->
                    deleteLauncher.launch(
                        IntentSenderRequest.Builder(intentSender as IntentSender).build()
                    )
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
