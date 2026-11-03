package com.rteats.calendarik.diagnostics

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.File
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Bounded, non-blocking journal of APPLICATION-LEVEL network operations.
 *
 * No email, UID, pair ID, file path, URL query, event content, mood,
 * request/response body, encryption key, auth token or exception message
 * ever reaches this file. Log only typed operation names and result codes.
 */
class NetworkDiagnostics private constructor(context: Context) {
    private val application = context.applicationContext
    private val folder = File(application.filesDir, "network_diagnostics")
    private val journal = File(folder, "network.log")

    fun log(
        area: String,
        operation: String,
        status: String,
        durationMs: Long = -1L,
        count: Int = -1,
        failure: Throwable? = null,
    ) {
        val a = safeToken(area, 32)
        val op = safeToken(operation, 48)
        val state = safeToken(status, 24)
        val elapsed = durationMs.coerceIn(-1L, 86_400_000L)
        val items = count.coerceIn(-1, 100_000)
        val error = failure?.let(::safeFailure) ?: "none"
        val row = "${Instant.now()} [$a] operation=$op state=$state ms=$elapsed count=$items failure=$error\n"
        writer.execute {
            runCatching {
                synchronized(lock) {
                    folder.mkdirs()
                    journal.appendText(row)
                    trimLocked()
                }
            }
        }
    }

    fun logConnectivity() {
        val type = runCatching {
            val cm = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            when {
                caps == null -> "offline"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                else -> "other"
            }
        }.getOrDefault("unknown")
        log("network", "transport_$type", "observed")
    }

    /** Wait for all pending writes, then return the bounded journal. Call on IO dispatcher. */
    fun read(): String = writer.submit<String> {
        synchronized(lock) {
            if (journal.exists()) journal.readText() else ""
        }
    }.get(10, TimeUnit.SECONDS)

    fun clear() {
        writer.submit {
            synchronized(lock) { journal.delete() }
        }.get(10, TimeUnit.SECONDS)
    }

    private fun trimLocked() {
        if (!journal.exists() || journal.length() <= MAX_BYTES) return
        val lines = journal.readLines()
        journal.writeText(lines.takeLast(MAX_LINES).joinToString("\n", postfix = "\n"))
    }

    companion object {
        private const val MAX_BYTES = 512 * 1024L
        private const val MAX_LINES = 2_000
        private val lock = Any()
        private val writer = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "CalendarikDiagnostics").apply { isDaemon = true }
        }
        @Volatile private var instance: NetworkDiagnostics? = null

        fun get(context: Context): NetworkDiagnostics =
            instance ?: synchronized(this) {
                instance ?: NetworkDiagnostics(context).also { instance = it }
            }

        fun safeToken(value: String, maxLength: Int): String =
            value.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(maxLength)

        fun safeFailure(error: Throwable): String {
            val type = safeToken(error.javaClass.simpleName, 56)
            val code = when (error) {
                is FirebaseFirestoreException -> safeToken(error.code.name, 36)
                is FirebaseAuthException -> safeToken(error.errorCode, 36)
                else -> "unknown"
            }
            return "$type/$code"
        }
    }
}

