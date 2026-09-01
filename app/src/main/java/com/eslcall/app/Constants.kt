package com.eslcall.app

object Constants {
    val RELAY_URL        = BuildConfig.RELAY_URL.trimEnd('/')
    val CONNECT_TIMEOUT_MS = BuildConfig.NETWORK_CONNECT_TIMEOUT_SECONDS.coerceIn(1, 120) * 1_000
    val READ_TIMEOUT_MS    = BuildConfig.NETWORK_READ_TIMEOUT_SECONDS.coerceIn(1, 300) * 1_000

    // Relay admin endpoints (proxied to Solum, see esl-relay/src/index.js).
    const val PATH_AUTH_LOGIN          = "/auth/login"
    const val PATH_AUTH_LOGOUT         = "/auth/logout"
    const val PATH_AUTH_STATUS         = "/auth/status"
    const val PATH_DEVICE_REGISTER     = "/devices/register"
    const val PATH_DEVICE_UNREGISTER   = "/devices/unregister"
    const val PATH_ESL_ACKNOWLEDGE     = "/esl/acknowledge"
    const val PATH_ESL_STATUS          = "/esl/status"
    const val PATH_ADMIN_STORES        = "/admin/stores"
    const val PATH_ADMIN_FORMAT        = "/admin/articles/upload/format"
    const val PATH_ADMIN_FIELD_MAPPING = "/admin/field-mapping"
    const val PATH_ADMIN_ANALYTICS     = "/admin/analytics"

    // FCM topic prefix — the relay sends alerts to <prefix>-<company>-<store>.
}
