package com.techindika.liveconnect.service

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.techindika.liveconnect.model.*
import java.util.Date
import java.util.UUID

/**
 * Manages conversation thread state. Observable via LiveData for UI updates.
 *
 * State lives in [threadsState] / [activeThreadIdState] and is mutated
 * SYNCHRONOUSLY on the main thread; the LiveData fields are only a notification
 * mirror for the UI. This deliberately uses a plain field plus a synchronous
 * notification step rather than deferred state updates.
 *
 * Why this matters for the delivery ticks: the backend fires a *burst* of events
 * for a single visitor message — the echo (`message:received`), then
 * `messages:status_updated` for delivered, then read — and these can land in the
 * same main-thread loop. The previous implementation stored state in
 * `LiveData` and wrote with `postValue`, which defers the write. A later handler
 * in the burst then read a STALE `_threads.value` snapshot and clobbered the
 * optimistic-id swap / status bump made by the earlier handler, so the ticks got
 * stuck on the first state (single grey ✓). Reading/writing a plain field makes
 * each handler observe the result of the previous one.
 */
internal class ConversationManager {

    private val mainHandler = Handler(Looper.getMainLooper())

    // ── Source of truth (only ever touched on the main thread via runOnMain) ──
    private var threadsState: List<ConversationThread> = emptyList()
    private var activeThreadIdState: String? = null

    private val _threads = MutableLiveData<List<ConversationThread>>(emptyList())
    val threads: LiveData<List<ConversationThread>> = _threads

    private val _activeThreadId = MutableLiveData<String?>(null)
    val activeThreadId: LiveData<String?> = _activeThreadId

    // Maps threadId -> ticketId (API)
    private val threadToTicket = mutableMapOf<String, String>()

    /** Run a state mutation on the main thread (inline if already there). */
    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    /** Replace the thread list and notify observers. Must be called on the main thread. */
    private fun setThreads(value: List<ConversationThread>) {
        threadsState = value
        _threads.value = value
    }

    /** Set the active thread id and notify observers. Must be called on the main thread. */
    private fun setActiveThreadId(value: String?) {
        activeThreadIdState = value
        _activeThreadId.value = value
    }

    /** The currently active thread. */
    val activeThread: ConversationThread?
        get() {
            val id = activeThreadIdState ?: return null
            return threadsState.find { it.id == id }
        }

    /** Ticket ID for the active thread. */
    val activeTicketId: String?
        get() {
            val threadId = activeThreadIdState ?: return null
            return threadToTicket[threadId]
        }

    /** Initialize from API ticket list. */
    fun initializeFromTickets(tickets: List<WidgetTicket>) = runOnMain {
        val converted = tickets.map { convertTicketToThread(it) }
        setThreads(converted)

        // Find first open ticket for active thread
        val activeTicket = tickets.firstOrNull { it.status == "open" }
        if (activeTicket != null) {
            val thread = converted.find { threadToTicket[it.id] == activeTicket.id }
            setActiveThreadId(thread?.id)
        } else {
            initializeWithNewThread()
        }
    }

    /** Create a fresh empty active thread. */
    fun initializeWithNewThread() = runOnMain {
        val newThread = ConversationThread(
            id = UUID.randomUUID().toString(),
            title = "New Conversation",
            status = ConversationStatus.ACTIVE,
            messages = emptyList(),
            updatedAt = Date(),
            createdAt = Date()
        )
        setThreads(listOf(newThread) + threadsState)
        setActiveThreadId(newThread.id)
    }

    /** Set the ticket ID for the active thread (after ticket:created). */
    fun setTicketIdForActiveThread(ticketId: String) = runOnMain {
        val threadId = activeThreadIdState ?: return@runOnMain
        threadToTicket[threadId] = ticketId
    }

    /** Add a message to the active thread. */
    fun addMessageToActiveThread(message: Message) = runOnMain {
        val threadId = activeThreadIdState ?: return@runOnMain
        updateThread(threadId) { thread ->
            thread.copyWith(
                messages = thread.messages + message,
                updatedAt = Date(),
                lastMessage = message.text
            )
        }
    }

    /** Add a message to a thread identified by ticket ID. */
    fun addMessageToThreadByTicketId(ticketId: String, message: Message) = runOnMain {
        val threadId = threadToTicket.entries.find { it.value == ticketId }?.key
            ?: activeThreadIdState ?: return@runOnMain
        updateThread(threadId) { thread ->
            thread.copyWith(
                messages = thread.messages + message,
                updatedAt = Date()
            )
        }
    }

    /** Replace an optimistic message with the server-confirmed version. */
    fun replaceOptimisticMessage(optimisticId: String, serverMessage: Message) = runOnMain {
        // Scan every thread so a resumed/non-active thread still
        // reconciles its optimistic placeholder with the server id.
        val threadId = threadsState.firstOrNull { t -> t.messages.any { it.id == optimisticId } }?.id
            ?: activeThreadIdState ?: return@runOnMain
        updateThread(threadId) { thread ->
            val updated = thread.messages.map {
                if (it.id == optimisticId) serverMessage else it
            }
            thread.copyWith(messages = updated)
        }
    }

    /** Update message delivery/read status by message id. */
    fun updateMessageStatus(messageId: String, status: MessageStatus) = runOnMain {
        // Find the thread that actually contains this message by scanning every
        // thread rather than assuming the active one. (A status
        // update can arrive for a message in a thread that is no longer active.)
        val threadId = threadsState.firstOrNull { t -> t.messages.any { it.id == messageId } }?.id
            ?: return@runOnMain
        updateThread(threadId) { thread ->
            val updated = thread.messages.map {
                if (it.id == messageId) it.copyWith(status = status) else it
            }
            thread.copyWith(messages = updated)
        }
    }

    /**
     * Ticket-wide status update. The backend's messages:status_updated event
     * frequently carries only a ticketId (no messageId) — e.g. when the agent
     * opens the chat and every visitor message is marked read at once. Without
     * this path the double-tick never turns gold.
     *  - DELIVERED: bump only SENT messages to DELIVERED
     *  - READ: bump every not-yet-READ message to READ
     */
    fun updateMessageStatusByTicketId(ticketId: String, status: MessageStatus) = runOnMain {
        val threadId = threadToTicket.entries.find { it.value == ticketId }?.key ?: return@runOnMain
        updateThread(threadId) { thread ->
            val updated = thread.messages.map { msg ->
                when (status) {
                    MessageStatus.DELIVERED ->
                        if (msg.status == MessageStatus.SENT) msg.copyWith(status = status) else msg
                    MessageStatus.READ ->
                        if (msg.status != MessageStatus.READ) msg.copyWith(status = status) else msg
                    else -> msg
                }
            }
            thread.copyWith(messages = updated)
        }
    }

    /** Load API messages into a thread. */
    fun updateThreadMessages(threadId: String, messages: List<TicketMessage>) = runOnMain {
        updateThread(threadId) { thread ->
            val converted = messages.map { convertTicketMessageToMessage(it) }
            thread.copyWith(messages = converted)
        }
    }

    /** Mark the active thread as resolved and create a new one. */
    fun markActiveThreadAsResolved() = runOnMain {
        val threadId = activeThreadIdState ?: return@runOnMain
        updateThread(threadId) { it.copyWith(status = ConversationStatus.CLOSED) }
        initializeWithNewThread()
    }

    /** Switch to a different thread by ID. */
    fun switchToThread(threadId: String) = runOnMain {
        setActiveThreadId(threadId)
    }

    /**
     * Update threads from a fresh API ticket list while PRESERVING the current
     * active thread. Used after marking a conversation as resolved to refresh
     * the Activity tab without losing the new welcome-message thread the user
     * is now sitting on.
     */
    fun updateThreadsFromTickets(tickets: List<WidgetTicket>) = runOnMain {
        val currentActiveId = activeThreadIdState
        val currentActiveThread = threadsState.firstOrNull { it.id == currentActiveId }

        // Rebuild threads from the API list (this regenerates threadToTicket too)
        val newThreads = tickets.map { convertTicketToThread(it) }

        if (currentActiveId != null && currentActiveThread != null) {
            // Only prepend the existing active thread if it isn't already from the API
            // (i.e. it's a fresh client-side thread the API doesn't know about yet).
            val isClientSide = newThreads.none { it.id == currentActiveId }
            if (isClientSide) {
                setThreads(listOf(currentActiveThread) + newThreads)
                setActiveThreadId(currentActiveId)
                return@runOnMain
            }
        }
        setThreads(newThreads)
    }

    /** Get ticket ID for a thread. */
    fun getTicketIdForThread(threadId: String): String? = threadToTicket[threadId]

    // ── Internal helpers ──

    private fun updateThread(threadId: String, transform: (ConversationThread) -> ConversationThread) {
        val current = threadsState.toMutableList()
        val index = current.indexOfFirst { it.id == threadId }
        if (index >= 0) {
            current[index] = transform(current[index])
            setThreads(current)
        }
    }

    private fun convertTicketToThread(ticket: WidgetTicket): ConversationThread {
        val threadId = UUID.randomUUID().toString()
        threadToTicket[threadId] = ticket.id

        val status = if (ticket.status == "open") ConversationStatus.ACTIVE else ConversationStatus.CLOSED
        val createdAt = parseIsoDate(ticket.createdAt) ?: Date()
        val updatedAt = parseIsoDate(ticket.updatedAt) ?: createdAt

        return ConversationThread(
            id = threadId,
            title = ticket.firstMessage?.take(50) ?: "Conversation",
            status = status,
            messages = emptyList(),
            agentName = ticket.agentName ?: "",
            agentStatus = ticket.agentStatus ?: "",
            updatedAt = updatedAt,
            createdAt = createdAt,
            firstMessage = ticket.firstMessage,
            lastMessage = ticket.lastMessage
        )
    }

    private fun convertTicketMessageToMessage(tm: TicketMessage): Message {
        val sender = MessageSender.fromString(tm.senderType)
        val status = MessageStatus.fromString(tm.status)

        val attachment = if (!tm.fileUrl.isNullOrEmpty()) {
            val mimeType = normalizeMimeType(tm.fileType ?: "")
            val type = if (mimeType.startsWith("image/")) AttachmentType.MEDIA else AttachmentType.DOCUMENT
            Attachment(
                filename = tm.fileName ?: "attachment",
                filePath = tm.fileUrl,
                size = 0,
                type = type,
                mimeType = mimeType
            )
        } else null

        return Message(
            id = tm.id,
            text = tm.content ?: "",
            sender = sender,
            timestamp = tm.createdAt,
            attachment = attachment,
            status = status
        )
    }

    private fun normalizeMimeType(fileType: String): String = when (fileType.lowercase()) {
        "image" -> "image/jpeg"
        "document" -> "application/pdf"
        "video" -> "video/mp4"
        else -> if (fileType.contains("/")) fileType else "application/octet-stream"
    }

    private fun parseIsoDate(dateStr: String?): Date? {
        if (dateStr.isNullOrBlank()) return null
        return try {
            val cleaned = dateStr.replace("Z", "").split(".").first()
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            sdf.parse(cleaned)
        } catch (_: Exception) {
            null
        }
    }
}
