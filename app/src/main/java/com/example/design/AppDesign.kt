package com.example.design

import com.example.model.AppProject
import java.util.Locale

data class AppDesign(
    val primaryColor: Long,
    val secondaryColor: Long,
    val backgroundColor: Long,
    val cardStyle: String,
    val layoutType: String,
    val designStyle: String = "Modern"
) {
    val isDarkBackground: Boolean
        get() {
            val r = (backgroundColor shr 16) and 0xFF
            val g = (backgroundColor shr 8) and 0xFF
            val b = backgroundColor and 0xFF
            val luminance = (0.299 * r + 0.587 * g + 0.114 * b)
            return luminance < 140.0
        }

    fun toHexLiteral(colorValue: Long): String {
        return "0x" + (colorValue and 0xFFFFFFFFL).toString(16).uppercase(Locale.US).padStart(8, '0')
    }
}

fun designFor(project: AppProject): AppDesign {
    val combinedText = "${project.name} ${project.description} ${project.prompt}".lowercase(Locale.US)

    val baseDesign = when {
        containsAny(combinedText, "store", "shop", "ecommerce", "e-commerce", "clothes", "fashion", "retail", "cart") ->
            AppDesign(
                primaryColor = 0xFFFF6B00,
                secondaryColor = 0xFFFF8F00,
                backgroundColor = 0xFFFFFFFF,
                cardStyle = "LargeImageCard",
                layoutType = "GridProducts",
                designStyle = "Bold"
            )

        containsAny(combinedText, "clinic", "doctor", "hospital", "medical", "patient", "appointment", "dental") ->
            AppDesign(
                primaryColor = 0xFF00ACC1,
                secondaryColor = 0xFF26C6DA,
                backgroundColor = 0xFFFFFFFF,
                cardStyle = "DoctorCard",
                layoutType = "DoctorList",
                designStyle = "Professional"
            )

        containsAny(combinedText, "fitness", "gym", "workout", "exercise", "training", "health", "calorie") ->
            AppDesign(
                primaryColor = 0xFFE53935,
                secondaryColor = 0xFFFF5252,
                backgroundColor = 0xFF121212,
                cardStyle = "DarkStatCard",
                layoutType = "ProgressList",
                designStyle = "Bold"
            )

        containsAny(combinedText, "food", "restaurant", "coffee", "recipe", "menu", "kitchen", "delivery", "meal") ->
            AppDesign(
                primaryColor = 0xFFFFA000,
                secondaryColor = 0xFFFF6F00,
                backgroundColor = 0xFFFFF8E1,
                cardStyle = "FoodCard",
                layoutType = "FoodList",
                designStyle = "Playful"
            )

        containsAny(combinedText, "chat", "social", "message", "messenger", "community", "friends", "call") ->
            AppDesign(
                primaryColor = 0xFF25D366,
                secondaryColor = 0xFF128C7E,
                backgroundColor = 0xFFFFFFFF,
                cardStyle = "ChatBubble",
                layoutType = "ChatList",
                designStyle = "Modern"
            )

        containsAny(combinedText, "finance", "money", "budget", "expense", "wallet", "savings", "bank", "transaction") ->
            AppDesign(
                primaryColor = 0xFF1A237E,
                secondaryColor = 0xFF3949AB,
                backgroundColor = 0xFFF5F5F5,
                cardStyle = "BalanceCard",
                layoutType = "Dashboard",
                designStyle = "Professional"
            )

        containsAny(combinedText, "education", "school", "study", "flashcard", "quiz", "learn", "course", "lesson") ->
            AppDesign(
                primaryColor = 0xFF1565C0,
                secondaryColor = 0xFF42A5F5,
                backgroundColor = 0xFFFFFFFF,
                cardStyle = "CourseCard",
                layoutType = "CourseList",
                designStyle = "Modern"
            )

        containsAny(combinedText, "travel", "trip", "flight", "hotel", "vacation", "itinerary", "tour", "booking") ->
            AppDesign(
                primaryColor = 0xFF00897B,
                secondaryColor = 0xFF26A69A,
                backgroundColor = 0xFFFFFFFF,
                cardStyle = "TravelCard",
                layoutType = "TravelGrid",
                designStyle = "Modern"
            )

        else ->
            AppDesign(
                primaryColor = 0xFF6200EE,
                secondaryColor = 0xFF03DAC5,
                backgroundColor = 0xFF121212,
                cardStyle = "DefaultCard",
                layoutType = "SimpleList",
                designStyle = "Minimal"
            )
    }

    val customPrimary = parseColorHexOrNull(project.primaryColorHex)
    val customStyle = project.designStyle.takeIf { it.isNotBlank() }

    return baseDesign.copy(
        primaryColor = customPrimary ?: baseDesign.primaryColor,
        designStyle = customStyle ?: baseDesign.designStyle
    )
}

internal fun parseColorHexOrNull(hexString: String): Long? {
    val clean = hexString.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
    if (clean.length != 6 && clean.length != 8) return null
    val parsed = clean.toLongOrNull(16) ?: return null
    return if (clean.length == 6) {
        0xFF000000L or parsed
    } else {
        parsed
    }
}

private fun containsAny(text: String, vararg keywords: String): Boolean {
    return keywords.any { text.contains(it) }
}
