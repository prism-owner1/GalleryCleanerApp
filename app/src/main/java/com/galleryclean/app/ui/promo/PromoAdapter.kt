package com.galleryclean.app.ui.promo

import android.view.*
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.galleryclean.app.R
import com.galleryclean.app.data.model.PhotoItem

class PromoAdapter(
    private val formatSize: (Long) -> String,
    private val onSelectionChanged: () -> Unit
) : RecyclerView.Adapter<PromoAdapter.VH>() {

    private val photos = mutableListOf<PhotoItem>()

    fun submitList(list: List<PhotoItem>) {
        photos.clear(); photos.addAll(list); notifyDataSetChanged()
    }

    fun selectAll()         { photos.forEach { it.isSelected = true }; notifyDataSetChanged(); onSelectionChanged() }
    fun selectedCount()     = photos.count { it.isSelected }
    fun getSelected()       = photos.filter { it.isSelected }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_promo_photo, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(photos[position])
    override fun getItemCount() = photos.size

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val img: ImageView    = v.findViewById(R.id.imgPhoto)
        private val check: CheckBox   = v.findViewById(R.id.checkSelect)
        private val tvReason: TextView = v.findViewById(R.id.tvReason)

        fun bind(photo: PhotoItem) {
            Glide.with(img).load(photo.uri).centerCrop().into(img)
            check.isChecked = photo.isSelected
            tvReason.text = photo.promoReason

            val toggle = {
                photo.isSelected = !photo.isSelected
                check.isChecked = photo.isSelected
                onSelectionChanged()
            }
            check.setOnClickListener { toggle() }
            img.setOnClickListener  { toggle() }
        }
    }
}
