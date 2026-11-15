package com.draftlens.vision.heroes

object HeroIds {
    /**
     * Normalized English name -> provider-independent slug.
     *
     * Lower-cased, every run of non-alphanumeric characters becomes one hyphen, leading and
     * trailing hyphens removed. MLBBDex slugs follow the same rule for the names checked in
     * this sprint ("Yi Sun-shin" -> "yi-sun-shin").
     */
    fun slugOf(name: String): String =
        name.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .also { require(it.isNotEmpty()) { "hero name '$name' normalizes to an empty slug" } }
}
