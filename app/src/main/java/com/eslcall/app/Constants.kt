package com.eslcall.app

object Constants {
    const val RELAY_URL        = "https://esl-relay-production.up.railway.app"
    const val AUTH_HEADER      = "x-auth-key"
    const val AUTH_KEY         = "esl-secret-2024"
    const val ALERT_TIMEOUT_MS = 60_000L

    // Relay admin endpoints (proxied to Solum, see esl-relay/src/index.js).
    const val PATH_AUTH_LOGIN          = "/auth/login"
    const val PATH_AUTH_LOGOUT         = "/auth/logout"
    const val PATH_AUTH_STATUS         = "/auth/status"
    const val PATH_ESL_ACKNOWLEDGE     = "/esl/acknowledge"
    const val PATH_ESL_STATUS          = "/esl/status"
    const val PATH_ADMIN_STORES        = "/admin/stores"
    const val PATH_ADMIN_FORMAT        = "/admin/articles/upload/format"
    const val PATH_ADMIN_FIELD_MAPPING = "/admin/field-mapping"
    const val PATH_ADMIN_ANALYTICS     = "/admin/analytics"

    // FCM topic prefix — the relay sends alerts to <prefix>-<company>-<store>.
    const val FCM_TOPIC_PREFIX = "employee-calls"
}
