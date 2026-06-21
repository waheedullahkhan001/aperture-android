package com.fuuastisb.aperture.ui.upload

import androidx.lifecycle.ViewModel
import com.fuuastisb.aperture.data.upload.UploadClipStatus
import com.fuuastisb.aperture.data.upload.UploadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Backs the debug "Upload queue" screen — observes the retro-upload status and can kick a drain. */
@HiltViewModel
class UploadQueueViewModel @Inject constructor(
    private val uploadManager: UploadManager,
) : ViewModel() {
    val statuses: StateFlow<List<UploadClipStatus>> = uploadManager.statuses

    /** Refresh the snapshot (e.g. when the screen opens). */
    fun refresh() = uploadManager.refresh()

    /** Force a drain attempt now. */
    fun retryNow() = uploadManager.kick()
}
