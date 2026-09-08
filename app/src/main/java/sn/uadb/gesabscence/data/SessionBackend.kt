package sn.uadb.gesabscence.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

private const val FUNCTIONS_REGION = "europe-west1"

/** Session lifecycle on the backend: `openSession` / `closeSession` callables. */
interface SessionBackend {
    val isAvailable: Boolean

    suspend fun open(
        sessionId: String,
        classId: String,
        teacherId: String,
        autoCloseInMinutes: Int? = DEFAULT_AUTO_CLOSE_MINUTES,
    ): Result<Unit>

    suspend fun close(sessionId: String): Result<Unit>

    companion object {
        const val DEFAULT_AUTO_CLOSE_MINUTES = 30
    }
}

object NoopSessionBackend : SessionBackend {
    override val isAvailable = false
    override suspend fun open(
        sessionId: String,
        classId: String,
        teacherId: String,
        autoCloseInMinutes: Int?,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun close(sessionId: String): Result<Unit> = Result.success(Unit)
}

class FirebaseFunctionsSessionBackend(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance(FUNCTIONS_REGION),
) : SessionBackend {

    override val isAvailable = true

    override suspend fun open(
        sessionId: String,
        classId: String,
        teacherId: String,
        autoCloseInMinutes: Int?,
    ): Result<Unit> = runCatching {
        val payload = hashMapOf<String, Any>(
            "sessionId" to sessionId,
            "classId" to classId,
            "teacherId" to teacherId,
        )
        if (autoCloseInMinutes != null) payload["autoCloseInMinutes"] = autoCloseInMinutes
        functions.getHttpsCallable("openSession").call(payload).await()
        Unit
    }

    override suspend fun close(sessionId: String): Result<Unit> = runCatching {
        functions.getHttpsCallable("closeSession")
            .call(hashMapOf("sessionId" to sessionId))
            .await()
        Unit
    }
}

object SessionBackendFactory {
    fun create(context: Context): SessionBackend =
        if (FirebaseApp.getApps(context.applicationContext).isNotEmpty()) {
            FirebaseFunctionsSessionBackend()
        } else {
            NoopSessionBackend
        }
}
