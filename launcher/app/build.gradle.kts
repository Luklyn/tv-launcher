import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun prop(name: String, default: String = ""): String = localProps.getProperty(name, default)

android {
    namespace = "com.custom.tvlauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.custom.tvlauncher"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        resourceConfigurations += listOf("fr", "en") // on ne garde que le français et l'anglais des bibliothèques

        buildConfigField("String", "OPENWEATHER_API_KEY", "\"${prop("OPENWEATHER_API_KEY")}\"")
        buildConfigField("String", "UNSPLASH_ACCESS_KEY", "\"${prop("UNSPLASH_ACCESS_KEY")}\"")
        buildConfigField("String", "TMDB_API_KEY", "\"${prop("TMDB_API_KEY")}\"")
        buildConfigField("String", "YOUTUBE_API_KEY", "\"${prop("YOUTUBE_API_KEY")}\"")
        // Options choisies dans l'installeur (local.properties) : afficher RAM/stockage, afficher les suggestions (true par défaut)
        buildConfigField("boolean", "SHOW_SYSTEM_STATS", if (prop("SHOW_SYSTEM_STATS", "true") == "false") "false" else "true")
        buildConfigField("boolean", "SHOW_SUGGESTIONS", if (prop("SHOW_SUGGESTIONS", "true") == "false") "false" else "true")
        buildConfigField("String", "WEATHER_CITY", "\"${prop("WEATHER_CITY", "Sainte-Foy-lès-Lyon")}\"")
        buildConfigField("double", "WEATHER_LAT", prop("WEATHER_LAT", "45.7325"))
        buildConfigField("double", "WEATHER_LON", prop("WEATHER_LON", "4.8032"))
    }

    buildTypes {
        release {
            isMinifyEnabled = true      // R8 : code réduit, optimisé et obfusqué
            isShrinkResources = true    // supprime les ressources inutilisées
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Même clé que le debug : l'APK release remplace directement ta version actuelle (adb install -r)
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/*.version", "DebugProbesKt.bin", "kotlin-tooling-metadata.json")
    }

    lint { checkReleaseBuilds = false }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material:material-icons-core")

    // Compose for TV
    implementation("androidx.tv:tv-foundation:1.0.0-alpha11")
    implementation("androidx.tv:tv-material:1.0.0")

    // Images
    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
