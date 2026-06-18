package com.fuuastisb.aperture.recording

/**
 * Captures a single recording session. Each implementation owns one pipeline:
 *  - [com.fuuastisb.aperture.recording.CameraXRecorder] — local-only (CameraX → MediaStore).
 *  - a streaming recorder — RootEncoder pushing to a media server while writing a local file.
 *
 * The concrete recorder is chosen per session from a settings snapshot taken at start, so a
 * recording's behaviour never changes underneath it mid-capture (mirrors the POC's contract,
 * minus the god-object that implemented both pipelines inline).
 */
interface Recorder {

    /**
     * Begin capturing. Suspends only until capture has started — not until it ends.
     * Throws if the pipeline cannot be initialised (e.g. the camera is unavailable); the
     * caller is responsible for surfacing the failure and tearing down.
     */
    suspend fun start()

    /** Stop capturing and finalise the output (flush, then publish the file to the gallery). */
    fun stop()
}
