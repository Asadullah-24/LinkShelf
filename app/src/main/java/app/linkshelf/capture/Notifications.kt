package app.linkshelf.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.linkshelf.R
import app.linkshelf.ui.MainActivity

object Notifications {

    const val CHANNEL_BUBBLE = "floating_button"
    const val BUBBLE_NOTIFICATION_ID = 1001

    fun createChannels(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_BUBBLE,
            "Floating save button",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shown while the floating save button is on."
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun bubbleNotification(context: Context): Notification {
        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val turnOff = PendingIntent.getService(
            context, 1,
            Intent(context, BubbleService::class.java).setAction(BubbleService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_BUBBLE)
            .setSmallIcon(R.drawable.ic_bookmark)
            .setContentTitle("Floating save button is on")
            .setContentText("Copy a link in any app, then tap the button to save it.")
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openApp)
            .addAction(0, "Turn off", turnOff)
            .build()
    }
}
