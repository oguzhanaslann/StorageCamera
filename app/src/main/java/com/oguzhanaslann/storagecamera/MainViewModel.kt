package com.oguzhanaslann.storagecamera

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    private val _storageEventsChannel = Channel<StorageEvent>()
    val storageEvents = _storageEventsChannel.receiveAsFlow()

    fun sendEvent(event: StorageEvent) {
        viewModelScope.launch {
            delay(1500)
            Log.e("TAG", "sendEvent: send event $event")
            _storageEventsChannel.send(event)
        }
    }

    sealed class StorageEvent {
        object InternalStorageUpdated : StorageEvent()
        object ScopeStorageUpdated : StorageEvent()
        object SharedStorageUpdated : StorageEvent()
    }
}
