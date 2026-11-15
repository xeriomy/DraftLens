package com.draftlens.vision.heroes

import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * HTTPS-only transport over the platform HTTP client. No third-party dependency.
 * One retry on I/O errors and 5xx; 429 fails fast with its code. The size cap is
 * enforced while reading, so a hostile or broken endpoint cannot exhaust memory.
 */
class AndroidHeroCatalogTransport(
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
) : HeroCatalogTransport {

    override fun get(url: String, maxBytes: Long, accept: String?): HeroHttpResult {
        if (maxBytes <= 0L) return HeroHttpResult.Failure("The size cap must be positive")
        if (!url.startsWith("https://")) {
            return HeroHttpResult.Failure("Only HTTPS URLs are allowed: $url")
        }
        var lastFailure: HeroHttpResult.Failure? = null
        repeat(2) { attempt ->
            when (val outcome = attemptOnce(url, maxBytes, accept)) {
                is HeroHttpResult.Success -> return outcome
                is HeroHttpResult.Failure -> {
                    lastFailure = outcome
                    val retryable = outcome.httpCode == null || outcome.httpCode >= 500
                    if (!retryable) return outcome // 4xx, including 429: fail fast, do not parallel-hammer
                    if (attempt == 1) return outcome
                }
            }
        }
        return lastFailure ?: HeroHttpResult.Failure("The request failed")
    }

    private fun attemptOnce(url: String, maxBytes: Long, accept: String?): HeroHttpResult {
        val connection = try {
            (URL(url).openConnection() as? HttpURLConnection)
                ?: return HeroHttpResult.Failure("Only HTTP(S) URLs are supported: $url")
        } catch (e: Exception) {
            return HeroHttpResult.Failure("The URL could not be opened: ${e.message}")
        }
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            if (accept != null) connection.setRequestProperty("Accept", accept)
            connection.setRequestProperty("User-Agent", "DraftLens-HeroCatalog/1.0")
            val code = connection.responseCode
            if (code in 200..299) {
                val bytes = readCapped(connection.inputStream, maxBytes)
                    ?: return HeroHttpResult.Failure("The response exceeds $maxBytes bytes", code)
                val contentType = connection.contentType?.substringBefore(';')?.trim()?.lowercase()
                HeroHttpResult.Success(HeroHttpResponse(bytes, contentType))
            } else {
                HeroHttpResult.Failure("HTTP $code from $url", code)
            }
        } catch (e: Exception) {
            HeroHttpResult.Failure("The request failed: ${e.message}")
        } finally {
            try {
                connection.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    private fun readCapped(input: java.io.InputStream, maxBytes: Long): ByteArray? {
        val buffer = ByteArray(8192)
        val out = java.io.ByteArrayOutputStream()
        BufferedInputStream(input).use { stream ->
            var total = 0L
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                total += read
                if (total > maxBytes) return null
                out.write(buffer, 0, read)
            }
        }
        return out.toByteArray()
    }
}
