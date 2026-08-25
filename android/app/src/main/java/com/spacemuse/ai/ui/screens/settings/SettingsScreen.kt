package com.spacemuse.ai.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spacemuse.ai.core.network.ApiConfig
import com.spacemuse.ai.core.network.BackendUrlStore
import kotlinx.coroutines.launch

// Real-device testing needs the backend's LAN address, which changes per
// network/machine and can't be baked into a build — this screen lets it be
// set/changed without a rebuild. See BackendUrlStore / ApiClient.baseUrl.
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var urlText by remember { mutableStateOf(ApiConfig.BASE_URL) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        urlText = BackendUrlStore.getBaseUrl(context)
    }

    fun save() {
        val normalized = if (urlText.endsWith("/")) urlText else "$urlText/"
        if (!BackendUrlStore.isValidBaseUrl(normalized)) {
            error = "That doesn't look like a valid URL."
            saved = false
            return
        }
        error = null
        coroutineScope.launch {
            BackendUrlStore.setBaseUrl(context, normalized)
            urlText = normalized
            saved = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "←",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(end = 16.dp)
            )
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Backend server URL",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "SpaceMuse AI needs your backend server's address to scan rooms and " +
                "search products. On the Android Emulator, the default (10.0.2.2) works " +
                "automatically. On a real phone, use this computer's LAN IP instead — " +
                "e.g. http://192.168.1.10:4000/api/v1/. The phone and computer must be on " +
                "the same Wi-Fi network, and the backend server must be running.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = urlText,
            onValueChange = {
                urlText = it
                error = null
                saved = false
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Backend URL") },
            placeholder = { Text("http://192.168.1.10:4000/api/v1/") },
            isError = error != null
        )

        error?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (saved) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Saved.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = {
                urlText = ApiConfig.BASE_URL
                error = null
                saved = false
            }) {
                Text("Reset to emulator default")
            }
            Button(onClick = ::save) {
                Text("Save")
            }
        }
    }
}
