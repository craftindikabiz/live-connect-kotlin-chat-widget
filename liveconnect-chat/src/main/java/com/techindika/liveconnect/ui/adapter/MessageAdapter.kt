package com.techindika.liveconnect.ui.adapter

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.techindika.liveconnect.LiveConnectTheme
import com.techindika.liveconnect.R
import com.techindika.liveconnect.model.Message
import com.techindika.liveconnect.model.MessageSender
import com.techindika.liveconnect.model.MessageStatus
import com.techindika.liveconnect.ui.ImagePreviewDialogFragment
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * RecyclerView adapter for chat messages with multiple view types.
 *
 * Messages are wrapped into [ChatListItem]s so that a "Today" / "Yesterday" /
 * dated separator row can be synthesized wherever the calendar day changes —
 * callers should submit messages via [submitMessages] rather than the raw
 * [ListAdapter.submitList].
 */
internal class MessageAdapter(
    private val theme: LiveConnectTheme
) : ListAdapter<ChatListItem, RecyclerView.ViewHolder>(MessageDiffCallback()) {

    /**
     * Builds the display list (messages + interleaved date headers) from raw
     * chronological messages and submits it to the underlying [ListAdapter].
     */
    fun submitMessages(messages: List<Message>, commitCallback: Runnable? = null) {
        submitList(buildDisplayList(messages), commitCallback)
    }

    override fun getItemViewType(position: Int): Int {
        return when (val item = getItem(position)) {
            is ChatListItem.DateHeaderItem -> VIEW_TYPE_DATE_HEADER
            is ChatListItem.MessageItem -> when (item.message.sender) {
                MessageSender.VISITOR -> VIEW_TYPE_VISITOR
                MessageSender.AGENT -> VIEW_TYPE_AGENT
                MessageSender.SYSTEM, MessageSender.BROADCAST -> VIEW_TYPE_SYSTEM
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_VISITOR -> {
                val view = inflater.inflate(R.layout.item_message_visitor, parent, false)
                VisitorViewHolder(view)
            }
            VIEW_TYPE_AGENT -> {
                val view = inflater.inflate(R.layout.item_message_agent, parent, false)
                AgentViewHolder(view)
            }
            VIEW_TYPE_DATE_HEADER -> {
                val view = inflater.inflate(R.layout.item_date_header, parent, false)
                DateHeaderViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_message_system, parent, false)
                SystemViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ChatListItem.DateHeaderItem -> (holder as DateHeaderViewHolder).bind(item, theme)
            is ChatListItem.MessageItem -> {
                val message = item.message
                when (holder) {
                    is VisitorViewHolder -> holder.bind(message, theme)
                    is AgentViewHolder -> holder.bind(message, theme)
                    is SystemViewHolder -> holder.bind(message, theme)
                }
            }
        }
    }

    // ── View Holders ──

    class VisitorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val bubbleContainer: LinearLayout = itemView.findViewById(R.id.bubbleContainer)
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val timestamp: TextView = itemView.findViewById(R.id.timestamp)
        private val statusIcon: ImageView = itemView.findViewById(R.id.statusIcon)
        private val attachmentImage: ImageView = itemView.findViewById(R.id.attachmentImage)
        private val attachmentDoc: LinearLayout = itemView.findViewById(R.id.attachmentDoc)
        private val docEmoji: TextView = itemView.findViewById(R.id.docEmoji)
        private val docName: TextView = itemView.findViewById(R.id.docName)

        fun bind(message: Message, theme: LiveConnectTheme) {
            messageText.text = message.text
            messageText.setTextColor(theme.visitorTextColor)
            messageText.visibility = if (message.text.isNotEmpty()) View.VISIBLE else View.GONE

            timestamp.text = TIME_FORMAT.get()!!.format(message.timestamp)

            // Status icon — vector drawables (not font glyphs) so the ticks render
            // identically across OEM fonts. Some devices (e.g. Vivo) lack the
            // checkmark/hourglass glyphs and showed a tofu box before.
            // Read state uses golden so it stands out against the primary-colour bubble.
            when (message.status) {
                MessageStatus.SENDING -> {
                    statusIcon.setImageResource(R.drawable.lc_ic_clock)
                    statusIcon.setColorFilter(STATUS_COLOR_TRANSLUCENT_WHITE)
                }
                MessageStatus.SENT -> {
                    statusIcon.setImageResource(R.drawable.lc_ic_check_single)
                    statusIcon.setColorFilter(STATUS_COLOR_TRANSLUCENT_WHITE)
                }
                MessageStatus.DELIVERED -> {
                    statusIcon.setImageResource(R.drawable.lc_ic_check_double)
                    statusIcon.setColorFilter(STATUS_COLOR_TRANSLUCENT_WHITE)
                }
                MessageStatus.READ -> {
                    statusIcon.setImageResource(R.drawable.lc_ic_check_double)
                    statusIcon.setColorFilter(STATUS_COLOR_GOLD)
                }
            }

            // Attachment
            val attachment = message.attachment
            if (attachment != null && attachment.isImage) {
                attachmentImage.visibility = View.VISIBLE
                attachmentDoc.visibility = View.GONE
                Glide.with(itemView.context)
                    .load(attachment.filePath)
                    .centerCrop()
                    .into(attachmentImage)
                // Tap the thumbnail to see the full-size image.
                attachmentImage.setOnClickListener { v ->
                    openImagePreview(v, attachment.filePath)
                }
            } else if (attachment != null) {
                attachmentImage.visibility = View.GONE
                attachmentDoc.visibility = View.VISIBLE
                docEmoji.text = attachment.typeEmoji
                docName.text = attachment.filename
                // Open the document in the user's default handler on tap.
                attachmentDoc.setOnClickListener { v ->
                    openAttachment(v, attachment.filePath)
                }
            } else {
                attachmentImage.visibility = View.GONE
                attachmentDoc.visibility = View.GONE
                attachmentImage.setOnClickListener(null)
                attachmentDoc.setOnClickListener(null)
            }

            // Theme bubble color
            bubbleContainer.background?.setTint(theme.visitorBubbleColor)
        }
    }

    class AgentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val bubbleContainer: LinearLayout = itemView.findViewById(R.id.bubbleContainer)
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val timestamp: TextView = itemView.findViewById(R.id.timestamp)
        private val attachmentImage: ImageView = itemView.findViewById(R.id.attachmentImage)
        private val attachmentDoc: LinearLayout = itemView.findViewById(R.id.attachmentDoc)
        private val docEmoji: TextView = itemView.findViewById(R.id.docEmoji)
        private val docName: TextView = itemView.findViewById(R.id.docName)

        fun bind(message: Message, theme: LiveConnectTheme) {
            messageText.text = message.text
            messageText.setTextColor(theme.agentTextColor)
            messageText.visibility = if (message.text.isNotEmpty()) View.VISIBLE else View.GONE

            timestamp.text = TIME_FORMAT.get()!!.format(message.timestamp)

            val attachment = message.attachment
            if (attachment != null && attachment.isImage) {
                attachmentImage.visibility = View.VISIBLE
                attachmentDoc.visibility = View.GONE
                Glide.with(itemView.context)
                    .load(attachment.filePath)
                    .centerCrop()
                    .into(attachmentImage)
                // Tap the thumbnail to see the full-size image.
                attachmentImage.setOnClickListener { v ->
                    openImagePreview(v, attachment.filePath)
                }
            } else if (attachment != null) {
                attachmentImage.visibility = View.GONE
                attachmentDoc.visibility = View.VISIBLE
                docEmoji.text = attachment.typeEmoji
                docName.text = attachment.filename
                attachmentDoc.setOnClickListener { v ->
                    openAttachment(v, attachment.filePath)
                }
            } else {
                attachmentImage.visibility = View.GONE
                attachmentDoc.visibility = View.GONE
                attachmentImage.setOnClickListener(null)
                attachmentDoc.setOnClickListener(null)
            }

            bubbleContainer.background?.setTint(theme.agentBubbleColor)
        }
    }

    class DateHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dateHeaderText: TextView = itemView.findViewById(R.id.dateHeaderText)

        fun bind(item: ChatListItem.DateHeaderItem, theme: LiveConnectTheme) {
            dateHeaderText.text = item.label
            dateHeaderText.setTextColor(theme.systemMessageTextColor)
            dateHeaderText.background?.setTint(theme.systemMessageBackgroundColor)
        }
    }

    class SystemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val systemText: TextView = itemView.findViewById(R.id.systemText)

        fun bind(message: Message, theme: LiveConnectTheme) {
            systemText.text = message.text
            // Broadcast events (e.g. "Chat reassigned to John") use a distinct colour
            // so they don't visually compete with regular system notices.
            if (message.sender == MessageSender.BROADCAST) {
                systemText.setTextColor(theme.agentTextColor)
                systemText.background?.setTint(theme.broadcastMessageBackgroundColor)
            } else {
                systemText.setTextColor(theme.systemMessageTextColor)
                systemText.background?.setTint(theme.systemMessageBackgroundColor)
            }
        }
    }

    companion object {
        /**
         * Open a remote attachment URL in the OS default handler (browser, PDF
         * viewer, etc.). No-op on
         * empty url or activity-not-found — failure is silent rather than crashy.
         */
        private fun openAttachment(anchor: View, url: String) {
            if (url.isEmpty()) return
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                anchor.context.startActivity(intent)
            } catch (_: Exception) {
                // No matching activity / malformed url — ignore silently.
            }
        }

        /**
         * Show the tapped image attachment full-screen via [ImagePreviewDialogFragment].
         * Walks the view's context wrappers to find the hosting [FragmentActivity] —
         * the RecyclerView's context is normally the Activity itself, but this stays
         * safe if it's ever wrapped (e.g. a themed context). No-op if none is found or
         * the fragment manager can no longer accept a transaction (e.g. mid-destroy).
         */
        private fun openImagePreview(anchor: View, url: String) {
            if (url.isEmpty()) return
            val activity = anchor.context.findFragmentActivity() ?: return
            if (activity.supportFragmentManager.isStateSaved) return
            ImagePreviewDialogFragment.newInstance(url)
                .show(activity.supportFragmentManager, ImagePreviewDialogFragment.TAG)
        }

        private fun Context.findFragmentActivity(): FragmentActivity? {
            var context = this
            while (context is ContextWrapper) {
                if (context is FragmentActivity) return context
                context = context.baseContext
            }
            return null
        }

        private const val VIEW_TYPE_VISITOR = 0
        private const val VIEW_TYPE_AGENT = 1
        private const val VIEW_TYPE_SYSTEM = 2
        private const val VIEW_TYPE_DATE_HEADER = 3

        // Status icon colours on visitor (primary-coloured) bubbles.
        // Translucent white = sent/delivered, gold = read.
        private const val STATUS_COLOR_TRANSLUCENT_WHITE = 0xB3FFFFFF.toInt()
        private const val STATUS_COLOR_GOLD = 0xFFFFD700.toInt()

        private val TIME_FORMAT = ThreadLocal.withInitial { SimpleDateFormat("h:mm a", Locale.US) }

        // Key used to detect a day boundary — not shown to the user.
        private val DATE_KEY_FORMAT = ThreadLocal.withInitial { SimpleDateFormat("yyyyMMdd", Locale.US) }
        // Shown for dates within the current calendar year, e.g. "July 29".
        private val SAME_YEAR_DATE_FORMAT = ThreadLocal.withInitial { SimpleDateFormat("MMMM d", Locale.US) }
        // Shown for dates in a past year, e.g. "July 29, 2025".
        private val FULL_DATE_FORMAT = ThreadLocal.withInitial { SimpleDateFormat("MMMM d, yyyy", Locale.US) }

        /**
         * Wraps chronological messages into [ChatListItem]s, inserting a
         * [ChatListItem.DateHeaderItem] before the first message of every new
         * calendar day (assumes [messages] is ordered oldest-first).
         */
        internal fun buildDisplayList(messages: List<Message>): List<ChatListItem> {
            val items = ArrayList<ChatListItem>(messages.size + 4)
            var lastDateKey: String? = null
            for (message in messages) {
                val dateKey = DATE_KEY_FORMAT.get()!!.format(message.timestamp)
                if (dateKey != lastDateKey) {
                    items.add(ChatListItem.DateHeaderItem(dateKey, formatDateLabel(message.timestamp)))
                    lastDateKey = dateKey
                }
                items.add(ChatListItem.MessageItem(message))
            }
            return items
        }

        /** "Today", "Yesterday", "July 29" (this year), or "July 29, 2025" (past years). */
        private fun formatDateLabel(date: Date): String {
            val today = Calendar.getInstance()
            val target = Calendar.getInstance().apply { time = date }
            val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            return when {
                isSameDay(today, target) -> "Today"
                isSameDay(yesterday, target) -> "Yesterday"
                today.get(Calendar.YEAR) == target.get(Calendar.YEAR) ->
                    SAME_YEAR_DATE_FORMAT.get()!!.format(date)
                else -> FULL_DATE_FORMAT.get()!!.format(date)
            }
        }

        private fun isSameDay(a: Calendar, b: Calendar): Boolean =
            a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }
}

internal class MessageDiffCallback : DiffUtil.ItemCallback<ChatListItem>() {
    override fun areItemsTheSame(oldItem: ChatListItem, newItem: ChatListItem) =
        oldItem.itemId == newItem.itemId
    override fun areContentsTheSame(oldItem: ChatListItem, newItem: ChatListItem) = oldItem == newItem
}
