package com.spacemuse.ai.camera

import android.app.Activity
import android.content.Context
import com.google.ar.core.ArCoreApk

// Wraps ArCoreApk's availability check into a small result type the UI can
// branch on directly, so a device without ARCore / Google Play Services for
// AR gets a graceful fallback message instead of a crash — see
// docs/architecture/spatial-architecture.md (Phase 7).
sealed interface ArAvailabilityResult {
    data object Supported : ArAvailabilityResult
    data object NeedsInstall : ArAvailabilityResult
    data object NeedsUpdate : ArAvailabilityResult
    data object Unsupported : ArAvailabilityResult
}

object ArAvailability {
    fun check(context: Context): ArAvailabilityResult =
        when (ArCoreApk.getInstance().checkAvailability(context)) {
            ArCoreApk.Availability.SUPPORTED_INSTALLED -> ArAvailabilityResult.Supported
            ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD -> ArAvailabilityResult.NeedsUpdate
            ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> ArAvailabilityResult.NeedsInstall
            else -> ArAvailabilityResult.Unsupported
        }

    // Launches the Play Store install/update flow for Google Play Services
    // for AR. Caller should re-run check() when the activity next resumes.
    fun requestInstall(activity: Activity): Boolean =
        runCatching {
            ArCoreApk.getInstance().requestInstall(activity, /* userRequestedInstall = */ true)
        }.isSuccess
}
