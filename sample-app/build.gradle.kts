plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Apply the Google Services plugin only when a google-services.json has been
// added. This lets the sample build out-of-the-box; once you drop in your own
// google-services.json (from the Firebase project that owns this widget's
// service account) FCM push wiring is activated automatically.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.techindika.liveconnect.sample"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.techindika.liveconnect.sample"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    implementation(project(":liveconnect-chat"))
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")

    // Firebase Cloud Messaging — the consumer app owns Firebase; the widget
    // library only ships the token to the backend. See NOTIFICATIONS_03_KOTLIN_FIX.md.
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging")
}
