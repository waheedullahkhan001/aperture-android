package com.fuuastisb.aperture.launcher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.fuuastisb.aperture.recording.RecordingService

/**
 * Invisible bridge that exists solely to satisfy Android 11+'s rule that a camera/microphone
 * foreground service must be started from the foreground. The accessibility service launches
 * this (allowed from the background); it starts the recording service and finishes immediately,
 * so the user never sees it.
 */
class TransparentLauncherActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startForegroundService(RecordingService.startIntent(this))
        finish()
    }

    companion object {
        fun launchIntent(context: Context): Intent =
            Intent(context, TransparentLauncherActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
    }
}
