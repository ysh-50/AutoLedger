package com.autoledger.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autoledger.app.data.model.TransactionEntity
import com.autoledger.app.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AutoLedgerRoot()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoLedgerRoot(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val selectedMonth by vm.selectedMonth.collectAsState()
    val transactions by vm.monthlyTransactions.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val categories by vm.categories.collectAsState()
    val statusMessage by vm.statusMessage.collectAsState()
    val isScanning by vm.isScanning.collectAsState()

    var currentTab by remember { mutableIntStateOf(0) }
    var editingTx by remember { mutableStateOf<TransactionEntity?>(null) }
    var showAddTx by remember { mutableStateOf(false) }

    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasNotifAccess by remember {
        mutableStateOf(NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName))
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearStatus()
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasSmsPermission = (grants[Manifest.permission.RECEIVE_SMS] == true) &&
            (grants[Manifest.permission.READ_SMS] == true)
    }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { vm.exportCsv(it) } }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> uri?.let { vm.backupSqlite(it) } }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.restoreSqlite(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AutoLedger") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (currentTab == 0) {
                FloatingActionButton(onClick = { showAddTx = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add transaction")
                }
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(Icons.Default.ReceiptLong, contentDescription = null) },
                    label = { Text("Trans.") }
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(Icons.Default.PieChart, contentDescription = null) },
                    label = { Text("Stats") }
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
                    label = { Text("Accounts") }
                )
                NavigationBarItem(
                    selected = currentTab == 3,
                    onClick = {
                        hasNotifAccess = NotificationManagerCompat
                            .getEnabledListenerPackages(context).contains(context.packageName)
                        currentTab = 3
                    },
                    icon = { Icon(Icons.Default.MoreHoriz, contentDescription = null) },
                    label = { Text("More") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (currentTab) {
                0 -> TransactionsScreen(
                    selectedMonth = selectedMonth,
                    transactions = transactions,
                    accounts = accounts,
                    categories = categories,
                    onPrevMonth = vm::prevMonth,
                    onNextMonth = vm::nextMonth,
                    onEditClick = { editingTx = it }
                )
                1 -> StatsBudgetScreen(
                    transactions = transactions,
                    categories = categories,
                    onUpdateBudget = vm::updateCategoryBudget
                )
                2 -> AccountsScreen(
                    accounts = accounts,
                    onSaveAccount = vm::saveAccount,
                    onDeleteAccount = vm::deleteAccount
                )
                3 -> MoreScreen(
                    hasSmsPermission = hasSmsPermission,
                    hasNotifAccess = hasNotifAccess,
                    isScanning = isScanning,
                    onRequestSmsPermission = {
                        val perms = buildList {
                            add(Manifest.permission.RECEIVE_SMS)
                            add(Manifest.permission.READ_SMS)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }.toTypedArray()
                        permLauncher.launch(perms)
                    },
                    onOpenNotifSettings = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    onOpenBatterySettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                        )
                    },
                    onScanPastSms = { vm.scanPastSms(90) },
                    onExportCsv = { csvLauncher.launch("AutoLedger_Export.csv") },
                    onBackupSqlite = { backupLauncher.launch("autoledger_backup.sqlite") },
                    onRestoreSqlite = { restoreLauncher.launch(arrayOf("*/*")) }
                )
            }
        }
    }

    if (showAddTx || editingTx != null) {
        TransactionEditorDialog(
            initial = editingTx,
            accounts = accounts,
            categories = categories,
            onDismiss = {
                showAddTx = false
                editingTx = null
            },
            onSave = { tx ->
                vm.addOrUpdateTransaction(tx)
                showAddTx = false
                editingTx = null
            },
            onDelete = editingTx?.let { tx ->
                {
                    vm.deleteTransaction(tx)
                    editingTx = null
                }
            }
        )
    }
}
