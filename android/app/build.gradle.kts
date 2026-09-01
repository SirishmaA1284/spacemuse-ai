plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.spacemuse.ai"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.spacemuse.ai"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":ai"))
    implementation(project(":camera"))

    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("com.google.dagger:hilt-android:2.51.1")
    kapt("com.google.dagger:hilt-compiler:2.51.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // CameraScreen references ImageCapture/ImageProxy directly to trigger a
    // real capture — the :camera module declares camera-core as
    // implementation (not api), so it isn't on this module's compile
    // classpath transitively; version must match android/camera/build.gradle.kts.
    implementation("androidx.camera:camera-core:1.3.4")

    // ArScanScreen references TrackingState/TrackingFailureReason directly
    // to render live AR status text — same transitive-visibility issue as
    // camera-core above (:camera declares com.google.ar:core as
    // implementation, not api); version must match android/camera/build.gradle.kts.
    implementation("com.google.ar:core:1.54.0")

    // TryInSpaceScreen (Milestone 4 of Phase 7 — product image overlay) loads
    // a product photo from its retailer imageUrl. Coil is the standard
    // Compose-native image loader (AsyncImage) — deliberately not a
    // hand-rolled OkHttp+BitmapFactory loader, since correct threading/
    // caching/error-state handling for network images is a solved problem
    // not worth re-deriving blind (no local build/run in this environment).
    implementation("io.coil-kt:coil-compose:2.6.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
