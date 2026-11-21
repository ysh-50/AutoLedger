package com.autoledger.app.capture

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.autoledger.app.data.LedgerRepository
import com.autoledger.app.data.db.AppDatabase
import com.autoledger.app.data.model.CaptureSource
import com.autoledger.app.parser.TransactionParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PaymentNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private val MONITORED_PACKAGES = mapOf(
            "com.google.android.apps.nbu.paisa.user" to "Google Pay",
            "com.phonepe.app" to "PhonePe",
            "net.one97.paytm" to "Paytm",
            "in.org.npci.upiapp" to "BHIM UPI",
            "com.dreamplug.androidapp" to "CRED",
            "in.amazon.mShop.android.shopping" to "Amazon Pay",
            "com.snapwork.hdfc" to "HDFC",
            "com.sbi.lotusintouch" to "SBI",
            "com.csam.icici.bank.imobile" to "ICICI",
            "com.axis.mobile" to "Axis",
            "com.msf.kbank.mobile" to "Kotak",
            "com.google.android.apps.messaging" to "SMS",
            "com.samsung.android.messaging" to "SMS"
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return

        val appLabel = MONITORED_PACKAGES[pkg] ?: return
        val extras = sbn.notification?.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()

        val combinedBody = listOf(title, bigText.ifBlank { text })
            .filter { it.isNotBlank() }
            .joinToString(" - ")

        val parsed = TransactionParser.parse(appLabel, combinedBody, sbn.postTime) ?: return

        scope.launch {
            val repo = LedgerRepository(AppDatabase.getInstance(applicationContext))
            val saved = repo.saveAutoCapturedTransaction(parsed, CaptureSource.NOTIFICATION)
            if (saved != null) {
                val catName = repo.getAllCategories()
                    .firstOrNull { it.id == saved.categoryId }?.name ?: "General"
                NotificationHelper.notifyAutoLogged(applicationContext, saved, catName)
            }
        }
    }
}
