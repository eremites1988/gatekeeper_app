package com.gatekeeper.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun gateTypeToString(v: GateType): String = v.name
    @TypeConverter fun stringToGateType(v: String): GateType = GateType.valueOf(v)
    @TypeConverter fun exerciseUnitToString(v: ExerciseUnit): String = v.name
    @TypeConverter fun stringToExerciseUnit(v: String): ExerciseUnit = ExerciseUnit.valueOf(v)
    @TypeConverter fun logTypeToString(v: LogType): String = v.name
    @TypeConverter fun stringToLogType(v: String): LogType = LogType.valueOf(v)
}

@Database(
    entities = [
        BlockedApp::class,
        GateConfig::class,
        Task::class,
        Exercise::class,
        Book::class,
        SessionGrant::class,
        CompletionLog::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun gateConfigDao(): GateConfigDao
    abstract fun taskDao(): TaskDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun bookDao(): BookDao
    abstract fun sessionGrantDao(): SessionGrantDao
    abstract fun completionLogDao(): CompletionLogDao
}
