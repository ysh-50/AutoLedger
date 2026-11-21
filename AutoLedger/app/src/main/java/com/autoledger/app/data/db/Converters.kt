package com.autoledger.app.data.db

import androidx.room.TypeConverter
import com.autoledger.app.data.model.AccountType
import com.autoledger.app.data.model.CaptureSource
import com.autoledger.app.data.model.TransactionType

class Converters {
    @TypeConverter fun fromAccountType(v: AccountType): String = v.name
    @TypeConverter fun toAccountType(v: String): AccountType = AccountType.valueOf(v)

    @TypeConverter fun fromTransactionType(v: TransactionType): String = v.name
    @TypeConverter fun toTransactionType(v: String): TransactionType = TransactionType.valueOf(v)

    @TypeConverter fun fromCaptureSource(v: CaptureSource): String = v.name
    @TypeConverter fun toCaptureSource(v: String): CaptureSource = CaptureSource.valueOf(v)
}
