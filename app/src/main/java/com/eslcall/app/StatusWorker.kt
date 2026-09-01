package com.eslcall.app

import android.content.Context
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
import java.util.concurrent.TimeUnit

class StatusWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val company = inputData.getString("company").orEmpty()
        val store = inputData.getString("store").orEmpty()
        val label = inputData.getString("label").orEmpty()
        val status = inputData.getString("status").orEmpty()
        if (company.isBlank() || store.isBlank() || label.isBlank() ||
            status !in setOf("missed", "dismissed")) return@withContext Result.failure()
        try {
            RelayApi.postJson(Constants.PATH_ESL_STATUS, JSONObject().apply {
                put("companyCode", company)
                put("storeCode", store)
                put("labelCode", label)
                put("status", status)
            })
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun enqueue(context: Context, company: String, store: String, label: String, status: String) {
            if (company.isBlank() || store.isBlank() || label.isBlank()) return
            val request = OneTimeWorkRequestBuilder<StatusWorker>()
                .setInputData(workDataOf("company" to company, "store" to store,
                    "label" to label, "status" to status))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                "status:$company:$store:$label:$status", ExistingWorkPolicy.KEEP, request)
        }
    }
}
