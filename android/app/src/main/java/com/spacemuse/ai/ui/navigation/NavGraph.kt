package com.spacemuse.ai.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.spacemuse.ai.ui.screens.arscan.ArScanScreen
import com.spacemuse.ai.ui.screens.camera.CameraScreen
import com.spacemuse.ai.ui.screens.home.HomeScreen
import com.spacemuse.ai.ui.screens.settings.SettingsScreen
import com.spacemuse.ai.ui.screens.tryinspace.ArTryOnScreen
import com.spacemuse.ai.ui.screens.tryinspace.TryInSpaceScreen

// Only Home, Camera, Settings, ArScan, TryInSpace, and ArTryOn are wired in
// this pass — see docs/architecture/mobile-architecture.md for the full
// planned screen set (Room Analysis, Design Studio, Budget, etc.), which
// depends on backend agents that aren't implemented yet.
sealed class Destination(val route: String) {
    data object Home : Destination("home")
    data object Camera : Destination("camera")
    data object ArScan : Destination("ar_scan")
    data object TryInSpace : Destination("try_in_space")
    data object ArTryOn : Destination("ar_try_on")
    data object Settings : Destination("settings")
}

@Composable
fun SpaceMuseNavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Destination.Home.route) {
        composable(Destination.Home.route) {
            HomeScreen(
                onScanMySpace = { navController.navigate(Destination.Camera.route) },
                onScanRoomAr = { navController.navigate(Destination.ArScan.route) },
                onTryInSpace = { navController.navigate(Destination.TryInSpace.route) },
                onArTryOn = { navController.navigate(Destination.ArTryOn.route) },
                onOpenSettings = { navController.navigate(Destination.Settings.route) }
            )
        }
        composable(Destination.Camera.route) {
            CameraScreen(onBack = { navController.popBackStack() })
        }
        composable(Destination.ArScan.route) {
            ArScanScreen(onBack = { navController.popBackStack() })
        }
        composable(Destination.TryInSpace.route) {
            TryInSpaceScreen(onBack = { navController.popBackStack() })
        }
        composable(Destination.ArTryOn.route) {
            ArTryOnScreen(onBack = { navController.popBackStack() })
        }
        composable(Destination.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
