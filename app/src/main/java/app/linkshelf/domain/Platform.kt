package app.linkshelf.domain

/**
 * Social platforms the app sorts links into. Each one is a folder in the UI.
 * Colors are ARGB values used for the folder and card gradients.
 */
enum class Platform(
    val displayName: String,
    val shortLabel: String,
    val gradient: List<Long>,
) {
    INSTAGRAM("Instagram", "IG", listOf(0xFFF58529, 0xFFDD2A7B, 0xFF8134AF)),
    FACEBOOK("Facebook", "FB", listOf(0xFF3B8BFF, 0xFF0B4FB3)),
    YOUTUBE("YouTube", "YT", listOf(0xFFFF4B4B, 0xFF9E0000)),
    LINKEDIN("LinkedIn", "LI", listOf(0xFF2A86E0, 0xFF063F7A)),
    TIKTOK("TikTok", "TT", listOf(0xFF161616, 0xFF1F6F6C)),
    X("X / Twitter", "X", listOf(0xFF3A404D, 0xFF0D0F14)),
    OTHER("Other", "•••", listOf(0xFF3A4252, 0xFF1A1F29));

    companion object {
        fun fromKey(key: String?): Platform =
            entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: OTHER
    }
}
