package com.draftlens.vision.heroes

/** One ranked hero shown after an Identify action. Similarity is [0, 1], never a probability. */
data class HeroCandidate(
    val hero: HeroCatalogEntry,
    val similarity: Float,
    val ambiguous: Boolean,
)

/** What the Identify button produced. */
sealed interface HeroIdentifyOutcome {
    data class Candidates(val candidates: List<HeroCandidate>) : HeroIdentifyOutcome
    data class NoMatch(val bestSimilarity: Float) : HeroIdentifyOutcome
    data class Failed(val message: String) : HeroIdentifyOutcome
}

/**
 * Recognition v0 orchestration. Pure: pixels and preloaded references in, outcome out.
 * Decoding assets and building references happen on the caller's side so tests can inject
 * synthetic references.
 */
object HeroRecognition {

    fun identify(
        pixels: IntArray,
        width: Int,
        height: Int,
        references: List<HeroReference>,
        config: HeroMatchConfig = HeroMatchConfig(),
    ): HeroIdentifyOutcome {
        val usable = references.filter { it.pixels.size == it.width * it.height && it.width > 0 && it.height > 0 }
        if (width <= 0 || height <= 0 || pixels.size != width * height) {
            return HeroIdentifyOutcome.Failed("The extracted image is empty or invalid.")
        }
        if (usable.isEmpty()) {
            return HeroIdentifyOutcome.Failed(
                "No usable hero reference images are cached. Sync the hero catalog first.",
            )
        }
        return when (val result = HeroMatcher.match(pixels, width, height, usable, config)) {
            is HeroMatchResult.Candidates -> HeroIdentifyOutcome.Candidates(
                result.matches.map { HeroCandidate(it.entry, it.similarity, it.ambiguous) },
            )

            is HeroMatchResult.NoMatch -> HeroIdentifyOutcome.NoMatch(result.bestSimilarity)
        }
    }
}
