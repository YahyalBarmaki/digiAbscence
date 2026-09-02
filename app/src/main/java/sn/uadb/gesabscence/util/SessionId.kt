package sn.uadb.gesabscence.util

import java.util.UUID

/**
 * Short, URL-safe session identifier fit for BLE manufacturer data
 * (payload budget is ~23 bytes). We keep 8 hex chars = 4 bytes, which is
 * plenty to disambiguate concurrent classes while staying scannable.
 */
object SessionId {
    fun generate(): String =
        UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
}
