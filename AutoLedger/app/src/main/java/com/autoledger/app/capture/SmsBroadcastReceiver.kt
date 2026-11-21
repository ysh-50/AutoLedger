package com.autoledger.app.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.autoledger.app.data.LedgerRepository
import com.autoledger.app.data.db.AppDatabase
import com.autoledger.app.data.model.CaptureSource
import com.autoledger.app.parser.TransactionParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages.firstOrNull()?.displayOriginatingAddress.orEmpty()
        val fullBody = messages.joinToString("") { it.displayMessageBody.orEmpty() }
        val timestamp = messages.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()

        val parsed = TransactionParser.parse(sender, fullBody, timestamp) ?: return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = LedgerRepository(AppDatabase.getInstance(context))
                val saved = repo.saveAutoCapturedTransaction(parsed, CaptureSource.SMS)
                if (saved != null) {
                    val catName = repo.getAllCategories()
                        .firstOrNull { it.id == saved.categoryId }?.name ?: "General"
                    NotificationHelper.notifyAutoLogged(context, saved, catName)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
