package com.example.core

/**
 * Centralized brand configuration for MK Assist.
 */
object AppBrand {
    const val NAME = "MK Assist"
    const val TAGLINE = "Global Free AI Creative Super App"
    const val VERSION_LABEL = "v1.0 • Free & Open Access"
    const val FREE_POLICY_SUMMARY =
        "100% Free for everyone worldwide. No subscriptions, no paywalls, no locked tools. " +
            "AI features rely on free/authorized Gemini tiers and clearly report service limits if reached."
}

data class SupportedLanguage(
    val code: String,
    val label: String,
    val nativeName: String
)

object AppLanguages {
    val all = listOf(
        SupportedLanguage("en", "English", "English"),
        SupportedLanguage("bn", "Bengali", "বাংলা"),
        SupportedLanguage("ar", "Arabic", "العربية"),
        SupportedLanguage("es", "Spanish", "Español"),
        SupportedLanguage("fr", "French", "Français"),
        SupportedLanguage("hi", "Hindi", "हिन्दी")
    )

    fun byCode(code: String): SupportedLanguage =
        all.firstOrNull { it.code == code } ?: all.first()
}
