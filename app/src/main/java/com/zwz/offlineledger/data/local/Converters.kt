package com.zwz.offlineledger.data.local

import androidx.room.TypeConverter
import com.zwz.offlineledger.data.model.AccountType
import com.zwz.offlineledger.data.model.RecordType

/** 枚举以名称字符串落库，避免用 ordinal（加枚举值时会错位）。 */
class Converters {
    @TypeConverter fun accountTypeToString(v: AccountType): String = v.name
    @TypeConverter fun stringToAccountType(v: String): AccountType = AccountType.valueOf(v)

    @TypeConverter fun recordTypeToString(v: RecordType): String = v.name
    @TypeConverter fun stringToRecordType(v: String): RecordType = RecordType.valueOf(v)
}
