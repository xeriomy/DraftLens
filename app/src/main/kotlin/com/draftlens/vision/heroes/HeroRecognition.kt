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

    /**
     * The crop is too empty or featureless for matching to mean anything: a uniform gray,
     * black, white or single-colour image, or a crop smaller than the minimum useful size.
     * Distinct from [NoMatch]: no similarity was computed and no candidates exist.
     */
    data class InsufficientVisualInformation(val reason: String) : HeroIdentifyOutcome
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
        if (width <= 0 || height <= 0 || pixels.size != width * height) {
            return HeroIdentifyOutcome.Failed("The extracted image is empty or invalid.")
        }
        // Blank and low-information crops must never produce candidates. This runs before the
        // catalog is consulted: there is nothing meaningful to match.
        val information = VisualInformationAnalyzer.analyze(pixels, width, height)
        information.insufficientReason?.let { reason ->
            return HeroIdentifyOutcome.InsufficientVisualInformation(
                "Not enough visual information: $reason. Select a clearer hero crop.",
            )
        }
        val usable = references.filter { it.pixels.size == it.width * it.height && it.width > 0 && it.height > 0 }
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
