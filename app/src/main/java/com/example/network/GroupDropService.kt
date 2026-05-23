package com.example.network

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class GroupDropService : Service() {
    private val CHANNEL_ID = "groupdrop_service_channel"
    private val NOTIFICATION_ID = 9001

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Start local TCP server and NSD discovery/advertising
        GroupDropManager.getInstance(applicationContext).startSharing()

        return START_STICKY
    }

    override fun onDestroy() {
        // Stop sharing and cleanup
        GroupDropManager.getInstance(applicationContext).stopSharing()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val smallIconResId = android.R.drawable.stat_sys_download_done

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GroupDrop is active")
            .setContentText("Ready to receive files offline from group devices.")
            .setSmallIcon(smallIconResId)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "GroupDrop Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps GroupDrop alive in the background for local file receiving"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
