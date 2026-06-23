plugins {
    id("com.android.application") version "8.6.1" apply false
    id("com.android.library") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    // For FCM push in the sample app. Applied conditionally in :sample-app
    // (only when a google-services.json is present) so the build never breaks.
    id("com.google.gms.google-services") version "4.4.2" apply false
}
