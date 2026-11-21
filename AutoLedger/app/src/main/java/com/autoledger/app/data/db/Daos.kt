package com.autoledger.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.autoledger.app.data.model.AccountEntity
import com.autoledger.app.data.model.CategoryEntity
import com.autoledger.app.data.model.MerchantRuleEntity
import com.autoledger.app.data.model.TransactionEntity
import com.autoledger.app.data.model.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY isDefault DESC, id ASC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY isDefault DESC, id ASC")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Delete
    suspend fun delete(account: AccountEntity)

    @Query("UPDATE accounts SET balance = balance + :delta WHERE id = :accountId")
    suspend fun adjustBalance(accountId: Long, delta: Double)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY id ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY id ASC")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE name = :name AND type = :type LIMIT 1")
    suspend fun findByNameAndType(name: String, type: TransactionType): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("UPDATE categories SET monthlyBudget = :budget WHERE id = :categoryId")
    suspend fun updateBudget(categoryId: Long, budget: Double)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :startMs AND :endMs ORDER BY timestamp DESC")
    fun observeBetween(startMs: Long, endMs: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    suspend fun getAll(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT COUNT(*) FROM transactions WHERE utrRef = :utr AND utrRef IS NOT NULL AND utrRef != ''")
    suspend fun countByUtr(utr: String): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE dedupHash = :hash AND dedupHash IS NOT NULL")
    suspend fun countByHash(hash: String): Int

    @Query(
        "SELECT COUNT(*) FROM transactions " +
        "WHERE type = :type AND ABS(amount - :amount) < 0.01 " +
        "AND timestamp BETWEEN :minTs AND :maxTs"
    )
    suspend fun countNearDuplicate(
        type: TransactionType,
        amount: Double,
        minTs: Long,
        maxTs: Long
    ): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tx: TransactionEntity): Long

    @Update
    suspend fun update(tx: TransactionEntity)

    @Delete
    suspend fun delete(tx: TransactionEntity)
}

@Dao
interface MerchantRuleDao {
    @Query("SELECT * FROM merchant_rules WHERE merchantKey = :key LIMIT 1")
    suspend fun findByKey(key: String): MerchantRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: MerchantRuleEntity)
}
