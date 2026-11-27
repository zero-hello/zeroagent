package me.rerere.zeroagent.data.sync

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

private val nonPortableFields = setOf(
    "apikey", "apikeys", "token", "accesstoken", "refreshtoken", "idtoken", "authtoken",
    "password", "proxypassword", "passphrase", "accesskey", "accesskeyid", "secretkey",
    "secretaccesskey", "clientsecret", "sessiontoken", "privatekey", "credential",
    "credentials", "authorization", "cookie", "cookies", "user", "username",
    "customheaders", "custombodies", "headers", "mcpservers", "searchservices",
    "networksetting", "webdavconfig", "s3config", "ttsproviders", "selectedttsproviderid", "serviceaccountemail",
)

/** Settings archives carry portable configuration. Authentication is configured on each device. */
internal fun stripBackupCredentials(value: JsonElement): JsonElement = when (value) {
    is JsonObject -> JsonObject(value.entries.mapNotNull { (key, child) ->
        val normalized = key.lowercase().filter(Char::isLetterOrDigit)
        val isCredential = normalized in nonPortableFields || normalized.endsWith("apikey") ||
            normalized.endsWith("password") || normalized.endsWith("secret") || normalized.endsWith("token")
        if (isCredential) null else key to stripBackupCredentials(child)
    }.toMap())
    is JsonArray -> JsonArray(value.map(::stripBackupCredentials))
    else -> value
}

/** Restore a local secret only for the same provider configuration and exact API endpoint. */
internal fun mergeLocalProviderCredentials(imported: JsonArray, local: JsonArray): JsonArray {
    val routeKeys = setOf("type", "id", "baseUrl", "chatCompletionsPath", "responsesPath",
        "useResponseApi", "protocol", "vertexAI", "useServiceAccount", "location", "projectId")
    val localCredentialKeys = setOf("apiKey", "privateKey", "customHeaders", "serviceAccountEmail")
    fun route(entry: JsonObject) = entry.filterKeys { it in routeKeys }
    val indexed = local.filterIsInstance<JsonObject>().associateBy(::route)
    return JsonArray(imported.map { entry ->
        val provider = stripBackupCredentials(entry) as? JsonObject ?: return@map entry
        val previous = indexed[route(provider)] ?: return@map provider
        JsonObject(provider + previous.filterKeys { it in localCredentialKeys })
    })
}
