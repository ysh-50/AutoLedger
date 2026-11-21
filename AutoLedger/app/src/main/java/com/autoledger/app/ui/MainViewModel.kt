package com.autoledger.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autoledger.app.capture.SmsInboxScanner
import com.autoledger.app.data.LedgerRepository
import com.autoledger.app.data.db.AppDatabase
import com.autoledger.app.data.model.AccountEntity
import com.autoledger.app.data.model.CategoryEntity
import com.autoledger.app.data.model.TransactionEntity
import com.autoledger.app.data.model.TransactionType
import com.autoledger.app.export.BackupExportManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repo: LedgerRepository
        get() = LedgerRepository(AppDatabase.getInstance(getApplication()))

    val selectedMonth = MutableStateFlow(YearMonth.now())
    val statusMessage = MutableStateFlow<String?>(null)
    val isScanning = MutableStateFlow(false)

    val accounts: StateFlow<List<AccountEntity>> = repo.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = repo.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monthlyTransactions: StateFlow<List<TransactionEntity>> = selectedMonth
        .flatMapLatest { ym ->
            val zone = ZoneId.systemDefault()
            val start = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val end = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
            repo.observeTransactionsBetween(start, end)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun prevMonth() {
        selectedMonth.value = selectedMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        selectedMonth.value = selectedMonth.value.plusMonths(1)
    }

    fun clearStatus() {
        statusMessage.value = null
    }

    fun addOrUpdateTransaction(tx: TransactionEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            if (tx.id == 0L) {
                repo.recordTransaction(tx)
            } else {
                repo.updateTransaction(tx, learnMerchantRule = true)
            }
        }
    }

    fun deleteTransaction(tx: TransactionEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.deleteTransaction(tx)
        }
    }

    fun saveAccount(account: AccountEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.saveAccount(account)
        }
    }

    fun deleteAccount(account: AccountEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.deleteAccount(account)
        }
    }

    fun updateCategoryBudget(categoryId: Long, budget: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.updateCategoryBudget(categoryId, budget)
        }
    }

    fun scanPastSms(days: Int = 90) {
        if (isScanning.value) return
        viewModelScope.launch {
            isScanning.value = true
            try {
                val res = withContext(Dispatchers.IO) {
                    SmsInboxScanner.scanPastDays(getApplication(), repo, days)
                }
                statusMessage.value = "Scanned ${res.scannedCount} SMS • Imported ${res.importedCount} new transactions"
            } catch (e: Exception) {
                statusMessage.value = "SMS scan failed: ${e.message}"
            } finally {
                isScanning.value = false
            }
        }
    }

    fun exportCsv(uri: Uri) {
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) {
                BackupExportManager.exportCsv(getApplication(), uri, repo)
            }
            statusMessage.value = "Exported $count transactions to CSV"
        }
    }

    fun backupSqlite(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                BackupExportManager.backupSqliteDatabase(getApplication(), uri)
            }
            statusMessage.value = if (ok) "SQLite backup saved" else "Backup failed"
        }
    }

    fun restoreSqlite(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                BackupExportManager.restoreSqliteDatabase(getApplication(), uri)
            }
            statusMessage.value = if (ok) "Backup restored — restart app to reload" else "Restore failed"
        }
    }
}
