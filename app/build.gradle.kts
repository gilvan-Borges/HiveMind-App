plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "dev.hivemind.mobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.hivemind.mobile"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.2.0"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    // Assinatura de release externa: nunca embutida no Git. Lê variáveis de
    // ambiente HIVEMIND_KEYSTORE / HIVEMIND_KEYSTORE_PASSWORD / HIVEMIND_KEY_ALIAS /
    // HIVEMIND_KEY_PASSWORD (ou as gradle properties hivemindKeystore /
    // hivemindKeystorePassword / hivemindKeyAlias / hivemindKeyPassword).
    // Sem keystore configurada, a release sai unsigned — nunca assina com a chave de debug.
    val keystoreFile = providers.gradleProperty("hivemindKeystore")
        .orElse(providers.environmentVariable("HIVEMIND_KEYSTORE"))
        .orNull
    val keystorePassword = providers.gradleProperty("hivemindKeystorePassword")
        .orElse(providers.environmentVariable("HIVEMIND_KEYSTORE_PASSWORD"))
        .orNull
    val keyAlias = providers.gradleProperty("hivemindKeyAlias")
        .orElse(providers.environmentVariable("HIVEMIND_KEY_ALIAS"))
        .orNull
    val keyPassword = providers.gradleProperty("hivemindKeyPassword")
        .orElse(providers.environmentVariable("HIVEMIND_KEY_PASSWORD"))
        .orNull

    val hasExternalKeystore = !keystoreFile.isNullOrBlank() &&
        !keystorePassword.isNullOrBlank() &&
        !keyAlias.isNullOrBlank()

    signingConfigs {
        if (hasExternalKeystore) {
            create("release") {
                val store = rootProject.layout.projectDirectory.file(keystoreFile!!)
                if (!store.asFile.exists()) {
                    throw GradleException("HIVEMIND_KEYSTORE aponta para '${keystoreFile}' e o arquivo não existe")
                }
                storeFile = store.asFile
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword ?: keystorePassword
            }
        }
    }

    buildTypes {
        release {
            // R8 + shrink de recursos (Spec 10): ofusca, corta e valida com lint.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasExternalKeystore) {
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
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
            excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.05.01"))
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("androidx.datastore:datastore-preferences:1.1.4")
    implementation("androidx.exifinterface:exifinterface:1.4.1")

    // Cache offline Room/SQLite (Spec 10): projetos, lanes, mensagens e diffs.
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")

    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.7.0")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
