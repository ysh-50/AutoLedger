package com.autoledger.app.capture

import android.content.Context
import android.provider.Telephony
import com.autoledger.app.data.LedgerRepository
import com.autoledger.app.data.model.CaptureSource
import com.autoledger.app.parser.TransactionParser

data class ScanResult(val scannedCount: Int, val importedCount: Int)

object SmsInboxScanner {
    suspend fun scanPastDays(
        context: Context,
        repository: LedgerRepository,
        daysBack: Int = 90
    ): ScanResult {
        val cutoffMs = System.currentTimeMillis() - daysBack.toLong() * 24L * 60L * 60L * 1000L
        var scanned = 0
        var imported = 0

        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )
        val selection = "${Telephony.Sms.DATE} >= ?"
        val selectionArgs = arrayOf(cutoffMs.toString())

        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${Telephony.Sms.DATE} ASC"
        )?.use { cursor ->
            val addrIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)

            while (cursor.moveToNext()) {
                scanned++
                val sender = cursor.getString(addrIdx).orEmpty()
                val body = cursor.getString(bodyIdx).orEmpty()
                val dateMs = cursor.getLong(dateIdx)

                val parsed = TransactionParser.parse(sender, body, dateMs) ?: continue
                val saved = repository.saveAutoCapturedTransaction(parsed, CaptureSource.SMS_BACKFILL)
                if (saved != null) imported++
            }
        }
        return ScanResult(scanned, imported)
    }
}
