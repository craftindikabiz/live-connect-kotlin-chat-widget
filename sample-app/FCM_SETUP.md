# Sample app — FCM push setup

The sample app is already wired for push notifications. Code is in place:

- `build.gradle.kts` — Firebase BOM + `firebase-messaging`; the
  `com.google.gms.google-services` plugin is applied **only when**
  `google-services.json` exists, so the project still builds without it.
- `AndroidManifest.xml` — `POST_NOTIFICATIONS` permission, the
  `LiveConnectMessagingService`, and the default notification channel meta-data.
- `MainActivity.kt` — requests the notification permission, fetches the FCM token,
  and calls `LiveConnectChat.setFcmToken(token)`.
- `LiveConnectMessagingService.kt` — `onNewToken` + foreground display.

## What you still must do (per‑project, can't be committed generically)

1. **Add `google-services.json`.** Download it from the Firebase project that owns
   this widget's service account and place it at `sample-app/google-services.json`.
   The package name inside it must equal the app's `applicationId`
   (`com.techindika.liveconnect.sample`) — this is also the `domain` the widget
   sends to the backend on token registration.

2. **Set a real `widgetKey`** in `MainActivity.kt` (currently
   `"your-widget-key-here"`).

3. **Backend / admin:** upload a Firebase service account for this widget that
   includes `com.techindika.liveconnect.sample` in its `domains[]`
   (`PUT /api/admin/widgets/:widgetId/firebase`). It must belong to the same
   Firebase project as the `google-services.json` above.

These three values must be the identical package‑name string everywhere:

```
applicationId  ==  domain registered by the widget  ==  package_name in google-services.json
               ==  a domain in the widget's WidgetFirebaseConfig
```

See `../../NOTIFICATIONS_03_KOTLIN_FIX.md` for full details.
