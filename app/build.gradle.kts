import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")

    if (propertiesFile.exists()) {
        propertiesFile.inputStream().use { load(it) }
    }
}

// Convierte las propiedades en cadenas válidas para BuildConfig.
fun buildConfigString(value: String): String =
    "\"" + value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r", "\\r")
        .replace("\n", "\\n") + "\""

android {
    namespace = "com.timevault.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.timevault.app"
        minSdk = 24
        targetSdk = 36

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField(
            "String",
            "SUPABASE_URL",
            buildConfigString(
                localProperties.getProperty("SUPABASE_URL", "")
            )
        )

        buildConfigField(
            "String",
            "SUPABASE_ANON_KEY",
            buildConfigString(
                localProperties.getProperty("SUPABASE_ANON_KEY", "")
            )
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Jetpack Compose
    implementation(
        platform("androidx.compose:compose-bom:2024.02.00")
    )
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Supabase
    implementation(
        platform("io.github.jan-tennert.supabase:bom:3.1.1")
    )
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")

    // Motor HTTP
    implementation("io.ktor:ktor-client-okhttp:3.0.3")

    // Pruebas
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation(
        "androidx.test.espresso:espresso-core:3.5.1"
    )
    androidTestImplementation(
        platform("androidx.compose:compose-bom:2024.02.00")
    )
    androidTestImplementation(
        "androidx.compose.ui:ui-test-junit4"
    )

    // Herramientas de desarrollo
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")


}