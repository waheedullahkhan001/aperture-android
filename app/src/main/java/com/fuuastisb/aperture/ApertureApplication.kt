package com.fuuastisb.aperture

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. Annotated with [HiltAndroidApp] so Hilt generates the
 * application-level dependency container that every injected component descends from.
 */
@HiltAndroidApp
class ApertureApplication : Application()
