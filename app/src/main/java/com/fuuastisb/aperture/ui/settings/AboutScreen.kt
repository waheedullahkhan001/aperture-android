package com.fuuastisb.aperture.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fuuastisb.aperture.BuildConfig

/** Build & environment info — so an installed build is identifiable on-device (which versions it carries). */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    SettingsScaffold("About", onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            InfoRow("App version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            InfoRow("Build type", BuildConfig.BUILD_TYPE)
            InfoRow("Package", BuildConfig.APPLICATION_ID)
            InfoRow("Stream engine", "RootEncoder ${BuildConfig.ROOT_ENCODER_VERSION}")
            InfoRow("Camera", "CameraX ${BuildConfig.CAMERAX_VERSION}")
            InfoRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            InfoRow("Device", "${Build.MANUFACTURER} ${Build.MODEL}")

            Spacer(Modifier.height(16.dp))
            Text(
                "Aperture Emergency Camera — Final Year Project.\nIcons by Lucide (ISC). See NOTICE for licenses.",
                modifier = Modifier.padding(horizontal = 20.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.width(120.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
    HorizontalDivider()
}
