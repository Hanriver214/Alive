plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.alive.alive"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.alive.alive"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "1.3.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // 开源仓库默认用 debug 签名让 assembleRelease 直接产出可装 APK；
            // 想换成自有 release keystore 时，在 CI 里设置以下 gradle.properties 即可覆盖：
            //   alive.signing.keystore / alive.signing.keystorePass
            //   alive.signing.keyAlias   / alive.signing.keyPass
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // 若 gradle.properties 提供了 alive.signing.keystore 等键，则补一个 release
    // signingConfig，并把 release buildType 切到该签名上。
    val aliveKeystore: String? = providers.gradleProperty("alive.signing.keystore").orNull
    if (!aliveKeystore.isNullOrEmpty()) {
        signingConfigs.create("release") {
            storeFile = file(providers.gradleProperty("alive.signing.keystore").get())
            storePassword = providers.gradleProperty("alive.signing.keystorePass").get()
            keyAlias = providers.gradleProperty("alive.signing.keyAlias").get()
            keyPassword = providers.gradleProperty("alive.signing.keyPass").get()
        }
        buildTypes.getByName("release") {
            signingConfig = signingConfigs.getByName("release")
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
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/NOTICE*"
            excludes += "/META-INF/LICENSE*"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.sun.android.mail)
    implementation(libs.sun.android.activation)

    debugImplementation(libs.androidx.ui.tooling)
}
