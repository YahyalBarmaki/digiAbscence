package sn.uadb.gesabscence.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionIdTest {

    @Test
    fun generates_eight_uppercase_hex_chars() {
        val id = SessionId.generate()
        assertEquals(8, id.length)
        assertTrue("unexpected chars in $id", id.all { it in '0'..'9' || it in 'A'..'F' })
    }

    @Test
    fun generates_distinct_ids() {
        val ids = List(500) { SessionId.generate() }
        assertNotEquals(1, ids.toSet().size)
    }
}
