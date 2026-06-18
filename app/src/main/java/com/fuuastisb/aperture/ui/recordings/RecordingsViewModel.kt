package com.fuuastisb.aperture.ui.recordings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuuastisb.aperture.data.recordings.RecordingsRepository
import com.fuuastisb.aperture.data.settings.SettingsRepository
import com.fuuastisb.aperture.domain.model.RecordingItem
import com.fuuastisb.aperture.domain.model.StoragePolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs the recordings library and the storage settings screen. */
@HiltViewModel
class RecordingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val recordingsRepository: RecordingsRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _recordings = MutableStateFlow<List<RecordingItem>>(emptyList())
    val recordings: StateFlow<List<RecordingItem>> = _recordings.asStateFlow()

    val storagePolicy: StateFlow<StoragePolicy> = settingsRepository.storagePolicy
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StoragePolicy())

    fun refresh() {
        viewModelScope.launch { _recordings.value = recordingsRepository.list() }
    }

    fun delete(uri: Uri) {
        viewModelScope.launch {
            recordingsRepository.delete(uri)
            _recordings.value = recordingsRepository.list()
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            recordingsRepository.deleteAll()
            _recordings.value = recordingsRepository.list()
        }
    }

    fun setStoragePolicy(policy: StoragePolicy) {
        viewModelScope.launch { settingsRepository.setStoragePolicy(policy) }
    }

    fun play(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { appContext.startActivity(intent) }
    }
}
