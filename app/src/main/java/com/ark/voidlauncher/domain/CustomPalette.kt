package com.ark.voidlauncher.domain

/**
 * A user-defined colour palette. Colours are ARGB hex strings ("#FF121212") so
 * this type stays pure JVM and can live in the domain layer, be persisted by
 * DataStore, and travel inside backups without importing Compose.
 */
data class CustomPalette(
    val background: String,
    val surface: String,
    val surfaceVariant: String,
    val onBackground: String,
    val onSurface: String,
    val primary: String,
    val secondary: String,
    val outline: String,
) {
    companion object {
        /** Calm near-black base with soft tonal steps for depth. */
        fun darkDefault(): CustomPalette = CustomPalette(
            background = "#FF080808",
            surface = "#FF141414",
            surfaceVariant = "#FF202020",
            onBackground = "#FFEDEDED",
            onSurface = "#FFEDEDED",
            primary = "#FFE6E2D8",
            secondary = "#FFA8A29A",
            outline = "#FF3A3A3A",
        )

        /** Warm, low-glare light base — not a harsh pure white/black swap. */
        fun lightDefault(): CustomPalette = CustomPalette(
            background = "#FFF7F4EE",
            surface = "#FFFFFCF8",
            surfaceVariant = "#FFEDE6DA",
            onBackground = "#FF1C1917",
            onSurface = "#FF292524",
            primary = "#FF43382E",
            secondary = "#FF6B6157",
            outline = "#FFD6CDBF",
        )

        private val HEX = Regex("^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")

        fun isValidHex(value: String): Boolean = HEX.matches(value.trim())

        /** Normalises "#RRGGBB" to opaque "#FFRRGGBB"; returns null when invalid. */
        fun normalizeHex(value: String): String? {
            val trimmed = value.trim()
            if (!HEX.matches(trimmed)) return null
            val digits = trimmed.removePrefix("#")
            return if (digits.length == 6) "#FF$digits" else "#$digits"
        }
    }
}
