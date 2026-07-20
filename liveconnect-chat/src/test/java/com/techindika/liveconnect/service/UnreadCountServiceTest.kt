package com.techindika.liveconnect.service

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class UnreadCountServiceTest {

    @get:Rule val rule = InstantTaskExecutorRule()

    @Before fun setUp() { UnreadCountService.reset() }

    @Test
    fun `getTotalUnreadCount sums per-ticket counts`() {
        UnreadCountService.handleUnreadCountEvent("t1", 3)
        UnreadCountService.handleUnreadCountEvent("t2", 5)
        assertEquals(8, UnreadCountService.getTotalUnreadCount())
    }

    @Test
    fun `getUnreadCount returns the per-ticket value`() {
        UnreadCountService.handleUnreadCountEvent("t1", 3)
        UnreadCountService.handleUnreadCountEvent("t2", 5)
        assertEquals(3, UnreadCountService.getUnreadCount("t1"))
        assertEquals(5, UnreadCountService.getUnreadCount("t2"))
    }

    @Test
    fun `getUnreadCount returns 0 for unknown ticket`() {
        assertEquals(0, UnreadCountService.getUnreadCount("nope"))
    }

    @Test
    fun `handleUnreadCountEvent overwrites existing count for the same ticket`() {
        UnreadCountService.handleUnreadCountEvent("t1", 3)
        UnreadCountService.handleUnreadCountEvent("t1", 10)
        assertEquals(10, UnreadCountService.getUnreadCount("t1"))
        assertEquals(10, UnreadCountService.getTotalUnreadCount())
    }

    @Test
    fun `clearForTicket removes only that ticket from the total`() {
        UnreadCountService.handleUnreadCountEvent("t1", 3)
        UnreadCountService.handleUnreadCountEvent("t2", 5)
        UnreadCountService.clearForTicket("t1")
        assertEquals(0, UnreadCountService.getUnreadCount("t1"))
        assertEquals(5, UnreadCountService.getUnreadCount("t2"))
        assertEquals(5, UnreadCountService.getTotalUnreadCount())
    }

    @Test
    fun `reset clears everything`() {
        UnreadCountService.handleUnreadCountEvent("t1", 3)
        UnreadCountService.handleUnreadCountEvent("t2", 5)
        UnreadCountService.reset()
        assertEquals(0, UnreadCountService.getTotalUnreadCount())
        assertEquals(0, UnreadCountService.getUnreadCount("t1"))
    }

    // ── Server counts + read watermarks ──

    @Test
    fun `applyServerCounts adopts counts for tickets never read`() {
        UnreadCountService.applyServerCounts(
            serverCounts = mapOf("t1" to 2, "t2" to 3),
            lastMessageAt = mapOf("t1" to "2026-07-15T10:00:00Z", "t2" to "2026-07-15T11:00:00Z")
        )
        assertEquals(5, UnreadCountService.getTotalUnreadCount())
    }

    @Test
    fun `server count is ignored once the ticket has been read`() {
        // Visitor read the conversation; the server still counts them unread because
        // its message:read receipt hasn't landed.
        UnreadCountService.setReadWatermarks(mapOf("t1" to "2026-07-15T10:00:00Z"))

        UnreadCountService.applyServerCounts(
            serverCounts = mapOf("t1" to 2),
            lastMessageAt = mapOf("t1" to "2026-07-15T10:00:00Z")
        )

        // The badge must stay cleared — this is the regression that kept the old count.
        assertEquals(0, UnreadCountService.getTotalUnreadCount())
    }

    @Test
    fun `a newer message after reading re-lights the badge`() {
        UnreadCountService.setReadWatermarks(mapOf("t1" to "2026-07-15T10:00:00Z"))

        // Agent replies while the app is backgrounded — lastMessageAt moves on.
        UnreadCountService.applyServerCounts(
            serverCounts = mapOf("t1" to 1),
            lastMessageAt = mapOf("t1" to "2026-07-15T12:00:00Z")
        )

        assertEquals(1, UnreadCountService.getTotalUnreadCount())
    }

    @Test
    fun `setReadWatermarks clears the current count`() {
        UnreadCountService.handleUnreadCountEvent("t1", 4)
        assertEquals(4, UnreadCountService.getTotalUnreadCount())

        UnreadCountService.setReadWatermarks(mapOf("t1" to "2026-07-15T10:00:00Z"))
        assertEquals(0, UnreadCountService.getTotalUnreadCount())
    }

    @Test
    fun `watermark only suppresses the ticket it belongs to`() {
        UnreadCountService.setReadWatermarks(mapOf("t1" to "2026-07-15T10:00:00Z"))

        UnreadCountService.applyServerCounts(
            serverCounts = mapOf("t1" to 2, "t2" to 7),
            lastMessageAt = mapOf("t1" to "2026-07-15T10:00:00Z", "t2" to "2026-07-15T09:00:00Z")
        )

        assertEquals(0, UnreadCountService.getUnreadCount("t1"))
        assertEquals(7, UnreadCountService.getUnreadCount("t2"))
    }
}
