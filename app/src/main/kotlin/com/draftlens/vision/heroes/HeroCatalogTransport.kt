package com.draftlens.vision.heroes

/** One HTTPS GET. Implementations enforce the size cap while reading. */
interface HeroCatalogTransport {
    fun get(url: String, maxBytes: Long, accept: String?): HeroHttpResult
}

data class HeroHttpResponse(val bytes: ByteArray, val contentType: String?)

sealed interface HeroHttpResult {
    data class Success(val response: HeroHttpResponse) : HeroHttpResult
    data class Failure(val message: String, val httpCode: Int? = null) : HeroHttpResult
}
