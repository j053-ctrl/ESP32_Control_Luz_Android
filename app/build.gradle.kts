
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.esp32_control_luz"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.esp32_control_luz"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11

        // Compatibilidad con AWS Amplify
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {

    // Librerías originales de Android
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    // Amazon Cognito - Inicio de sesión
    implementation(
        "com.amplifyframework:aws-auth-cognito:2.30.4"
    )

    // Compatibilidad con las librerías AWS
    coreLibraryDesugaring(
        "com.android.tools:desugar_jdk_libs:2.1.5"
    )

    // Librerías para pruebas
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
