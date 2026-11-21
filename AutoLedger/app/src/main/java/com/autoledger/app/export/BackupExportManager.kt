package com.autoledger.app.export

import android.content.Context
import android.net.Uri
import com.autoledger.app.data.LedgerRepository
import com.autoledger.app.data.db.AppDatabase
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupExportManager {

    suspend fun exportCsv(context: Context, uri: Uri, repository: LedgerRepository): Int {
        val transactions = repository.getAllTransactions()
        val accounts = repository.getAllAccounts().associateBy { it.id }
        val categories = repository.getAllCategories().associateBy { it.id }
        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        context.contentResolver.openOutputStream(uri)?.use { out ->
            // UTF-8 BOM for clean Excel compatibility
            out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            val header = "Date,Type,Account,ToAccount,Category,Merchant,Amount,Source,UTR,Description\n"
            out.write(header.toByteArray(Charsets.UTF_8))

            for (tx in transactions) {
                val row = listOf(
                    dateFmt.format(Date(tx.timestamp)),
                    tx.type.displayName,
                    accounts[tx.accountId]?.name.orEmpty(),
                    tx.toAccountId?.let { accounts[it]?.name }.orEmpty(),
                    tx.categoryId?.let { categories[it]?.name }.orEmpty(),
                    tx.merchantName,
                    "%.2f".format(Locale.US, tx.amount),
                    tx.source.label,
                    tx.utrRef.orEmpty(),
                    tx.description
                ).joinToString(",") { escapeCsv(it) }
                out.write("$row\n".toByteArray(Charsets.UTF_8))
            }
        }
        return transactions.size
    }

    fun backupSqliteDatabase(context: Context, uri: Uri): Boolean {
        val dbFile = context.getDatabasePath(AppDatabase.DB_NAME)
        if (!dbFile.exists()) return false
        AppDatabase.closeInstance()
        return try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                FileInputStream(dbFile).use { input ->
                    input.copyTo(out)
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun restoreSqliteDatabase(context: Context, uri: Uri): Boolean {
        val dbFile = context.getDatabasePath(AppDatabase.DB_NAME)
        AppDatabase.closeInstance()
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dbFile).use { out ->
                    input.copyTo(out)
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun escapeCsv(value: String): String {
        val clean = value.replace("\"", "\"\"").replace("\n", " ")
        return "\"$clean\""
    }
}
