package com.autoledger.app.data

import androidx.room.withTransaction
import com.autoledger.app.data.db.AppDatabase
import com.autoledger.app.data.model.AccountEntity
import com.autoledger.app.data.model.CaptureSource
import com.autoledger.app.data.model.CategoryEntity
import com.autoledger.app.data.model.MerchantRuleEntity
import com.autoledger.app.data.model.TransactionEntity
import com.autoledger.app.data.model.TransactionType
import com.autoledger.app.parser.AutoCategorizer
import com.autoledger.app.parser.ParsedTransaction
import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest

class LedgerRepository(private val db: AppDatabase) {

    fun observeAccounts(): Flow<List<AccountEntity>> = db.accountDao().observeAll()
    fun observeCategories(): Flow<List<CategoryEntity>> = db.categoryDao().observeAll()
    fun observeTransactionsBetween(startMs: Long, endMs: Long): Flow<List<TransactionEntity>> =
        db.transactionDao().observeBetween(startMs, endMs)

    suspend fun getAllAccounts(): List<AccountEntity> = db.accountDao().getAll()
    suspend fun getAllCategories(): List<CategoryEntity> = db.categoryDao().getAll()
    suspend fun getAllTransactions(): List<TransactionEntity> = db.transactionDao().getAll()

    suspend fun saveAccount(account: AccountEntity) {
        if (account.id == 0L) db.accountDao().insert(account) else db.accountDao().update(account)
    }

    suspend fun deleteAccount(account: AccountEntity) {
        db.accountDao().delete(account)
    }

    suspend fun updateCategoryBudget(categoryId: Long, budget: Double) {
        db.categoryDao().updateBudget(categoryId, budget)
    }

    suspend fun recordTransaction(tx: TransactionEntity): Long = db.withTransaction {
        val id = db.transactionDao().insert(tx)
        applyBalanceImpact(tx, multiplier = 1.0)
        id
    }

    suspend fun updateTransaction(updated: TransactionEntity, learnMerchantRule: Boolean = true) =
        db.withTransaction {
            val old = db.transactionDao().getById(updated.id) ?: return@withTransaction
            applyBalanceImpact(old, multiplier = -1.0)
            db.transactionDao().update(updated)
            applyBalanceImpact(updated, multiplier = 1.0)

            if (learnMerchantRule && updated.merchantName.isNotBlank() && updated.categoryId != null) {
                val key = AutoCategorizer.normalizeMerchantKey(updated.merchantName)
                if (key.isNotBlank()) {
                    db.merchantRuleDao().upsert(
                        MerchantRuleEntity(
                            merchantKey = key,
                            categoryId = updated.categoryId,
                            preferredDescription = updated.description
                        )
                    )
                }
            }
        }

    suspend fun deleteTransaction(tx: TransactionEntity) = db.withTransaction {
        val existing = db.transactionDao().getById(tx.id) ?: return@withTransaction
        applyBalanceImpact(existing, multiplier = -1.0)
        db.transactionDao().delete(existing)
    }

    /**
     * Auto-saves a parsed SMS or Notification transaction with UTR + time-window deduplication,
     * masked account matching, and merchant-memory + keyword auto-categorization.
     * Returns the saved TransactionEntity, or null if skipped as a duplicate.
     */
    suspend fun saveAutoCapturedTransaction(
        parsed: ParsedTransaction,
        source: CaptureSource
    ): TransactionEntity? {
        AppDatabase.seedDefaultsIfEmpty(db)

        val dedupHash = sha256(
            "${parsed.type.name}|${"%.2f".format(parsed.amount)}|${parsed.timestamp / 60000}|${parsed.utrRef.orEmpty()}"
        )

        if (!parsed.utrRef.isNullOrBlank() && db.transactionDao().countByUtr(parsed.utrRef) > 0) {
            return null
        }
        if (db.transactionDao().countByHash(dedupHash) > 0) {
            return null
        }
        val twoMin = 120_000L
        val nearDup = db.transactionDao().countNearDuplicate(
            type = parsed.type,
            amount = parsed.amount,
            minTs = parsed.timestamp - twoMin,
            maxTs = parsed.timestamp + twoMin
        )
        if (nearDup > 0) {
            return null
        }

        val accounts = db.accountDao().getAll()
        val matchedAccount = matchAccount(accounts, parsed)
        val categories = db.categoryDao().getAll()

        val merchantKey = AutoCategorizer.normalizeMerchantKey(parsed.merchantName)
        val learnedRule = if (merchantKey.isNotBlank()) db.merchantRuleDao().findByKey(merchantKey) else null

        val categoryId = learnedRule?.categoryId ?: AutoCategorizer.categorize(
            merchantName = parsed.merchantName,
            rawText = parsed.rawText,
            type = parsed.type,
            categories = categories
        )

        val finalDescription = learnedRule?.preferredDescription?.takeIf { it.isNotBlank() }
            ?: parsed.description

        val entity = TransactionEntity(
            type = parsed.type,
            amount = parsed.amount,
            timestamp = parsed.timestamp,
            accountId = matchedAccount.id,
            toAccountId = null,
            categoryId = categoryId,
            merchantName = parsed.merchantName,
            description = finalDescription,
            source = source,
            utrRef = parsed.utrRef,
            dedupHash = dedupHash,
            rawSnippet = parsed.rawText.take(240)
        )

        val newId = recordTransaction(entity)
        return entity.copy(id = newId)
    }

    private fun matchAccount(accounts: List<AccountEntity>, parsed: ParsedTransaction): AccountEntity {
        if (!parsed.maskedAccount.isNullOrBlank()) {
            val byDigits = accounts.firstOrNull {
                it.maskedNumber.isNotBlank() &&
                    parsed.maskedAccount.endsWith(it.maskedNumber.takeLast(4))
            }
            if (byDigits != null) return byDigits
        }
        if (!parsed.bankName.isNullOrBlank()) {
            val byBank = accounts.firstOrNull {
                it.bankKeyword.isNotBlank() &&
                    parsed.bankName.contains(it.bankKeyword, ignoreCase = true)
            }
            if (byBank != null) return byBank
        }
        return accounts.firstOrNull { it.bankKeyword.equals("UPI", ignoreCase = true) }
            ?: accounts.firstOrNull { it.isDefault }
            ?: accounts.first()
    }

    private suspend fun applyBalanceImpact(tx: TransactionEntity, multiplier: Double) {
        when (tx.type) {
            TransactionType.EXPENSE -> {
                db.accountDao().adjustBalance(tx.accountId, -tx.amount * multiplier)
            }
            TransactionType.INCOME -> {
                db.accountDao().adjustBalance(tx.accountId, tx.amount * multiplier)
            }
            TransactionType.TRANSFER -> {
                db.accountDao().adjustBalance(tx.accountId, -tx.amount * multiplier)
                tx.toAccountId?.let { target ->
                    db.accountDao().adjustBalance(target, tx.amount * multiplier)
                }
            }
        }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
