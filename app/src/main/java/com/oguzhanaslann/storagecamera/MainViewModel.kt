package com.oguzhanaslann.storagecamera

import android.content.Context
import android.util.Log
import androidx.lifecycle.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel : ViewModel() {
    private val _storageEventsChannel = MutableSharedFlow<StorageEvent>()
    val storageEvents = _storageEventsChannel

    private val _isReadPermissionGranted = MutableLiveData<Boolean>()
    val isReadPermissionGranted: LiveData<Boolean> = _isReadPermissionGranted

    private val _isWritePermissionGranted = MutableLiveData<Boolean>()
    val isWritePermissionGranted: LiveData<Boolean> = _isWritePermissionGranted

    val shouldRequestPermission = _isWritePermissionGranted.asFlow()
        .combine(_isReadPermissionGranted.asFlow()) { write, read ->
            !(write && read)
        }

    private val _deleteMode = MutableStateFlow<Boolean>(false)
    val deleteMode: StateFlow<Boolean>
        get() = _deleteMode

    private val _selectedPhotos = MutableStateFlow<List<Pair<Photo, StorageType>>>(emptyList())
    val selectedPhotos: StateFlow<List<Pair<Photo, StorageType>>>
        get() = _selectedPhotos

    fun sendEvent(event: StorageEvent) {
        viewModelScope.launch {
            _storageEventsChannel.emit(event)
        }
    }

    private fun setReadPermissionGrant(hasReadPermission: Boolean) {
        _isReadPermissionGranted.value = hasReadPermission
    }

    private fun setWritePermissionGrant(hasWritePermission: Boolean) {
        _isWritePermissionGranted.value = hasWritePermission
    }

    fun setPermissionsGranted(hasReadPermission: Boolean, hasWritePermission: Boolean) {
        setReadPermissionGrant(hasReadPermission)
        setWritePermissionGrant(hasWritePermission)
        sendEvent(StorageEvent.InternalStorageUpdated)
        sendEvent(StorageEvent.ScopeStorageUpdated)
    }

    private fun activateDeleteMode() {
        _deleteMode.value = true
    }

    fun deactivateDeleteMode() {
        _deleteMode.value = false
        _selectedPhotos.value = emptyList()
    }

    fun activateDeleteModeOrIgnore() {
        val isDeleteModePassive = !_deleteMode.value
        if (isDeleteModePassive) {
            activateDeleteMode()
        }
    }

    fun selectPhoto(photo: Photo, storageType: StorageType) {
        val isDeleteModeActive = _deleteMode.value
        if (isDeleteModeActive) {
            val selectedPhotos = _selectedPhotos.value
            val newSelectedPhotos = selectedPhotos.toMutableList()
            val isPhotoSelected = newSelectedPhotos.any { it.first == photo }
            if (!isPhotoSelected) {
                newSelectedPhotos.add(Pair(photo, storageType))
            }
            _selectedPhotos.value = newSelectedPhotos
        }
    }

    fun togglePhotoSelection(photo: Photo, storageType: StorageType) {
        val isDeleteModeActive = _deleteMode.value
        if (isDeleteModeActive) {
            val selectedPhotos = _selectedPhotos.value
            val newSelectedPhotos = selectedPhotos.toMutableList()
            val index = newSelectedPhotos.indexOfFirst { it.first == photo }
            if (index != -1) {
                newSelectedPhotos.removeAt(index)
            } else {
                newSelectedPhotos.add(Pair(photo, storageType))
            }
            _selectedPhotos.value = newSelectedPhotos
        }
    }

    fun deactivateDeleteModeIfNoPhotoLeft() {
        val isDeleteModeActive = _deleteMode.value
        if (isDeleteModeActive) {
            val selectedPhotos = _selectedPhotos.value
            if (selectedPhotos.isEmpty()) {
                deactivateDeleteMode()
            }
        }
    }

    fun deleteSelectedPhotos() {
        viewModelScope.launch {
            val selectedPhotos = _selectedPhotos.value
            selectedPhotos.forEach {
                val (photo, storageType) = it
                when (storageType) {
                    StorageType.Internal -> {
                        deleteInternalStoragePhoto(photo)
                    }
                    StorageType.Scoped ->
                        deleteScopedStoragePhoto(photo)
                }
            }

            sendEvent(StorageEvent.InternalStorageUpdated)
            sendEvent(StorageEvent.ScopeStorageUpdated)
            deactivateDeleteMode()
        }
    }

    private fun deleteInternalStoragePhoto(photo: Photo) {
       viewModelScope.launch {
           _storageEventsChannel.emit(StorageEvent.DeleteInternalStoragePhoto(photo))
       }
    }

    private fun deleteScopedStoragePhoto(photo: Photo) {
        viewModelScope.launch {
            _storageEventsChannel.emit(StorageEvent.DeleteScopedStoragePhoto(photo))
        }
    }

    sealed class StorageEvent {
        object InternalStorageUpdated : StorageEvent()
        data class DeleteInternalStoragePhoto(val photo: Photo) : StorageEvent()
        object ScopeStorageUpdated : StorageEvent()
        data class DeleteScopedStoragePhoto(val photo: Photo) : StorageEvent()
    }
}
