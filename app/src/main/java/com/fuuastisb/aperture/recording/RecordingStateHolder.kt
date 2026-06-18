package com.fuuastisb.aperture.recording

import com.fuuastisb.aperture.domain.model.RecordingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide single source of truth for [RecordingState] — written by [RecordingService],
 * observed by the UI. An injected singleton, replacing the POC's static companion StateFlows.
 */
@Singleton
class RecordingStateHolder @Inject constructor() {

    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    fun set(state: RecordingState) {
        _state.value = state
    }

    /** Update the fields of the current [RecordingState.Active] (or start from a fresh Active). */
    fun updateActive(transform: (RecordingState.Active) -> RecordingState.Active) {
        val current = _state.value
        _state.value = transform(current as? RecordingState.Active ?: RecordingState.Active())
    }

    private val _recordingId = MutableStateFlow<String?>(null)
    /** Backend recording id for the active session (null when idle) — used to cancel its alert. */
    val recordingId: StateFlow<String?> = _recordingId.asStateFlow()

    fun setRecordingId(id: String?) {
        _recordingId.value = id
    }

    private val _lastError = MutableStateFlow<String?>(null)
    /** A transient user-facing error (e.g. recording failed to start); cleared on dismiss. */
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun setError(message: String) {
        _lastError.value = message
    }

    fun clearError() {
        _lastError.value = null
    }
}
