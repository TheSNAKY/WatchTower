package com.snakydesign.watchtower.android

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.support.v4.app.NotificationCompat
import com.snakydesign.watchtower.WatchTower
import com.snakydesign.watchtower.models.WatchtowerLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch


object WatchTowerAndroid {
    fun notificationFlow(application: Application, serverPort: Int): Flow<Notification> {
        return flow {
            val latestRequests = mutableListOf<WatchtowerLog>()
            val notificationCount = 5

            emitAll(WatchTower.networkFlow().flatMapLatest { log ->
                if (latestRequests.count() > notificationCount)
                    latestRequests.removeAt(0)
                latestRequests.add(log)
                flowOf(
                    application.createNotification(application, serverPort,
                        latestRequests.joinToString("\n") { it.toNotificationTitle() })
                )
            }.onStart {
                emit(application.createNotification(application, serverPort, null))
            })
        }
    }

    private fun WatchtowerLog.toNotificationTitle(): String {
        val status: String
        val method: String
        val url: String
        val footer: String
        val header: String
        fun String.toUrl(): String {
            val queryIndex = this.indexOf("?")
            val urlWithoutQuery = if (queryIndex >= 1) this.substring(0, queryIndex) else this
            return (if (urlWithoutQuery.length > 25) "..." else "") + urlWithoutQuery.takeLast(25)
        }

        fun String.toMethod(): String = "[$this]"
        when (this) {
            is WatchtowerLog.RequestLog -> {
                method = this.request.method.toMethod()
                url = "..." + this.request.url.toUrl()
                status = ""
                footer = ""
                header = "➡️"
            }
            is WatchtowerLog.ResponseLog -> {
                method = this.response.requestData.method.toMethod()
                url = "..." + this.response.requestData.url.toUrl()
                status = if (this.response.responseCode !in 200..299) "❌" else ""
                footer = "(${this.response.responseCode})"
                header = "⬅️"
            }
        }

        return arrayOf(header, method, url, footer, status).joinToString(" ")
    }

    private fun Context.getApplicationName(): String? {
        val applicationInfo = applicationInfo
        val stringId = applicationInfo.labelRes
        return if (stringId == 0) applicationInfo.nonLocalizedLabel.toString() else getString(
            stringId
        )
    }

    private fun Context.createNotification(
        application: android.app.Application,
        serverPort: Int,
        content: String?
    ): Notification {
        val notificationManager = getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "WATCHTOWER_CHANNEL",
                "Watchtower network notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.description = "For network logs"
            notificationManager.createNotificationChannel(channel)
        }
        val mBuilder = NotificationCompat.Builder(this, "WATCHTOWER_CHANNEL")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle((application.getApplicationName() ?: "") + " Watchtower!")
            .setStyle(NotificationCompat.BigTextStyle())
            .setContentText(content ?: "Listening for requests...")
            .setAutoCancel(false)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://127.0.0.1:$serverPort")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pi = PendingIntent.getActivity(this, 0, intent, 0)
        mBuilder.setContentIntent(pi)
        return mBuilder.build()
    }
}