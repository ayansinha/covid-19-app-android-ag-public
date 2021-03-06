package uk.nhs.nhsx.covid19.android.app.exposure.encounter

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.NetworkType.CONNECTED
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import uk.nhs.nhsx.covid19.android.app.appComponent
import uk.nhs.nhsx.covid19.android.app.notifications.NotificationProvider
import uk.nhs.nhsx.covid19.android.app.util.toWorkerResult
import javax.inject.Inject

class ExposureNotificationWorker(
    val context: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters) {

    @Inject
    lateinit var exposureNotificationWork: ExposureNotificationWork

    @Inject
    lateinit var notificationProvider: NotificationProvider

    override suspend fun doWork(): Result {
        context.appComponent.inject(this)

        setForeground()
        val matchesFound = inputData.getBoolean(INPUT_MATCHES_FOUND, true)

        val result = if (matchesFound) {
            val inputToken = inputData.getString(INPUT_TOKEN) ?: ""
            exposureNotificationWork.handleMatchesFound(inputToken)
        } else {
            exposureNotificationWork.handleNoMatchesFound()
        }
        return result.toWorkerResult()
    }

    private suspend fun setForeground() {
        val updatingDatabaseNotification = notificationProvider.getUpdatingDatabaseNotification()
        val foregroundInfo =
            ForegroundInfo(NOTIFICATION_UPDATING_DATABASE_ID, updatingDatabaseNotification)
        setForeground(foregroundInfo)
    }

    companion object : ExposureNotificationWorkerScheduler {
        const val INPUT_TOKEN = "INPUT_TOKEN"
        const val INPUT_MATCHES_FOUND = "INPUT_MATCHES_FOUND"
        private const val NOTIFICATION_UPDATING_DATABASE_ID = 112

        override fun scheduleMatchesFound(context: Context, token: String) {
            schedule(context) {
                it.setInputData(
                    Data.Builder()
                        .putBoolean(INPUT_MATCHES_FOUND, true)
                        .putString(INPUT_TOKEN, token)
                        .build()
                )
            }
        }

        override fun scheduleNoMatchesFound(context: Context) {
            schedule(context) {
                it.setInputData(Data.Builder().putBoolean(INPUT_MATCHES_FOUND, false).build())
            }
        }

        private fun schedule(
            context: Context,
            inputConfigurator: (OneTimeWorkRequest.Builder) -> OneTimeWorkRequest.Builder
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(CONNECTED)
                .build()

            val exposureNotificationWork = OneTimeWorkRequestBuilder<ExposureNotificationWorker>()
                .setConstraints(constraints)
                .apply {
                    inputConfigurator(this)
                }
                .build()

            WorkManager.getInstance(context).enqueue(exposureNotificationWork)
        }
    }
}

interface ExposureNotificationWorkerScheduler {
    fun scheduleMatchesFound(context: Context, token: String)
    fun scheduleNoMatchesFound(context: Context)
}
