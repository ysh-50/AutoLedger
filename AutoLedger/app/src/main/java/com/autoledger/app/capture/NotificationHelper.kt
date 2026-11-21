package com.autoledger.app.capture

import android.Manifest
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
import com.autoledger.app.MainActivity
import com.autoledger.app.R
import com.autoledger.app.data.model.TransactionEntity
import com.autoledger.app.data.model.TransactionType

object NotificationHelper {
    private const val CHANNEL_ID = "autoledger_capture"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Auto-Logged Transactions",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Alerts when a bank SMS or UPI notification is auto-logged"
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    fun notifyAutoLogged(context: Context, tx: TransactionEntity, categoryName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, tx.id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sign = if (tx.type == TransactionType.INCOME) "+" else "-"
        val title = "Auto-logged ${sign}₹${"%.2f".format(tx.amount)} • $categoryName"
        val body = tx.description.ifBlank { tx.merchantName }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        NotificationManagerCompat.from(context).notify(tx.id.toInt(), notification)
    }
}
