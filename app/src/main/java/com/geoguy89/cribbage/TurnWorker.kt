package com.geoguy89.cribbage

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.geoguy89.cribbage.data.PrefsKeyValueStore
import com.geoguy89.cribbage.data.Store
import com.geoguy89.cribbage.net.OnlineConfig
import com.geoguy89.cribbage.net.SyncResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Checks in with the game server every so often while the game is closed, and posts a notification when a friend
 * has moved (so it's your turn), invited you to a game, or asked to be friends. No push service needed.
 */
class TurnWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (foreground) return Result.success()
        val store = Store(PrefsKeyValueStore(applicationContext))
        if (!store.settings().notifications) return Result.success()
        val acc = store.account() ?: return Result.success()
        val body = withContext(Dispatchers.IO) {
            runCatching {
                OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build().newCall(
                    Request.Builder().url(OnlineConfig.baseUrl + "/v1/sync").header("Authorization", "Bearer ${acc.playerId}.${acc.secret}").build(),
                ).execute().use { if (it.isSuccessful) it.body?.string() else null }
            }.getOrNull()
        } ?: return Result.retry()
        val sync = runCatching { Json { ignoreUnknownKeys = true }.decodeFromString(SyncResponse.serializer(), body) }.getOrNull() ?: return Result.success()

        // Tell the player about each thing once.
        val seen = store.notified()
        val fresh = mutableListOf<Pair<String, String>>()
        for (g in sync.games.filter { it.myMove && !it.over }) {
            val key = "turn-${g.id}-${g.hand}-${g.myScore}-${g.theirScore}"
            if (key !in seen) fresh += key to "Your turn against ${g.name} (${g.myScore}–${g.theirScore})"
        }
        for (i in sync.invites.filter { it.incoming }) if ("invite-${i.id}" !in seen) fresh += "invite-${i.id}" to "${i.name} invited you to a game of cribbage"
        for (r in sync.incoming) if ("friend-${r.id}" !in seen) fresh += "friend-${r.id}" to "${r.name} wants to be friends"
        if (fresh.isEmpty()) return Result.success()
        store.saveNotified((seen.filterKeys { it.startsWith("shown-") } + seen.filterKeys { k -> !k.startsWith("shown-") }.entries.toList().takeLast(150).associate { it.key to it.value }) + fresh.associate { it.first to 1 })
        notify(applicationContext, fresh.map { it.second })
        return Result.success()
    }

    companion object {
        const val EXTRA_FRIENDS = "open_friends"
        private const val CHANNEL = "turns"
        @Volatile var foreground = false

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<TurnWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("turns", ExistingPeriodicWorkPolicy.KEEP, req)
        }

        private fun notify(context: Context, lines: List<String>) {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Games with friends", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Your turn, invitations and friend requests"
            })
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).putExtra(EXTRA_FRIENDS, true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_moose)
                .setContentTitle(if (lines.size == 1) "Maine Cozy Cribbage" else "${lines.size} things waiting at the table")
                .setContentText(lines.first())
                .setStyle(NotificationCompat.InboxStyle().also { s -> lines.forEach(s::addLine) })
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(1, n)
        }
    }
}
