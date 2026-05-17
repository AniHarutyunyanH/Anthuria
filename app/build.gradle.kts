import java.util.Properties

plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
}

val apiKeysFile = rootProject.file("api-keys.properties")
val apiKeys = Properties()
if (apiKeysFile.exists()) {
    apiKeysFile.inputStream().use { apiKeys.load(it) }
}

fun apiProp(key: String, default: String = ""): String =
    apiKeys.getProperty(key, default)?.trim().orEmpty()

fun apiPropEscaped(key: String, default: String = ""): String =
    apiProp(key, default)
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")

android {
    namespace = "com.inania.Anthuria"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.inania.Anthuria"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "OPENROUTER_API_KEY", "\"${apiPropEscaped("OPENROUTER_API_KEY")}\"")
        buildConfigField(
            "String",
            "OPENROUTER_API_URL",
            "\"${apiPropEscaped("OPENROUTER_API_URL", "https://openrouter.ai/api/v1/chat/completions")}\""
        )
        buildConfigField("String", "TRIPO_API_KEY", "\"${apiPropEscaped("TRIPO_API_KEY")}\"")
        buildConfigField(
            "String",
            "TRIPO_BASE_URL",
            "\"${apiPropEscaped("TRIPO_BASE_URL", "https://api.tripo3d.ai/")}\""
        )
        buildConfigField("String", "NANO_BANANA_API_KEY", "\"${apiPropEscaped("NANO_BANANA_API_KEY")}\"")
        buildConfigField(
            "String",
            "NANO_BANANA_BASE_URL",
            "\"${apiPropEscaped("NANO_BANANA_BASE_URL", "https://api.nanobanana.ai")}\""
        )
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
        // Enable support for modern Java APIs
        isCoreLibraryDesugaringEnabled = true

        sourceCompatibility = JavaVersion.VERSION_17 // Recommended for AGP 8.x
        targetCompatibility = JavaVersion.VERSION_17
    }

    // If using Kotlin, also update this:
//    kotlinOptions {
//        jvmTarget = "17"
//    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:34.9.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("org.opencv:opencv:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.9.0")
    implementation("com.gorisse.thomas.sceneform:sceneform:1.23.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.firebase.auth)
    implementation(libs.gridlayout)
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}