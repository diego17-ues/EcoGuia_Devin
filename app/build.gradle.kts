
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// LEER CONFIGURACION LOCAL
val localProperties = Properties()

val localPropertiesFile = rootProject.file("local.properties")

if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use {
        localProperties.load(it)
    }
}

val supabaseUrl =
    localProperties.getProperty("SUPABASE_URL", "")

val supabasePublishableKey =
    localProperties.getProperty("SUPABASE_PUBLISHABLE_KEY", "")

val mapboxAccessToken =
    localProperties.getProperty("MAPBOX_ACCESS_TOKEN", "")

android {
    namespace = "com.proyecto.ecoguia"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.proyecto.ecoguia"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        resValue("string", "mapbox_access_token", mapboxAccessToken)

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "SUPABASE_URL",
            "\"$supabaseUrl\""
        )

        buildConfigField(
            "String",
            "SUPABASE_PUBLISHABLE_KEY",
            "\"$supabasePublishableKey\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false

            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
}

dependencies {
    //Agregando la dependencia biométrica
    implementation("androidx.biometric:biometric:1.1.0")

    //dependencia para localicación
    implementation("com.google.android.gms:play-services-location:21.3.0")

    implementation("androidx.fragment:fragment-ktx:1.8.9")



    // DEPENDENCIAS ORIGINALES
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.mapbox.maps.android)
    implementation(libs.mapbox.maps.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // SUPABASE
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.realtime)

    // CLIENTE HTTP
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.okhttp)

    // PRUEBAS
    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    androidTestImplementation(
        platform(libs.androidx.compose.bom)
    )

    androidTestImplementation(
        libs.androidx.compose.ui.test.junit4
    )

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(
        libs.androidx.compose.ui.test.manifest
    )
}
