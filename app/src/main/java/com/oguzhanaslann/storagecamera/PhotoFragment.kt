package com.oguzhanaslann.storagecamera

import android.app.Activity.RESULT_OK
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.oguzhanaslann.storagecamera.databinding.FragmentPhotoBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "PhotoFragment"

class PhotoFragment : Fragment(R.layout.fragment_photo) {
    private var binder: FragmentPhotoBinding? = null
    private val binding get() = binder!!

    private val mainViewModel: MainViewModel by activityViewModels()

    private val photoAdapter by lazy {
        PhotoAdapter(
            onPhotoClicked = ::onPhotoClicked,
            onPhotoSelected = ::onPhotoSelected
        )
    }

    val storageType: StorageType by lazy {
        arguments?.getSerializable(STORAGE_TYPE_KEY) as StorageType
    }

    private lateinit var intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>

    private lateinit var contentObserver: ContentObserver

    private var deletedImageUri: Uri? = null

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
        }

        contentObserver = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                if (storageType == StorageType.Scoped) {
                    loadDataFromScopedStorage()
                }
            }
        }

        context?.contentResolver?.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )


        intentSenderLauncher =
            registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
                if (it.resultCode == RESULT_OK) {
                    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                        lifecycleScope.launch {
                            deletePhotoFromScopedStorage(deletedImageUri ?: return@launch)
                        }
                    }
                    Toast.makeText(context, "Photo deleted successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Photo couldn't be deleted", Toast.LENGTH_SHORT).show()
                }
            }

        subscribeObservers()
    }

    private fun onPhotoSelected(photo: Photo) {
        mainViewModel.activateDeleteModeOrIgnore()
        mainViewModel.selectPhoto(photo, storageType)
    }

    private fun onPhotoClicked(photo: Photo) {
        mainViewModel.togglePhotoSelection(photo, storageType)
        mainViewModel.deactivateDeleteModeIfNoPhotoLeft()
    }


    private fun subscribeObservers() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            mainViewModel.storageEvents.collect {
                when (it) {
                    MainViewModel.StorageEvent.InternalStorageUpdated -> {
                        updateIfInternalStorage()
                    }
                    is MainViewModel.StorageEvent.DeleteInternalStoragePhoto -> {
                        deletePhotoIfInternalStorage(it.photo)
                    }

                    MainViewModel.StorageEvent.ScopeStorageUpdated -> {
                        Log.e(TAG, "subscribeObservers:ScopeStorageUpdated")
                        updateIfScopeStorage()
                    }

                    is MainViewModel.StorageEvent.DeleteScopedStoragePhoto -> {
                        deletePhotoIfScopeStorage(it.photo)
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.selectedPhotos.collect {
                it.filter {
                    it.second == storageType
                }.also {
                    photoAdapter.setSelectedPhotos(it)
                }
            }
        }
    }

    private fun deletePhotoIfScopeStorage(photo: Photo) {
        val uri = Uri.parse(photo.url)
        deletedImageUri = uri
        lifecycleScope.launch {
            deletePhotoFromScopedStorage(uri)
        }
    }

    private suspend fun deletePhotoFromScopedStorage(photoUri: Uri) {
        withContext(Dispatchers.IO) {
            context?.run {

                val deletion = runCatching {
                    contentResolver.delete(photoUri, null, null)
                }

                deletion.onFailure {
                    if (it is SecurityException) {
                        val intentSender = when {
                            aboveAndroid11() -> {
                                MediaStore.createDeleteRequest(
                                    contentResolver,
                                    listOf(photoUri)
                                ).intentSender
                            }

                            aboveAndroid10() -> {
                                val recoverableSecurityException =
                                    it as? RecoverableSecurityException
                                recoverableSecurityException?.userAction?.actionIntent?.intentSender
                            }

                            else -> null
                        }


                        intentSender?.let { sender ->
                            intentSenderLauncher.launch(
                                IntentSenderRequest.Builder(sender).build()
                            )
                        }
                    }
                }
            }
        }
    }

    private fun aboveAndroid10() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    private fun aboveAndroid11() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    private fun deletePhotoIfInternalStorage(photo: Photo) {
        if (storageType == StorageType.Internal) {
            val url = photo.url
            val file = File(url)
            context?.deleteFile(file.name)
        }
    }

    private fun updateIfScopeStorage() {
        Log.e(TAG, "updateIfScopeStorage: storageType $storageType , hashcode : ${hashCode()}")
        if (storageType == StorageType.Scoped) {
            loadDataFromScopedStorage()
        }
    }

    private fun loadDataFromScopedStorage() {
        Log.e(TAG, "loadDataFromScopedStorage:")
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
                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val contentUri =
                        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    photos.add(Photo(contentUri.toString()))
                }
                photoAdapter.submitList(photos)
            }
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
