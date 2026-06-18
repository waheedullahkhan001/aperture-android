package com.fuuastisb.aperture.data.server

import com.fuuastisb.aperture.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Progress of cancelling the emergency alert for a recording. */
sealed interface AlertCancelState {
    data object Idle : AlertCancelState
    data class InProgress(val attempt: Int) : AlertCancelState
    data class Done(val alertsAlreadyDispatched: Boolean) : AlertCancelState
}

/**
 * Cancels the emergency alert for the active recording and **keeps trying until the server confirms**
 * — the dispatch is server-side, so cancelling has to reach it. A 404 (recording not yet on the server
 * after an offline start) just means "retry"; the server's countdown only starts at first contact, so
 * nothing can fire during that window. Runs on a process-lifetime scope so it survives screen changes
 * and the recording stopping.
 */
@Singleton
class AlertCanceller @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val deviceApi: DeviceApi,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<AlertCancelState>(AlertCancelState.Idle)
    val state: StateFlow<AlertCancelState> = _state.asStateFlow()

    private var job: Job? = null

    /** Begin (or keep) cancelling [recordingId]; idempotent while a request is already in flight. */
    fun requestCancel(recordingId: String) {
        if (job?.isActive == true) return
        job = scope.launch {
            var attempt = 0
            while (isActive) {
                attempt++
                _state.value = AlertCancelState.InProgress(attempt)
                val config = settingsRepository.serverConfigSnapshot()
                if (config.isConfigured) {
                    when (val outcome = deviceApi.cancelAlerts(config, recordingId)) {
                        is CancelOutcome.Done -> {
                            _state.value = AlertCancelState.Done(outcome.alertsAlreadyDispatched)
                            return@launch
                        }
                        CancelOutcome.NotFoundRetry, CancelOutcome.Failed -> Unit // keep retrying
                    }
                }
                delay(backoffMs(attempt))
            }
        }
    }

    /** Clear state and stop retrying — called when a fresh recording starts. */
    fun reset() {
        job?.cancel()
        job = null
        _state.value = AlertCancelState.Idle
    }

    /** 1s, 2s, 4s … capped at 30s. */
    private fun backoffMs(attempt: Int): Long =
        (1_000L shl (attempt - 1).coerceIn(0, 5)).coerceAtMost(30_000L)
}
