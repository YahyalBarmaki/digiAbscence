package sn.uadb.gesabscence.ble

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionNotifierTest {

    @Test
    fun notifies_on_first_sight() {
        val n = DetectionNotifier(cooldownMs = 60_000)
        assertTrue(n.shouldNotify("ABCD1234", nowMs = 1_000))
    }

    @Test
    fun suppresses_same_session_within_cooldown() {
        val n = DetectionNotifier(cooldownMs = 60_000)
        n.shouldNotify("ABCD1234", nowMs = 1_000)
        assertFalse(n.shouldNotify("ABCD1234", nowMs = 30_000))
        assertFalse(n.shouldNotify("ABCD1234", nowMs = 60_000))
    }

    @Test
    fun re_notifies_same_session_after_cooldown() {
        val n = DetectionNotifier(cooldownMs = 60_000)
        n.shouldNotify("ABCD1234", nowMs = 1_000)
        assertTrue(n.shouldNotify("ABCD1234", nowMs = 61_001))
    }

    @Test
    fun notifies_immediately_on_new_session() {
        val n = DetectionNotifier(cooldownMs = 60_000)
        n.shouldNotify("ABCD1234", nowMs = 1_000)
        assertTrue(n.shouldNotify("EEEE0000", nowMs = 2_000))
    }

    @Test
    fun reset_clears_history() {
        val n = DetectionNotifier(cooldownMs = 60_000)
        n.shouldNotify("ABCD1234", nowMs = 1_000)
        n.reset()
        assertTrue(n.shouldNotify("ABCD1234", nowMs = 1_500))
    }
}
