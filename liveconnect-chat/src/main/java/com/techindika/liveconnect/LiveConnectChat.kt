package com.techindika.liveconnect

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.techindika.liveconnect.callback.InitCallback
import com.techindika.liveconnect.model.VisitorProfile
import com.techindika.liveconnect.model.WidgetConfig
import com.techindika.liveconnect.network.ApiResult
import com.techindika.liveconnect.network.RetrofitClient
import com.techindika.liveconnect.service.UnreadCountService
import com.techindika.liveconnect.service.VisitorProfileStore
import com.techindika.liveconnect.ui.ChatActivity
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Main entry point for the LiveConnect Chat SDK.
 *
 * **Kotlin usage:**
 * ```kotlin
 * LiveConnectChat.init(context, "widget-key",
 *     visitorDetails = VisitorProfile("John", "john@example.com"))
 * LiveConnectChat.show(context)
 * ```
 *
 * **Java usage:**
 * ```java
 * LiveConnectChat.init(context, "widget-key",
 *     new VisitorProfile("John", "john@example.com", ""),
 *     null, callback);
 * LiveConnectChat.show(context);
 * ```
 */
object LiveConnectChat {

    private const val TAG = "LiveConnect"

    private var _initialized = false
    private var _widgetKey: String? = null
    private var _visitorProfile: VisitorProfile? = null
    private var _theme: LiveConnectTheme = LiveConnectTheme.defaults()
    private var _widgetConfig: WidgetConfig? = null
    private var _fcmToken: String? = null
    private var _firebaseServiceAccount: Map<String, Any>? = null
    private var _appContext: Context? = null
    private var _visitorId: String? = null
    @Volatile private var _chatScreenOpen = false
    private var _resumeRefreshRegistered = false

    private var scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _themeVersion = MutableLiveData(0)
    private val themeCounter = java.util.concurrent.atomic.AtomicInteger(0)

    // ── Public getters ──

    @JvmStatic val isInitialized: Boolean get() = _initialized
    @JvmStatic val widgetKey: String? get() = _widgetKey
    @JvmStatic val visitorProfile: VisitorProfile? get() = _visitorProfile
    @JvmStatic val hasCompleteProfile: Boolean get() = _visitorProfile?.isComplete == true
    @JvmStatic val currentTheme: LiveConnectTheme get() = _theme
    @JvmStatic val widgetConfig: WidgetConfig? get() = _widgetConfig
    @JvmStatic val themeVersion: LiveData<Int> get() = _themeVersion

    /** Whether the chat screen is currently open. */
    @JvmStatic val isChatScreenOpen: Boolean get() = _chatScreenOpen

    /**
     * Total unread message count, so a badge can be shown anywhere in the app —
     * not just on the built-in [com.techindika.liveconnect.ui.view.FloatingChatButton].
     *
     * The count is persisted locally and updates from two sources:
     * - The `ticket:unread_count` socket event while the chat screen is open.
     * - [registerIncomingPush], which the app calls from its FCM handlers, so the
     *   count reflects messages received while chat is closed (including while
     *   the app is fully terminated).
     *
     * It resets to zero whenever the chat screen is opened.
     *
     * ```kotlin
     * LiveConnectChat.totalUnreadCount.observe(this) { count ->
     *     badge.isVisible = count > 0
     *     badge.text = if (count > 99) "99+" else count.toString()
     * }
     * ```
     */
    @JvmStatic val totalUnreadCount: LiveData<Int> get() = UnreadCountService.totalUnreadCount

    internal val appContext: Context? get() = _appContext
    internal val visitorId: String? get() = _visitorId

    /** Set by [ChatActivity] as it is created / destroyed. */
    internal var chatScreenOpen: Boolean
        get() = _chatScreenOpen
        set(value) {
            _chatScreenOpen = value
        }

    // ── Initialization ──

    /**
     * Initialize the SDK. Must be called before [show].
     *
     * @param context Application or Activity context.
     * @param widgetKey Unique key from the LiveConnect dashboard.
     * @param visitorDetails Optional visitor profile. If null or incomplete, a form is shown.
     * @param theme Optional theme override.
     * @param callback Optional callback for Java callers (null for Kotlin — use suspend version).
     */
    @JvmStatic
    @JvmOverloads
    fun init(
        context: Context,
        widgetKey: String,
        visitorDetails: VisitorProfile? = null,
        theme: LiveConnectTheme? = null,
        callback: InitCallback? = null
    ) {
        scope.launch {
            try {
                val result = initInternal(context, widgetKey, visitorDetails, theme)
                if (result.isSuccess) {
                    callback?.onSuccess()
                } else {
                    callback?.onFailure(result.errorOrNull() ?: "Initialization failed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Init failed: ${e.message}")
                callback?.onFailure(e.message ?: "Initialization failed")
            }
        }
    }

    /**
     * Kotlin-friendly suspend initialization.
     */
    suspend fun initSuspend(
        context: Context,
        widgetKey: String,
        visitorDetails: VisitorProfile? = null,
        theme: LiveConnectTheme? = null
    ): ApiResult<Unit> {
        return initInternal(context, widgetKey, visitorDetails, theme)
    }

    private suspend fun initInternal(
        context: Context,
        widgetKey: String,
        visitorDetails: VisitorProfile?,
        theme: LiveConnectTheme?
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        // Cancel any previously running init coroutines
        if (_initialized) {
            scope.cancel()
            scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        }

        _appContext = context.applicationContext
        _widgetKey = widgetKey

        UnreadCountService.initFromStorage(_appContext!!)
        ensureUnreadCountResumeRefresh(_appContext!!)

        // Apply user theme if provided
        if (theme != null) {
            _theme = theme
        }

        // Fetch widget config from API
        try {
            val response = RetrofitClient.apiService.fetchWidgetConfig(widgetKey)
            val json = JSONObject(response.string())
            val status = json.optString("status", "")
            if (status == "success") {
                val widgetJson = json.optJSONObject("data")?.optJSONObject("widget")
                if (widgetJson != null) {
                    _widgetConfig = WidgetConfig.fromJson(widgetJson)

                    // Merge API config into theme (API colors are lower priority than user theme)
                    if (theme == null) {
                        _widgetConfig?.let { config ->
                            _theme = LiveConnectTheme.fromPrimary(config.parsedColor())
                                .toBuilder().apply {
                                    welcomeText = config.welcomeText.ifEmpty { welcomeText }
                                    offlineText = config.offlineText.ifEmpty { offlineText }
                                    widgetPosition = config.position
                                    suggestedMessages = config.suggestedMessages.toMutableList()
                                    iconUrl = config.iconUrl.ifEmpty { null }
                                    headerTitle = config.name.ifEmpty { headerTitle }
                                }.build()
                        }
                    } else {
                        // User provided theme — merge only non-theme config
                        _widgetConfig?.let { config ->
                            _theme = _theme.toBuilder().apply {
                                if (welcomeText == LiveConnectTheme.builder().welcomeText) {
                                    welcomeText = config.welcomeText.ifEmpty { welcomeText }
                                }
                                widgetPosition = config.position
                                suggestedMessages = config.suggestedMessages.toMutableList()
                                if (iconUrl == null) iconUrl = config.iconUrl.ifEmpty { null }
                            }.build()
                        }
                    }
                    _themeVersion.postValue(themeCounter.incrementAndGet())
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch widget config: ${e.message}")
            // Non-fatal — continue with defaults
        }

        // Handle visitor profile
        if (visitorDetails != null && visitorDetails.isComplete) {
            _visitorProfile = visitorDetails
            // Register visitor with API
            registerVisitor(visitorDetails)
            // Persist locally
            VisitorProfileStore.save(context.applicationContext, widgetKey, visitorDetails)
        } else {
            // Try loading from storage
            val stored = VisitorProfileStore.load(context.applicationContext, widgetKey)
            if (stored?.isComplete == true) {
                _visitorProfile = stored
            }
        }

        // Load visitor ID
        _visitorId = VisitorProfileStore.loadVisitorId(context.applicationContext, widgetKey)

        _initialized = true
        Log.d(TAG, "SDK initialized for widget: $widgetKey")

        // If the consumer app called setFcmToken() before init finished (a common
        // race — the cached FCM token often returns faster than the widget-config
        // network round-trip), registration was skipped because _initialized was
        // still false. Re-attempt it now that we're initialized and the profile is
        // known. Registration should happen as soon as the profile is set.
        val pendingToken = _fcmToken
        if (pendingToken != null && hasCompleteProfile) {
            registerFcmToken(pendingToken)
        }

        ApiResult.success(Unit)
    }

    /**
     * Register (once) an app-lifecycle listener that reloads the unread count from
     * storage whenever the app returns to the foreground.
     */
    private fun ensureUnreadCountResumeRefresh(context: Context) {
        if (_resumeRefreshRegistered) return
        val app = context.applicationContext as? Application ?: return

        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var startedActivities = 0

            override fun onActivityStarted(activity: Activity) {
                if (startedActivities == 0) {
                    Log.d(TAG, "App resumed — refreshing unread count from storage")
                    UnreadCountService.initFromStorage(activity.applicationContext)
                }
                startedActivities++
            }

            override fun onActivityStopped(activity: Activity) {
                if (startedActivities > 0) startedActivities--
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        _resumeRefreshRegistered = true
        Log.d(TAG, "Unread-count resume observer registered")
    }

    // ── Show / Hide ──

    /**
     * Open the chat screen. If visitor profile is incomplete, a form is shown first.
     *
     * No-op if the chat screen is already open — it won't stack a duplicate on top.
     *
     * @param context Activity context (required for starting Activity).
     */
    @JvmStatic
    fun show(context: Context) {
        check(_initialized && _widgetKey != null) {
            "Call LiveConnectChat.init() before LiveConnectChat.show()"
        }
        if (_chatScreenOpen) {
            Log.d(TAG, "Chat screen already open — ignoring duplicate open request")
            return
        }
        context.startActivity(chatIntent(context))
    }

    /**
     * Open the chat screen from outside an Activity — a `FirebaseMessagingService`,
     * a `BroadcastReceiver`, or a notification tap that cold-starts the app after it
     * was fully closed.
     *
     * Unlike [show], this does not assume [context] is an Activity: it adds
     * `FLAG_ACTIVITY_NEW_TASK` so the chat screen can be launched from an
     * application context.
     *
     * ```kotlin
     * class MyMessagingService : FirebaseMessagingService() {
     *     override fun onMessageReceived(message: RemoteMessage) {
     *         LiveConnectChat.showFromNotification(this)
     *     }
     * }
     * ```
     *
     * If the chat screen is already open — e.g. the app was backgrounded while chat
     * was visible and the user tapped another notification — `FLAG_ACTIVITY_SINGLE_TOP`
     * makes Android reuse that instance and bring its task to the foreground rather
     * than stacking a duplicate on top of it. (Deliberately not short-circuited on
     * [isChatScreenOpen]: returning early here would leave the chat screen sitting in
     * the background, so the notification tap would appear to do nothing.)
     *
     * @return `true` if the chat screen was opened, `false` if [init] hasn't been
     *   called yet.
     */
    @JvmStatic
    @JvmOverloads
    fun showFromNotification(context: Context, showCloseButton: Boolean = true): Boolean {
        if (!_initialized || _widgetKey == null) {
            Log.w(TAG, "showFromNotification() called before init()")
            return false
        }
        context.startActivity(
            chatIntent(context, showCloseButton).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
        return true
    }

    /** SINGLE_TOP because ChatActivity is `launchMode="standard"` — without it a
     *  second open request stacks a duplicate chat screen behind the first. */
    private fun chatIntent(context: Context, showCloseButton: Boolean = true): Intent =
        Intent(context, ChatActivity::class.java).apply {
            putExtra(ChatActivity.EXTRA_WIDGET_KEY, _widgetKey)
            putExtra(ChatActivity.EXTRA_SHOW_CLOSE_BUTTON, showCloseButton)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

    // ── Unread count ──

    /**
     * Register that a chat push notification was received outside the chat screen,
     * incrementing [totalUnreadCount] by one.
     *
     * Call this from the app's FCM listener so the badge stays accurate regardless
     * of app state:
     * ```kotlin
     * class MyMessagingService : FirebaseMessagingService() {
     *     override fun onMessageReceived(message: RemoteMessage) {
     *         LiveConnectChat.registerIncomingPush(this, message.data["ticketId"])
     *         // ... then show the notification
     *     }
     * }
     * ```
     *
     * Safe to call before [init] — FCM starts the app's process to deliver a push
     * even when the app is fully closed, and the count is written straight to disk
     * rather than relying on the SDK being initialized.
     *
     * Don't call this for the message the user *tapped* to open chat (the tap
     * handler that calls [show] / [showFromNotification]) — opening the chat screen
     * already clears the count.
     *
     * @param context any Context (a Service, a Receiver, or an Activity).
     * @param ticketId optional, e.g. `message.data["ticketId"]`.
     */
    @JvmStatic
    @JvmOverloads
    fun registerIncomingPush(context: Context, ticketId: String? = null) {
        UnreadCountService.registerIncomingPush(context, ticketId)
    }

    // ── FCM ──

    /**
     * Set the FCM token for push notifications.
     */
    @JvmStatic
    fun setFcmToken(fcmToken: String) {
        _fcmToken = fcmToken
        // Register with server if profile is complete
        if (_initialized && hasCompleteProfile) {
            scope.launch(Dispatchers.IO) {
                registerFcmToken(fcmToken)
            }
        }
    }

    /**
     * Set the Firebase service account JSON for admin push notifications.
     */
    @JvmStatic
    fun setFirebaseServiceAccount(serviceAccount: Map<String, Any>) {
        _firebaseServiceAccount = serviceAccount
        // Upload to server if widget config available
        if (_initialized && _widgetConfig != null) {
            scope.launch(Dispatchers.IO) {
                uploadFirebaseServiceAccount(serviceAccount)
            }
        }
    }

    // ── Theme ──

    /**
     * Override the theme at runtime.
     *
     * Auto-sync rule: if the developer changed `primaryColor` but left
     * `visitorBubbleColor` unchanged (i.e. it still equals the OLD primary),
     * we propagate the new primary into all 14 primary-dependent colour
     * fields.
     */
    @JvmStatic
    fun setTheme(theme: LiveConnectTheme) {
        val oldPrimary = _theme.primaryColor
        val newPrimary = theme.primaryColor
        val primaryChanged = newPrimary != oldPrimary
        val visitorBubbleStillTracksOldPrimary = theme.visitorBubbleColor == oldPrimary

        _theme = if (primaryChanged && visitorBubbleStillTracksOldPrimary) {
            theme.toBuilder().apply {
                visitorBubbleColor = newPrimary
                sendButtonStartColor = newPrimary
                sendButtonEndColor = LiveConnectTheme.darkenColor(newPrimary, 0.15f)
                attachButtonIconColor = newPrimary
                emptyChatIconColor = newPrimary
                tabLabelColor = newPrimary
                tabIndicatorColor = newPrimary
                formButtonColor = newPrimary
                formFieldFocusBorderColor = newPrimary
                activityTitleColor = newPrimary
                activityCardBorderColor = LiveConnectTheme.withAlphaColor(newPrimary, 0.20f)
                inputFieldFocusBorderColor = newPrimary
                readOnlyNoticeBackgroundColor = LiveConnectTheme.withAlphaColor(newPrimary, 0.08f)
                readOnlyNoticeTextColor = newPrimary
                headerAvatarBackgroundColor = newPrimary
            }.build()
        } else {
            theme
        }
        _themeVersion.postValue(themeCounter.incrementAndGet())
    }

    // ── Internal API calls ──

    private suspend fun registerVisitor(profile: VisitorProfile) {
        try {
            val body = JSONObject().apply {
                put("name", profile.name)
                put("email", profile.email)
                put("phone", profile.phone)
            }
            val requestBody = body.toString()
                .toRequestBody("application/json".toMediaType())
            val response = RetrofitClient.apiService.upsertVisitor(_widgetKey!!, requestBody)
            val json = JSONObject(response.string())
            // Extract visitor ID if returned
            val data = json.optJSONObject("data")
            val visitorId = data?.optString("visitorId", data.optString("_id", ""))
            if (!visitorId.isNullOrEmpty()) {
                _visitorId = visitorId
                _appContext?.let { ctx ->
                    VisitorProfileStore.saveVisitorId(ctx, _widgetKey!!, visitorId)
                }
            }
            Log.d(TAG, "Visitor registered: ${profile.email}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register visitor: ${e.message}")
        }
    }

    internal suspend fun registerVisitorProfile(profile: VisitorProfile): Boolean {
        _visitorProfile = profile
        _appContext?.let { ctx ->
            VisitorProfileStore.save(ctx, _widgetKey!!, profile)
        }
        return try {
            registerVisitor(profile)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Update the stored visitor profile and re-register it with the server.
     * Public counterpart to [registerVisitorProfile] — callable from app code
     * after [init] to update the visitor's name/email/phone.
     *
     * @param updatedProfile new visitor details
     * @param callback fires with `true` on success, `false` on failure
     */
    @JvmStatic
    @JvmOverloads
    fun updateVisitorProfile(
        updatedProfile: VisitorProfile,
        callback: ((Boolean) -> Unit)? = null
    ) {
        scope.launch {
            val ok = registerVisitorProfile(updatedProfile)
            callback?.invoke(ok)
        }
    }

    private suspend fun registerFcmToken(token: String) {
        try {
            val profile = _visitorProfile ?: return
            // On mobile the backend keys tokens by the app package name as the
            // "domain" (the app package name is sent as that value).
            // Both fields are REQUIRED by the backend — without them it returns 400.
            val domain = _appContext?.packageName ?: run {
                Log.w(TAG, "Cannot register FCM token: no app context for package name")
                return
            }
            val body = JSONObject().apply {
                put("widgetKey", _widgetKey)
                put("email", profile.email)
                put("token", token)
                put("domain", domain)
            }
            val requestBody = body.toString()
                .toRequestBody("application/json".toMediaType())
            RetrofitClient.apiService.registerFcmToken(requestBody)
            Log.d(TAG, "FCM token registered (domain=$domain)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register FCM token: ${e.message}")
        }
    }

    private suspend fun uploadFirebaseServiceAccount(serviceAccount: Map<String, Any>) {
        try {
            val widgetId = _widgetConfig?.id ?: return
            val body = JSONObject(serviceAccount)
            val requestBody = body.toString()
                .toRequestBody("application/json".toMediaType())
            RetrofitClient.apiService.uploadFirebaseServiceAccount(widgetId, requestBody)
            Log.d(TAG, "Firebase service account uploaded")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to upload Firebase service account: ${e.message}")
        }
    }
}
