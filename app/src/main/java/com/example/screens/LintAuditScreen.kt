package com.example.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.AuditCategory
import com.example.data.LintAuditIssue
import com.example.data.ProjectLintAuditReport
import com.example.data.ProjectLintAuditor
import com.example.model.AppProject

@Composable
fun LintAuditScreen(
    selectedProject: AppProject?,
    availableProjects: List<AppProject> = emptyList(),
    onSelectProject: (String?) -> Unit = {},
    onUpdateProject: (AppProject) -> Unit = {},
    onOpenBuildConfig: ((String) -> Unit)? = null,
    onOpenDependencyGraph: (() -> Unit)? = null,
    onBack: () -> Unit = {},
    initialCustomGradleScript: String? = null,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val selectionKey = selectedProject?.id ?: "__workspace_gradle__"
    val isCustomScript = initialCustomGradleScript != null

    var gradleScriptText by remember(selectionKey, initialCustomGradleScript) {
        mutableStateOf(
            initialCustomGradleScript
                ?: selectedProject?.let { ProjectLintAuditor.synthesizeGradleScript(it) }
                ?: ProjectLintAuditor.defaultSampleGradleWithWarnings()
        )
    }
    var selectedCategory by remember { mutableStateOf<AuditCategory?>(null) }
    var scanNonce by remember { mutableIntStateOf(0) }
    var appliedFixesCount by remember(selectionKey) { mutableIntStateOf(0) }

    val report: ProjectLintAuditReport = remember(selectedProject, gradleScriptText, scanNonce) {
        ProjectLintAuditor.auditProject(
            project = selectedProject,
            context = context,
            customGradleScript = gradleScriptText
        )
    }

    val filteredIssues = remember(report.issues, selectedCategory) {
        if (selectedCategory == null) {
            report.issues
        } else {
            report.issues.filter { it.category == selectedCategory }
        }
    }

    fun handleApplySingleFix(issue: LintAuditIssue) {
        val fixKind = issue.fixKind ?: return
        var updatedProj = selectedProject
        if (updatedProj != null) {
            val patched = ProjectLintAuditor.applyProjectFix(updatedProj, fixKind, context)
            if (patched != updatedProj) {
                updatedProj = patched
                onUpdateProject(patched)
            }
        }
        val patchedGradle = if (!isCustomScript && updatedProj != null && selectedProject != null &&
            gradleScriptText == ProjectLintAuditor.synthesizeGradleScript(selectedProject)
        ) {
            ProjectLintAuditor.synthesizeGradleScript(updatedProj)
        } else {
            ProjectLintAuditor.applyGradleScriptFix(gradleScriptText, fixKind)
        }
        gradleScriptText = patchedGradle
        appliedFixesCount++
        scanNonce++
        Toast.makeText(context, "Applied fix: ${issue.fixActionLabel}", Toast.LENGTH_SHORT).show()
    }

    fun handleFixAll() {
        var currentProject = selectedProject
        var currentGradle = gradleScriptText
        val originalSynth = currentProject?.let { ProjectLintAuditor.synthesizeGradleScript(it) }
        val wasSynthesized = currentProject != null && currentGradle == originalSynth

        var fixedInBatch = 0
        report.issues.forEach { issue ->
            val kind = issue.fixKind ?: return@forEach
            if (currentProject != null) {
                val nextProj = ProjectLintAuditor.applyProjectFix(currentProject!!, kind, context)
                if (nextProj != currentProject) {
                    currentProject = nextProj
                }
            }
            currentGradle = ProjectLintAuditor.applyGradleScriptFix(currentGradle, kind)
            fixedInBatch++
        }

        if (currentProject != null && currentProject != selectedProject) {
            onUpdateProject(currentProject!!)
            if (wasSynthesized) {
                currentGradle = ProjectLintAuditor.synthesizeGradleScript(currentProject!!)
            }
        }
        gradleScriptText = currentGradle
        appliedFixesCount += fixedInBatch
        scanNonce++
        if (fixedInBatch > 0) {
            Toast.makeText(context, "Resolved $fixedInBatch issue(s)", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("lint_audit_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Lint & Audit",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("lint_audit_title")
                    )
                    Text(
                        text = "Static structure, dependency & Gradle warning analyzer",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { scanNonce++ },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("lint_run_scan_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Re-run Static Scan"
                    )
                }
            }

            // Target Selector Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedProject == null,
                    onClick = { onSelectProject(null) },
                    label = { Text("Gradle Workspace") },
                    modifier = Modifier.testTag("lint_target_workspace_chip")
                )
                availableProjects.forEach { proj ->
                    FilterChip(
                        selected = selectedProject?.id == proj.id,
                        onClick = { onSelectProject(proj.id) },
                        label = {
                            Text(
                                text = proj.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier.testTag("lint_target_project_${proj.id}")
                    )
                }
            }

            // Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("All (${report.issues.size})") },
                    modifier = Modifier.testTag("lint_filter_all")
                )
                AuditCategory.entries.forEach { cat ->
                    val count = report.issues.count { it.category == cat }
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                        label = { Text("${cat.label} ($count)") },
                        modifier = Modifier.testTag("lint_filter_${cat.name.lowercase()}")
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("lint_audit_findings_list"),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "summary_card") {
                    LintAuditSummaryCard(
                        report = report,
                        appliedFixesCount = appliedFixesCount,
                        onFixAll = ::handleFixAll
                    )
                }

                if (selectedProject != null && onOpenBuildConfig != null) {
                    item(key = "build_config_shortcut") {
                        OutlinedButton(
                            onClick = { onOpenBuildConfig(selectedProject.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("lint_open_build_config_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Build Configuration for ${selectedProject.name}")
                        }
                    }
                }

                if (filteredIssues.isEmpty()) {
                    item(key = "empty_clean_card") {
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF10B981).copy(alpha = 0.1f)
                            ),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.45f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("lint_clean_state_card")
                        ) {
                            Row(
                                modifier = Modifier.padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "All Clear",
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(28.dp)
                                )
                                Column {
                                    Text(
                                        text = "Zero Build Warnings in Current Filter",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Structure, dependencies, and Gradle build config passed static inspection.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else {
                    items(filteredIssues, key = { it.id }) { issue ->
                        LintFindingCard(
                            issue = issue,
                            onApplyFix = ::handleApplySingleFix
                        )
                    }
                }

                item(key = "dependencies_card") {
                    DependencyAuditCard(dependencies = report.dependencies)
                }

                if (onOpenDependencyGraph != null) {
                    item(key = "open_dependency_graph_button") {
                        OutlinedButton(
                            onClick = onOpenDependencyGraph,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("lint_open_dependency_graph_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountTree,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Visualize Dependency Graph")
                        }
                    }
                }

                item(key = "gradle_script_card") {
                    GradleScriptInspectorCard(
                        gradleScript = gradleScriptText,
                        onScriptChange = { gradleScriptText = it }
                    )
                }
            }
        }
    }
}
