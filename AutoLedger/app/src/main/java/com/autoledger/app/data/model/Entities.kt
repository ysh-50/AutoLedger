package com.autoledger.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class AccountType(val displayName: String) {
    CASH("Cash"),
    BANK("Bank Account"),
    CARD("Credit / Debit Card"),
    WALLET("UPI / Wallet")
}

enum class TransactionType(val displayName: String) {
    EXPENSE("Expense"),
    INCOME("Income"),
    TRANSFER("Transfer")
}

enum class CaptureSource(val label: String) {
    MANUAL("Manual"),
    SMS("SMS"),
    NOTIFICATION("Notif"),
    SMS_BACKFILL("SMS Scan")
}

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType,
    val balance: Double = 0.0,
    val maskedNumber: String = "",
    val bankKeyword: String = "",
    val isDefault: Boolean = false
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: TransactionType,
    val colorHex: Long,
    val monthlyBudget: Double = 0.0
)

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["utrRef"]),
        Index(value = ["dedupHash"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TransactionType,
    val amount: Double,
    val timestamp: Long,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val merchantName: String = "",
    val description: String = "",
    val source: CaptureSource = CaptureSource.MANUAL,
    val utrRef: String? = null,
    val dedupHash: String? = null,
    val rawSnippet: String = ""
)

@Entity(
    tableName = "merchant_rules",
    indices = [Index(value = ["merchantKey"], unique = true)]
)
data class MerchantRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val merchantKey: String,
    val categoryId: Long,
    val preferredDescription: String = ""
)
