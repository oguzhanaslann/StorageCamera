package com.oguzhanaslann.storagecamera

import java.io.Serializable

sealed class StorageType :Serializable {
    object Internal : StorageType()
    object Shared : StorageType()
    object Scoped : StorageType()
}
