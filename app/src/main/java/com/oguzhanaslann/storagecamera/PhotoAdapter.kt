package com.oguzhanaslann.storagecamera

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Scale
import com.oguzhanaslann.storagecamera.databinding.ItemPhotoLayoutBinding

class PhotoAdapter(

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
        }
    }

    companion object : DiffUtil.ItemCallback<Photo>() {
        override fun areItemsTheSame(oldItem: Photo, newItem: Photo): Boolean =
            oldItem.url == newItem.url


        override fun areContentsTheSame(oldItem: Photo, newItem: Photo): Boolean = oldItem == newItem

    }
}



data class Photo(
    val url: String
)
