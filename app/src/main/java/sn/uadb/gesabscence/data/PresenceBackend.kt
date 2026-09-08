package sn.uadb.gesabscence.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import sn.uadb.gesabscence.data.model.PresenceConfirmation

/** Region of the deployed Cloud Functions (see the functions project). */
private const val FUNCTIONS_REGION = "europe-west1"

/**
 * Server side of a presence confirmation: the `confirmPresence` callable
 * Cloud Function. Kept behind an interface so the app builds and runs with
 * no Firebase config (see [NoopPresenceBackend]).
 */
interface PresenceBackend {
    val isAvailable: Boolean

    /** Push one confirmation. Success also covers the server's "already recorded". */
    suspend fun confirm(confirmation: PresenceConfirmation): Result<Unit>
}

object NoopPresenceBackend : PresenceBackend {
    override val isAvailable = false
    override suspend fun confirm(confirmation: PresenceConfirmation): Result<Unit> =
        Result.failure(IllegalStateException("Backend Firebase non configuré (google-services.json absent)."))
}

class FirebaseFunctionsPresenceBackend(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance(FUNCTIONS_REGION),
) : PresenceBackend {

    override val isAvailable = true

    override suspend fun confirm(confirmation: PresenceConfirmation): Result<Unit> = runCatching {
        val payload = hashMapOf<String, Any>(
            "sessionId" to confirmation.sessionId,
            "studentId" to confirmation.studentId,
            "deviceTimestamp" to confirmation.timestampMillis,
        )
        functions.getHttpsCallable("confirmPresence").call(payload).await()
        Unit
    }
}

/** Picks the real backend when Firebase is initialised, else a no-op. */
object PresenceBackendFactory {
    fun create(context: Context): PresenceBackend =
        if (FirebaseApp.getApps(context).isNotEmpty()) {
            FirebaseFunctionsPresenceBackend()
        } else {
            NoopPresenceBackend
        }
}
