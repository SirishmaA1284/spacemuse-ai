package com.spacemuse.ai.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.spacemuse.ai.ui.screens.camera.CameraScreen
import com.spacemuse.ai.ui.screens.home.HomeScreen

// Only Home and Camera are wired in this pass — see
// docs/architecture/mobile-architecture.md for the full planned screen set
// (Room Analysis, Design Studio, Product Search, Budget, etc.), which
// depends on backend agents that aren't implemented yet.
sealed class Destination(val route: String) {
    data object Home : Destination("home")
    data object Camera : Destination("camera")
}

@Composable
fun SpaceMuseNavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Destination.Home.route) {
        composable(Destination.Home.route) {
            HomeScreen(onScanMySpace = { navController.navigate(Destination.Camera.route) })
        }
        composable(Destination.Camera.route) {
            CameraScreen(onBack = { navController.popBackStack() })
        }
    }
}
