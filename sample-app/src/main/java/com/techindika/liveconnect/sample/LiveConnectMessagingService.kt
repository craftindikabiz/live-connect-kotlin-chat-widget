package com.techindika.liveconnect.sample

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.techindika.liveconnect.LiveConnectChat

/**
 * Receives FCM pushes for the sample app.
 *
 * The LiveConnect widget library only *registers* the device token with the
 * backend ([LiveConnectChat.setFcmToken]); the consumer app owns Firebase and is
 * responsible for displaying notifications. The backend sends a "notification"
 * payload, so:
 *   - App in background / killed → Android shows it automatically (this service
 *     is not invoked for display).
 *   - App in foreground → the system tray is suppressed, so we render it here in
 *     [onMessageReceived].
 *
 * It also forwards rotated tokens via [onNewToken].
 */
class LiveConnectMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // Token rotated — re-register it through the widget.
        LiveConnectChat.setFcmToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: "New message"
        val body = message.notification?.body ?: message.data["body"] ?: ""
        showNotification(title, body, message.data["ticketId"])
    }

    private fun showNotification(title: String, body: String, ticketId: String?) {
        val nm = getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Chat messages",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }

        // Tapping the notification reopens the app.
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.techindika.liveconnect.R.drawable.lc_ic_chat_bubble_outline)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        nm.notify(ticketId?.hashCode() ?: 0, notification)
    }

    companion object {
        // Must match the default_notification_channel_id meta-data in the manifest.
        private const val CHANNEL_ID = "liveconnect_messages"
    }
}
