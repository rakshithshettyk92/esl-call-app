package com.eslcall.app

data class Store(
    val code:    String,
    val name:    String,
    val region:  String? = null,
    val city:    String? = null,
    val country: String? = null,
) {
    fun display(): String = if (name.isNotBlank() && name != code) "$name ($code)" else code
}

data class CallFieldMapping(
    val articleIdField:     String,
    val articleNameField:   String,
    val helpEnabledField:   String,
    val helpEnabledValue:   String,
    val aisleField:         String? = null,
    /** Seconds the ESL stays on page 2 after acknowledge before reverting to page 1. */
    val revertDelaySeconds: Int = 60,
    val allColumns:         List<String> = emptyList(),
) {
    fun isComplete(): Boolean =
        articleIdField.isNotBlank() &&
        articleNameField.isNotBlank() &&
        helpEnabledField.isNotBlank() &&
        helpEnabledValue.isNotBlank() &&
        revertDelaySeconds in 5..600

    companion object {
        val DEFAULT = CallFieldMapping(
            articleIdField     = "ARTICLE_ID",
            articleNameField   = "ITEM_NAME",
            helpEnabledField   = "ASSOCIATE_HELP_ENABLED",
            helpEnabledValue   = "Y",
            aisleField         = null,
            revertDelaySeconds = 60,
        )
    }
}
