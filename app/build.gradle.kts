// App module build file. Behaviour/appearance tunables are NOT here; see
// app/src/main/java/com/vislupus/waifuverse/config/*.kt
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.vislupus.waifuverse"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vislupus.waifuverse"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    // Release signing: uses a keystore from environment variables when present
    // (CI / your own key), otherwise the debug keystore so `make release` just works.
    val keystorePath = System.getenv("WAIFUVERSE_KEYSTORE").orEmpty()
    if (keystorePath.isNotEmpty()) {
        signingConfigs.create("release") {
            storeFile = file(keystorePath)
            storePassword = System.getenv("WAIFUVERSE_KEYSTORE_PASSWORD").orEmpty()
            keyAlias = System.getenv("WAIFUVERSE_KEY_ALIAS").orEmpty()
            keyPassword = System.getenv("WAIFUVERSE_KEY_PASSWORD").orEmpty()
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (keystorePath.isNotEmpty()) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
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
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    // Name the APKs WaifuVerse-<version>.apk (release) and WaifuVerse-<version>-debug.apk.
    applicationVariants.all {
        val variant = this
        outputs.all {
            val output = this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl ?: return@all
            val suffix = if (variant.buildType.name == "release") "" else "-${variant.buildType.name}"
            output.outputFileName = "WaifuVerse-${variant.versionName}$suffix.apk"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
