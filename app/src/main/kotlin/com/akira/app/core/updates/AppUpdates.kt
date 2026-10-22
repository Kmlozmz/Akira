package com.akira.app.core.updates

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.akira.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class AppUpdate(
    val versionName: String,
    val releaseNotes: String,
    val releaseUrl: String,
    val downloadUrl: String,
    val fileName: String,
    val size: Long,
    val sha256: String,
    val prerelease: Boolean,
)

data class UpdateInstaller(val intent: Intent, val versionCode: Long)

sealed interface UpdateCheck {
    data class Available(val update: AppUpdate) : UpdateCheck
    data object UpToDate : UpdateCheck
    data object NoRelease : UpdateCheck
}

enum class UpdateFailure { NETWORK, RATE_LIMIT, INVALID_RELEASE, INVALID_APK, STORAGE }

class UpdateException(val reason: UpdateFailure) : IOException(reason.name)

/** Updates use public release assets; account cookies and API credentials are never attached. */
class AppUpdates(context: Context) {
    private val app = context.applicationContext
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.MINUTES)
        .followSslRedirects(false)
        .build()
    private val repository = "Kmlozmz/Akira"
    @Volatile private var downloadCall: Call? = null
    private val releasePrefix = "https://github.com/$repository/releases/"
    private val directory get() = File(app.cacheDir, "updates")

    suspend fun check(): UpdateCheck = withContext(Dispatchers.IO) {
        if (BuildConfig.DEBUG) return@withContext UpdateCheck.NoRelease
        val installed = ReleaseVersion.parse(BuildConfig.VERSION_NAME)
            ?: throw UpdateException(UpdateFailure.INVALID_RELEASE)
        val request = Request.Builder()
            .url("https://api.github.com/repos/$repository/releases?per_page=20")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Akira/${BuildConfig.VERSION_NAME}")
            .header("X-GitHub-Api-Version", "2026-03-10")
            .build()
        val releases = response(request).use { response ->
            when (response.code) {
                404 -> return@withContext UpdateCheck.NoRelease
                403, 429 -> throw UpdateException(UpdateFailure.RATE_LIMIT)
            }
            if (!response.isSuccessful) throw UpdateException(UpdateFailure.NETWORK)
            val body = response.body ?: throw UpdateException(UpdateFailure.INVALID_RELEASE)
            org.json.JSONArray(body.source().readUtf8Limited(2L * 1024 * 1024))
        }
        var compatible = false
        var newest: AppUpdate? = null
        var newestVersion: ReleaseVersion? = null
        for (index in 0 until releases.length()) {
            currentCoroutineContext().ensureActive()
            val release = releases.getJSONObject(index)
            if (release.optBoolean("draft")) continue
            val tag = release.optString("tag_name")
            if (!tag.startsWith('v')) continue
            val version = tag.removePrefix("v")
            val parsed = ReleaseVersion.parse(version) ?: continue
            val prerelease = release.optBoolean("prerelease") || parsed.isPrerelease
            if (prerelease && !installed.isPrerelease) continue
            val assets = release.optJSONArray("assets") ?: continue
            val fileName = "Akira-$version.apk"
            val apkAsset = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name") == fileName && it.optString("state") == "uploaded" } ?: continue
            val url = apkAsset.getString("browser_download_url")
            val assetDigest = apkAsset.optString("digest")
            if (!assetDigest.startsWith("sha256:")) continue
            val hash = assetDigest.removePrefix("sha256:").lowercase()
            val size = apkAsset.getLong("size")
            val releaseUrl = release.optString("html_url")
            if (!allowedAsset(url) || !releaseUrl.startsWith(releasePrefix + "tag/") ||
                !Regex("Akira-[A-Za-z0-9.+-]+\\.apk").matches(fileName) ||
                !Regex("[a-f0-9]{64}").matches(hash) || size !in 1..MAX_APK_BYTES)
                throw UpdateException(UpdateFailure.INVALID_RELEASE)
            compatible = true
            if (parsed > installed && (newestVersion == null || parsed > newestVersion)) {
                newestVersion = parsed
                newest = AppUpdate(version, release.optString("body").takeUnless { it == "null" }.orEmpty(), releaseUrl,
                    url, fileName, size, hash, prerelease)
            }
        }
        newest?.let { UpdateCheck.Available(it) } ?: if (compatible) UpdateCheck.UpToDate else UpdateCheck.NoRelease
    }

    suspend fun download(update: AppUpdate, onProgress: (Long) -> Unit, onVerifying: () -> Unit): File = withContext(Dispatchers.IO) {
        if (!allowedAsset(update.downloadUrl)) throw UpdateException(UpdateFailure.INVALID_RELEASE)
        if (!directory.exists() && !directory.mkdirs()) throw UpdateException(UpdateFailure.STORAGE)
        val destination = File(directory, update.fileName)
        val partial = File(directory, update.fileName + ".part")
        if (destination.isFile && destination.length() == update.size && verifyDigest(destination, update.sha256)) {
            onVerifying()
            currentCoroutineContext().ensureActive()
            verifyPackage(destination, update)
            return@withContext destination
        }
        if (directory.usableSpace < update.size) throw UpdateException(UpdateFailure.STORAGE)
        // Keep at most the selected update; previous downloads can be recreated from Releases.
        directory.listFiles()?.filter { it != destination }?.forEach { it.delete() }
        try {
            response(Request.Builder().url(update.downloadUrl).build(), downloading = true).use { response ->
                if (!response.isSuccessful) throw UpdateException(UpdateFailure.NETWORK)
                val body = response.body ?: throw UpdateException(UpdateFailure.NETWORK)
                if (body.contentLength() > MAX_APK_BYTES) throw UpdateException(UpdateFailure.INVALID_APK)
                var received = 0L
                var reported = -1
                body.byteStream().use { input ->
                    val output = try { partial.outputStream() }
                    catch (_: IOException) { throw UpdateException(UpdateFailure.STORAGE) }
                    output.use {
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            received += count
                            if (received > update.size) throw UpdateException(UpdateFailure.INVALID_APK)
                            try { output.write(buffer, 0, count) }
                            catch (_: IOException) { throw UpdateException(UpdateFailure.STORAGE) }
                            val percent = (received * 100 / update.size).toInt()
                            if (percent != reported) { reported = percent; onProgress(received) }
                        }
                    }
                }
                onVerifying()
                currentCoroutineContext().ensureActive()
                if (received != update.size || !verifyDigest(partial, update.sha256)) {
                    throw UpdateException(UpdateFailure.INVALID_APK)
                }
                verifyPackage(partial, update)
                if (!partial.renameTo(destination)) throw UpdateException(UpdateFailure.STORAGE)
            }
            destination
        } finally {
            downloadCall = null
            partial.delete()
        }
    }

    fun canInstall(): Boolean = app.packageManager.canRequestPackageInstalls()

    fun cancelRequests() { downloadCall?.cancel(); client.dispatcher.cancelAll() }

    fun installPermissionIntent(): Intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        android.net.Uri.parse("package:${app.packageName}"))

    fun prepareInstaller(file: File, update: AppUpdate): UpdateInstaller {
        if (!file.isFile || file.canonicalFile.parentFile != directory.canonicalFile ||
            file.name != update.fileName || !verifyDigest(file, update.sha256)) {
            throw UpdateException(UpdateFailure.INVALID_APK)
        }
        val versionCode = verifyPackage(file, update)
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .putExtra(Intent.EXTRA_RETURN_RESULT, true)
            .apply { clipData = android.content.ClipData.newRawUri("Akira update", uri) }
        return UpdateInstaller(intent, versionCode)
    }

    @Suppress("DEPRECATION")
    private fun verifyPackage(file: File, update: AppUpdate): Long {
        val pm = app.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw UpdateException(UpdateFailure.INVALID_APK)
        val installed = pm.getPackageInfo(app.packageName, flags)
        val code = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        if (archive.packageName != app.packageName || code <= BuildConfig.VERSION_CODE ||
            archive.versionName != update.versionName || !isNewerReleaseVersion(update.versionName, BuildConfig.VERSION_NAME) ||
            archive.applicationInfo?.minSdkVersion?.let { it > Build.VERSION.SDK_INT } != false) {
            throw UpdateException(UpdateFailure.INVALID_APK)
        }
        fun android.content.pm.PackageInfo.signers(): Set<String> {
            val signatures = if (Build.VERSION.SDK_INT >= 28) signingInfo?.apkContentsSigners else this.signatures
            return signatures.orEmpty().map { signature -> digest(signature.toByteArray()) }.toSet()
        }
        val signers = archive.signers()
        if (signers.isEmpty() || signers != installed.signers()) throw UpdateException(UpdateFailure.INVALID_APK)
        return code
    }

    private fun allowedAsset(url: String): Boolean = url.startsWith(releasePrefix + "download/") &&
        runCatching { android.net.Uri.parse(url).let { it.scheme == "https" && it.host == "github.com" && it.userInfo == null } }.getOrDefault(false)

    private fun verifyDigest(file: File, expected: String): Boolean {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val count = input.read(buffer); if (count < 0) break; md.update(buffer, 0, count) }
        }
        return md.digest().toHex() == expected
    }

    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun okio.BufferedSource.readUtf8Limited(limit: Long): String {
        if (request(limit + 1)) throw UpdateException(UpdateFailure.INVALID_RELEASE)
        return readUtf8()
    }

    private suspend fun response(request: Request, downloading: Boolean = false): Response = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        if (downloading) downloadCall = call
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                continuation.resumeWithException(error)
            }
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, value, _ -> value.close() }
            }
        })
    }

    companion object { private const val MAX_APK_BYTES = 250L * 1024 * 1024 }
}
