package com.geoguy89.cribbage

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Self-update from GitHub Releases: download the release APK, then install it through a PackageInstaller session. On
 * Android 12+ an app updating itself needs no confirmation screen (after the first "allow from this source"); older
 * versions show Android's prompt. Android only accepts it when it's signed with the same key as the installed app,
 * which CI guarantees by signing every release with the repository's key.
 */
object Updater {
    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 30_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "MaineCozyCribbage-Android")
    }

    fun install(context: Context, apkUrl: String, progress: (Float) -> Unit, done: (String?) -> Unit) {
        thread(isDaemon = true, name = "cribbage-update") {
            try {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val apk = File(dir, "update.apk")
                var c = open(apkUrl)
                // GitHub answers asset downloads with a redirect to its CDN on another host.
                var hops = 0
                while (c.responseCode in 300..399 && hops++ < 5) c = open(c.getHeaderField("Location"))
                if (c.responseCode != 200) throw IllegalStateException("Download failed (${c.responseCode}).")
                val total = c.contentLengthLong
                c.inputStream.use { input ->
                    apk.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var read = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            read += n
                            if (total > 0) progress((read.toFloat() / total).coerceAtMost(1f))
                        }
                    }
                }
                val installer = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(context.packageName)
                    if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
                val id = installer.createSession(params)
                installer.openSession(id).use { session ->
                    session.openWrite("cribbage.apk", 0, apk.length()).use { out -> apk.inputStream().use { it.copyTo(out) }; session.fsync(out) }
                    val intent = Intent(context, InstallStatusReceiver::class.java).setAction(InstallStatusReceiver.ACTION)
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                    session.commit(PendingIntent.getBroadcast(context, id, intent, flags).intentSender)
                }
                done(null)
            } catch (e: Exception) {
                Log.w("Updater", "Update failed", e)
                done(e.message ?: "The update couldn't be installed.")
            }
        }
    }
}

/** Receives the installer's result; when Android needs the user to confirm, it opens that screen. */
class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // The installer closes the game; UpdatedReceiver offers to reopen it.
            else -> Log.w("AndroidUpdater", "Install status: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
        }
    }

    companion object {
        const val ACTION = "com.geoguy89.cribbage.INSTALL_STATUS"
    }
}

/**
 * The game was just replaced by a newer version (the installer stops the old one). Android doesn't let an app
 * relaunch itself from the background, so it posts a notification to tap instead.
 */
class UpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
        val nm = context.getSystemService(android.app.NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                android.app.NotificationChannel(CHANNEL, "Updates", android.app.NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "When the game has updated itself" },
            )
        }
        val open = PendingIntent.getActivity(
            context, 1, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = androidx.core.app.NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_moose)
            .setContentTitle("Maine Cozy Cribbage updated")
            .setContentText(if (version.isNotEmpty()) "Version $version is ready. Tap to deal." else "Tap to deal.")
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { nm.notify(UPDATED_ID, n) } // Without the notification permission there's simply no notice.
    }

    companion object {
        const val CHANNEL = "updates"
        const val UPDATED_ID = 4242
    }
}
