package com.oguzhanaslann.storagecamera

import android.Manifest
import android.content.ContentUris
import android.database.ContentObserver
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.oguzhanaslann.storagecamera.databinding.FragmentPhotoBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PhotoFragment : Fragment(R.layout.fragment_photo) {

    private var binder: FragmentPhotoBinding? = null
    private val binding get() = binder!!


    private val mainViewModel: MainViewModel by activityViewModels()

    private val photoAdapter by lazy {
        PhotoAdapter()
    }

    val storageType: StorageType by lazy {
        arguments?.getSerializable(STORAGE_TYPE_KEY) as StorageType
    }

    private lateinit var contentObserver : ContentObserver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.e("TAG", "onCreate : instance : ${hashCode()}", )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binder = FragmentPhotoBinding.bind(view)
        binding.photoList.apply {
            adapter = photoAdapter
            layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        }


        when (storageType) {
            StorageType.Internal -> loadDataFromInternalStorage()
            StorageType.Scoped -> loadDataFromScopedStorage()
            StorageType.Shared -> photoAdapter.submitList(
                listOf(
                    Photo("https://images.unsplash.com/photo-1518791841217-8f162f1e1131?ixlib=rb-1.2.1&ixid=eyJhcHBfaWQiOjEyMDd9&auto=format&fit=crop&w=800&q=60"),
                    Photo("https://images.unsplash.com/photo-1518791841217-8f162f1e1131?ixlib=rb-1.2.1&ixid=eyJhcHBfaWQiOjEyMDd9&auto=format&fit=crop&w=800&q=60"),
                    Photo("https://images.unsplash.com/photo-1518791841217-8f162f1e1131?ixlib=rb-1.2.1&ixid=eyJhcHBfaWQiOjEyMDd9&auto=format&fit=crop&w=800&q=60"),
                    Photo("https://images.unsplash.com/photo-1518791841217-8f162f1e1131?ixlib=rb-1.2.1&ixid=eyJhcHBfaWQiOjEyMDd9&auto=format&fit=crop&w=800&q=60"),
                    Photo("https://images.unsplash.com/photo-1518791841217-8f162f1e1131?ixlib=rb-1.2.1&ixid=eyJhcHBfaWQiOjEyMDd9&auto=format&fit=crop&w=800&q=60"),
                    Photo("https://images.unsplash.com/photo-1518791841217-8f162f1e1131?ixlib=rb-1.2.1&ixid=eyJhcHBfaWQiOjEyMDd9&auto=format&fit=crop&w=800&q=60"),
                )
            )
        }

        contentObserver = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                loadDataFromScopedStorage()
            }
        }


        context?.contentResolver?.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )


        subscribeObservers()

    }


    private fun subscribeObservers() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            mainViewModel.storageEvents.collect {
                when (it) {
                    MainViewModel.StorageEvent.InternalStorageUpdated -> updateIfInternalStorage()
                    MainViewModel.StorageEvent.ScopeStorageUpdated ->  {
                        Log.e("TAG", "subscribeObservers:ScopeStorageUpdated")
                        updateIfScopeStorage()
                    }
                    MainViewModel.StorageEvent.SharedStorageUpdated -> updateIfSharedStorage()
                }
            }
        }
    }

    private fun updateIfScopeStorage() {
        Log.e("TAG", "updateIfScopeStorage: storageType $storageType , hashcode : ${hashCode()}", )
        if (storageType == StorageType.Scoped) {
            loadDataFromScopedStorage()
        }
    }

    private fun loadDataFromScopedStorage() {
        Log.e("TAG", "loadDataFromScopedStorage:")
        val collection = sdk29AndUp {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } ?: MediaStore.Images.Media.EXTERNAL_CONTENT_URI


        context?.let {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_ADDED
            )

            val photos = mutableListOf<Photo>()
            it.contentResolver.query(
                collection,
                projection,
                null,
                null,
                MediaStore.Images.Media.DATE_ADDED + " DESC"
            )?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while(it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    photos.add(Photo(contentUri.toString()))
                }
                photoAdapter.submitList(photos)
            }
        }
    }


    private fun updateIfSharedStorage() {
        if (storageType == StorageType.Shared) {
//   photoAdapter.submitList(mainViewModel.sharedStorage.value)
        }
    }

    private fun updateIfInternalStorage() {
        if (storageType == StorageType.Internal) {
            loadDataFromInternalStorage()
        }
    }

    private fun loadDataFromInternalStorage() {
        context?.let {
            val photos = mutableListOf<Photo>()
            it.filesDir.listFiles()?.forEach {
                photos.add(Photo(it.toURI().toURL().toString()))
            }
            photoAdapter.submitList(photos)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binder = null
        context?.contentResolver?.unregisterContentObserver(contentObserver)
    }


    companion object {

        // storage type key
        const val STORAGE_TYPE_KEY = "STORAGE_TYPE_KEY"

        @JvmStatic
        fun newInstance(storageType: StorageType) =
            PhotoFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(STORAGE_TYPE_KEY, storageType)
                }
            }
    }
}
