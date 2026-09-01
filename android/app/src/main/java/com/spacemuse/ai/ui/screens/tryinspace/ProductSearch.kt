package com.spacemuse.ai.ui.screens.tryinspace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.spacemuse.ai.core.model.Product
import com.spacemuse.ai.core.network.ApiClient
import kotlinx.coroutines.launch

// Shared by TryInSpaceScreen (Milestone 4) and ArTryOnScreen (Milestone 5)
// -- both flows start the same way: search a product, then move on to a
// photo/AR step, and both need the same simple back-button top bar.
// Extracted here rather than duplicated so a fix to search behavior
// doesn't need to land in two places.
internal sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Loaded(val results: List<Product>) : SearchState
    data class Error(val message: String) : SearchState
}

@Composable
internal fun ProductSearchStep(onBack: () -> Unit, onProductSelected: (Product) -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var searchState by remember { mutableStateOf<SearchState>(SearchState.Idle) }

    fun search() {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        searchState = SearchState.Loading
        coroutineScope.launch {
            try {
                val response = ApiClient.api.searchProducts(trimmed)
                searchState = if (response.providersConfigured) {
                    SearchState.Loaded(response.results)
                } else {
                    SearchState.Error(
                        "No shopping provider is configured on the backend yet. " +
                            "Add a product API key (e.g. SERPAPI_KEY) to enable search."
                    )
                }
            } catch (error: Exception) {
                searchState = SearchState.Error(error.message ?: "Could not reach the SpaceMuse AI backend.")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TryInSpaceTopBar(title = "Try a product", onBack = onBack)

        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Search for a product to try in your room",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("e.g. sofa, floor lamp") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Button(onClick = ::search) { Text("Search") }
            }
        }

        when (val state = searchState) {
            SearchState.Idle -> Unit

            SearchState.Loading -> Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            is SearchState.Error -> Text(
                text = state.message,
                modifier = Modifier.padding(20.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )

            is SearchState.Loaded ->
                if (state.results.isEmpty()) {
                    Text(
                        text = "No products found for \"$query\".",
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                        items(state.results) { product ->
                            ProductSearchRow(product = product, onClick = { onProductSelected(product) })
                        }
                    }
                }
        }
    }
}

@Composable
internal fun ProductSearchRow(product: Product, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = product.imageUrl,
            contentDescription = null,
            modifier = Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = product.name, style = MaterialTheme.typography.bodyMedium)
            product.brand?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            text = formatPrice(product.priceMinor, product.currency),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

internal fun formatPrice(priceMinor: Int, currency: String): String {
    val major = priceMinor / 100
    return if (currency == "INR") "₹$major" else "$currency $major"
}

@Composable
internal fun TryInSpaceTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onBack,
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.4f)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(text = "←", color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
