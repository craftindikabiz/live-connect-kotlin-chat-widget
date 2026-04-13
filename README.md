# LiveConnect Kotlin Chat Widget

A production-ready Android chat widget for real-time customer support with ticket-based conversations, push notifications, and persistent sessions.

This package is designed for internal/private use, enabling seamless integration of live chat functionality into Kotlin Android applications.

---

## Table of Contents

1. [Features](#features)
2. [Requirements](#requirements)
3. [Installation](#installation)
4. [Package Initialization](#package-initialization)
5. [Adding the Chat Button (FAB)](#adding-the-chat-button-fab)
6. [Opening Chat Programmatically](#opening-chat-programmatically)
7. [Theme Customization](#theme-customization)
8. [Firebase Project Setup](#firebase-project-setup)
9. [Push Notification Configuration](#push-notification-configuration)
10. [Full MainActivity.kt Example](#full-mainactivitykt-example)
11. [How It Works](#how-it-works)
12. [API Reference](#api-reference)
13. [Security Guidelines](#security-guidelines)
14. [Platform Support](#platform-support)
15. [Troubleshooting](#troubleshooting)
16. [License](#license)

---

## Features

- Real-time chat messaging using Socket.IO
- Ticket-based conversation system
- Session persistence using SharedPreferences
- Push notifications via Firebase Cloud Messaging (FCM)
- Optional file attachments (images and documents)
- Theme customization (100+ properties)
- Unread message badge
- Conversation history (Activity tab)
- Lightweight and efficient

---

## Requirements

| Requirement       | Minimum Version         |
|-------------------|-------------------------|
| Android API Level | 21+ (Lollipop)          |
| Kotlin            | 1.9+                    |
| Gradle            | 8.0+                    |
| Java              | 17+                     |
| Firebase Project  | Required for FCM        |

---

## Installation

### Step 1 — Add JitPack Repository

Since the package is hosted on GitHub and not published to Maven Central, add JitPack as a repository in your `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### Step 2 — Add Dependency

In your app-level `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.craftindikabiz:live-connect-kotlin-chat-widget:v1.0.0")
    implementation("com.google.firebase:firebase-messaging:23.4.0")
}
```

### Step 3 — Sync

```bash
./gradlew sync
```

> **Warning — JitPack build cache.** JitPack caches built artifacts per commit SHA. If a build was already triggered on an older commit of a tag that has since been moved, subsequent installs may receive a stale artifact. To force a rebuild, open the version page in a browser and click **Get it** on the affected version:
>
> ```
> https://jitpack.io/#craftindikabiz/live-connect-kotlin-chat-widget/v1.0.0
> ```
>
> JitPack will re-resolve the tag to the latest SHA and rebuild. If you need to switch to a new commit without moving the tag, pin to a fresh version instead.

---

## Package Initialization

Initialize the package in your `MainActivity.kt` or `Application` class. This is mandatory. The widget will not function correctly if initialization is deferred.

```kotlin
import com.techindika.liveconnect.LiveConnectChat
import com.techindika.liveconnect.model.VisitorProfile

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        LiveConnectChat.init(
            context = this,
            widgetKey = "your-widget-key",
            visitorDetails = VisitorProfile(
                name = "John Doe",
                email = "john@example.com",
                phone = "+14155552671"
            )
        )

        setContentView(R.layout.activity_main)
    }
}
```

**Parameters:**

| Parameter        | Type               | Required | Description                                      |
|------------------|--------------------|----------|--------------------------------------------------|
| `context`        | `Context`          | Yes      | Application or Activity context                  |
| `widgetKey`      | `String`           | Yes      | Unique key provided by the LiveConnect dashboard |
| `visitorDetails` | `VisitorProfile`   | No       | Identifies the end user in chat sessions         |
| `theme`          | `LiveConnectTheme` | No       | Overrides the default visual theme               |

**VisitorProfile fields:**

| Field   | Type     | Required | Description                             |
|---------|----------|----------|-----------------------------------------|
| `name`  | `String` | Yes      | Full name of the visitor                |
| `email` | `String` | Yes      | Email address                           |
| `phone` | `String` | No       | Phone number (E.164 format recommended) |

---

## Adding the Chat Button (FAB)

Use `FloatingChatButton` in your layout XML. The button automatically opens the chat screen on tap and shows an unread message badge.

```xml
<com.techindika.liveconnect.ui.view.FloatingChatButton
    android:id="@+id/chatFab"
    android:layout_width="56dp"
    android:layout_height="56dp"
    android:layout_gravity="bottom|end"
    android:layout_margin="16dp" />
```

No additional Kotlin code is needed. The FAB handles click-to-open and unread badge automatically.

---

## Opening Chat Programmatically

To open the chat screen directly from any widget or user interaction, call:

```kotlin
LiveConnectChat.show(context)
```

This method can be used independently without the floating action button (FAB). It is useful for triggering the chat from a custom button, a menu item, a notification tap, or any other event in your application.

### Example: Opening Chat from a Custom Button

```kotlin
findViewById<Button>(R.id.supportButton).setOnClickListener {
    LiveConnectChat.show(this)
}
```

### Example: Opening Chat from a Navigation Drawer

```kotlin
navigationView.setNavigationItemSelectedListener { menuItem ->
    when (menuItem.itemId) {
        R.id.nav_chat -> {
            drawerLayout.closeDrawers()
            LiveConnectChat.show(this)
            true
        }
        else -> false
    }
}
```

### Example: Using FAB and Programmatic Show Together

Both the floating action button and `LiveConnectChat.show()` can coexist in your application. Use the FAB for permanent access and `LiveConnectChat.show()` for contextual triggers.

```kotlin
class MyApp : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Open chat from toolbar help button
        findViewById<ImageButton>(R.id.helpButton).setOnClickListener {
            LiveConnectChat.show(this)
        }

        // FloatingChatButton in layout handles itself automatically
    }
}
```

---

## Theme Customization

A custom theme can be applied at initialization time using the `theme` parameter of `LiveConnectChat.init()`.

### Quick Setup (Single Color)

```kotlin
LiveConnectChat.init(
    context = this,
    widgetKey = "your-widget-key",
    visitorDetails = VisitorProfile(
        name = "John Doe",
        email = "john@example.com",
        phone = "+14155552671"
    ),
    theme = LiveConnectTheme.fromPrimary(Color.parseColor("#4F46E5"))
)
```

### Detailed Customization

```kotlin
val theme = LiveConnectTheme.builder()
    .primaryColor(Color.BLUE)
    .headerBackgroundColor(Color.WHITE)
    .headerTitle("Support Chat")
    .headerTitleColor(Color.DKGRAY)
    .visitorBubbleColor(Color.BLUE)
    .agentBubbleColor(Color.LTGRAY)
    .build()

LiveConnectChat.init(
    context = this,
    widgetKey = "your-widget-key",
    theme = theme
)
```

The `LiveConnectTheme` class accepts 100+ visual properties including colors, font sizes, border radii, and spacing. The `fromPrimary()` factory auto-derives all sub-colors from a single brand color.

---

## Firebase Project Setup

Push notifications require a configured Firebase project. Follow the steps below carefully.

### Step 1 — Create or Select a Firebase Project

1. Open the Firebase Console: https://console.firebase.google.com
2. Create a new project or open an existing one.
3. Navigate to **Project Settings** and ensure **Cloud Messaging** is enabled under the **Cloud Messaging** tab.

### Step 2 — Add Your Android App to Firebase

1. In the Firebase Console, click **Add app** and select Android.
2. Enter your app's package name (found in `build.gradle.kts` under `applicationId`).
3. Download the generated `google-services.json` file.
4. Place the file at:

```
app/google-services.json
```

5. In your root `build.gradle.kts`, add the Google services plugin:

```kotlin
plugins {
    id("com.google.gms.google-services") version "4.4.0" apply false
}
```

6. In your app-level `build.gradle.kts`, apply the plugin:

```kotlin
plugins {
    id("com.google.gms.google-services")
}
```

### Step 3 — Add Platform Permissions

Add the following to `AndroidManifest.xml` inside the `<manifest>` tag:

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

---

## Push Notification Configuration

### Step 4 — Download and Add the Firebase Service Account

1. In the Firebase Console, go to **Project Settings** → **Service Accounts**.
2. Click **Generate New Private Key**.
3. Save the downloaded file as `firebase_service_account.json`.
4. Place the file at:

```
app/src/main/assets/firebase_service_account.json
```

5. Add the file to `.gitignore`:

```
# Firebase Service Account — do not commit
**/assets/firebase_service_account.json
```

**IMPORTANT SECURITY NOTE:** Never hardcode private keys or service account credentials directly in your code. Always load from a secure file in assets.

### Step 5 — Load the Service Account and Register the FCM Token

The order of operations is critical — `setFirebaseServiceAccount()` must be called before `setFcmToken()`.

```kotlin
// Load Firebase service account from assets
val serviceAccountJson = assets.open("firebase_service_account.json")
    .bufferedReader().use { it.readText() }
val serviceAccount = JSONObject(serviceAccountJson).toMap()

// Set the service account (required before setting the FCM token)
LiveConnectChat.setFirebaseServiceAccount(serviceAccount)

// Get and register the FCM device token
FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
    LiveConnectChat.setFcmToken(token)
}
```

### Step 6 — Request Notification Permission (Android 13+)

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
        != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001
        )
    }
}
```

---

## Full MainActivity.kt Example

The following is a complete, production-oriented `MainActivity.kt` that combines all steps:

```kotlin
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.techindika.liveconnect.LiveConnectChat
import com.techindika.liveconnect.LiveConnectTheme
import com.techindika.liveconnect.model.VisitorProfile
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val serviceAccountJson = assets.open("firebase_service_account.json")
            .bufferedReader().use { it.readText() }
        val serviceAccount = JSONObject(serviceAccountJson).toMap()

        LiveConnectChat.init(
            context = this,
            widgetKey = "your-widget-key",
            visitorDetails = VisitorProfile(
                name = "John Doe",
                email = "john@example.com",
                phone = "+14155552671"
            ),
            theme = LiveConnectTheme.fromPrimary(Color.parseColor("#4F46E5"))
        )

        LiveConnectChat.setFirebaseServiceAccount(serviceAccount)

        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            LiveConnectChat.setFcmToken(token)
        }

        requestNotificationPermission()

        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.supportButton).setOnClickListener {
            LiveConnectChat.show(this)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001
                )
            }
        }
    }
}
```

---

## How It Works

The widget uses a ticket-based conversation system:

1. When a user sends their first message, a ticket is created on the server
2. The active ticket ID is stored locally using SharedPreferences
3. On app restart, the previous ticket is automatically resumed
4. Socket.IO maintains a persistent connection for real-time messaging
5. FCM tokens receive push notifications even when the app is in the background

---

## API Reference

### Methods

| Method                                        | Description                                                                          | Parameters                                              |
|-----------------------------------------------|--------------------------------------------------------------------------------------|---------------------------------------------------------|
| `LiveConnectChat.init()`                      | Initializes the chat widget. Must be called before `show()`.                         | `context`, `widgetKey`, `visitorDetails`, `theme`       |
| `LiveConnectChat.show()`                      | Opens the chat screen programmatically.                                              | `context`                                               |
| `LiveConnectChat.setFirebaseServiceAccount()` | Passes Firebase service account credentials. Must be called before `setFcmToken()`. | `serviceAccount` (Map<String, Any>)                     |
| `LiveConnectChat.setFcmToken()`               | Registers the device FCM token for push notifications.                               | `fcmToken` (String)                                     |
| `LiveConnectChat.setTheme()`                  | Overrides the theme at runtime.                                                      | `theme` (LiveConnectTheme)                              |

### Widgets

| Widget                      | Description                                                                |
|-----------------------------|----------------------------------------------------------------------------|
| `FloatingChatButton`        | A pre-built floating action button that opens the chat screen on tap.      |

### VisitorProfile

```kotlin
VisitorProfile(
    name = "John Doe",           // Required
    email = "john@example.com",  // Required
    phone = "+14155552671"       // Optional
)
```

---

## Security Guidelines

The Firebase service account key grants privileged access to your Firebase project. Handle it with the same care as a production password.

- Load the service account exclusively from `assets/firebase_service_account.json`, never inline it as a string in code.
- Add `firebase_service_account.json` to `.gitignore` before the first commit.
- Never share the service account file via email, Slack, or public channels.
- Do not log or print any fields from the service account object.
- Rotate the service account key if you suspect it has been exposed.

---

## Platform Support

| Platform          | Supported |
|-------------------|-----------|
| Android (API 21+) | Yes       |

---

## Troubleshooting

### Chat Widget Not Showing

1. Confirm `LiveConnectChat.init()` is called before `show()`
2. Verify the `widgetKey` is valid and matches the one in your LiveConnect dashboard
3. Ensure the device has an active internet connection
4. Check that `INTERNET` permission is declared in `AndroidManifest.xml`

### FCM Token Not Available

1. Confirm `google-services.json` is placed in `app/`
2. Test on a physical device — FCM tokens are unreliable on emulators
3. Ensure the app has internet access

### Notifications Not Received

1. Confirm `setFirebaseServiceAccount()` is called before `setFcmToken()`
2. Confirm `setFcmToken()` is called with a non-null, valid token
3. Verify Cloud Messaging is enabled in the Firebase project settings
4. On Android 13+, confirm the notification runtime permission has been granted
5. Verify the Firebase service account has the Firebase Cloud Messaging Admin role in Google Cloud IAM
6. Confirm the device has an active internet connection

### Messages Not Appearing in Chat

1. Check internet connectivity
2. Verify the Socket.IO connection is active (review logcat for `LiveConnect` tag)
3. Confirm the LiveConnect backend server is reachable
4. Review logcat output for any error messages from the package

---

## License

This package is private and intended for internal use only.

**Status:** Production Ready
**Version:** 1.0.0
**Last Updated:** April 10, 2026
