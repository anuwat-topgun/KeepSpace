package com.smartstorage.cleaner.media

import android.app.Application
import androidx.lifecycle.AndroidViewModel

/** Keeps the [LibraryStore] (and its analysis cache) alive across rotation and resizing. */
class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private var store: LibraryStore? = null

    fun store(demo: Boolean): LibraryStore = store ?: LibraryStore(getApplication(), demo).also { store = it }

    override fun onCleared() {
        store?.close()
    }
}
