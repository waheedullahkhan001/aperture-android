package com.fuuastisb.aperture.domain.model

/**
 * The single source of truth for what the recording subsystem is doing, observed by the UI.
 *
 * Replaces the POC's two scattered `companion object` StateFlows (`isRecording` +
 * `streamingState`) with one coherent model. The UI branches on this to render the home
 * screen (ready / recording / streaming badge / emergency countdown / stop).
 */
sealed interface RecordingState {

    /** Not recording; armed and ready to be triggered. */
    data object Idle : RecordingState

    /**
     * A recording session is active.
     *
     * [streaming] reflects the optional media-server push, and [emergency] reflects the
     * optional cancelable-alert shell. Both default to "off", which is the plain
     * local-only / everyday-recording case.
     */
    data class Active(
        val streaming: StreamingState = StreamingState.Off,
        val emergency: EmergencyState = EmergencyState.Off,
    ) : RecordingState
}

/** Live-stream push status during an active recording (the POC tracked this separately). */
sealed interface StreamingState {
    /** Local-only recording — not streaming to any server. */
    data object Off : StreamingState
    data object Connecting : StreamingState
    data object Live : StreamingState
    data class Failed(val reason: String) : StreamingState
}

/**
 * The cancelable emergency-alert shell during an active recording. Recording and streaming
 * begin immediately; the *alert to contacts* waits out a grace period the user can cancel,
 * which separates preserving evidence from raising the alarm.
 */
sealed interface EmergencyState {
    /** Not an emergency recording (no countdown). */
    data object Off : EmergencyState

    /** Grace period in progress — the user can still cancel to suppress the alert. */
    data class CountingDown(val remainingMs: Long) : EmergencyState

    /** Countdown elapsed; alerts have been dispatched and can no longer be retracted. */
    data object AlertsDispatched : EmergencyState
}
