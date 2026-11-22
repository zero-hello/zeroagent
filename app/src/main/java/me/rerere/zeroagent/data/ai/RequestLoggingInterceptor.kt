package me.rerere.zeroagent.data.ai

import me.rerere.common.android.LogEntry
import me.rerere.common.android.Logging
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit

class RequestLoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!Logging.isRequestLoggingEnabled()) return chain.proceed(request)

        // Endpoints may embed credentials in any part of the URL.
        val target = if (request.url.isHttps) "HTTPS" else "HTTP"
        val method = request.method.takeIf { it in safeMethods } ?: "OTHER"
        val startedAt = System.nanoTime()
        val response = try {
            chain.proceed(request)
        } catch (failure: Exception) {
            Logging.logRequest(LogEntry.RequestLog(
                tag = "HTTP",
                url = target,
                method = method,
                durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
                error = failure.javaClass.simpleName,
            ))
            throw failure
        }
        Logging.logRequest(LogEntry.RequestLog(
            tag = "HTTP",
            url = target,
            method = method,
            responseCode = response.code,
            durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
        ))
        return response
    }

    private companion object {
        val safeMethods = setOf("GET", "HEAD", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "CONNECT", "TRACE")
    }
}
