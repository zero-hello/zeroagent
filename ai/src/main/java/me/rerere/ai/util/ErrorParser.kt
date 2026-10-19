package me.rerere.ai.util

import kotlinx.serialization.json.*

class HttpException(
    message: String,
    val statusCode: Int? = null,
    val errorCode: String? = null,
) : RuntimeException(message)

/** Keep transport status and machine codes when unwrapping a provider's nested message. */
fun JsonElement.parseErrorDetail(statusCode: Int? = null, errorCode: String? = null): HttpException = when (this) {
    is JsonObject -> {
        val code = listOf("code", "type", "status").firstNotNullOfOrNull { key ->
            (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.toIntOrNull() == null && it != "error" }
        } ?: errorCode
        val status = statusCode ?: (this["code"] as? JsonPrimitive)?.intOrNull?.takeIf { it in 400..599 }
            ?: when (code) {
                "invalid_request_error", "unsupported_value" -> 400
                "authentication_error", "invalid_api_key" -> 401
                "permission_error" -> 403
                "not_found_error", "model_not_found" -> 404
                "rate_limit_error", "rate_limit_exceeded", "insufficient_quota" -> 429
                "api_error" -> 500
                "overloaded_error" -> 529
                else -> null
            }
        val field = listOf("error", "detail", "message", "description").firstOrNull { this[it] != null && this[it] != JsonNull }
        if (field != null) this.getValue(field).parseErrorDetail(status, code)
        else HttpException(Json.encodeToString(JsonElement.serializer(), this), status, code)
    }
    is JsonArray -> if (isEmpty()) HttpException("Unknown error: Empty JSON array", statusCode, errorCode)
        else first().parseErrorDetail(statusCode, errorCode)
    is JsonPrimitive -> HttpException(content, statusCode, errorCode)
}
