package com.techindika.liveconnect.sample

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.techindika.liveconnect.LiveConnectChat
import com.techindika.liveconnect.LiveConnectTheme
import com.techindika.liveconnect.callback.InitCallback
import com.techindika.liveconnect.model.VisitorProfile

/**
 * Sample activity demonstrating LiveConnect SDK usage, including FCM push setup.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ask for the runtime notification permission (Android 13+).
        requestNotificationPermission()

        // Initialize the SDK
        LiveConnectChat.init(
            context = this,
            widgetKey = "your-widget-key-here",
            visitorDetails = VisitorProfile(
                name = "John Doe",
                email = "john@example.com",
                phone = "+14155552671"
            ),
            theme = LiveConnectTheme.fromPrimary(Color.parseColor("#4F46E5")),
            callback = object : InitCallback {
                override fun onSuccess() {
                    // SDK is ready — show UI
                    setContentView(R.layout.activity_main)
                    setupUI()
                    // Fetch the FCM token and hand it to the widget so the
                    // backend can push notifications to this device.
                    registerFcmToken()
                }

                override fun onFailure(error: String) {
                    // Still show UI, chat may work with defaults
                    setContentView(R.layout.activity_main)
                    setupUI()
                    registerFcmToken()
                }
            }
        )
    }

    private fun setupUI() {
        // Open chat from a custom button (like a menu item)
        findViewById<Button>(R.id.openChatButton).setOnClickListener {
            LiveConnectChat.show(this)
        }

        // FloatingChatButton in the layout auto-opens chat on tap
    }

    /**
     * Retrieve the FCM device token and pass it to [LiveConnectChat.setFcmToken].
     * The widget registers it with the backend (keyed by the app package name as
     * the "domain"). Wrapped defensively so the sample still runs if Firebase
     * isn't configured yet (no google-services.json).
     */
    private fun registerFcmToken() {
        try {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    Log.d(TAG, "FCM token obtained")
                    LiveConnectChat.setFcmToken(token)
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Failed to fetch FCM token: ${e.message}")
                }
        } catch (e: Exception) {
            // Firebase not initialized (e.g. google-services.json missing).
            Log.w(TAG, "Firebase not configured — skipping FCM registration: ${e.message}")
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_NOTIFICATION_PERMISSION
                )
            }
        }
    }

    companion object {
        private const val TAG = "LiveConnectSample"
        private const val REQUEST_NOTIFICATION_PERMISSION = 1001
    }
}
