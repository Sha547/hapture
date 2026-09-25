package com.motionlab.app.core.spec

/**
 * Ready-made feel sets: one spring per common role, tuned for a kind of app, so a new project
 * starts from a coherent personality instead of eight unrelated numbers.
 */
object Archetypes {

    data class Named(val name: String, val stiffness: Float, val dampingRatio: Float)

    data class Archetype(val id: String, val title: String, val blurb: String, val springs: List<Named>)

    val all: List<Archetype> = listOf(
        Archetype(
            "fintech", "Fintech: restrained", "Fast, exact, no bounce. Nothing should feel like it could slip.",
            listOf(Named("tap", 700f, 1.0f), Named("toggle", 600f, 0.9f), Named("sheet", 420f, 0.95f), Named("list", 350f, 1.0f), Named("dismiss", 500f, 1.0f)),
        ),
        Archetype(
            "productivity", "Productivity: snappy", "Quick and light, a hint of life without ever waiting on it.",
            listOf(Named("tap", 900f, 0.85f), Named("toggle", 800f, 0.8f), Named("sheet", 500f, 0.9f), Named("list", 600f, 0.9f), Named("dismiss", 700f, 0.95f)),
        ),
        Archetype(
            "social", "Social: playful", "Springy and generous, with visible overshoot on the things people tap most.",
            listOf(Named("tap", 500f, 0.45f), Named("toggle", 450f, 0.5f), Named("sheet", 300f, 0.55f), Named("list", 260f, 0.6f), Named("celebrate", 350f, 0.3f)),
        ),
        Archetype(
            "wellness", "Wellness: calm", "Slow, soft arrivals that never startle.",
            listOf(Named("tap", 220f, 1.0f), Named("toggle", 240f, 0.95f), Named("sheet", 160f, 1.0f), Named("list", 140f, 1.0f), Named("breathe", 90f, 1.0f)),
        ),
        Archetype(
            "games", "Games: bouncy", "Big, exaggerated motion that rewards every touch.",
            listOf(Named("tap", 420f, 0.3f), Named("toggle", 380f, 0.35f), Named("sheet", 260f, 0.4f), Named("list", 220f, 0.45f), Named("reward", 300f, 0.2f)),
        ),
    )

    /** What applying [springs] to a token list with these [existingNames] would do, so the UI can say it before doing it. */
    data class Plan(val added: Int, val overwritten: Int)

    fun plan(existingNames: List<String>, springs: List<Named>): Plan {
        val have = existingNames.map { it.lowercase() }.toSet()
        val over = springs.count { it.name.lowercase() in have }
        return Plan(springs.size - over, over)
    }
}
