package com.rteats.calendarik.diagnostics

import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Pure JVM ZIP assembly. The only log input is the bounded, scrubbed journal. */
object DiagnosticsArchive {
    private val nameTime = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
        .withZone(ZoneOffset.UTC)

    fun fileName(instant: Instant): String =
        "Calendarik-diagnostics-${nameTime.format(instant)}-UTC.zip"

    fun create(
        log: String,
        versionName: String,
        versionCode: Int,
        sdk: Int,
        instant: Instant,
    ): ByteArray {
        val about = """
            Calendarik network diagnostics
            Created UTC: $instant
            Version: $versionName
            Version code: $versionCode
            Android API level: $sdk

            Network operations are recorded at the application layer.
            This is not packet capture or decrypted Firebase transport traffic.
            Logged: operation, outcome, duration, error type/code, coarse counts.
            Not logged: usernames, email, UIDs, event text, passwords,
            access tokens, pair QR codes, cryptographic keys or cloud payloads.
            Review the ZIP before sharing it.
        """.trimIndent() + "\n"
        return ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { zip ->
                fun entry(name: String, text: String) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(text.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
                entry("network.log", log)
                entry("info.txt", about)
            }
            bytes.toByteArray()
        }
    }
}

