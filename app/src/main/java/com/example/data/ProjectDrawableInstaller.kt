package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

data class InstalledDrawableEntry(
    val relativePath: String,
    val file: File,
    val sizeBytes: Long,
    val kindLabel: String
)

data class InstalledDrawableResult(
    val projectId: String,
    val drawableName: String,
    val backgroundHex: String,
    val resDir: File,
    val pngFile: File,
    val entries: List<InstalledDrawableEntry>
) {
    val relativePaths: List<String>
        get() = entries.map { it.relativePath }
}

object ProjectDrawableInstaller {

    const val DEFAULT_WORKSPACE_ID = "studio_draft"
    const val DEFAULT_DRAWABLE_NAME = "ic_launcher_custom"
    private const val ROOT_DIR_NAME = "project_resources"

    fun sanitizeDrawableName(raw: String): String {
        val normalized = raw.trim()
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9_]+"), "_")
            .trim('_')
        if (normalized.isEmpty()) return DEFAULT_DRAWABLE_NAME
        val prefixed = if (normalized.first().isLetter()) normalized else "ic_$normalized"
        return prefixed.take(40)
    }

    fun sanitizeProjectFolderId(projectId: String?): String {
        val raw = projectId?.trim().orEmpty()
        if (raw.isEmpty()) return DEFAULT_WORKSPACE_ID
        val clean = raw.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return clean.ifBlank { DEFAULT_WORKSPACE_ID }
    }

    fun sampleCornerBackgroundHex(bitmap: Bitmap): String {
        if (bitmap.width <= 0 || bitmap.height <= 0) return "#0B1120"
        val maxX = (bitmap.width - 1).coerceAtLeast(0)
        val maxY = (bitmap.height - 1).coerceAtLeast(0)
        val marginX = (bitmap.width / 24).coerceIn(0, maxX)
        val marginY = (bitmap.height / 24).coerceIn(0, maxY)
        val samples = listOf(
            bitmap.getPixel(marginX, marginY),
            bitmap.getPixel(maxX - marginX, marginY),
            bitmap.getPixel(marginX, maxY - marginY),
            bitmap.getPixel(maxX - marginX, maxY - marginY)
        )
        var redSum = 0
        var greenSum = 0
        var blueSum = 0
        var count = 0
        samples.forEach { pixel ->
            if (Color.alpha(pixel) > 32) {
                redSum += Color.red(pixel)
                greenSum += Color.green(pixel)
                blueSum += Color.blue(pixel)
                count++
            }
        }
        if (count == 0) return "#0B1120"
        val r = (redSum / count).coerceIn(0, 255)
        val g = (greenSum / count).coerceIn(0, 255)
        val b = (blueSum / count).coerceIn(0, 255)
        return String.format(Locale.US, "#%02X%02X%02X", r, g, b)
    }

    fun buildAdaptiveForegroundXml(drawableName: String): String {
        val safeName = sanitizeDrawableName(drawableName)
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <layer-list xmlns:android="http://schemas.android.com/apk/res/android">
                <item
                    android:width="66dp"
                    android:height="66dp"
                    android:drawable="@drawable/$safeName"
                    android:gravity="center" />
            </layer-list>
        """.trimIndent() + "\n"
    }

    fun buildAdaptiveBackgroundXml(backgroundHex: String): String {
        val validHex = if (Regex("^#[0-9A-Fa-f]{6}$").matches(backgroundHex.trim())) {
            backgroundHex.trim().uppercase(Locale.US)
        } else {
            "#0B1120"
        }
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <shape xmlns:android="http://schemas.android.com/apk/res/android"
                android:shape="rectangle">
                <solid android:color="$validHex" />
            </shape>
        """.trimIndent() + "\n"
    }

    fun buildAdaptiveMipmapXml(): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
            <background android:drawable="@drawable/ic_launcher_background" />
            <foreground android:drawable="@drawable/ic_launcher_foreground" />
            <monochrome android:drawable="@drawable/ic_launcher_foreground" />
        </adaptive-icon>
    """.trimIndent() + "\n"

    fun projectResDir(context: Context, projectId: String?): File {
        val safeId = sanitizeProjectFolderId(projectId)
        return File(File(context.filesDir, ROOT_DIR_NAME), "$safeId/res")
    }

    fun installLauncherIcon(
        context: Context,
        projectId: String?,
        bitmap: Bitmap,
        resourceName: String = DEFAULT_DRAWABLE_NAME
    ): InstalledDrawableResult {
        val safeId = sanitizeProjectFolderId(projectId)
        val safeDrawableName = sanitizeDrawableName(resourceName)
        val bgHex = sampleCornerBackgroundHex(bitmap)

        val resDir = projectResDir(context, safeId).apply { mkdirs() }
        val drawableDir = File(resDir, "drawable").apply { mkdirs() }
        val mipmapDir = File(resDir, "mipmap-anydpi-v26").apply { mkdirs() }

        // Remove stale PNGs other than the current resourceName so directory stays clean
        drawableDir.listFiles()?.forEach { existing ->
            if (existing.isFile && existing.name.endsWith(".png") &&
                existing.name != "$safeDrawableName.png"
            ) {
                existing.delete()
            }
        }

        val scaledBitmap = if (bitmap.width == 512 && bitmap.height == 512) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, 512, 512, true)
        }

        val pngFile = File(drawableDir, "$safeDrawableName.png")
        FileOutputStream(pngFile).use { out ->
            scaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
        }
        if (scaledBitmap !== bitmap && !scaledBitmap.isRecycled) {
            scaledBitmap.recycle()
        }

        val foregroundXml = File(drawableDir, "ic_launcher_foreground.xml")
        foregroundXml.writeText(buildAdaptiveForegroundXml(safeDrawableName), Charsets.UTF_8)

        val backgroundXml = File(drawableDir, "ic_launcher_background.xml")
        backgroundXml.writeText(buildAdaptiveBackgroundXml(bgHex), Charsets.UTF_8)

        val mipmapLauncher = File(mipmapDir, "ic_launcher.xml")
        mipmapLauncher.writeText(buildAdaptiveMipmapXml(), Charsets.UTF_8)

        val mipmapRound = File(mipmapDir, "ic_launcher_round.xml")
        mipmapRound.writeText(buildAdaptiveMipmapXml(), Charsets.UTF_8)

        val entries = listInstalledDrawableEntries(context, safeId)
        return InstalledDrawableResult(
            projectId = safeId,
            drawableName = safeDrawableName,
            backgroundHex = bgHex,
            resDir = resDir,
            pngFile = pngFile,
            entries = entries
        )
    }

    fun listInstalledDrawableEntries(
        context: Context,
        projectId: String?
    ): List<InstalledDrawableEntry> {
        val resDir = projectResDir(context, projectId)
        if (!resDir.exists()) return emptyList()

        val drawableDir = File(resDir, "drawable")
        val mipmapDir = File(resDir, "mipmap-anydpi-v26")
        val items = mutableListOf<InstalledDrawableEntry>()

        drawableDir.listFiles()?.sortedBy { it.name }?.forEach { file ->
            if (file.isFile) {
                val kind = if (file.name.endsWith(".png")) "PNG Artwork (512px)" else "Drawable XML"
                items.add(
                    InstalledDrawableEntry(
                        relativePath = "res/drawable/${file.name}",
                        file = file,
                        sizeBytes = file.length(),
                        kindLabel = kind
                    )
                )
            }
        }
        mipmapDir.listFiles()?.sortedBy { it.name }?.forEach { file ->
            if (file.isFile) {
                items.add(
                    InstalledDrawableEntry(
                        relativePath = "res/mipmap-anydpi-v26/${file.name}",
                        file = file,
                        sizeBytes = file.length(),
                        kindLabel = "Adaptive Icon XML"
                    )
                )
            }
        }
        return items
    }

    fun findInstalledPngFile(
        context: Context?,
        projectId: String?,
        preferredDrawableName: String = ""
    ): File? {
        if (context == null) return null
        val drawableDir = File(projectResDir(context, projectId), "drawable")
        if (!drawableDir.isDirectory) return null
        if (preferredDrawableName.isNotBlank()) {
            val preferred = File(drawableDir, "${sanitizeDrawableName(preferredDrawableName)}.png")
            if (preferred.isFile && preferred.length() > 0L) return preferred
        }
        return drawableDir.listFiles()
            ?.firstOrNull { it.isFile && it.name.endsWith(".png") && it.length() > 0L }
    }

    fun loadInstalledIconBitmap(
        context: Context,
        projectId: String?,
        preferredDrawableName: String = ""
    ): Bitmap? {
        val pngFile = findInstalledPngFile(context, projectId, preferredDrawableName) ?: return null
        return runCatching { BitmapFactory.decodeFile(pngFile.absolutePath) }.getOrNull()
    }
}
