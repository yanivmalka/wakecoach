package com.wakecoach.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.wakecoach.BuildConfig
import com.wakecoach.container
import com.wakecoach.core.update.ReleaseInfo
import com.wakecoach.core.update.UpdateCheck
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState
    data class Downloading(val release: ReleaseInfo, val progress: Float) : UpdateState
    data class Installing(val release: ReleaseInfo) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Self-update from GitHub Releases: the CI workflow publishes a signed wakecoach.apk on every
 * push to main; this checks the latest release, downloads it and hands it to PackageInstaller.
 */
class Updater(private val context: Context) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    val installedVersion: String get() = BuildConfig.VERSION_NAME

    suspend fun check(): UpdateState {
        if (_state.value is UpdateState.Downloading || _state.value is UpdateState.Installing) return _state.value
        _state.value = UpdateState.Checking
        val result = try {
            val release = withContext(Dispatchers.IO) { fetchLatest() }
            if (release != null && UpdateCheck.isNewer(release, BuildConfig.VERSION_CODE)) UpdateState.Available(release)
            else UpdateState.UpToDate
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Network, HTTP or malformed JSON: report, never crash the alarm app over an update.
            UpdateState.Failed(e.message ?: e.javaClass.simpleName)
        }
        _state.value = result
        return result
    }

    /** True when Android still needs the user to allow installs from this app. */
    fun needsInstallPermission(): Boolean = !context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    suspend fun downloadAndInstall(release: ReleaseInfo) {
        try {
            _state.value = UpdateState.Downloading(release, 0f)
            val apk = withContext(Dispatchers.IO) {
                download(release.apkUrl) { p -> _state.value = UpdateState.Downloading(release, p) }
            }
            _state.value = UpdateState.Installing(release)
            withContext(Dispatchers.IO) { install(apk) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = UpdateState.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    internal fun onInstallResult(status: Int, message: String?) {
        if (status != PackageInstaller.STATUS_SUCCESS) {
            _state.value = UpdateState.Failed(message ?: "install status $status")
        }
        // On success the app process is replaced; nothing to do.
    }

    fun dismissError() {
        if (_state.value is UpdateState.Failed) _state.value = UpdateState.Idle
    }

    private fun fetchLatest(): ReleaseInfo? {
        val conn = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "WakeCoach/${BuildConfig.VERSION_NAME}")
        }
        try {
            when (conn.responseCode) {
                HttpURLConnection.HTTP_OK -> Unit
                HttpURLConnection.HTTP_NOT_FOUND -> return null // no release published yet
                else -> throw IOException("GitHub responded ${conn.responseCode}")
            }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val assets = json.optJSONArray("assets")
            val files = (0 until (assets?.length() ?: 0)).map {
                val a = assets!!.getJSONObject(it)
                a.getString("name") to a.getString("browser_download_url")
            }
            return UpdateCheck.parse(json.getString("tag_name"), json.optString("body"), files)
        } finally {
            conn.disconnect()
        }
    }

    private fun download(url: String, onProgress: (Float) -> Unit): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "wakecoach.apk")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "WakeCoach/${BuildConfig.VERSION_NAME}")
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("Download failed: ${conn.responseCode}")
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0) onProgress(read.toFloat() / total)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        return target
    }

    private fun install(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("wakecoach.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val callback = PendingIntent.getBroadcast(
                context, sessionId, Intent(context, InstallResultReceiver::class.java),
                // The installer fills in status extras, so the intent must be mutable.
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(callback.intentSender)
        }
    }

    companion object {
        const val LATEST_URL = "https://api.github.com/repos/yanivmalka/wakecoach/releases/latest"
    }
}

/** Receives PackageInstaller results; asks the user to confirm when Android requires it. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
            context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        context.container.updater.onInstallResult(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
    }
}
