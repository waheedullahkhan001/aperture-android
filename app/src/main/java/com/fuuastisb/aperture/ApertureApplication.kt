package com.fuuastisb.aperture

import android.app.Application
import com.fuuastisb.aperture.data.upload.UploadManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent

/**
 * Application entry point. Annotated with [HiltAndroidApp] so Hilt generates the application-level
 * dependency container that every injected component descends from. On start it nudges the
 * [UploadManager] to drain any clips queued for retro-upload from a previous offline session.
 */
@HiltAndroidApp
class ApertureApplication : Application() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface UploadEntryPoint {
        fun uploadManager(): UploadManager
    }

    override fun onCreate() {
        super.onCreate()
        // Instantiating the manager also registers its network-available callback; kick() retries the
        // queue now in case we're already online. Best-effort — it defers itself if a recording starts.
        EntryPointAccessors.fromApplication(this, UploadEntryPoint::class.java).uploadManager().kick()
    }
}
