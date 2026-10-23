package dev.neoworks.otter.profiles

// Mirrors packages/otter-app/src/avatars.ts. Presets are stored on the profile
// as "<set>:<name>" and ship as assets/avatars/<set>-<name>.webp.

data class AvatarSet(val key: String, val label: String, val avatars: List<String>)

private fun avatarSet(key: String, label: String, names: List<String>): AvatarSet {
    return AvatarSet(key, label, names.map { name -> "$key:$name" })
}

/** The groups the icon chooser shows, in order. */
val AVATAR_SETS: List<AvatarSet> = listOf(
    avatarSet(
        "otter", "Otters",
        listOf("smile", "wink", "sunglasses", "headphones", "beanie", "sleepy", "surprised", "tongue", "bowtie", "popcorn", "glasses3d", "crown"),
    ),
    avatarSet(
        "heroes", "Movie Heroes",
        listOf("astronaut", "detective", "cowboy", "pirate", "knight", "wizard", "ninja", "vampire", "superhero", "robot", "zombie", "rockstar"),
    ),
    avatarSet(
        "monsters", "Monsters",
        listOf("fluffy", "alien", "dragon", "ghost", "yeti", "slime", "furball", "cyclops", "gremlin", "kraken", "moss", "star"),
    ),
    avatarSet(
        "forest", "Forest",
        listOf("fox", "raccoon", "redpanda", "owl", "hedgehog", "bear", "fawn", "squirrel", "badger", "wolf", "bunny", "koala"),
    ),
    avatarSet(
        "sea", "River & Sea",
        listOf("beaver", "seal", "penguin", "axolotl", "turtle", "puffin", "frog", "duckling", "octopus", "crab", "narwhal", "platypus"),
    ),
    avatarSet(
        "robots", "Robots",
        listOf("happy", "wink", "hearts", "sleepy", "surprised", "laughing", "cool", "thinking", "excited", "shy", "grin", "lovestruck"),
    ),
    avatarSet(
        "kids", "Kids",
        listOf("otter", "chick", "piglet", "puppy", "kitten", "elephant", "lamb", "panda", "giraffe", "bunny", "penguin", "dino"),
    ),
)

val PRESET_AVATARS: List<String> = AVATAR_SETS.flatMap { set -> set.avatars }

private val presetIds = PRESET_AVATARS.toSet()

/** A stable otter for a name; same hash as the web app so both show the same default. */
fun defaultAvatarFor(name: String): String {
    val defaults = AVATAR_SETS.first().avatars
    var hash = 0L
    name.codePoints().forEach { codePoint -> hash = (hash * 31 + firstCodeUnit(codePoint)) and 0xFFFFFFFFL }
    return defaults[(hash % defaults.size).toInt()]
}

// The web app hashes `character.charCodeAt(0)` per code point: the high surrogate for emoji.
private fun firstCodeUnit(codePoint: Int): Int {
    if (Character.isSupplementaryCodePoint(codePoint)) {
        return Character.highSurrogate(codePoint).code
    }
    return codePoint
}

/**
 * Image model (URL) for a stored avatar value: presets become their bundled
 * asset, remote URLs pass through, anything else (including the old inline-SVG
 * "avatar:<color>-<face>" values) falls back to the name's default otter.
 */
fun avatarImage(avatarUrl: String?, name: String): String {
    if (avatarUrl != null && isRemote(avatarUrl)) {
        return avatarUrl
    }
    if (avatarUrl != null && avatarUrl in presetIds) {
        return presetAssetUrl(avatarUrl)
    }
    return presetAssetUrl(defaultAvatarFor(name))
}

private fun isRemote(avatarUrl: String): Boolean {
    return avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://") || avatarUrl.startsWith("data:")
}

private fun presetAssetUrl(avatarId: String): String {
    return "file:///android_asset/avatars/${avatarId.replace(':', '-')}.webp"
}
