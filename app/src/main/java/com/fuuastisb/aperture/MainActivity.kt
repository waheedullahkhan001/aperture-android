package com.fuuastisb.aperture

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.fuuastisb.aperture.ui.navigation.AppNavHost
import com.fuuastisb.aperture.ui.theme.ApertureTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ApertureTheme {
                // A solid themed surface behind the nav graph. Screen transitions cross-fade, which
                // briefly makes the sliding screens semi-transparent; without this the (white) window
                // background showed through — the flash. Now they fade over the dark app background.
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavHost()
                }
            }
        }
    }
}
