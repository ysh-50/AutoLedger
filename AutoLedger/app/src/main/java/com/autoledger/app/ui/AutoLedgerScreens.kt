package com.autoledger.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autoledger.app.data.model.*
import com.autoledger.app.parser.TransactionParser
import java.text.SimpleDateFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.*

private val IncomeColor = Color(0xFF1565C0)
private val ExpenseColor = Color(0xFFD32F2F)
private val TransferColor = Color(0xFF546E7A)

fun formatRupees(amount: Double): String = "₹${"%,.2f".format(Locale("en", "IN"), amount)}"

@Composable
fun TransactionsScreen(
    selectedMonth: YearMonth,
    transactions: List<TransactionEntity>,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onEditClick: (TransactionEntity) -> Unit
) {
    val accountMap = remember(accounts) { accounts.associateBy { it.id } }
    val categoryMap = remember(categories) { categories.associateBy { it.id } }

    val totalIncome = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    val totalExpense = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    val netTotal = totalIncome - totalExpense

    val dayFormat = remember { SimpleDateFormat("dd MMM (EEE)", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val grouped = remember(transactions) {
        transactions.groupBy { dayFormat.format(Date(it.timestamp)) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Month Selector Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onPrevMonth) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month")
            }
            Text(
                text = selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            IconButton(onClick = onNextMonth) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Next month")
            }
        }

        // Money Manager-style Summary Strip
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                SummaryMetric("Income", formatRupees(totalIncome), IncomeColor)
                SummaryMetric("Expenses", formatRupees(totalExpense), ExpenseColor)
                SummaryMetric("Total", formatRupees(netTotal), MaterialTheme.colorScheme.onSurface)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (transactions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No transactions this month", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Tap + to add manually, or scan past SMS in More",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                grouped.forEach { (dayLabel, dayItems) ->
                    item(key = "hdr_$dayLabel") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(dayLabel, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            val dayExp = dayItems.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
                            if (dayExp > 0) {
                                Text("-${formatRupees(dayExp)}", color = ExpenseColor, fontSize = 13.sp)
                            }
                        }
                    }
                    items(dayItems, key = { it.id }) { tx ->
                        val cat = tx.categoryId?.let { categoryMap[it] }
                        val acc = accountMap[tx.accountId]
                        val toAcc = tx.toAccountId?.let { accountMap[it] }
                        TransactionRow(
                            tx = tx,
                            categoryName = if (tx.type == TransactionType.TRANSFER) "Transfer" else (cat?.name ?: "General"),
                            categoryColor = Color(cat?.colorHex ?: 0xFF546E7A),
                            accountLabel = if (tx.type == TransactionType.TRANSFER) {
                                "${acc?.name ?: "Account"} → ${toAcc?.name ?: "Account"}"
                            } else {
                                acc?.name ?: "Account"
                            },
                            timeLabel = timeFormat.format(Date(tx.timestamp)),
                            onClick = { onEditClick(tx) }
                        )
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = color)
    }
}

@Composable
private fun TransactionRow(
    tx: TransactionEntity,
    categoryName: String,
    categoryColor: Color,
    accountLabel: String,
    timeLabel: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(categoryColor)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = tx.merchantName.ifBlank { categoryName },
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp
                )
                if (tx.source != CaptureSource.MANUAL) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = tx.source.label,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
            Text(
                text = "$categoryName • $accountLabel • $timeLabel",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (tx.description.isNotBlank()) {
                Text(
                    text = tx.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        val amtColor = when (tx.type) {
            TransactionType.INCOME -> IncomeColor
            TransactionType.EXPENSE -> ExpenseColor
            TransactionType.TRANSFER -> TransferColor
        }
        Text(
            text = formatRupees(tx.amount),
            color = amtColor,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp
        )
    }
}

@Composable
fun StatsBudgetScreen(
    transactions: List<TransactionEntity>,
    categories: List<CategoryEntity>,
    onUpdateBudget: (Long, Double) -> Unit
) {
    val expenseCategories = categories.filter { it.type == TransactionType.EXPENSE }
    val spendByCat = remember(transactions) {
        transactions
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.categoryId }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
    }
    val totalExpense = spendByCat.values.sum()
    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Monthly Expense Breakdown", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    if (totalExpense <= 0.0) {
                        Text(
                            "No expenses recorded this month yet.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Box(
                            modifier = Modifier.size(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                var startAngle = -90f
                                val strokeWidth = 34.dp.toPx()
                                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                                val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

                                expenseCategories.forEach { cat ->
                                    val spent = spendByCat[cat.id] ?: 0.0
                                    if (spent > 0) {
                                        val sweep = ((spent / totalExpense) * 360.0).toFloat()
                                        drawArc(
                                            color = Color(cat.colorHex),
                                            startAngle = startAngle,
                                            sweepAngle = sweep,
                                            useCenter = false,
                                            topLeft = topLeft,
                                            size = arcSize,
                                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                                        )
                                        startAngle += sweep
                                    }
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Spent", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(formatRupees(totalExpense), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Category Budgets (tap any row to set limit)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(expenseCategories, key = { it.id }) { cat ->
            val spent = spendByCat[cat.id] ?: 0.0
            val budget = cat.monthlyBudget
            val pct = if (budget > 0) (spent / budget).toFloat().coerceIn(0f, 1f) else 0f
            val overBudget = budget > 0 && spent > budget

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { editingCategory = cat }
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(cat.colorHex))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(cat.name, fontWeight = FontWeight.Medium)
                        }
                        Text(
                            text = if (budget > 0) "${formatRupees(spent)} / ${formatRupees(budget)}" else formatRupees(spent),
                            color = if (overBudget) ExpenseColor else MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (budget > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { pct },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (overBudget) ExpenseColor else Color(cat.colorHex)
                        )
                    }
                }
            }
        }
    }

    editingCategory?.let { cat ->
        var budgetText by remember(cat) { mutableStateOf(if (cat.monthlyBudget > 0) cat.monthlyBudget.toString() else "") }
        AlertDialog(
            onDismissRequest = { editingCategory = null },
            title = { Text("Budget for ${cat.name}") },
            text = {
                OutlinedTextField(
                    value = budgetText,
                    onValueChange = { budgetText = it },
                    label = { Text("Monthly Limit (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = budgetText.toDoubleOrNull() ?: 0.0
                    onUpdateBudget(cat.id, v)
                    editingCategory = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingCategory = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun AccountsScreen(
    accounts: List<AccountEntity>,
    onSaveAccount: (AccountEntity) -> Unit,
    onDeleteAccount: (AccountEntity) -> Unit
) {
    val totalAssets = accounts.filter { it.balance >= 0 }.sumOf { it.balance }
    val totalLiabilities = accounts.filter { it.balance < 0 }.sumOf { -it.balance }
    val netWorth = totalAssets - totalLiabilities

    var editingAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    SummaryMetric("Assets", formatRupees(totalAssets), IncomeColor)
                    SummaryMetric("Liabilities", formatRupees(totalLiabilities), ExpenseColor)
                    SummaryMetric("Net Total", formatRupees(netWorth), MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Your Accounts", style = MaterialTheme.typography.titleMedium)
                FilledTonalButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Account")
                }
            }
        }

        items(accounts, key = { it.id }) { acc ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { editingAccount = acc }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(acc.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        val meta = buildList {
                            add(acc.type.displayName)
                            if (acc.maskedNumber.isNotBlank()) add("A/c XX${acc.maskedNumber}")
                            if (acc.bankKeyword.isNotBlank()) add("Match: ${acc.bankKeyword}")
                        }.joinToString(" • ")
                        Text(meta, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        text = formatRupees(acc.balance),
                        fontWeight = FontWeight.Bold,
                        color = if (acc.balance >= 0) IncomeColor else ExpenseColor
                    )
                }
            }
        }
    }

    if (showAddDialog || editingAccount != null) {
        AccountEditorDialog(
            initial = editingAccount,
            onDismiss = {
                showAddDialog = false
                editingAccount = null
            },
            onSave = { acc ->
                onSaveAccount(acc)
                showAddDialog = false
                editingAccount = null
            },
            onDelete = editingAccount?.let { acc ->
                {
                    onDeleteAccount(acc)
                    editingAccount = null
                }
            }
        )
    }
}

@Composable
private fun AccountEditorDialog(
    initial: AccountEntity?,
    onDismiss: () -> Unit,
    onSave: (AccountEntity) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: AccountType.BANK) }
    var balanceText by remember { mutableStateOf(initial?.balance?.toString() ?: "0.0") }
    var masked by remember { mutableStateOf(initial?.maskedNumber ?: "") }
    var keyword by remember { mutableStateOf(initial?.bankKeyword ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New Account" else "Edit Account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Account Name") }, singleLine = true)
                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { balanceText = it },
                    label = { Text("Current Balance (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = masked,
                    onValueChange = { masked = it.filter(Char::isDigit).take(4) },
                    label = { Text("Last 4 Digits (for SMS match)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    label = { Text("Bank Keyword (e.g. HDFC, SBI)") },
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AccountType.entries.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(t.name, fontSize = 11.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    onSave(
                        AccountEntity(
                            id = initial?.id ?: 0L,
                            name = name.trim(),
                            type = type,
                            balance = balanceText.toDoubleOrNull() ?: 0.0,
                            maskedNumber = masked.trim(),
                            bankKeyword = keyword.trim(),
                            isDefault = initial?.isDefault ?: false
                        )
                    )
                }
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete", color = ExpenseColor) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
fun MoreScreen(
    hasSmsPermission: Boolean,
    hasNotifAccess: Boolean,
    isScanning: Boolean,
    onRequestSmsPermission: () -> Unit,
    onOpenNotifSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onScanPastSms: () -> Unit,
    onExportCsv: () -> Unit,
    onBackupSqlite: () -> Unit,
    onRestoreSqlite: () -> Unit
) {
    var testSmsInput by remember {
        mutableStateOf("Sent Rs.250.00 From HDFC Bank A/C *1234 To SWIGGY On 21/11/26 Ref 432512345678")
    }
    var testOutput by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Auto-Capture Permissions", style = MaterialTheme.typography.titleMedium)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PermissionRow(
                    title = "Bank SMS Access",
                    subtitle = if (hasSmsPermission) "Active — auto-logging bank alerts" else "Required for HDFC/SBI/ICICI SMS capture",
                    granted = hasSmsPermission,
                    actionLabel = "Grant",
                    onClick = onRequestSmsPermission
                )
                HorizontalDivider()
                PermissionRow(
                    title = "Notification Listener",
                    subtitle = if (hasNotifAccess) "Active — watching GPay, PhonePe, Paytm" else "Enable AutoLedger in Notification Access",
                    granted = hasNotifAccess,
                    actionLabel = "Open",
                    onClick = onOpenNotifSettings
                )
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Battery & OEM Background Fix", fontWeight = FontWeight.Medium)
                        Text(
                            "Set Battery to Unrestricted (Xiaomi/Oppo/Vivo: enable Autostart & Service SMS)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = onOpenBatterySettings) { Text("Settings") }
                }
            }
        }

        Text("Import, Backup & Export", style = MaterialTheme.typography.titleMedium)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onScanPastSms,
                    enabled = hasSmsPermission && !isScanning,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isScanning) "Scanning SMS Inbox..." else "One-Time Scan: Past 90 Days SMS")
                }
                OutlinedButton(onClick = onExportCsv, modifier = Modifier.fillMaxWidth()) {
                    Text("Export Transactions to CSV (Excel)")
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onBackupSqlite, modifier = Modifier.weight(1f)) {
                        Text("Backup SQLite")
                    }
                    OutlinedButton(onClick = onRestoreSqlite, modifier = Modifier.weight(1f)) {
                        Text("Restore SQLite")
                    }
                }
            }
        }

        Text("Test SMS / Notification Parser", style = MaterialTheme.typography.titleMedium)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = testSmsInput,
                    onValueChange = { testSmsInput = it },
                    label = { Text("Paste any bank SMS or UPI alert") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                FilledTonalButton(onClick = {
                    val res = TransactionParser.parse("TEST", testSmsInput)
                    testOutput = if (res == null) {
                        "Ignored (OTP, non-transactional, or unrecognized format)"
                    } else {
                        "Type: ${res.type.displayName} | Amount: ${formatRupees(res.amount)}\n" +
                            "Merchant: ${res.merchantName} | Bank: ${res.bankName ?: "Generic"} ${res.maskedAccount?.let { "XX$it" }.orEmpty()}\n" +
                            "UTR: ${res.utrRef ?: "-"} | Desc: ${res.description}"
                    }
                }) {
                    Text("Run Parser")
                }
                testOutput?.let {
                    Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    actionLabel: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (granted) {
            Icon(Icons.Default.CheckCircle, contentDescription = "Granted", tint = Color(0xFF2E7D32))
        } else {
            Button(onClick = onClick) { Text(actionLabel) }
        }
    }
}

@Composable
fun TransactionEditorDialog(
    initial: TransactionEntity?,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (TransactionEntity) -> Unit,
    onDelete: (() -> Unit)?
) {
    if (accounts.isEmpty()) return

    var type by remember { mutableStateOf(initial?.type ?: TransactionType.EXPENSE) }
    var amountText by remember { mutableStateOf(initial?.amount?.toString() ?: "") }
    var accountId by remember { mutableStateOf(initial?.accountId ?: accounts.first().id) }
    var toAccountId by remember {
        mutableStateOf(initial?.toAccountId ?: accounts.getOrNull(1)?.id ?: accounts.first().id)
    }
    val matchingCats = categories.filter { it.type == type }
    var categoryId by remember(type) {
        mutableStateOf(
            initial?.takeIf { it.type == type }?.categoryId ?: matchingCats.firstOrNull()?.id
        )
    }
    var merchant by remember { mutableStateOf(initial?.merchantName ?: "") }
    var description by remember { mutableStateOf(initial?.description ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Transaction" else "Edit Transaction") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TransactionType.entries.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(t.displayName) }
                        )
                    }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("Merchant / Payee") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description / Note") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    if (type == TransactionType.TRANSFER) "From Account" else "Account",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowChipRow(
                    items = accounts.map { it.id to it.name },
                    selectedId = accountId,
                    onSelect = { accountId = it }
                )

                if (type == TransactionType.TRANSFER) {
                    Text("To Account", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowChipRow(
                        items = accounts.map { it.id to it.name },
                        selectedId = toAccountId,
                        onSelect = { toAccountId = it }
                    )
                } else {
                    Text("Category (edits teach auto-categorizer)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowChipRow(
                        items = matchingCats.map { it.id to it.name },
                        selectedId = categoryId,
                        onSelect = { categoryId = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amt = amountText.toDoubleOrNull() ?: return@TextButton
                if (amt <= 0.0) return@TextButton
                onSave(
                    TransactionEntity(
                        id = initial?.id ?: 0L,
                        type = type,
                        amount = amt,
                        timestamp = initial?.timestamp ?: System.currentTimeMillis(),
                        accountId = accountId,
                        toAccountId = if (type == TransactionType.TRANSFER) toAccountId else null,
                        categoryId = if (type == TransactionType.TRANSFER) null else categoryId,
                        merchantName = merchant.trim(),
                        description = description.trim(),
                        source = initial?.source ?: CaptureSource.MANUAL,
                        utrRef = initial?.utrRef,
                        dedupHash = initial?.dedupHash,
                        rawSnippet = initial?.rawSnippet ?: ""
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete", color = ExpenseColor) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowChipRow(
    items: List<Pair<Long, String>>,
    selectedId: Long?,
    onSelect: (Long) -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (id, label) ->
            FilterChip(
                selected = selectedId == id,
                onClick = { onSelect(id) },
                label = { Text(label, fontSize = 12.sp) }
            )
        }
    }
}
