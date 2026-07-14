package com.techindika.liveconnect.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.json.JSONObject

/**
 * Tracks the visitor's unread message counts per ticket.
 *
 * Counts are persisted with [SharedPreferences] so a badge (see
 * [totalUnreadCount]) reflects unread messages even before the chat screen has
 * ever been opened in the current app session — including messages that arrived
 * while the app was fully closed, recorded via [registerIncomingPush] from a
 * `FirebaseMessagingService` running in a freshly-started process.
 *
 * Two ways counts get updated:
 * 1. The `ticket:unread_count` socket event while the chat screen is open, via
 *    [handleUnreadCountEvent] (server-authoritative for that ticket).
 * 2. Push notifications received outside the chat screen (foreground,
 *    background, or while fully closed), via [registerIncomingPush]
 *    (client-derived, incremented locally).
 *
 * Observable via LiveData for badge updates.
 */
internal object UnreadCountService {

    private const val TAG = "LiveConnect"
    private const val PREFS_NAME = "liveconnect_unread"
    private const val KEY_COUNTS = "liveconnect_unread_counts"

    /** Bucket used for pushes that don't carry a ticketId. */
    private const val UNKNOWN_TICKET_KEY = "_unknown"

    private val counts = mutableMapOf<String, Int>()

    private val _totalUnreadCount = MutableLiveData(0)

    /** Total unread count across all tickets. Observable. */
    val totalUnreadCount: LiveData<Int> = _totalUnreadCount

    /**
     * Application context captured at init, so the socket-driven updates can
     * persist without threading a Context through the UI layer. Null until
     * [initFromStorage] runs — persistence is skipped while it is (e.g. in
     * plain JVM unit tests, which stay in-memory only).
     */
    @Volatile
    private var appContext: Context? = null

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Load persisted unread counts from disk and refresh [totalUnreadCount].
     *
     * Called by `LiveConnectChat.init()` so a badge shown outside the chat
     * screen has the correct count immediately, and again whenever the app
     * returns to the foreground (a push handled while the app was backgrounded
     * may have written a newer count).
     */
    @JvmStatic
    fun initFromStorage(context: Context) {
        appContext = context.applicationContext
        val stored = readPersisted(context.applicationContext)
        synchronized(counts) {
            counts.clear()
            counts.putAll(stored)
        }
        publishTotal()
        Log.d(TAG, "Unread counts loaded from storage: $stored")
    }

    /** Handle an unread count event from the socket (server-authoritative). */
    @JvmStatic
    fun handleUnreadCountEvent(ticketId: String, unreadCount: Int) {
        val snapshot = synchronized(counts) {
            counts[ticketId] = unreadCount
            counts.toMap()
        }
        publishTotal()
        persist(snapshot)
    }

    /**
     * Register that a chat push notification was received outside the chat
     * screen (app foregrounded elsewhere, backgrounded, or fully closed),
     * incrementing the persisted unread count by one.
     *
     * Takes an explicit [context] rather than relying on [appContext] because
     * when FCM starts a dead process to deliver a push, the SDK has not been
     * initialized yet — there is no cached context, but the count still has to
     * be recorded. The next `LiveConnectChat.init()` (or the next foreground
     * resume) picks it up via [initFromStorage].
     *
     * Pass [ticketId] when available (e.g. `message.data["ticketId"]`) so the
     * count can later be cleared precisely via [clearForTicket]; otherwise it is
     * tracked under a generic bucket that [markAllRead] clears.
     *
     * Writes with `commit()` rather than `apply()`: the process may be killed
     * immediately after a background push is handled, and an in-flight async
     * write would be lost.
     */
    @JvmStatic
    @JvmOverloads
    fun registerIncomingPush(context: Context, ticketId: String? = null) {
        val ctx = context.applicationContext
        try {
            val current = readPersisted(ctx)

            val key = ticketId?.trim()?.takeIf { it.isNotEmpty() } ?: UNKNOWN_TICKET_KEY
            current[key] = (current[key] ?: 0) + 1

            prefs(ctx).edit().putString(KEY_COUNTS, toJson(current)).commit()
            Log.d(TAG, "registerIncomingPush() persisted: $current")

            appContext = ctx
            synchronized(counts) {
                counts.clear()
                counts.putAll(current)
            }
            publishTotal()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register incoming push: ${e.message}")
        }
    }

    /** Get the current total. */
    @JvmStatic
    fun getTotalUnreadCount(): Int = synchronized(counts) { counts.values.sum() }

    /** Get the unread count for a specific ticket (0 if none). */
    @JvmStatic
    fun getUnreadCount(ticketId: String): Int = synchronized(counts) { counts[ticketId] ?: 0 }

    /** Clear counts for a specific ticket. */
    @JvmStatic
    fun clearForTicket(ticketId: String) {
        val snapshot = synchronized(counts) {
            counts.remove(ticketId)
            counts.toMap()
        }
        publishTotal()
        persist(snapshot)
    }

    /**
     * Clear all counts, in memory and on disk. Called when the visitor opens the
     * chat screen — they are reading the messages now.
     */
    @JvmStatic
    fun markAllRead() {
        synchronized(counts) { counts.clear() }
        publishTotal()
        persist(emptyMap())
    }

    /** Reset all counts. Alias of [markAllRead]. */
    @JvmStatic
    fun reset() = markAllRead()

    // ── Internals ──

    private fun publishTotal() {
        _totalUnreadCount.postValue(getTotalUnreadCount())
    }

    private fun persist(snapshot: Map<String, Int>) {
        val ctx = appContext ?: return
        try {
            prefs(ctx).edit().putString(KEY_COUNTS, toJson(snapshot)).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist unread counts: ${e.message}")
        }
    }

    private fun readPersisted(context: Context): MutableMap<String, Int> {
        val result = mutableMapOf<String, Int>()
        try {
            val raw = prefs(context).getString(KEY_COUNTS, null)
            if (raw.isNullOrEmpty()) return result
            val json = JSONObject(raw)
            json.keys().forEach { key ->
                result[key] = json.optInt(key, 0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load unread counts from storage: ${e.message}")
        }
        return result
    }

    private fun toJson(map: Map<String, Int>): String =
        JSONObject().apply { map.forEach { (key, value) -> put(key, value) } }.toString()
}
