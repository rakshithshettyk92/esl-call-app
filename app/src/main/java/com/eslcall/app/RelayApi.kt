package com.eslcall.app

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Thin HTTP wrapper around the relay. Associate requests carry only the
 * device's opaque relay session; the AIMS webhook secret is never in the APK.
 */
object RelayApi {

    fun get(path: String, query: Map<String, String> = emptyMap()): JSONObject {
        val url  = buildUrl(path, query)
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            Session.relaySessionToken(EslCallApplication.instance)?.let {
                setRequestProperty("X-Session-Token", it)
            }
            setRequestProperty("accept", "application/json")
            connectTimeout = Constants.CONNECT_TIMEOUT_MS
            readTimeout    = Constants.READ_TIMEOUT_MS
        }
        return readJson(conn)
    }

    fun postJson(path: String, body: JSONObject, query: Map<String, String> = emptyMap(),
                 sessionToken: String? = Session.relaySessionToken(EslCallApplication.instance)): JSONObject {
        val url  = buildUrl(path, query)
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            sessionToken?.let { setRequestProperty("X-Session-Token", it) }
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("accept", "application/json")
            doOutput       = true
            connectTimeout = Constants.CONNECT_TIMEOUT_MS
            readTimeout    = Constants.READ_TIMEOUT_MS
        }
        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
        return readJson(conn)
    }

    fun registerCurrentDeviceAsync(context: Context) {
        val appContext = context.applicationContext
        val company = Session.companyCode(appContext).orEmpty()
        val store = Session.storeCode(appContext).orEmpty()
        if (Session.relaySessionToken(appContext).isNullOrBlank() || company.isBlank() || store.isBlank()) return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            registerDeviceTokenAsync(appContext, token)
        }.addOnFailureListener { Log.w("RelayApi", "Could not obtain FCM token", it) }
    }

    fun registerDeviceTokenAsync(context: Context, fcmToken: String) {
        val appContext = context.applicationContext
        val company = Session.companyCode(appContext).orEmpty()
        val store = Session.storeCode(appContext).orEmpty()
        if (fcmToken.isBlank() || company.isBlank() || store.isBlank()) return
        Thread {
            runCatching {
                postJson(Constants.PATH_DEVICE_REGISTER, JSONObject().apply {
                    put("fcmToken", fcmToken)
                    put("companyCode", company)
                    put("storeCode", store)
                    put("storeName", Session.storeName(appContext).orEmpty())
                })
            }.onFailure { Log.w("RelayApi", "Device registration failed", it) }
        }.start()
    }

    fun unregisterCurrentDeviceAsync(sessionToken: String?) {
        if (sessionToken.isNullOrBlank()) return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { fcmToken ->
            Thread {
                runCatching {
                    postJson(Constants.PATH_DEVICE_UNREGISTER, JSONObject().put("fcmToken", fcmToken),
                        sessionToken = sessionToken)
                }.onFailure { Log.w("RelayApi", "Device unregistration failed", it) }
            }.start()
        }
    }

    fun logoutSessionAsync(sessionToken: String?) {
        if (sessionToken.isNullOrBlank()) return
        Thread {
            runCatching {
                postJson(Constants.PATH_AUTH_LOGOUT, JSONObject(), sessionToken = sessionToken)
            }.onFailure { Log.w("RelayApi", "Session logout failed", it) }
        }.start()
    }

    fun fetchStores(company: String): List<Store> {
        val json = get(Constants.PATH_ADMIN_STORES, mapOf("company" to company))
        if (json.optString("responseCode") != "200") {
            throw RuntimeException("Stores fetch failed: ${json.optString("responseMessage")}")
        }
        val arr = json.optJSONArray("stores") ?: return emptyList()
        val out = ArrayList<Store>(arr.length())
        for (i in 0 until arr.length()) {
            val o    = arr.optJSONObject(i) ?: continue
            val code = o.optString("store")
            if (code.isEmpty()) continue
            out.add(Store(
                code    = code,
                name    = o.optString("storeName", code),
                region  = o.optString("region").takeIf { it.isNotEmpty() },
                city    = o.optString("city").takeIf { it.isNotEmpty() },
                country = o.optString("country").takeIf { it.isNotEmpty() },
            ))
        }
        return out
    }

    /** Returns the article column list so the admin screen can populate dropdowns. */
    fun fetchArticleColumns(company: String): List<String> {
        val json = get(Constants.PATH_ADMIN_FORMAT, mapOf("company" to company))
        val arr  = json.optJSONArray("articleData") ?: return emptyList()
        return jsonArrayToStringList(arr)
    }

    fun fetchFieldMapping(company: String, store: String): CallFieldMapping {
        val json = get(Constants.PATH_ADMIN_FIELD_MAPPING, mapOf("company" to company, "store" to store))
        val m    = json.optJSONObject("mapping") ?: return CallFieldMapping.DEFAULT
        return CallFieldMapping(
            articleIdField     = m.optString("articleIdField",   "ARTICLE_ID"),
            articleNameField   = m.optString("articleNameField", "ITEM_NAME"),
            helpEnabledField   = m.optString("helpEnabledField", "ASSOCIATE_HELP_ENABLED"),
            helpEnabledValue   = m.optString("helpEnabledValue", "Y"),
            aisleField         = m.optString("aisleField").takeIf { it.isNotBlank() && it != "null" },
            revertDelaySeconds = m.optInt("revertDelaySeconds", 60),
        )
    }

    /**
     * Fire-and-forget report of a missed/dismissed alert so the relay's
     * analytics can record outcomes it never saw on its own. WorkManager keeps
     * retrying across temporary network loss and process restarts.
     */
    fun reportStatusAsync(context: android.content.Context, companyCode: String,
                          storeCode: String, labelCode: String, status: String) {
        StatusWorker.enqueue(context, companyCode, storeCode, labelCode, status)
    }

    fun saveFieldMapping(company: String, store: String, mapping: CallFieldMapping) {
        val body = JSONObject().apply {
            put("company", company)
            put("store",   store)
            put("mapping", JSONObject().apply {
                put("articleIdField",     mapping.articleIdField)
                put("articleNameField",   mapping.articleNameField)
                put("helpEnabledField",   mapping.helpEnabledField)
                put("helpEnabledValue",   mapping.helpEnabledValue)
                put("aisleField",         mapping.aisleField ?: "")
                put("revertDelaySeconds", mapping.revertDelaySeconds)
            })
        }
        val json = postJson(Constants.PATH_ADMIN_FIELD_MAPPING, body)
        if (json.optString("status") != "ok") {
            throw RuntimeException("Save failed: ${json.optString("error", "unknown")}")
        }
    }

    /** Raw analytics JSON for AnalyticsActivity to render. */
    fun fetchAnalytics(company: String, store: String, range: String): JSONObject {
        val localMidnight = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        return get(Constants.PATH_ADMIN_ANALYTICS, mapOf(
            "company" to company,
            "store" to store,
            "range" to range,
            "timeZone" to java.util.TimeZone.getDefault().id,
            "todayStartMs" to localMidnight.toString(),
        ))
    }

    /** Returns the final outcomes for this associate's selected store. */
    fun fetchCallHistory(company: String, store: String): List<AlertHistoryItem> {
        val json = get(Constants.PATH_CALL_HISTORY, mapOf(
            "company" to company,
            "store" to store,
            "limit" to "100",
        ))
        val array = json.optJSONArray("calls") ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val status = runCatching {
                AlertStatus.valueOf(item.optString("status"))
            }.getOrNull() ?: return@mapNotNull null
            AlertHistoryItem(
                message = item.optString("message", "Customer help requested"),
                companyCode = item.optString("companyCode", company),
                labelCode = item.optString("labelCode"),
                timestamp = item.optLong("timestamp"),
                status = status,
                handledBy = item.optString("handledBy").takeIf { it.isNotBlank() },
                callId = item.optString("id").takeIf { it.isNotBlank() },
                missedReason = item.optString("missedReason").takeIf { it.isNotBlank() },
            )
        }
    }

    private fun buildUrl(path: String, query: Map<String, String>): String {
        val base = Constants.RELAY_URL + path
        if (query.isEmpty()) return base
        val qs = query.entries.joinToString("&") { (k, v) ->
            URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
        }
        return "$base?$qs"
    }

    private fun readJson(conn: HttpURLConnection): JSONObject {
        try {
            val code   = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text   = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val responseBody = runCatching { JSONObject(text) }.getOrNull()
                val message = responseBody?.optString("message").orEmpty()
                    .ifBlank { responseBody?.optString("error").orEmpty() }
                    .ifBlank { "Server returned HTTP $code" }
                throw RelayHttpException(code, message, responseBody)
            }
            return JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }

    private fun jsonArrayToStringList(arr: JSONArray): List<String> {
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) out.add(arr.optString(i))
        return out
    }
}

class RelayHttpException(
    val statusCode: Int,
    message: String,
    val responseBody: JSONObject? = null,
) : RuntimeException(message)
