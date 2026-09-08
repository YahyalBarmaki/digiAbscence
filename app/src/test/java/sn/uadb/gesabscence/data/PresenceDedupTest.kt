package sn.uadb.gesabscence.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sn.uadb.gesabscence.data.model.PresenceConfirmation

class PresenceDedupTest {

    private fun key(student: String, session: String) =
        PresenceConfirmation.key(student, session)

    @Test
    fun first_confirmation_is_accepted() {
        val dedup = PresenceDedup()
        assertTrue(dedup.tryAdd(key("etu1", "AB12CD34")))
    }

    @Test
    fun second_confirmation_same_pair_is_rejected() {
        val dedup = PresenceDedup()
        dedup.tryAdd(key("etu1", "AB12CD34"))
        assertFalse(dedup.tryAdd(key("etu1", "AB12CD34")))
    }

    @Test
    fun same_student_other_session_is_accepted() {
        val dedup = PresenceDedup()
        dedup.tryAdd(key("etu1", "AB12CD34"))
        assertTrue(dedup.tryAdd(key("etu1", "EF56AB78")))
    }

    @Test
    fun other_student_same_session_is_accepted() {
        val dedup = PresenceDedup()
        dedup.tryAdd(key("etu1", "AB12CD34"))
        assertTrue(dedup.tryAdd(key("etu2", "AB12CD34")))
    }

    @Test
    fun isConfirmed_reflects_history_and_survives_reconstruction() {
        val dedup = PresenceDedup()
        dedup.tryAdd(key("etu1", "AB12CD34"))
        assertTrue(dedup.isConfirmed(key("etu1", "AB12CD34")))

        // Simulate a relaunch: rebuild from persisted keys.
        val restored = PresenceDedup(dedup.keys())
        assertTrue(restored.isConfirmed(key("etu1", "AB12CD34")))
        assertFalse(restored.tryAdd(key("etu1", "AB12CD34")))
    }

    @Test
    fun keys_are_stable_and_parseable() {
        assertEquals("AB12CD34::etu1", PresenceConfirmation.key("etu1", "AB12CD34"))
    }
}
