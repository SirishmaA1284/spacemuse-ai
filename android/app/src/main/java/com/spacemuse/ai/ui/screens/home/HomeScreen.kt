package com.spacemuse.ai.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Section 59 of the product spec: Scan My Space, Continue Previous Design,
// My Designs, Explore Style, Saved Products. Only "Scan My Space" is wired
// to a real destination in this pass — the rest need Design/Product
// persistence (Phase 4/9) to be meaningful, so they're intentionally
// omitted rather than wired to dead stubs.
@Composable
fun HomeScreen(onScanMySpace: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "SpaceMuse AI")
        Button(onClick = onScanMySpace) {
            Text(text = "Scan My Space")
        }
    }
}
