package com.gatekeeper.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gatekeeper.app.data.db.TaskDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Resets recurring tasks to not-done once a day (R-5.4). */
@HiltWorker
class DailyResetWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val taskDao: TaskDao,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        taskDao.resetRecurring()
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "daily_recurring_task_reset"
    }
}
