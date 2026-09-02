package sn.uadb.gesabscence.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import sn.uadb.gesabscence.util.SessionId

class BleContractTest {

    @Test
    fun session_id_round_trips_through_manufacturer_bytes() {
        repeat(100) {
            val id = SessionId.generate()
            val bytes = BleContract.sessionIdToBytes(id)
            assertEquals(BleContract.SESSION_ID_BYTES, bytes.size)
            assertEquals(id, BleContract.sessionIdFromBytes(bytes))
        }
    }

    @Test
    fun rejects_too_short_payload() {
        assertNull(BleContract.sessionIdFromBytes(byteArrayOf(0x41, 0x42)))
        assertNull(BleContract.sessionIdFromBytes(null))
    }

    @Test
    fun rejects_non_hex_payload() {
        assertNull(BleContract.sessionIdFromBytes("ZZZZZZZZ".toByteArray(Charsets.US_ASCII)))
    }
}
