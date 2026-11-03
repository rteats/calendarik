package com.rteats.calendarik.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import com.rteats.calendarik.diagnostics.NetworkDiagnostics
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.rteats.calendarik.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val MANIFEST_URL =
    "https://github.com/rteats/calendarik/releases/download/latest/update.json"
private const val APK_URL =
    "https://github.com/rteats/calendarik/releases/download/latest/Calendarik.apk"
private const val MAX_APK_BYTES = 150L * 1024 * 1024

data class UpdateManifest(val versionCode: Int, val versionName: String, val sha256: String)

object GithubUpdater {
    private suspend fun <T> traced(
        context: Context,
        name: String,
        block: suspend () -> T,
    ): T {
        val diagnostics = NetworkDiagnostics.get(context)
        val started = SystemClock.elapsedRealtime()
        diagnostics.log("github", name, "start")
        return try {
            block().also { diagnostics.log("github", name, "ok", SystemClock.elapsedRealtime() - started) }
        } catch (failure: Exception) {
            diagnostics.log("github", name, "error",
                SystemClock.elapsedRealtime() - started, failure = failure)
            throw failure
        }
    }

    suspend fun check(context: Context): UpdateManifest = traced(context, "update_check") {
        withContext(Dispatchers.IO) {
        val request = URL(MANIFEST_URL).openConnection() as HttpURLConnection
        request.connectTimeout = 15_000
        request.readTimeout = 15_000
        request.setRequestProperty("Accept", "application/json")
        request.setRequestProperty("Cache-Control", "no-cache")
        try {
            val httpStatus = request.responseCode
            NetworkDiagnostics.get(context).log("github", "update_check_http", "http_$httpStatus")
            check(httpStatus == 200) { "HTTP " + httpStatus }
            val bytes = request.inputStream.use { it.readBytes() }
            require(bytes.size < 4096) { "Неверный файл метаданных" }
            parseManifest(bytes.toString(Charsets.UTF_8))
        } finally {
            request.disconnect()
        }
        }
    }

    internal fun parseManifest(payload: String): UpdateManifest {
        val json = JSONObject(payload)
        val code = json.getInt("versionCode")
        val name = json.getString("versionName")
        val sha = json.getString("sha256").lowercase()
        require(code > 0)
        require(Regex("""\d{4}\.\d{2}\.\d{2}\.\d+""").matches(name))
        require(Regex("[a-f0-9]{64}").matches(sha))
        return UpdateManifest(code, name, sha)
    }

    suspend fun downloadAndCheck(context: Context, release: UpdateManifest): File =
        traced(context, "update_download_verify") {
            withContext(Dispatchers.IO) {
            require(release.versionCode > BuildConfig.VERSION_CODE) { "Это не новая версия" }
            val folder = File(context.cacheDir, "updates")
            check(folder.exists() || folder.mkdirs()) { "Не удалось создать каталог загрузок" }
            val part = File(folder, "Calendarik.apk.part")
            val dest = File(folder, "Calendarik.apk")
            part.delete()
            val request = URL(APK_URL).openConnection() as HttpURLConnection
            request.connectTimeout = 15_000
            request.readTimeout = 60_000
            try {
                val httpStatus = request.responseCode
                NetworkDiagnostics.get(context).log("github", "update_download_http", "http_$httpStatus")
                check(httpStatus == 200) { "HTTP " + httpStatus }
                val digest = MessageDigest.getInstance("SHA-256")
                var total = 0L
                request.inputStream.use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count == -1) break
                            total += count
                            check(total <= MAX_APK_BYTES) { "Файл APK слишком большой" }
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                        }
                    }
                }
                require(total > 0) { "Пустой файл" }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                require(actual == release.sha256) { "Контрольная сумма APK не совпадает" }
                check(part.renameTo(dest)) { "Не удалось сохранить APK" }
                verifyPackage(context, dest, release.versionCode)
                dest
            } catch (error: Exception) {
                part.delete()
                dest.delete()
                throw error
            } finally {
                request.disconnect()
            }
            }
        }

    fun installIntent(context: Context, file: File): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            val uri = FileProvider.getUriForFile(
                context, context.packageName + ".fileprovider", file
            )
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    private fun verifyPackage(context: Context, archive: File, expectedCode: Int) {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
            else PackageManager.GET_SIGNATURES
        @Suppress("DEPRECATION")
        val incoming = pm.getPackageArchiveInfo(archive.absolutePath, flags)
            ?: error("Повреждённый APK")
        @Suppress("DEPRECATION")
        val installed = pm.getPackageInfo(context.packageName, flags)
        require(incoming.packageName == context.packageName) { "Неверный идентификатор приложения" }
        require(PackageInfoCompat.getLongVersionCode(incoming) == expectedCode.toLong()) {
            "Версия APK отличается от указанной в манифесте"
        }
        val currentSigners = signatures(installed)
        val nextSigners = signatures(incoming)
        require(currentSigners.isNotEmpty() && nextSigners.isNotEmpty() &&
            currentSigners.size == nextSigners.size &&
            nextSigners.all { new -> currentSigners.any { old -> old.contentEquals(new) } }
        ) { "Сертификат подписи APK не совпадает" }
    }

    @Suppress("DEPRECATION")
    private fun signatures(info: PackageInfo): List<ByteArray> =
        if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners?.map { it.toByteArray() }.orEmpty()
        } else {
            info.signatures?.map { it.toByteArray() }.orEmpty()
        }
}

