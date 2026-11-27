package me.rerere.zeroagent.data.terminal

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Drain old external-installer jobs after migration; never downloads an APK. */
class TermuxInstallerWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = Result.success()
}
