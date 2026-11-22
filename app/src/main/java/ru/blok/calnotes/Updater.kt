package ru.blok.calnotes

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Информация о новой версии из version.json (ассет последнего релиза на GitHub). */
data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val changelog: String,
    val sha256: String?,
)

/** Автообновление: version.json + APK берутся из последнего релиза публичного репозитория GitHub. */
object Updater {
    const val REPO = "BlockRoes/zametki-kalendar"
    private const val VERSION_URL = "https://github.com/$REPO/releases/latest/download/version.json"

    fun installedCode(ctx: Context): Long {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
    }

    fun installedName(ctx: Context): String =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "ZametkiKalendar-Updater")
        }

    /** Возвращает новую версию, null — если новее нет. Бросает исключение при сетевой ошибке. */
    fun checkBlocking(ctx: Context): UpdateInfo? {
        val conn = open(VERSION_URL)
        try {
            if (conn.responseCode != 200) throw java.io.IOException("HTTP ${conn.responseCode}")
            val o = JSONObject(conn.inputStream.use { String(it.readBytes(), Charsets.UTF_8) })
            val info = UpdateInfo(
                versionCode = o.getLong("versionCode"),
                versionName = o.getString("versionName"),
                apkUrl = o.getString("apkUrl"),
                changelog = o.optString("changelog", ""),
                sha256 = o.optString("sha256", "").takeIf { it.isNotBlank() },
            )
            // только https и только наш репозиторий
            val u = Uri.parse(info.apkUrl)
            if (u.scheme != "https" || !info.apkUrl.startsWith("https://github.com/$REPO/")) return null
            return if (info.versionCode > installedCode(ctx)) info else null
        } finally {
            conn.disconnect()
        }
    }

    /** Скачивает APK в cache/updates, проверяет sha256 (если указан). */
    fun downloadBlocking(ctx: Context, info: UpdateInfo, onProgress: (Int) -> Unit): File {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "update-${info.versionCode}.apk")
        val conn = open(info.apkUrl)
        try {
            if (conn.responseCode != 200) throw java.io.IOException("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong
            val md = MessageDigest.getInstance("SHA-256")
            conn.inputStream.use { inp ->
                out.outputStream().use { os ->
                    val buf = ByteArray(32 * 1024)
                    var done = 0L
                    while (true) {
                        val n = inp.read(buf)
                        if (n < 0) break
                        os.write(buf, 0, n); md.update(buf, 0, n); done += n
                        if (total > 0) onProgress((done * 100 / total).toInt())
                    }
                }
            }
            val hex = md.digest().joinToString("") { "%02x".format(it) }
            if (info.sha256 != null && !hex.equals(info.sha256, ignoreCase = true)) {
                out.delete()
                throw java.io.IOException("checksum mismatch")
            }
            return out
        } finally {
            conn.disconnect()
        }
    }

    fun canInstall(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(ctx: Context) {
        try {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            try {
                ctx.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) { }
        }
    }

    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
