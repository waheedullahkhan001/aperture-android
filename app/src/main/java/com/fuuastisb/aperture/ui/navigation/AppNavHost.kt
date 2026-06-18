package com.fuuastisb.aperture.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fuuastisb.aperture.ui.HomeScreen
import com.fuuastisb.aperture.ui.readiness.ReadinessScreen
import com.fuuastisb.aperture.ui.recordings.RecordingsLibraryScreen
import com.fuuastisb.aperture.ui.settings.ActivationSettingsScreen
import com.fuuastisb.aperture.ui.settings.MetaInfoScreen
import com.fuuastisb.aperture.ui.settings.NotificationSettingsScreen
import com.fuuastisb.aperture.ui.settings.RecordingSettingsScreen
import com.fuuastisb.aperture.ui.settings.SettingsHubScreen
import com.fuuastisb.aperture.ui.settings.StorageSettingsScreen
import com.fuuastisb.aperture.ui.settings.StreamingSettingsScreen

/** Navigation routes. Kept as plain strings for simplicity. */
object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val ACTIVATION = "settings/activation"
    const val RECORDING = "settings/recording"
    const val STREAMING = "settings/streaming"
    const val NOTIFICATION = "settings/notification"
    const val METADATA = "settings/metadata"
    const val STORAGE = "settings/storage"
    const val RECORDINGS = "recordings"
    const val READINESS = "readiness"
}

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        // Smooth slide-through instead of the default cut/flash: forward slides toward the start,
        // back (pop) slides the other way, each with a short cross-fade.
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(280)) + fadeIn(tween(280)) },
        exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(280)) + fadeOut(tween(280)) },
        popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(280)) + fadeIn(tween(280)) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(280)) + fadeOut(tween(280)) },
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenReadiness = { nav.navigate(Routes.READINESS) },
                onOpenRecordings = { nav.navigate(Routes.RECORDINGS) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsHubScreen(
                onBack = { nav.popBackStack() },
                onOpen = { route -> nav.navigate(route) },
            )
        }
        composable(Routes.ACTIVATION) {
            ActivationSettingsScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.RECORDING) {
            RecordingSettingsScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.STREAMING) {
            StreamingSettingsScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.NOTIFICATION) {
            NotificationSettingsScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.METADATA) {
            MetaInfoScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.STORAGE) {
            StorageSettingsScreen(
                onBack = { nav.popBackStack() },
                onOpenRecordings = { nav.navigate(Routes.RECORDINGS) },
            )
        }
        composable(Routes.RECORDINGS) {
            RecordingsLibraryScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.READINESS) {
            ReadinessScreen(onBack = { nav.popBackStack() })
        }
    }
}
