package com.techindika.liveconnect.ui.adapter

import com.techindika.liveconnect.model.Message

/**
 * Row types rendered by [MessageAdapter]. A plain [Message] list doesn't carry
 * enough information to show "Today" / "Yesterday" / date separators between
 * days, so messages are wrapped into this sealed type and date headers are
 * synthesized in between whenever the calendar day changes.
 */
internal sealed class ChatListItem {

    /** Stable id used by [MessageDiffCallback] to tell rows apart across updates. */
    abstract val itemId: String

    data class MessageItem(val message: Message) : ChatListItem() {
        override val itemId: String get() = "msg_${message.id}"
    }

    data class DateHeaderItem(val dateKey: String, val label: String) : ChatListItem() {
        override val itemId: String get() = "date_$dateKey"
    }
}
