import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Read AdMob IDs from local.properties (gitignored — safe for secrets)
val localProps = Properties().also { props ->
    rootProject.file("local.properties")
        .takeIf { it.exists() }
        ?.inputStream()
        ?.use { props.load(it) }
}
val admobAppId: String = localProps.getProperty(
    "ADMOB_APP_ID",
    "ca-app-pub-3940256099942544~3347511713"  // Google test App ID
)
val admobInterstitialId: String = localProps.getProperty(
    "ADMOB_INTERSTITIAL_ID",
    "ca-app-pub-3940256099942544/1033173712"  // Google test interstitial ID
)

android {
    namespace = "com.shifttac"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.shifttac"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Inject App ID into AndroidManifest.xml
        manifestPlaceholders["admobAppId"] = admobAppId

        // Inject ad unit ID into generated BuildConfig class
        buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"$admobInterstitialId\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.13"
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")

    // AdMob — interstitial ads between games
    implementation("com.google.android.gms:play-services-ads:23.3.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
