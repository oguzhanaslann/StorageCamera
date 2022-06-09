package com.oguzhanaslann.storagecamera

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.CachePolicy
import coil.size.Scale
import com.oguzhanaslann.storagecamera.databinding.ItemPhotoLayoutBinding

class PhotoAdapter(
    private val onPhotoClicked: (Photo) -> Unit = {},
    private val onPhotoSelected: (Photo) -> Unit = {}
) : ListAdapter<Photo, PhotoAdapter.Holder>(Companion) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemPhotoLayoutBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val currentItem = getItem(position)
        holder.onBind(currentItem)
    }

    override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) {
        when {
            payloads.lastOrNull() == SELECTED -> {
                holder.binding.photoCheckIcon.isVisible = true
            }

            payloads.lastOrNull() == DESELECTED -> {
                holder.binding.photoCheckIcon.isVisible = false
            }

            else -> super.onBindViewHolder(holder, position, payloads)
        }
    }

    fun setSelectedPhotos(selected: List<Pair<Photo, StorageType>>) {

        selected.forEach {
            val photo = it.first
            val indexOfPhoto = currentList.indexOf(photo)
            if (indexOfPhoto != -1) {
                notifyItemChanged(indexOfPhoto,SELECTED)
            }
        }

        // rest of the photos are deselected
        for (i in currentList.indices) {
            val photo = currentList[i]
            if (!selected.any { it.first == photo }) {
                notifyItemChanged(i,DESELECTED)
            }
        }

    }

    inner class Holder(val binding: ItemPhotoLayoutBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun onBind(currentItem: Photo) = binding.run {
            photo.load(currentItem.url) {
                crossfade(true)
                this.diskCachePolicy(CachePolicy.ENABLED)
                this.placeholder(R.drawable.ic_baseline_monochrome_photos_24)
                this.error(R.drawable.ic_baseline_satellite_alt_24)
                scale(Scale.FILL)
                build()
            }

            photo.setOnLongClickListener {
                onPhotoSelected(currentItem)
                true
            }

            photo.setOnClickListener {
                onPhotoClicked(currentItem)
            }
        }
    }

    companion object : DiffUtil.ItemCallback<Photo>() {
        override fun areItemsTheSame(oldItem: Photo, newItem: Photo): Boolean =
            oldItem.url == newItem.url

        override fun areContentsTheSame(oldItem: Photo, newItem: Photo): Boolean =
            oldItem == newItem

        const val SELECTED = "selected"
        const val DESELECTED = "DESELECTED"
    }

}

data class Photo(
    val url: String
)
