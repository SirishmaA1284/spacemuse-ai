package com.spacemuse.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.spacemuse.ai.ui.navigation.SpaceMuseNavGraph
import com.spacemuse.ai.ui.theme.SpaceMuseTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SpaceMuseTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SpaceMuseNavGraph()
                }
            }
        }
    }
}
