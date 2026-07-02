package com.gatekeeper.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * PRIVACY NOTE: every entity in this database lives exclusively in the app's
 * private on-device storage. Gatekeeper has no network permission; nothing
 * here is ever transmitted anywhere.
 */

enum class GateType { TASKS, EXERCISES, READING }

enum class ExerciseUnit { REPS, SECONDS }

enum class LogType { TASK, EXERCISE, READING, GATE_SESSION }

/** An app the user chose to gate (R-3.2). */
@Entity(tableName = "blocked_apps")
data class BlockedApp(
    @PrimaryKey val packageName: String,
    val label: String,
    val enabled: Boolean = true,
    /** Null = inherit the global default config (R-3.3). */
    val configOverrideId: Long? = null,
)

/** Gate thresholds; one row is the global default, others are per-app overrides (R-4.1). */
@Entity(tableName = "gate_configs")
data class GateConfig(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val allowTasks: Boolean = true,
    val allowExercises: Boolean = true,
    val allowReading: Boolean = true,
    val tasksRequired: Int = 2,
    val exercisesRequired: Int = 1,
    val pagesRequired: Int = 10,
    val sessionDurationMinutes: Int = 10,
    /** 0 = unlimited (R-4.4). */
    val dailyCap: Int = 0,
    /** 0 = no cooldown (R-4.5). */
    val cooldownMinutes: Int = 0,
    val isGlobalDefault: Boolean = false,
) {
    fun allowedTypes(): List<GateType> = buildList {
        if (allowTasks) add(GateType.TASKS)
        if (allowExercises) add(GateType.EXERCISES)
        if (allowReading) add(GateType.READING)
    }
}

/** To-do item (R-5.1). Completed tasks are archived, never silently deleted (R-5.3). */
@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String? = null,
    /** 0 = none, 1 = low, 2 = medium, 3 = high. */
    val priority: Int = 0,
    val isDone: Boolean = false,
    /** Recurring tasks reset to not-done every day (R-5.4). */
    val isRecurring: Boolean = false,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Exercise library entry (R-6.1). */
@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val unit: ExerciseUnit = ExerciseUnit.REPS,
    val targetAmount: Int = 10,
    val description: String? = null,
)

/** Imported EPUB (R-7.1/R-7.2). [filePath] points at the app-private copy. */
@Entity(tableName = "books")
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String? = null,
    val coverPath: String? = null,
    val filePath: String,
    /** Total synthetic position count from Readium (R-7.6). */
    val totalPositions: Int = 0,
    /** Serialized Readium Locator — resume point (R-7.5). */
    val lastLocatorJson: String? = null,
    /** Furthest totalProgression ever reached, 0.0–1.0 (strict mode, R-7.10). */
    val furthestProgression: Double = 0.0,
    val addedAt: Long = System.currentTimeMillis(),
)

/** A timed unlock for exactly one package (R-8.1). */
@Entity(tableName = "session_grants")
data class SessionGrant(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val grantedAt: Long,
    val expiresAt: Long,
)

/** Completion history powering stats (R-11.2). */
@Entity(tableName = "completion_logs")
data class CompletionLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: LogType,
    /** Task/Exercise/Book id, or null (e.g. GATE_SESSION uses packageName in [detail]). */
    val refId: Long? = null,
    val detail: String? = null,
    /** Tasks/exercises: count; reading: pages (synthetic positions) read. */
    val amount: Int = 1,
    val durationSeconds: Int? = null,
    val timestamp: Long = System.currentTimeMillis(),
)
