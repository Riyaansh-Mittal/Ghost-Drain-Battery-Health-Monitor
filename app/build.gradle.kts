plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
    id 'kotlin-kapt'
    id 'com.google.gms.google-services'       // playstore flavor only — see note below
    id 'com.google.firebase.crashlytics'       // playstore flavor only
}

android {
    namespace 'com.ghost.drain.battery.health.monitor'
    compileSdk 35

    defaultConfig {
        applicationId "com.ghost.drain.battery.health.monitor"
        minSdk 26
        targetSdk 35
        versionCode 1
        versionName "1.0"
    }

    // ─── PRODUCT FLAVORS ─────────────────────────────────────────────────
    flavorDimensions "distribution"

    productFlavors {

        // Google Play + Aptoide + APKPure + Uptodown + Samsung + Xiaomi + OPPO + Vivo
        playstore {
            dimension "distribution"
            buildConfigField "boolean", "INCLUDE_ADS", "true"
            buildConfigField "boolean", "INCLUDE_FIREBASE", "true"
            buildConfigField "boolean", "INCLUDE_APPLOVIN", "true"
        }

        // F-Droid + IzzyOnDroid + GitHub Releases (Obtainium)
        // Create app/src/fdroid/AndroidManifest.xml to strip INTERNET permission
        fdroid {
            dimension "distribution"
            buildConfigField "boolean", "INCLUDE_ADS", "false"
            buildConfigField "boolean", "INCLUDE_FIREBASE", "false"
            buildConfigField "boolean", "INCLUDE_APPLOVIN", "false"
        }

        // Huawei AppGallery — HMS Ads Kit added in month 2
        huawei {
            dimension "distribution"
            buildConfigField "boolean", "INCLUDE_ADS", "false"
            buildConfigField "boolean", "INCLUDE_FIREBASE", "false"
            buildConfigField "boolean", "INCLUDE_APPLOVIN", "false"
        }
    }

    buildTypes {
        release {
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
            // Set up your keystore in Android Studio and uncomment:
            // signingConfig signingConfigs.release
        }
        debug {
            applicationIdSuffix ".debug"
        }
    }

    buildFeatures {
        viewBinding true
        buildConfig true
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = '17' }
}

dependencies {
    // ─── Core AndroidX ───────────────────────────────────────────────────
    implementation 'androidx.core:core-ktx:1.13.1'
    implementation 'androidx.appcompat:appcompat:1.7.0'
    implementation 'com.google.android.material:material:1.12.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
    implementation 'androidx.lifecycle:lifecycle-service:2.8.4'
    implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4'
    implementation 'androidx.work:work-runtime-ktx:2.9.1'

    // ─── Room — session history database ─────────────────────────────────
    implementation 'androidx.room:room-runtime:2.6.1'
    implementation 'androidx.room:room-ktx:2.6.1'
    kapt 'androidx.room:room-compiler:2.6.1'

    // ─── Charts — health screen 7-session trend graph ────────────────────
    implementation 'com.github.PhilJay:MPAndroidChart:v3.1.0'

    // ─── AppLovin MAX — mediation wrapper (playstore flavor only) ────────
    // MAX runs AdMob + Meta + Unity as real-time bidders.
    // If AdMob places an ad serving limit during a viral spike,
    // MAX automatically routes requests to Meta/Unity. Revenue continues.
    playstoreImplementation 'com.applovin:applovin-sdk:13.1.0'

    // AdMob adapter inside MAX (NOT the standalone AdMob SDK)
    playstoreImplementation 'com.applovin.mediation:google-adapter:23.2.0.0'

    // Meta Audience Network adapter inside MAX (optional but recommended)
    playstoreImplementation 'com.applovin.mediation:facebook-adapter:6.18.0.0'

    // ─── Firebase (playstore flavor only) ────────────────────────────────
    playstoreImplementation platform('com.google.firebase:firebase-bom:33.1.2')
    playstoreImplementation 'com.google.firebase:firebase-crashlytics-ktx'
    playstoreImplementation 'com.google.firebase:firebase-analytics-ktx'
    // Remote Config — master kill switch for ads during viral spikes
    playstoreImplementation 'com.google.firebase:firebase-config-ktx'
}