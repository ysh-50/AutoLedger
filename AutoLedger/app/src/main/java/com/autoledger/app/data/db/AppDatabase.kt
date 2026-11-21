package com.autoledger.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.autoledger.app.data.model.AccountEntity
import com.autoledger.app.data.model.AccountType
import com.autoledger.app.data.model.CategoryEntity
import com.autoledger.app.data.model.MerchantRuleEntity
import com.autoledger.app.data.model.TransactionEntity
import com.autoledger.app.data.model.TransactionType

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        MerchantRuleEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun merchantRuleDao(): MerchantRuleDao

    companion object {
        const val DB_NAME = "autoledger.sqlite"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                ).build().also { INSTANCE = it }
            }
        }

        fun closeInstance() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }

        suspend fun seedDefaultsIfEmpty(db: AppDatabase) {
            if (db.accountDao().getAll().isEmpty()) {
                val defaultAccounts = listOf(
                    AccountEntity(name = "HDFC Bank", type = AccountType.BANK, balance = 0.0, bankKeyword = "HDFC", isDefault = true),
                    AccountEntity(name = "SBI Bank", type = AccountType.BANK, balance = 0.0, bankKeyword = "SBI"),
                    AccountEntity(name = "ICICI Bank", type = AccountType.BANK, balance = 0.0, bankKeyword = "ICICI"),
                    AccountEntity(name = "Other Bank / UPI", type = AccountType.WALLET, balance = 0.0, bankKeyword = "UPI"),
                    AccountEntity(name = "Cash", type = AccountType.CASH, balance = 0.0)
                )
                defaultAccounts.forEach { db.accountDao().insert(it) }
            }

            if (db.categoryDao().getAll().isEmpty()) {
                val defaultCategories = listOf(
                    CategoryEntity(name = "Food & Dining", type = TransactionType.EXPENSE, colorHex = 0xFFE53935, monthlyBudget = 6000.0),
                    CategoryEntity(name = "Groceries", type = TransactionType.EXPENSE, colorHex = 0xFF43A047, monthlyBudget = 5000.0),
                    CategoryEntity(name = "Transport & Fuel", type = TransactionType.EXPENSE, colorHex = 0xFF1E88E5, monthlyBudget = 3000.0),
                    CategoryEntity(name = "Shopping", type = TransactionType.EXPENSE, colorHex = 0xFF8E24AA, monthlyBudget = 4000.0),
                    CategoryEntity(name = "Bills & Recharge", type = TransactionType.EXPENSE, colorHex = 0xFFFB8C00, monthlyBudget = 3000.0),
                    CategoryEntity(name = "Health & Pharmacy", type = TransactionType.EXPENSE, colorHex = 0xFF00ACC1, monthlyBudget = 2000.0),
                    CategoryEntity(name = "Entertainment", type = TransactionType.EXPENSE, colorHex = 0xFFD81B60, monthlyBudget = 2000.0),
                    CategoryEntity(name = "UPI / Other", type = TransactionType.EXPENSE, colorHex = 0xFF546E7A, monthlyBudget = 0.0),
                    CategoryEntity(name = "Salary", type = TransactionType.INCOME, colorHex = 0xFF2E7D32),
                    CategoryEntity(name = "Refund & Cashback", type = TransactionType.INCOME, colorHex = 0xFF00897B),
                    CategoryEntity(name = "Other Income", type = TransactionType.INCOME, colorHex = 0xFF3949AB)
                )
                defaultCategories.forEach { db.categoryDao().insert(it) }
            }
        }
    }
}
