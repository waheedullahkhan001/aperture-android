package com.fuuastisb.aperture.recording

import com.fuuastisb.aperture.domain.model.EmergencyState
import com.fuuastisb.aperture.domain.model.RecordingState
import com.fuuastisb.aperture.domain.model.StreamingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingStateHolderTest {

    @Test
    fun `starts idle`() {
        assertEquals(RecordingState.Idle, RecordingStateHolder().state.value)
    }

    @Test
    fun `updateActive from idle creates an active state`() {
        val holder = RecordingStateHolder()
        holder.updateActive { it.copy(streaming = StreamingState.Live) }
        val state = holder.state.value
        assertTrue(state is RecordingState.Active)
        assertEquals(StreamingState.Live, (state as RecordingState.Active).streaming)
    }

    @Test
    fun `updateActive preserves the other fields`() {
        val holder = RecordingStateHolder()
        holder.set(RecordingState.Active(streaming = StreamingState.Live))
        holder.updateActive { it.copy(emergency = EmergencyState.AlertsDispatched) }
        val state = holder.state.value as RecordingState.Active
        assertEquals(StreamingState.Live, state.streaming) // preserved across the update
        assertEquals(EmergencyState.AlertsDispatched, state.emergency)
    }
}
