package com.example.data

import com.example.model.AppProject

enum class DependencyNodeStatus(val label: String) {
    HEALTHY("Up to date"),
    OUTDATED("Outdated"),
    CONFLICT("Conflict")
}

data class DependencyNode(
    val id: String,
    val label: String,
    val coordinate: String,
    val declaredVersion: String,
    val recommendedVersion: String? = null,
    val layer: Int,
    val status: DependencyNodeStatus = DependencyNodeStatus.HEALTHY,
    val statusDetail: String = "Version aligned with Gradle catalog.",
    val fixSuggestion: String? = null
)

data class DependencyEdge(
    val fromId: String,
    val toId: String,
    val relationLabel: String = "implementation",
    val isConflict: Boolean = false,
    val isOutdated: Boolean = false
)

data class GradleDependencyGraph(
    val projectName: String,
    val gradleScript: String,
    val nodes: List<DependencyNode>,
    val edges: List<DependencyEdge>
) {
    val conflictCount: Int get() = nodes.count { it.status == DependencyNodeStatus.CONFLICT }
    val outdatedCount: Int get() = nodes.count { it.status == DependencyNodeStatus.OUTDATED }
    val healthyCount: Int get() = nodes.count { it.status == DependencyNodeStatus.HEALTHY }
}

/**
 * Parses project Gradle declarations and known Android/Jetpack transitive dependency metadata
 * into a native dependency DAG, flagging version conflicts and outdated artifacts.
 */
object GradleDependencyGraphAnalyzer {

    private data class CatalogArtifact(
        val key: String,
        val coordinate: String,
        val shortLabel: String,
        val defaultVersion: String,
        val latestVersion: String,
        val transitiveTargets: List<String> = emptyList()
    )

    private val KNOWN_ARTIFACTS = listOf(
        CatalogArtifact(
            key = "compose-bom",
            coordinate = "androidx.compose:compose-bom",
            shortLabel = "compose-bom",
            defaultVersion = "2025.09.00",
            latestVersion = "2025.09.00",
            transitiveTargets = listOf("compose-ui", "compose-material3")
        ),
        CatalogArtifact(
            key = "compose-ui",
            coordinate = "androidx.compose.ui:ui",
            shortLabel = "compose.ui",
            defaultVersion = "1.9.1",
            latestVersion = "1.9.1"
        ),
        CatalogArtifact(
            key = "compose-material3",
            coordinate = "androidx.compose.material3:material3",
            shortLabel = "material3",
            defaultVersion = "1.3.2",
            latestVersion = "1.3.2"
        ),
        CatalogArtifact(
            key = "core-ktx",
            coordinate = "androidx.core:core-ktx",
            shortLabel = "core-ktx",
            defaultVersion = "1.18.0",
            latestVersion = "1.18.0"
        ),
        CatalogArtifact(
            key = "activity-compose",
            coordinate = "androidx.activity:activity-compose",
            shortLabel = "activity-compose",
            defaultVersion = "1.10.1",
            latestVersion = "1.10.1",
            transitiveTargets = listOf("core-ktx", "compose-ui")
        ),
        CatalogArtifact(
            key = "navigation-compose",
            coordinate = "androidx.navigation:navigation-compose",
            shortLabel = "navigation-compose",
            defaultVersion = "2.8.9",
            latestVersion = "2.8.9",
            transitiveTargets = listOf("kotlinx-serialization", "compose-ui")
        ),
        CatalogArtifact(
            key = "kotlinx-serialization",
            coordinate = "org.jetbrains.kotlinx:kotlinx-serialization-json",
            shortLabel = "serialization-json",
            defaultVersion = "1.8.0",
            latestVersion = "1.8.0"
        ),
        CatalogArtifact(
            key = "litert",
            coordinate = "com.google.ai.edge.litert:litert",
            shortLabel = "litert",
            defaultVersion = "2.3.0",
            latestVersion = "2.3.0",
            transitiveTargets = listOf("litert-api")
        ),
        CatalogArtifact(
            key = "litert-gpu",
            coordinate = "com.google.ai.edge.litert:litert-gpu",
            shortLabel = "litert-gpu",
            defaultVersion = "2.3.0",
            latestVersion = "2.3.0",
            transitiveTargets = listOf("litert")
        ),
        CatalogArtifact(
            key = "litert-api",
            coordinate = "com.google.ai.edge.litert:litert-api",
            shortLabel = "litert-api",
            defaultVersion = "2.3.0",
            latestVersion = "2.3.0"
        ),
        CatalogArtifact(
            key = "retrofit",
            coordinate = "com.squareup.retrofit2:retrofit",
            shortLabel = "retrofit",
            defaultVersion = "2.12.0",
            latestVersion = "2.12.0",
            transitiveTargets = listOf("okhttp")
        ),
        CatalogArtifact(
            key = "okhttp",
            coordinate = "com.squareup.okhttp3:okhttp",
            shortLabel = "okhttp",
            defaultVersion = "4.10.0",
            latestVersion = "4.12.0",
            transitiveTargets = listOf("okio")
        ),
        CatalogArtifact(
            key = "okio",
            coordinate = "com.squareup.okio:okio",
            shortLabel = "okio",
            defaultVersion = "3.9.0",
            latestVersion = "3.9.0"
        ),
        CatalogArtifact(
            key = "room-runtime",
            coordinate = "androidx.room:room-runtime",
            shortLabel = "room-runtime",
            defaultVersion = "2.7.0",
            latestVersion = "2.7.0"
        ),
        CatalogArtifact(
            key = "coil-compose",
            coordinate = "io.coil-kt:coil-compose",
            shortLabel = "coil-compose",
            defaultVersion = "2.7.0",
            latestVersion = "2.7.0",
            transitiveTargets = listOf("okhttp")
        )
    )

    fun defaultWorkspaceGradleScript(): String = """
        dependencies {
            implementation(platform("androidx.compose:compose-bom:2025.09.00"))
            implementation("androidx.core:core-ktx:1.15.0")
            implementation("androidx.activity:activity-compose:1.10.1")
            implementation("androidx.compose.material3:material3")
            implementation("androidx.navigation:navigation-compose:2.8.9")
            implementation("com.squareup.retrofit2:retrofit:2.12.0")
            implementation("com.squareup.okhttp3:okhttp:4.10.0")
            implementation("com.google.ai.edge.litert:litert:2.3.0")
        }
    """.trimIndent()

    fun defaultConflictDemoGradleScript(): String = """
        dependencies {
            implementation("androidx.compose.ui:ui:1.7.0")
            implementation("androidx.compose.material3:material3:1.2.1")
            implementation("androidx.core:core-ktx:1.13.1")
            implementation("androidx.navigation:navigation-compose:2.7.7")
            implementation("com.squareup.retrofit2:retrofit:2.12.0")
            implementation("com.squareup.okhttp3:okhttp:4.9.3")
            implementation("com.google.ai.edge.litert:litert:2.1.0")
            implementation("com.google.ai.edge.litert:litert-gpu:2.3.0")
        }
    """.trimIndent()

    fun analyze(
        project: AppProject?,
        customGradleScript: String? = null
    ): GradleDependencyGraph {
        val script = customGradleScript ?: project?.let { ProjectLintAuditor.synthesizeGradleScript(it) }
            ?: defaultWorkspaceGradleScript()
        val projectLabel = project?.name ?: "Workspace Dependencies"

        val parsedEntries = extractDeclarations(script)
        val nodesById = linkedMapOf<String, DependencyNode>()
        val edges = mutableListOf<DependencyEdge>()

        // Root :app node
        val rootId = "app"
        nodesById[rootId] = DependencyNode(
            id = rootId,
            label = ":app",
            coordinate = project?.name ?: "com.example.app",
            declaredVersion = project?.buildOptions?.versionName ?: "1.0",
            layer = 0,
            status = DependencyNodeStatus.HEALTHY,
            statusDetail = "Root Android application module (API ${project?.buildOptions?.targetSdk ?: 36})"
        )

        val directVersionsByKey = mutableMapOf<String, MutableList<String>>()
        var hasComposeBom = false

        parsedEntries.forEach { decl ->
            val key = matchArtifactKey(decl.coordinateOrAlias)
            if (key == "compose-bom") hasComposeBom = true
            directVersionsByKey.getOrPut(key) { mutableListOf() }.add(decl.version ?: "catalog")
        }

        // Ensure synthesized project scripts always expose core Compose nodes clearly
        if (parsedEntries.isEmpty() && project != null) {
            directVersionsByKey["compose-bom"] = mutableListOf("2025.09.00")
            directVersionsByKey["core-ktx"] = mutableListOf("1.18.0")
            directVersionsByKey["navigation-compose"] = mutableListOf("2.8.9")
            hasComposeBom = true
            if (project.buildOptions.bundledTfliteModels.isNotEmpty()) {
                directVersionsByKey["litert"] = mutableListOf("2.3.0")
                directVersionsByKey["litert-gpu"] = mutableListOf("2.3.0")
            }
        }

        // Build Layer 1 direct nodes
        directVersionsByKey.forEach { (key, versions) ->
            val catalog = KNOWN_ARTIFACTS.firstOrNull { it.key == key }
            val distinctVersions = versions.distinct()
            val primaryVersion = distinctVersions.firstOrNull() ?: catalog?.defaultVersion ?: "1.0.0"
            val effectiveVersion = if (primaryVersion == "catalog") {
                catalog?.defaultVersion ?: "catalog"
            } else {
                primaryVersion
            }

            var status = DependencyNodeStatus.HEALTHY
            var detail = "Aligned at $effectiveVersion."
            var fix: String? = null
            val latest = catalog?.latestVersion

            if (distinctVersions.size > 1) {
                status = DependencyNodeStatus.CONFLICT
                detail = "Multiple conflicting versions declared: ${distinctVersions.joinToString(" vs ")}."
                fix = latest ?: distinctVersions.maxByOrNull { compareSemVer(it, "0.0.0") }
            } else if ((key == "compose-ui" || key == "compose-material3") && !hasComposeBom && effectiveVersion != "catalog") {
                status = DependencyNodeStatus.CONFLICT
                detail = "Compose artifact pinned manually ($effectiveVersion) without Compose BOM alignment."
                fix = "Use compose-bom:${KNOWN_ARTIFACTS.first { it.key == "compose-bom" }.latestVersion}"
            } else if (catalog != null && effectiveVersion != "BOM" && compareSemVer(effectiveVersion, catalog.latestVersion) < 0) {
                status = DependencyNodeStatus.OUTDATED
                detail = "Outdated library: $effectiveVersion declared, ${catalog.latestVersion} available."
                fix = catalog.latestVersion
            }

            // Detect litert vs litert-gpu minor/major mismatch
            if (key == "litert" && directVersionsByKey.containsKey("litert-gpu")) {
                val gpuVer = directVersionsByKey["litert-gpu"]?.firstOrNull()?.takeIf { it != "catalog" } ?: "2.3.0"
                if (effectiveVersion != gpuVer) {
                    status = DependencyNodeStatus.CONFLICT
                    detail = "Version conflict: litert ($effectiveVersion) mismatches litert-gpu ($gpuVer)."
                    fix = gpuVer
                }
            }

            // Detect retrofit 2.12.0 needing okhttp >= 4.12.0 when okhttp < 4.12.0
            if (key == "okhttp" && directVersionsByKey.containsKey("retrofit") && compareSemVer(effectiveVersion, "4.12.0") < 0) {
                status = DependencyNodeStatus.CONFLICT
                detail = "Transitive conflict: :app declares okhttp $effectiveVersion, while retrofit 2.12.0 expects >= 4.12.0."
                fix = "4.12.0"
            }

            val label = catalog?.shortLabel ?: key.substringAfterLast(':')
            val coord = catalog?.coordinate ?: key
            nodesById[key] = DependencyNode(
                id = key,
                label = label,
                coordinate = coord,
                declaredVersion = effectiveVersion,
                recommendedVersion = latest,
                layer = 1,
                status = status,
                statusDetail = detail,
                fixSuggestion = fix
            )

            edges += DependencyEdge(
                fromId = rootId,
                toId = key,
                relationLabel = if (key == "compose-bom") "platform BOM" else "implementation",
                isConflict = status == DependencyNodeStatus.CONFLICT,
                isOutdated = status == DependencyNodeStatus.OUTDATED
            )
        }

        // Build Layer 2 transitive nodes from direct dependencies
        directVersionsByKey.keys.forEach { parentKey ->
            val catalog = KNOWN_ARTIFACTS.firstOrNull { it.key == parentKey } ?: return@forEach
            catalog.transitiveTargets.forEach { childKey ->
                val childCatalog = KNOWN_ARTIFACTS.firstOrNull { it.key == childKey } ?: return@forEach
                val alreadyInDirect = nodesById.containsKey(childKey)
                val parentVersion = nodesById[parentKey]?.declaredVersion.orEmpty()

                if (!alreadyInDirect) {
                    var childStatus = DependencyNodeStatus.HEALTHY
                    var childDetail = "Transitively resolved via ${catalog.shortLabel}."
                    var childFix: String? = null
                    val childVer = childCatalog.defaultVersion

                    if (childKey == "okhttp" && parentKey == "retrofit" && compareSemVer(childVer, "4.12.0") < 0) {
                        childStatus = DependencyNodeStatus.CONFLICT
                        childDetail = "Retrofit 2.12.0 pulls okhttp 4.12.0, conflicting with catalog $childVer."
                        childFix = "4.12.0"
                    }

                    nodesById[childKey] = DependencyNode(
                        id = childKey,
                        label = childCatalog.shortLabel,
                        coordinate = childCatalog.coordinate,
                        declaredVersion = childVer,
                        recommendedVersion = childCatalog.latestVersion,
                        layer = 2,
                        status = childStatus,
                        statusDetail = childDetail,
                        fixSuggestion = childFix
                    )
                } else if (childKey != "compose-ui" && childKey != "compose-material3") {
                    // Move shared transitive target down to layer 2 if only pulled transitively, otherwise keep at layer 1
                }

                val childNode = nodesById[childKey]
                val edgeConflict = (childNode?.status == DependencyNodeStatus.CONFLICT) ||
                    (parentKey == "litert-gpu" && childKey == "litert" && parentVersion != childNode?.declaredVersion)

                edges += DependencyEdge(
                    fromId = parentKey,
                    toId = childKey,
                    relationLabel = "transitive",
                    isConflict = edgeConflict,
                    isOutdated = childNode?.status == DependencyNodeStatus.OUTDATED
                )
            }
        }

        // Promote strictly transitive leaves or clean layer spacing
        val orderedNodes = nodesById.values.sortedWith(compareBy({ it.layer }, { it.label }))
        val distinctEdges = edges.distinctBy { "${it.fromId}->${it.toId}" }

        return GradleDependencyGraph(
            projectName = projectLabel,
            gradleScript = script,
            nodes = orderedNodes,
            edges = distinctEdges
        )
    }

    fun applyNodeFix(gradleScript: String, node: DependencyNode): String {
        val targetVersion = node.recommendedVersion ?: node.fixSuggestion ?: return gradleScript
        if (node.fixSuggestion?.startsWith("Use compose-bom") == true) {
            val bomLine = "    implementation(platform(\"androidx.compose:compose-bom:2025.09.00\"))"
            val updated = if (!gradleScript.contains("compose-bom") && !gradleScript.contains("compose.bom")) {
                gradleScript.replaceFirst(Regex("""dependencies\s*\{"""), "dependencies {\n$bomLine")
            } else gradleScript
            return updated.replace(
                Regex("""(${Regex.escape(node.coordinate)}:)([^"'\)]+)"""),
                "$1$targetVersion"
            )
        }

        val cleanVersion = targetVersion.removePrefix("Use ").trim()
        val coordRegex = Regex("""(${Regex.escape(node.coordinate)}:)([^"'\)]+)""")
        if (coordRegex.containsMatchIn(gradleScript)) {
            return gradleScript.replace(coordRegex, "$1$cleanVersion")
        }

        // Handle catalog alias or add explicit pin override
        val aliasTail = node.id.replace('-', '.')
        if (gradleScript.contains("libs.$aliasTail") || gradleScript.contains("libs.androidx.$aliasTail")) {
            val pinLine = "    implementation(\"${node.coordinate}:$cleanVersion\")"
            return gradleScript.replaceFirst(
                Regex("""implementation\s*\(\s*libs\.[A-Za-z0-9_.]*${Regex.escape(aliasTail)}\s*\)"""),
                pinLine
            )
        }

        return gradleScript
    }

    private data class ParsedDepDecl(
        val coordinateOrAlias: String,
        val version: String?
    )

    private fun extractDeclarations(script: String): List<ParsedDepDecl> {
        val list = mutableListOf<ParsedDepDecl>()
        val stringDepRegex = Regex("""(?:implementation|api|compile|testImplementation)\s*\(\s*(?:platform\s*\(\s*)?["']([^"']+)["']\s*\)?\s*\)""")
        stringDepRegex.findAll(script).forEach { match ->
            val raw = match.groupValues[1].trim()
            val parts = raw.split(":")
            if (parts.size >= 3) {
                list += ParsedDepDecl("${parts[0]}:${parts[1]}", parts[2])
            } else if (parts.size == 2) {
                list += ParsedDepDecl("${parts[0]}:${parts[1]}", "BOM")
            }
        }

        val aliasDepRegex = Regex("""(?:implementation|api|ksp)\s*\(\s*(?:platform\s*\(\s*)?(libs\.[A-Za-z0-9_.]+)\s*\)?\s*\)""")
        aliasDepRegex.findAll(script).forEach { match ->
            list += ParsedDepDecl(match.groupValues[1].trim(), "catalog")
        }
        return list
    }

    private fun matchArtifactKey(coordOrAlias: String): String {
        val lower = coordOrAlias.lowercase()
        return KNOWN_ARTIFACTS.firstOrNull { art ->
            lower == art.coordinate.lowercase() ||
                lower.endsWith(".${art.key.replace('-', '.')}") ||
                lower.contains(art.key.replace('-', '.')) ||
                lower.contains(":${art.key}")
        }?.key ?: lower
    }

    internal fun compareSemVer(a: String, b: String): Int {
        val pa = a.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }
        val pb = b.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(pa.size, pb.size)
        for (i in 0 until maxLen) {
            val va = pa.getOrElse(i) { 0 }
            val vb = pb.getOrElse(i) { 0 }
            if (va != vb) return va.compareTo(vb)
        }
        return 0
    }
}
