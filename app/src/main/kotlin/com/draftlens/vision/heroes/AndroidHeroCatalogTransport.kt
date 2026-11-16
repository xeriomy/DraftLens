package com.draftlens.vision.heroes

import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL

/** Redirect policy shared by the transport; each hop is re-validated. */
object HeroRedirectPolicy {
    const val MAX_REDIRECTS = 5
    private const val MAX_URL_LENGTH = 2048

    /**
     * Resolves [location] against [currentUrl] (relative redirects allowed) and enforces the
     * only policy this app accepts: HTTPS everywhere. Downgrades to HTTP and any other
     * scheme are refused. Returns null when the redirect cannot be honoured safely.
     */
    fun resolveTarget(currentUrl: String, location: String?): String? {
        if (location.isNullOrBlank()) return null
        val target = try {
            URL(URL(currentUrl), location).toString()
        } catch (e: Exception) {
            return null
        }
        if (target.length > MAX_URL_LENGTH) return null
        if (!target.startsWith("https://")) return null
        return target
    }
}

/** One attempt's outcome: a terminal result, or a redirect the caller must re-validate. */
private sealed interface TryOutcome
private class Redirected(val target: String) : TryOutcome
private class Completed(val result: HeroHttpResult) : TryOutcome

/**
 * HTTPS-only transport over the platform HTTP client. No third-party dependency.
 * Redirects are followed manually and every hop is re-validated by [HeroRedirectPolicy]:
 * HTTP or HTTPS-to-HTTP downgrade redirects are refused. One retry on I/O errors and
 * 5xx; 429 fails fast with its code. The size cap is enforced while reading, so a
 * hostile or broken endpoint cannot exhaust memory.
 */
class AndroidHeroCatalogTransport(
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
) : HeroCatalogTransport {

    private val redirectCodes = setOf(301, 302, 303, 307, 308)

    override fun get(url: String, maxBytes: Long, accept: String?): HeroHttpResult {
        if (maxBytes <= 0L) return HeroHttpResult.Failure("The size cap must be positive")
        if (!url.startsWith("https://")) {
            return HeroHttpResult.Failure("Only HTTPS URLs are allowed: $url")
        }
        var current = url
        var redirectBudget = HeroRedirectPolicy.MAX_REDIRECTS
        while (true) {
            when (val outcome = tryWithRetries(current, maxBytes, accept)) {
                is Redirected -> {
                    if (redirectBudget == 0) {
                        return HeroHttpResult.Failure("Too many redirects from $url")
                    }
                    redirectBudget--
                    current = outcome.target
                }

                is Completed -> return outcome.result
            }
        }
    }

    /** One hop with the one-retry-on-I-O/5xx policy. Redirects bubble up unretried. */
    private fun tryWithRetries(url: String, maxBytes: Long, accept: String?): TryOutcome {
        var lastFailure: HeroHttpResult.Failure? = null
        repeat(2) { attempt ->
            when (val outcome = attemptOnce(url, maxBytes, accept)) {
                is Redirected -> return outcome
                is Completed -> when (val result = outcome.result) {
                    is HeroHttpResult.Success -> return outcome
                    is HeroHttpResult.Failure -> {
                        lastFailure = result
                        val retryable = result.httpCode == null || result.httpCode >= 500
                        if (!retryable) return outcome // 4xx, including 429: fail fast
                        if (attempt == 1) return outcome
                    }
                }
            }
        }
        return Completed(lastFailure ?: HeroHttpResult.Failure("The request failed"))
    }

    private fun attemptOnce(url: String, maxBytes: Long, accept: String?): TryOutcome {
        val connection = try {
            (URL(url).openConnection() as? HttpURLConnection)
                ?: return Completed(HeroHttpResult.Failure("Only HTTP(S) URLs are supported: $url"))
        } catch (e: Exception) {
            return Completed(HeroHttpResult.Failure("The URL could not be opened: ${e.message}"))
        }
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            // Redirects are handled manually so every hop is re-validated (HTTPS only).
            connection.instanceFollowRedirects = false
            if (accept != null) connection.setRequestProperty("Accept", accept)
            connection.setRequestProperty("User-Agent", "DraftLens-HeroCatalog/1.0")
            val code = connection.responseCode
            if (code in redirectCodes) {
                val location = connection.getHeaderField("Location")
                val target = HeroRedirectPolicy.resolveTarget(url, location)
                if (target != null) {
                    Redirected(target)
                } else {
                    Completed(
                        HeroHttpResult.Failure(
                            "Refusing insecure redirect from $url to $location",
                            code,
                        ),
                    )
                }
            } else if (code in 200..299) {
                val bytes = readCapped(connection.inputStream, maxBytes)
                    ?: return Completed(HeroHttpResult.Failure("The response exceeds $maxBytes bytes", code))
                val contentType = connection.contentType?.substringBefore(';')?.trim()?.lowercase()
                Completed(HeroHttpResult.Success(HeroHttpResponse(bytes, contentType)))
            } else {
                Completed(HeroHttpResult.Failure("HTTP $code from $url", code))
            }
        } catch (e: Exception) {
            Completed(HeroHttpResult.Failure("The request failed: ${e.message}"))
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
