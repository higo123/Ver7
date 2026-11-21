plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Release signing is supplied only by CI/local environment variables.
// Never commit a keystore or its passwords to source control.
val releaseStoreFile = providers.environmentVariable("PVC_RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("PVC_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("PVC_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("PVC_RELEASE_KEY_PASSWORD").orNull
val hasReleaseSigning = listOf(
    releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "vn.pvc.hub"
    compileSdk = 35

    defaultConfig {
        applicationId = "vn.pvc.hub"
        minSdk = 28          // Android 9 trở lên
        targetSdk = 34
        versionCode = 18
        versionName = "0.11.0"
    }

    if (hasReleaseSigning) {
        signingConfigs.create("release") {
            storeFile = file(requireNotNull(releaseStoreFile))
            storePassword = requireNotNull(releaseStorePassword)
            keyAlias = requireNotNull(releaseKeyAlias)
            keyPassword = requireNotNull(releaseKeyPassword)
        }
    }

    buildTypes {
        debug {
            // Use Android's standard debug key; never use the production key.
        }
        release {
            isMinifyEnabled = false
            // Without signing secrets Gradle produces an unsigned release APK.
            // CI must not publish it as an installable release.
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/DEPENDENCIES",
                "/META-INF/{AL2.0,LGPL2.1}"
            )
            pickFirsts += setOf("META-INF/mailcap", "META-INF/mimetypes.default")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.13.1")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.security:security-crypto:1.0.0")
    implementation("androidx.biometric:biometric:1.1.0")

    // IMAP cho Gmail
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")
}
