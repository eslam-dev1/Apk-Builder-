package com.example.design

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.model.AppProject
import com.example.model.ScreenSpec
import java.util.Locale

/**
 * Fixed whitelist of icon keys. The AI is told to pick from [keys] so the preview
 * never has to guess what an arbitrary string means.
 */
object ScreenIcons {

    val keys: List<String> = listOf(
        "home", "dashboard", "search", "explore", "person", "settings", "cart", "favorite",
        "notifications", "chat", "map", "calendar", "chart", "folder", "payments", "fitness",
        "restaurant", "school", "music", "photo", "history", "health", "flight", "note",
        "timer", "list", "mail", "call", "share", "lock", "info", "star", "add", "edit"
    )

    fun isValid(key: String): Boolean = key.trim().lowercase(Locale.ROOT) in keys

    fun vector(key: String): ImageVector = when (key.trim().lowercase(Locale.ROOT)) {
        "home" -> Icons.Default.Home
        "dashboard" -> Icons.Default.Dashboard
        "search" -> Icons.Default.Search
        "explore" -> Icons.Default.Explore
        "person" -> Icons.Default.Person
        "settings" -> Icons.Default.Settings
        "cart" -> Icons.Default.ShoppingCart
        "favorite" -> Icons.Default.Favorite
        "notifications" -> Icons.Default.Notifications
        "chat" -> Icons.Default.ChatBubble
        "map" -> Icons.Default.Map
        "calendar" -> Icons.Default.CalendarMonth
        "chart" -> Icons.Default.BarChart
        "folder" -> Icons.Default.Folder
        "payments" -> Icons.Default.Payments
        "fitness" -> Icons.Default.FitnessCenter
        "restaurant" -> Icons.Default.Restaurant
        "school" -> Icons.Default.School
        "music" -> Icons.Default.MusicNote
        "photo" -> Icons.Default.Photo
        "history" -> Icons.Default.History
        "health" -> Icons.Default.LocalHospital
        "flight" -> Icons.Default.Flight
        "note" -> Icons.Default.Description
        "timer" -> Icons.Default.Timer
        "list" -> Icons.Default.Menu
        "mail" -> Icons.Default.Email
        "call" -> Icons.Default.Call
        "share" -> Icons.Default.Share
        "lock" -> Icons.Default.Lock
        "info" -> Icons.Default.Info
        "star" -> Icons.Default.Star
        "add" -> Icons.Default.Add
        "edit" -> Icons.Default.Edit
        else -> Icons.Default.Widgets
    }
}

/**
 * Turns a project into a list of clear, labelled screens matching the count
 * determined by the AI or customized by the user (without hardcoded default tabs).
 * Uses the AI-provided specs when they exist, otherwise derives them from screen names.
 */
object ScreenSpecs {

    /** Every screen of the project (used by lists/chips), normalized with valid icons and per-screen content. */
    fun all(project: AppProject): List<ScreenSpec> {
        val explicit = project.screenSpecs.filter { it.name.isNotBlank() }
        if (explicit.isNotEmpty()) {
            return explicit.mapIndexed { index, spec ->
                normalizeSpec(spec, project, index)
            }
        }
        return derive(project)
    }

    /** The screens shown as navigation tabs in Live Preview (preserves dynamic count). */
    fun of(project: AppProject): List<ScreenSpec> = all(project)

    fun labelFor(project: AppProject, screenName: String): String {
        val match = all(project).firstOrNull { it.name == screenName }
        return match?.label?.takeIf { it.isNotBlank() } ?: prettyLabel(screenName)
    }

    private fun normalizeSpec(
        spec: ScreenSpec,
        project: AppProject,
        index: Int
    ): ScreenSpec {
        val label = spec.label.trim()
            .takeIf { it.isNotBlank() && !it.endsWith("Screen", ignoreCase = true) }
            ?: prettyLabel(spec.name)
        val icon = if (ScreenIcons.isValid(spec.icon)) {
            spec.icon.trim().lowercase(Locale.ROOT)
        } else {
            guessIcon("${spec.name} $label")
        }
        val purpose = spec.purpose.trim().ifBlank {
            project.features.getOrNull(index) ?: project.description
        }
        val components = spec.components.ifEmpty {
            defaultComponentsFor(spec.name, label)
        }
        val sampleItems = spec.sampleItems.ifEmpty {
            defaultSampleItemsFor(spec.name, label, project, index)
        }
        return spec.copy(
            label = label,
            icon = icon,
            purpose = purpose,
            components = components,
            sampleItems = sampleItems
        )
    }

    /** Splits "FileExplorerScreen" / "file explorer" into lowercase word tokens. */
    fun tokens(text: String): List<String> =
        text.replace(Regex("([a-z0-9])([A-Z])"), "\$1 \$2")
            .lowercase(Locale.ROOT)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotBlank() }

    fun prettyLabel(rawName: String): String {
        val words = tokens(rawName).filter { it != "screen" }
        if (words.isEmpty()) return rawName.ifBlank { "Screen" }
        val joined = words.joinToString(" ") { w -> w.replaceFirstChar { it.uppercaseChar() } }
        return if (joined.length <= 14) joined else words.take(2)
            .joinToString(" ") { w -> w.replaceFirstChar { it.uppercaseChar() } }
            .take(14)
    }

    fun guessIcon(rawName: String): String {
        val tokens = tokens(rawName)
        for ((icon, words) in ICON_RULES) {
            if (tokens.any { t -> words.any { w -> matches(t, w) } }) return icon
        }
        return "star"
    }

    private fun matches(token: String, keyword: String): Boolean =
        if (keyword.length <= 3) token == keyword || token == keyword + "s"
        else token == keyword || token.startsWith(keyword)

    private fun derive(project: AppProject): List<ScreenSpec> {
        if (project.screens.isEmpty()) return emptyList()
        val names = project.screens
        val features = project.features
        return names.mapIndexed { index, raw ->
            val label = prettyLabel(raw)
            ScreenSpec(
                name = raw,
                label = label,
                icon = guessIcon(raw),
                purpose = features.getOrNull(index) ?: project.description,
                components = defaultComponentsFor(raw, label),
                sampleItems = defaultSampleItemsFor(raw, label, project, index)
            )
        }
    }

    private fun defaultComponentsFor(rawName: String, label: String): List<String> {
        return when (guessIcon("$rawName $label")) {
            "fitness" -> listOf("Active Timer", "Set Logger", "Intensity Filter", "Rest Counter")
            "restaurant" -> listOf("Calorie Counter", "Macro Rings", "Meal Log", "Quick Add")
            "chart" -> listOf("Trend Chart", "KPI Summary", "Date Filter", "Export CSV")
            "cart" -> listOf("Product Grid", "Category Chips", "Cart Badge", "Checkout Bar")
            "payments" -> listOf("Balance Card", "Expense Filter", "Budget Bar", "Add Entry")
            "chat" -> listOf("Thread List", "Quick Reply", "Voice Note", "Search Bar")
            "folder" -> listOf("Path Breadcrumb", "Storage Meter", "Sort Menu", "File Inspector")
            "calendar" -> listOf("Week Strip", "Time Blocks", "Reminder Toggle", "Add Event")
            "person" -> listOf("Profile Header", "Preferences", "Achievements", "Backup Sync")
            "settings" -> listOf("Theme Toggle", "Notifications", "Data Export", "About App")
            "search" -> listOf("Search Input", "Filter Chips", "Recent Queries", "Result List")
            else -> listOf("$label Header", "Filter Chips", "Action List", "Quick Action")
        }
    }

    private fun defaultSampleItemsFor(
        rawName: String,
        label: String,
        project: AppProject,
        index: Int
    ): List<String> {
        return when (guessIcon("$rawName $label")) {
            "fitness" -> listOf(
                "Upper Body Strength • 45 min • 6 exercises",
                "HIIT Conditioning Circuit • 25 min • 320 kcal",
                "Core & Mobility Flow • 18 min • Recovery",
                "Lower Body Power • 40 min • 5 sets"
            )
            "restaurant" -> listOf(
                "Grilled Chicken Bowl • 540 kcal • 46g Protein",
                "Oats & Berry Shake • 390 kcal • Breakfast",
                "Salmon & Quinoa Plate • 620 kcal • Dinner",
                "Hydration Target • 2.4 L / 3.0 L today"
            )
            "chart" -> listOf(
                "Weekly Goal Completion • 84% on track",
                "7-Day Active Streak • Personal Best",
                "Monthly Trend • +12% vs last month",
                "Logged Check-ins • 18 entries this week"
            )
            "cart" -> listOf(
                "Featured Selection • $49.00 • In Stock",
                "Seasonal New Arrival • $74.50 • Free Shipping",
                "Best Seller Bundle • $89.00 • Save 15%",
                "Express Delivery • Est. 2 business days"
            )
            "payments" -> listOf(
                "Monthly Budget Status • 62% utilized",
                "Recent Transaction • Completed Today",
                "Recurring Subscriptions • 4 active items",
                "Savings Milestone • 78% funded"
            )
            "person" -> listOf(
                "${project.name} Member • Active Profile",
                "Saved Preferences • Synced locally",
                "Achievements Unlocked • 8 badges",
                "Export & Backup • Last updated today"
            )
            "settings" -> listOf(
                "Appearance & Theme • Dynamic Material 3",
                "Alerts & Reminders • Enabled",
                "Local Storage Backup • Ready",
                "App Version • 1.0 (${project.name})"
            )
            "folder" -> listOf(
                "Downloads/ • 24 items • 128 MB",
                "Documents/ • 16 items • 42 MB",
                "Packages/ • 5 APK archives",
                "Recent Activity • Cleaned 320 MB"
            )
            else -> {
                val primaryFeature = project.features.getOrNull(index)
                val appTopic = project.name.ifBlank { label }
                listOfNotNull(
                    primaryFeature,
                    "$label Overview • Ready in $appTopic",
                    "Recent $label Activity • Updated today",
                    "Quick $label Action • Tap to open"
                ).distinct().take(4)
            }
        }
    }

    // Order matters: the first rule that matches wins.
    private val ICON_RULES: List<Pair<String, List<String>>> = listOf(
        "cart" to listOf("cart", "basket", "checkout", "shop", "store", "order", "product", "catalog"),
        "payments" to listOf("payment", "wallet", "pay", "transaction", "invoice", "billing", "budget", "expense"),
        "fitness" to listOf("workout", "exercise", "gym", "fitness", "training", "hiit", "cardio"),
        "restaurant" to listOf("food", "recipe", "menu", "meal", "nutrition", "calorie", "macro", "diet", "restaurant", "coffee"),
        "school" to listOf("course", "lesson", "learn", "study", "quiz", "class", "flashcard", "exam"),
        "music" to listOf("music", "song", "playlist", "player", "audio", "podcast", "guitar", "tuner", "beat"),
        "photo" to listOf("photo", "gallery", "camera", "album", "image"),
        "health" to listOf("health", "doctor", "medical", "clinic", "patient", "record"),
        "flight" to listOf("trip", "travel", "flight", "hotel", "itinerary", "booking"),
        "note" to listOf("note", "journal", "diary", "memo", "write", "manifest"),
        "chat" to listOf("chat", "message", "inbox", "conversation", "contact", "community", "comment"),
        "calendar" to listOf("calendar", "schedule", "plan", "appointment", "event", "streak"),
        "chart" to listOf("stat", "analytic", "progress", "report", "chart", "insight", "finance", "metric"),
        "folder" to listOf("file", "folder", "storage", "document", "library", "directory", "apk", "archive"),
        "timer" to listOf("timer", "focus", "pomodoro", "clock", "interval"),
        "list" to listOf("task", "todo", "list", "checklist", "habit"),
        "map" to listOf("map", "location", "nearby", "route", "track"),
        "history" to listOf("history", "log", "past", "recent"),
        "favorite" to listOf("favorite", "wishlist", "saved", "like", "bookmark"),
        "notifications" to listOf("notif", "alert", "reminder"),
        "mail" to listOf("mail", "email"),
        "call" to listOf("call", "phone", "dial"),
        "share" to listOf("share", "invite", "friend", "referral"),
        "lock" to listOf("lock", "security", "privacy", "vault", "permission"),
        "person" to listOf("profile", "account", "user", "me", "member"),
        "settings" to listOf("setting", "config", "preference", "option"),
        "search" to listOf("search", "find", "discover"),
        "explore" to listOf("explore", "browse"),
        "dashboard" to listOf("dashboard", "overview", "summary"),
        "home" to listOf("home", "main", "feed", "start"),
        "edit" to listOf("tool", "utility", "editor", "studio"),
        "info" to listOf("help", "about", "info", "guide", "tips")
    )
}
