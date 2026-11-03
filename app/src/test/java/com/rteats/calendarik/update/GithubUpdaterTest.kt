package com.rteats.calendarik.update

import com.rteats.calendarik.BuildConfig
import org.junit.Assert.*
import org.junit.Test

class GithubUpdaterTest {
    @Test fun localBuildCanReturnToSameCodeReleaseButCannotDowngradeDatabase() {
        val same = UpdateManifest(BuildConfig.VERSION_CODE, "2026.11.03.10", "a".repeat(64), BuildConfig.DATABASE_VERSION)
        assertTrue(GithubUpdater.canInstall(same))
        assertFalse(GithubUpdater.canInstall(same.copy(versionCode = BuildConfig.VERSION_CODE - 1)))
        assertFalse(GithubUpdater.canInstall(same.copy(databaseVersion = BuildConfig.DATABASE_VERSION - 1)))
    }

    @Test fun parsesLegacyAndPinnedManifests() {
        val legacy = """{"versionCode":1000013,"versionName":"2026.11.03.9","sha256":"${"a".repeat(64)}"}"""
        assertEquals(5, GithubUpdater.parseManifest(legacy).databaseVersion)
        val pinned = legacy.dropLast(1) + """, "databaseVersion":6,"apkUrl":"https://github.com/rteats/calendarik/releases/download/build-1000014-aaaaaaaaaaaa/Calendarik.apk"}"""
        assertEquals(6, GithubUpdater.parseManifest(pinned).databaseVersion)
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsForeignApkUrl() {
        GithubUpdater.parseManifest("""{"versionCode":1,"versionName":"2026.11.03.9","sha256":"${"a".repeat(64)}","apkUrl":"https://example.com/app.apk"}""")
    }
}
