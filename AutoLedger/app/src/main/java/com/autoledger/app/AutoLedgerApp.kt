package com.autoledger.app

import android.app.Application
import com.autoledger.app.capture.NotificationHelper
import com.autoledger.app.data.LedgerRepository
import com.autoledger.app.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AutoLedgerApp : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase
        get() = AppDatabase.getInstance(this)

    val repository: LedgerRepository
        get() = LedgerRepository(database)

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
        applicationScope.launch {
            AppDatabase.seedDefaultsIfEmpty(database)
        }
    }
}
