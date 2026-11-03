package com.rteats.calendarik.diagnostics

import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.zip.ZipInputStream
import org.junit.Assert.*
import org.junit.Test

class DiagnosticsArchiveTest {
    @Test fun producesAValidZipWithJournalAndVersionInfo() {
        val time = Instant.parse("2026-11-03T12:00:00Z")
        val name = DiagnosticsArchive.fileName(time)
        assertEquals("Calendarik-diagnostics-2026-11-03_12-00-00-UTC.zip", name)
        val bytes = DiagnosticsArchive.create(
            log = "2026-11-03 [sync] operation=cycle state=ok\n",
            versionName = "2026.11.03.7", versionCode = 1000008,
            sdk = 36, instant = time
        )
        val files = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { archive ->
            while (true) {
                val entry = archive.nextEntry ?: break
                files[entry.name] = archive.readBytes().toString(Charsets.UTF_8)
                archive.closeEntry()
            }
        }
        assertEquals(setOf("network.log", "info.txt"), files.keys)
        assertTrue(files.getValue("network.log").contains("operation=cycle"))
        assertTrue(files.getValue("info.txt").contains("2026.11.03.7"))
    }
}
