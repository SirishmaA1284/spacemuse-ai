plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.spacemuse.ai.ai"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core"))
    // MediaPipe LLM Inference (Gemma on-device) — see
    // docs/architecture/gemma.md and ADR-002. Not yet added: the model
    // loading/inference implementation isn't written yet, so pulling in the
    // dependency ahead of real usage was deliberately deferred rather than
    // adding an unused import.
    // implementation("com.google.mediapipe:tasks-genai:<version>")
}
