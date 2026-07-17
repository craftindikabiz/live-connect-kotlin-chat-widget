# LiveConnect Kotlin Chat Widget

Real-time customer support chat for Android — a ready-to-use `Activity`, floating action button, ticket-based sessions, and optional Firebase Cloud Messaging push. Idiomatic Kotlin API.

---

## What you get

- Full-screen chat `Activity` with customisable theme
- `FloatingChatButton` view with unread badge
- Persistent visitor sessions (ticket-based conversations)
- Image + file attachments
- Optional Firebase Cloud Messaging push notifications

---

## Requirements

| | Minimum |
|---|---|
| Android `minSdk` | 21 (Lollipop) |
| Android `compileSdk` | 34 |
| JDK | 17 |
| Kotlin | 1.9.22 |
| Android Gradle Plugin | 8.2.2 |

---

## Install

### 1. Add JitPack to your `settings.gradle.kts`

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### 2. Add the dependency

In your **app-module** `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.craftindikabiz:live-connect-kotlin-chat-widget:v1.0.15")
}
```

### 3. Enable core library desugaring (**required**)

Still in the app-module `build.gradle.kts`:

```kotlin
android {
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true   // <-- required
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    implementation("com.github.craftindikabiz:live-connect-kotlin-chat-widget:v1.0.15")
}
```

> **Why?** The library uses `ThreadLocal.withInitial` and other Java 8+ APIs that need desugaring on `minSdk < 26`. Without this your app will crash on API 21-25 devices.

---

## Quick start

`MainActivity.kt`:

```kotlin
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.techindika.liveconnect.LiveConnectChat
import com.techindika.liveconnect.model.VisitorProfile

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Initialise once — typically in MainActivity or Application.
        LiveConnectChat.init(
            context = this,
            widgetKey = "your-widget-key",
            visitorDetails = VisitorProfile("John Doe", "john@example.com")
        )

        // 2. Open the chat on any click.
        findViewById<Button>(R.id.openChatButton).setOnClickListener {
            LiveConnectChat.show(this)
        }
    }
}
```

That's enough to get a working chat. Details below are opt-in.

---

## Adding a floating chat button

Drop this into any layout — it opens the chat on tap and shows an unread badge automatically:

```xml
<com.techindika.liveconnect.ui.view.FloatingChatButton
    android:id="@+id/chatFab"
    android:layout_width="56dp"
    android:layout_height="56dp"
    android:layout_gravity="bottom|end"
    android:layout_margin="16dp" />
```

No code required beyond `LiveConnectChat.init(...)` in `onCreate`.

---

## Theming

Quick: single-colour theme auto-generates the full palette. Pass it as the `theme` argument to `init`:

```kotlin
LiveConnectChat.init(
    context = this,
    widgetKey = "your-widget-key",
    visitorDetails = VisitorProfile("John Doe", "john@example.com"),
    theme = LiveConnectTheme.fromPrimary(Color.parseColor("#4F46E5")),
)
```

Detailed: use the builder for fine-grained control.

```kotlin
val theme = LiveConnectTheme.builder()
    .primaryColor(Color.parseColor("#4F46E5"))
    .headerTitle("Support")
    .headerBackgroundColor(Color.WHITE)
    .visitorBubbleColor(Color.parseColor("#4F46E5"))
    .agentBubbleColor(Color.parseColor("#F3F4F6"))
    .build()
```

---

## Push notifications (optional)

Chat works without push. Notifications are only needed for background delivery.

**Prerequisite:** a Firebase project with Cloud Messaging enabled, the `google-services.json` file in your `app/` directory, and the `com.google.gms.google-services` plugin applied. See the [Firebase Android setup guide](https://firebase.google.com/docs/android/setup) if you don't have one yet.

Once Firebase is wired up:

```kotlin
// 1. Load the service account JSON from assets (keep it gitignored).
val json = assets.open("firebase_service_account.json").bufferedReader().use { it.readText() }
val serviceAccount: Map<String, Any> = JSONObject(json).toMap()

// 2. Register with the library — service account FIRST, token SECOND.
LiveConnectChat.setFirebaseServiceAccount(serviceAccount)

FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
    LiveConnectChat.setFcmToken(token)
}
```

Add the permission to `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

Request it at runtime on Android 13+:

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
        != PackageManager.PERMISSION_GRANTED) {
    ActivityCompat.requestPermissions(
        this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
}
```

> **Important:** `setFirebaseServiceAccount()` must be called **before** `setFcmToken()`. Never commit `firebase_service_account.json` to version control.

---

## Opening chat from a notification tap

`show(context)` expects an Activity context. To open the chat screen from a `FirebaseMessagingService`, a `BroadcastReceiver`, or a notification tap that cold-starts the app after it was fully closed, use `showFromNotification(context)` instead — it adds `FLAG_ACTIVITY_NEW_TASK` so the chat screen can be launched from an application context.

```kotlin
class LiveConnectMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        // Build a PendingIntent that opens the chat screen directly.
        val intent = Intent(this, MainActivity::class.java)
            .putExtra("openChat", true)
        // ... attach to the notification you post
    }
}

// In MainActivity, once init() has completed:
if (intent.getBooleanExtra("openChat", false)) {
    LiveConnectChat.showFromNotification(this)
}
```

`showFromNotification()` returns `false` if `init()` hasn't been called yet — the SDK needs the widget config and visitor profile before it can open the chat screen.

> **Note:** Neither `show()` nor `showFromNotification()` will ever stack a duplicate chat screen. If the chat screen is already open (e.g. the app was backgrounded while chat was visible and the user taps another notification), `showFromNotification()` reuses the existing instance and brings it to the foreground; `show()` is a no-op, since chat is already in front of the caller. Check `LiveConnectChat.isChatScreenOpen` if you need to know the current state.

---

## Showing unread count outside the chat widget

`LiveConnectChat.totalUnreadCount` is a `LiveData<Int>` you can observe from anywhere in your app — a bell icon in your toolbar, a bottom-nav badge, a home-screen tile — not just the built-in `FloatingChatButton` badge.

```kotlin
LiveConnectChat.totalUnreadCount.observe(this) { count ->
    badge.isVisible = count > 0
    badge.text = if (count > 99) "99+" else count.toString()
}
```

**This works out of the box — no FCM wiring required.** The SDK asks the server for the real count on `init()` and every time your app returns to the foreground, so the badge is correct as soon as your app starts and after messages arrive while it was backgrounded or fully closed. It resets to zero whenever the chat screen is opened.

### How the count is updated

| Source | When it applies |
|---|---|
| **Server refresh** (automatic) | On `init()` and on every app foreground. Authoritative — this is what recovers messages received while the app was backgrounded or killed. No wiring needed. |
| `ticket:unread_count` socket event | Live, while the chat screen's socket is connected. |
| `LiveConnectChat.registerIncomingPush(context, ticketId)` | **Optional.** Client-side increment for an instant bump while your app is in the *foreground* with chat closed, without waiting for the next refresh. |

> [!NOTE]
> **Why Android differs from the Flutter widget.** The backend sends a `notification` payload, and Android's FCM renders those itself when your app is backgrounded — it **never calls `onMessageReceived`**. So unlike Flutter (whose background isolate can count pushes), no Android client can tally background messages locally. The SDK gets the same result a better way: it asks the server, which also self-heals any drift.

`registerIncomingPush()` is only worth wiring if you want the badge to tick up the instant a push lands while your app is open:

```kotlin
class LiveConnectMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        LiveConnectChat.registerIncomingPush(this, message.data["ticketId"])
        // ... then post the notification
    }
}
```

> **Important:** Don't call `registerIncomingPush()` for the notification the user *tapped* to open chat — opening the chat screen already clears the count.

---

## API reference

### `LiveConnectChat` — top-level singleton

| Method | Purpose |
|---|---|
| `init(context, widgetKey)` | Minimal init — anonymous visitor |
| `init(context, widgetKey, visitorDetails)` | Init with known visitor |
| `init(context, widgetKey, visitorDetails, theme, callback)` | Full init with theme + completion callback |
| `initSuspend(...)` | Coroutine-friendly variant — returns `ApiResult<Unit>` |
| `show(context)` | Open the chat `Activity` |
| `showFromNotification(context, showCloseButton = true)` | Open the chat `Activity` from a Service / Receiver / cold start — no Activity context needed. Returns `false` if `init()` hasn't run |
| `registerIncomingPush(context, ticketId = null)` | Optional — instantly bump the unread badge for a foreground push, instead of waiting for the next server refresh. Safe to call before `init()` |
| `setTheme(theme)` | Override the theme at runtime |
| `setFcmToken(token)` | Register the FCM device token |
| `setFirebaseServiceAccount(map)` | Register the Firebase service account for admin push |
| `isInitialized` | Has `init()` been called? |
| `hasCompleteProfile` | Is the visitor profile complete? |
| `isChatScreenOpen` | Is the chat screen currently on screen? |
| `totalUnreadCount: LiveData<Int>` | Observe the total unread message count (persisted across app restarts) |
| `themeVersion: LiveData<Int>` | Observe theme changes |

### `VisitorProfile`

| Field | Required | Description |
|---|---|---|
| `name` | yes | Full name |
| `email` | yes | Email address |
| `phone` | no | Phone number (E.164 recommended) |

### `LiveConnectTheme`

Build via `LiveConnectTheme.fromPrimary(Int)` for quick theming, or `LiveConnectTheme.builder()` for the full 40+ setters. See `LiveConnectTheme.Builder` in the source jar for the complete list.

### `FloatingChatButton` (view)

Drop-in `com.techindika.liveconnect.ui.view.FloatingChatButton`. Sized 56×56 dp is typical. Auto-tinted from the active theme's primary colour.

---

## Troubleshooting

**`Call LiveConnectChat.init() before LiveConnectChat.show()`**
You're calling `show()` before `init()`. Put the `init` call in `onCreate` (or your `Application.onCreate`) and make sure it runs on every activity entry point.

**Crash on Android 5-7 with `NoSuchMethodError: ThreadLocal.withInitial`**
Core library desugaring is not enabled in the consuming app. Re-read [step 3 of install](#3-enable-core-library-desugaring-required).

**`Could not find com.github.craftindikabiz:live-connect-kotlin-chat-widget`**
The JitPack repository is missing from `settings.gradle.kts`. Add it under `dependencyResolutionManagement.repositories`.

**Push notifications don't arrive**
Order of operations matters: call `setFirebaseServiceAccount(...)` **before** `setFcmToken(...)`. Also verify `google-services.json` is in the `app/` directory and the `com.google.gms.google-services` plugin is applied.

**Chat doesn't open from a notification tap**
Use `showFromNotification(context)`, not `show(context)`, from a `FirebaseMessagingService` or `BroadcastReceiver` — `show()` expects an Activity context. If it returns `false`, `init()` had not completed yet; call it from your Activity after init, or in your `Application.onCreate`.

**Unread badge stays at zero**
The count refreshes from the server on `init()` and on every app foreground, so check that `init()` completed and the visitor profile is set (`hasCompleteProfile`) — the refresh needs the visitor's email to query their tickets. Note the badge only counts **open** tickets; a resolved conversation never keeps it lit. For an instant bump on a foreground push (rather than waiting for the next refresh), wire `LiveConnectChat.registerIncomingPush(context, message.data["ticketId"])` into `onMessageReceived`.

**Stale JitPack build**
JitPack caches per commit SHA. If a tag was moved and you still see old behaviour, open
`https://jitpack.io/#craftindikabiz/live-connect-kotlin-chat-widget/v1.0.15` in a browser and click **Get it** to force a rebuild.

---

## License

UNLICENSED — for internal and licensed partner use only.
