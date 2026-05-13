package com.eslcall.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Thin HTTP wrapper around the relay. Adds the shared x-auth-key header and
 * surfaces non-2xx responses as exceptions so callers handle them.
 */
object RelayApi {

    fun get(path: String, query: Map<String, String> = emptyMap()): JSONObject {
        val url  = buildUrl(path, query)
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty(Constants.AUTH_HEADER, Constants.AUTH_KEY)
            setRequestProperty("accept", "application/json")
            connectTimeout = 10_000
            readTimeout    = 10_000
        }
        return readJson(conn)
    }

    fun postJson(path: String, body: JSONObject, query: Map<String, String> = emptyMap()): JSONObject {
        val url  = buildUrl(path, query)
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty(Constants.AUTH_HEADER, Constants.AUTH_KEY)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("accept", "application/json")
            doOutput       = true
            connectTimeout = 10_000
            readTimeout    = 10_000
        }
        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
        return readJson(conn)
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
     * analytics can record outcomes it never saw on its own. Runs on a
     * background thread; swallows all errors — never blocks the caller.
     */
    fun reportStatusAsync(companyCode: String, storeCode: String, labelCode: String, status: String) {
        if (companyCode.isBlank() || storeCode.isBlank() || labelCode.isBlank()) return
        Thread {
            try {
                postJson(Constants.PATH_ESL_STATUS, JSONObject().apply {
                    put("companyCode", companyCode)
                    put("storeCode",   storeCode)
                    put("labelCode",   labelCode)
                    put("status",      status)
                })
            } catch (_: Exception) { /* best-effort */ }
        }.start()
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
    fun fetchAnalytics(company: String, store: String, range: String): JSONObject =
        get(Constants.PATH_ADMIN_ANALYTICS, mapOf("company" to company, "store" to store, "range" to range))

    private fun buildUrl(path: String, query: Map<String, String>): String {
        val base = Constants.RELAY_URL + path
        if (query.isEmpty()) return base
        val qs = query.entries.joinToString("&") { (k, v) ->
            URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
        }
        return "$base?$qs"
    }

    private fun readJson(conn: HttpURLConnection): JSONObject {
        val code   = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text   = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) {
            throw RuntimeException("HTTP $code: ${text.take(500)}")
        }
        return JSONObject(text)
    }

    private fun jsonArrayToStringList(arr: JSONArray): List<String> {
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) out.add(arr.optString(i))
        return out
    }
}
