package com.galleryclean.app.ui.similar

import android.view.*
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.galleryclean.app.R
import com.galleryclean.app.data.model.PhotoItem
import com.galleryclean.app.data.model.SimilarGroup

class SimilarGroupAdapter(
    private val onSelectionChanged: () -> Unit,
    private val formatSize: (Long) -> String
) : RecyclerView.Adapter<SimilarGroupAdapter.GroupVH>() {

    private val groups = mutableListOf<SimilarGroup>()

    fun submitGroups(newGroups: List<SimilarGroup>) {
        groups.clear()
        groups.addAll(newGroups)
        notifyDataSetChanged()
    }

    /** Auto-select all but the best photo in each group */
    fun selectAllDuplicates() {
        groups.forEach { group ->
            val best = group.photos.maxByOrNull { it.size } ?: return@forEach
            group.photos.forEach { it.isSelected = it.id != best.id }
        }
        notifyDataSetChanged()
        onSelectionChanged()
    }

    fun selectedCount() = groups.sumOf { g -> g.photos.count { it.isSelected } }
    fun selectedSize()  = groups.sumOf { g -> g.photos.filter { it.isSelected }.sumOf { it.size } }
    fun getSelectedPhotos() = groups.flatMap { g -> g.photos.filter { it.isSelected } }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupVH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_similar_group, parent, false)
        return GroupVH(v)
    }

    override fun onBindViewHolder(holder: GroupVH, position: Int) = holder.bind(groups[position])
    override fun getItemCount() = groups.size

    inner class GroupVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView   = itemView.findViewById(R.id.tvGroupTitle)
        private val tvSim: TextView     = itemView.findViewById(R.id.tvSimilarity)
        private val innerRv: RecyclerView = itemView.findViewById(R.id.rvPhotos)

        fun bind(group: SimilarGroup) {
            tvTitle.text = "${group.photos.size} similar photos"
            tvSim.text   = "%.0f%% match".format(group.similarity)

            val photoAdapter = PhotoThumbnailAdapter(group.photos, formatSize) {
                onSelectionChanged()
                notifyItemChanged(bindingAdapterPosition)
            }
            innerRv.layoutManager = LinearLayoutManager(
                itemView.context, LinearLayoutManager.HORIZONTAL, false
            )
            innerRv.adapter = photoAdapter
        }
    }
}

class PhotoThumbnailAdapter(
    private val photos: MutableList<PhotoItem>,
    private val formatSize: (Long) -> String,
    private val onChanged: () -> Unit
) : RecyclerView.Adapter<PhotoThumbnailAdapter.VH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_photo_thumb, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(photos[position])
    override fun getItemCount() = photos.size

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val img: ImageView   = v.findViewById(R.id.imgThumb)
        private val check: CheckBox  = v.findViewById(R.id.checkSelect)
        private val tvSize: TextView = v.findViewById(R.id.tvSize)

        fun bind(photo: PhotoItem) {
            Glide.with(img).load(photo.uri).centerCrop().into(img)
            tvSize.text = formatSize(photo.size)
            check.isChecked = photo.isSelected

            val toggle: () -> Unit = {
                photo.isSelected = !photo.isSelected
                check.isChecked  = photo.isSelected
                onChanged()
            }
            check.setOnClickListener { toggle() }
            img.setOnClickListener  { toggle() }
        }
    }
}
