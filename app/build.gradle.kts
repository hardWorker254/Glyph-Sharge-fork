import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
    // No `kapt` plugin: the module has no `kapt(...)` dependency, so it only
    // cost a second annotation-processing pass per compile.
    // KSP stays — Hilt's compiler runs through it.
}

kotlin {
    compilerOptions {
        // Replaces android.kotlinOptions, which AGP 9 deprecates.
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

android {
    namespace = "com.bleelblep.glyphsharge"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bleelblep.glyphsharge"
        minSdk = 34  // Android 14+ only
        targetSdk = 34
        versionCode = 1031
        versionName = "1.0.31"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // The app ships an in-app language switcher (LanguageSettingsScreen applies
    // a locale at runtime). With Play's per-language resource splits on, the
    // bundle would only carry the device language and the switcher would show
    // nothing to switch to.
    bundle {
        language {
            enableSplit = false
        }
    }

    testOptions {
        unitTests {
            // android.util.Log is a stub in JVM unit tests; the glyph layer only
            // uses it for diagnostics, so returning defaults is enough.
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Core Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // collectAsStateWithLifecycle: stops collecting when the screen is not
    // visible, so a stopped screen does not keep recomposing.
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose dependencies
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.window.size)
    implementation(libs.androidx.compose.foundation)

    // Material Components for Android - required for Material 3 themes and TimePicker
    implementation(libs.google.material)

    // Animation dependencies
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.animation.graphics)
    implementation(libs.androidx.compose.animation.core)

    // Navigation with predictive back support
    implementation(libs.androidx.navigation.compose)

    // Hilt dependencies
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Nothing Glyph SDK - specific JAR file
    implementation(files("libs/KetchumSDK_Community_20250319.jar"))

    // Testing dependencies
    testImplementation(libs.junit)
    // Robolectric for local unit tests that need Android framework access
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.compose.material.icons.extended)

    // Lottie for Compose – required for new onboarding animations
    implementation(libs.lottie.compose)

    // LuaJ – the VM that runs user-written custom glyph animations.
    // luaj-jse is the JVM flavour; it pulls in the luaj core automatically.
    implementation(libs.luaj.jse)
}
