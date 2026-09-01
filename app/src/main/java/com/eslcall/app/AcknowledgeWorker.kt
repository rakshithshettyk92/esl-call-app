package com.eslcall.app

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class AcknowledgeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val company = inputData.getString(KEY_COMPANY).orEmpty()
        val store = inputData.getString(KEY_STORE).orEmpty()
        val label = inputData.getString(KEY_LABEL).orEmpty()
        val callId = inputData.getString(KEY_CALL_ID).orEmpty()
        if (company.isBlank() || store.isBlank() || label.isBlank()) return@withContext Result.failure()

        try {
            RelayApi.postJson(Constants.PATH_ESL_ACKNOWLEDGE, JSONObject().apply {
                put("callId", callId)
                put("companyCode", company)
                put("storeCode", store)
                put("labelCode", label)
            })
            completeLocally(label, acknowledgedHere = true)
            Result.success()
        } catch (e: RelayHttpException) {
            when {
                e.statusCode == 409 -> {
                    completeLocally(label, acknowledgedHere = false)
                    Result.success()
                }
                else -> Result.retry()
            }
        } catch (_: IOException) {
            Result.retry()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun completeLocally(label: String, acknowledgedHere: Boolean) {
        val alert = AlertQueueStore.loadAll(applicationContext).firstOrNull { it.labelCode == label }
        if (acknowledgedHere) {
            AcknowledgedStore.markAcknowledged(applicationContext, label)
            if (alert != null) {
                AlertHistoryStore.save(applicationContext, AlertHistoryItem(
                    alert.message, alert.companyCode, alert.labelCode,
                    System.currentTimeMillis(), AlertStatus.ACKNOWLEDGED,
                ))
            }
        }
        AlertQueueStore.removeByLabelCode(applicationContext, label)
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(notificationIdFor(label))
        if (AlertQueueStore.size(applicationContext) == 0) {
            nm.cancel(MyFirebaseMessagingService.GROUPED_NOTIFICATION_ID)
        }
        applicationContext.sendBroadcast(Intent(MyFirebaseMessagingService.ACTION_CANCEL_ALERT)
            .setPackage(applicationContext.packageName)
            .putExtra(MyFirebaseMessagingService.EXTRA_CANCEL_LABEL_CODE, label))
        applicationContext.sendBroadcast(Intent(MyFirebaseMessagingService.ACTION_ACTIVE_LIST_CHANGED)
            .setPackage(applicationContext.packageName))
    }

    companion object {
        private const val KEY_COMPANY = "company"
        private const val KEY_STORE = "store"
        private const val KEY_LABEL = "label"
        private const val KEY_CALL_ID = "callId"

        fun enqueue(context: Context, company: String, store: String, label: String, callId: String = "") {
            val request = OneTimeWorkRequestBuilder<AcknowledgeWorker>()
                .setInputData(workDataOf(KEY_COMPANY to company, KEY_STORE to store,
                    KEY_LABEL to label, KEY_CALL_ID to callId))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                "ack:$company:$store:$label", ExistingWorkPolicy.KEEP, request,
            )
        }
    }
}
