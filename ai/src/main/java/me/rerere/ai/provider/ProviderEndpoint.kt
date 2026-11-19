package me.rerere.ai.provider

import okhttp3.HttpUrl.Companion.toHttpUrl

/** Accept a base URL or a complete endpoint. Never append an endpoint twice. */
fun resolveProviderEndpoint(baseUrl: String, path: String): String {
    val url = baseUrl.trim().toHttpUrl()
    require(url.username.isEmpty() && url.password.isEmpty()) { "Put credentials in the API Key field." }
    val baseSegments = url.pathSegments.filter(String::isNotEmpty)
    val targetSegments = path.trim('/').split('/').filter(String::isNotEmpty)
    require(targetSegments.isNotEmpty() && targetSegments.none { it in setOf(".", "..") || '?' in it || '#' in it })
    val knownEndpoints = listOf(listOf("chat", "completions"), listOf("images", "generations"), listOf("images", "edits"), listOf("responses"), listOf("messages"), listOf("models"), listOf("chat"), listOf("images"))
    val completeSuffix = knownEndpoints.firstOrNull { baseSegments.takeLast(it.size) == it }
    var root = if (completeSuffix == null) baseSegments else baseSegments.dropLast(completeSuffix.size)
    val apiVersion = Regex("v[0-9]+(?:beta[0-9]*)?")
    if (targetSegments.first().matches(apiVersion) && root.lastOrNull()?.matches(apiVersion) == true) root = root.dropLast(1)
    val overlap = (minOf(root.size, targetSegments.size) downTo 1)
        .firstOrNull { root.takeLast(it) == targetSegments.take(it) } ?: 0
    val segments = root + targetSegments.drop(overlap)
    return url.newBuilder().encodedPath("/").apply {
        segments.forEach { addPathSegment(it) }
    }.fragment(null).build().toString()
}
