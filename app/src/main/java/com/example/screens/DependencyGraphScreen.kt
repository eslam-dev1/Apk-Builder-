package com.example.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.DependencyNode
import com.example.data.DependencyNodeStatus
import com.example.data.GradleDependencyGraphAnalyzer
import com.example.data.ProjectLintAuditor
import com.example.model.AppProject

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DependencyGraphScreen(
    selectedProject: AppProject?,
    availableProjects: List<AppProject> = emptyList(),
    onSelectProject: (String?) -> Unit = {},
    onBack: () -> Unit = {},
    initialCustomScript: String? = null,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    var presetMode by remember(selectedProject?.id) {
        mutableStateOf(
            when {
                selectedProject != null -> "project:${selectedProject.id}"
                initialCustomScript != null -> "custom"
                else -> "workspace"
            }
        )
    }

    var inspectedScript by remember(presetMode, selectedProject?.buildOptions, initialCustomScript) {
        mutableStateOf(
            when {
                presetMode == "custom" && initialCustomScript != null -> initialCustomScript
                presetMode == "conflict_demo" -> GradleDependencyGraphAnalyzer.defaultConflictDemoGradleScript()
                selectedProject != null && presetMode.startsWith("project:") ->
                    ProjectLintAuditor.synthesizeGradleScript(selectedProject)
                else -> GradleDependencyGraphAnalyzer.defaultWorkspaceGradleScript()
            }
        )
    }

    val graph = remember(selectedProject, inspectedScript) {
        GradleDependencyGraphAnalyzer.analyze(
            project = if (presetMode.startsWith("project:")) selectedProject else null,
            customGradleScript = inspectedScript
        )
    }

    var statusFilter by remember { mutableStateOf<DependencyNodeStatus?>(null) }
    var selectedNodeId by remember(graph) {
        mutableStateOf(
            graph.nodes.firstOrNull { it.status == DependencyNodeStatus.CONFLICT }?.id
                ?: graph.nodes.firstOrNull { it.status == DependencyNodeStatus.OUTDATED }?.id
                ?: graph.nodes.firstOrNull()?.id
        )
    }

    val selectedNode = remember(graph, selectedNodeId) {
        graph.nodes.firstOrNull { it.id == selectedNodeId } ?: graph.nodes.firstOrNull()
    }
    val flaggedNodes = remember(graph.nodes) {
        graph.nodes.filter { it.status != DependencyNodeStatus.HEALTHY }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("dep_graph_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Gradle Dependency Graph",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("dep_graph_title")
                    )
                    Text(
                        text = "Interactive native DAG • Highlights version conflicts & outdated libraries",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Target Source Switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = presetMode == "workspace",
                    onClick = {
                        onSelectProject(null)
                        presetMode = "workspace"
                    },
                    label = { Text("Workspace Catalog") },
                    modifier = Modifier.testTag("dep_graph_source_workspace")
                )
                FilterChip(
                    selected = presetMode == "conflict_demo",
                    onClick = {
                        onSelectProject(null)
                        presetMode = "conflict_demo"
                    },
                    label = { Text("Conflict Sample") },
                    modifier = Modifier.testTag("dep_graph_source_conflict_demo")
                )
                availableProjects.forEach { proj ->
                    FilterChip(
                        selected = presetMode == "project:${proj.id}",
                        onClick = {
                            onSelectProject(proj.id)
                            presetMode = "project:${proj.id}"
                        },
                        label = {
                            Text(
                                text = proj.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier.testTag("dep_graph_source_${proj.id}")
                    )
                }
            }

            // Status Filter / Legend Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = statusFilter == null,
                    onClick = { statusFilter = null },
                    label = { Text("All (${graph.nodes.size})") },
                    modifier = Modifier.testTag("dep_graph_filter_all")
                )
                FilterChip(
                    selected = statusFilter == DependencyNodeStatus.CONFLICT,
                    onClick = {
                        statusFilter = if (statusFilter == DependencyNodeStatus.CONFLICT) null else DependencyNodeStatus.CONFLICT
                    },
                    label = { Text("Conflicts (${graph.conflictCount})") },
                    modifier = Modifier.testTag("dep_graph_filter_conflict")
                )
                FilterChip(
                    selected = statusFilter == DependencyNodeStatus.OUTDATED,
                    onClick = {
                        statusFilter = if (statusFilter == DependencyNodeStatus.OUTDATED) null else DependencyNodeStatus.OUTDATED
                    },
                    label = { Text("Outdated (${graph.outdatedCount})") },
                    modifier = Modifier.testTag("dep_graph_filter_outdated")
                )
                FilterChip(
                    selected = statusFilter == DependencyNodeStatus.HEALTHY,
                    onClick = {
                        statusFilter = if (statusFilter == DependencyNodeStatus.HEALTHY) null else DependencyNodeStatus.HEALTHY
                    },
                    label = { Text("Up to date (${graph.healthyCount})") },
                    modifier = Modifier.testTag("dep_graph_filter_healthy")
                )
            }

            // Native Jetpack Compose Interactive Canvas Graph
            DependencyGraphCanvas(
                graph = graph,
                selectedNodeId = selectedNode?.id,
                statusFilter = statusFilter,
                onSelectNode = { node -> selectedNodeId = node.id }
            )

            // Quick Node Selector Strip
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                graph.nodes.forEach { node ->
                    FilterChip(
                        selected = node.id == selectedNode?.id,
                        onClick = { selectedNodeId = node.id },
                        label = {
                            Text(
                                text = "${node.label} ${node.declaredVersion}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        modifier = Modifier.testTag("dep_graph_node_chip_${node.id}")
                    )
                }
            }

            // Selected Node Inspector
            if (selectedNode != null) {
                SelectedDependencyInspectorCard(
                    node = selectedNode,
                    incomingFrom = graph.edges.filter { it.toId == selectedNode.id }.map { it.fromId },
                    outgoingTo = graph.edges.filter { it.fromId == selectedNode.id }.map { it.toId },
                    onResolve = { target ->
                        val updated = GradleDependencyGraphAnalyzer.applyNodeFix(inspectedScript, target)
                        if (updated != inspectedScript) {
                            inspectedScript = updated
                            Toast.makeText(
                                context,
                                "Aligned ${target.label} to ${target.recommendedVersion ?: target.fixSuggestion}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }

            // Flagged Conflicts & Outdated List
            Card(
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dep_graph_flagged_list")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountTree,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (flaggedNodes.isEmpty()) {
                                "All Dependencies Up to Date & Conflict-Free"
                            } else {
                                "Flagged Conflicts & Outdated (${flaggedNodes.size})"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (flaggedNodes.isEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Clean",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Every dependency in ${graph.projectName} matches recommended versions with zero transitive conflicts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        flaggedNodes.forEachIndexed { idx, node ->
                            if (idx > 0) HorizontalDivider()
                            val tint = if (node.status == DependencyNodeStatus.CONFLICT) {
                                Color(0xFFEF4444)
                            } else {
                                Color(0xFFD97706)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedNodeId = node.id }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (node.status == DependencyNodeStatus.CONFLICT) {
                                        Icons.Default.ErrorOutline
                                    } else {
                                        Icons.Default.WarningAmber
                                    },
                                    contentDescription = node.status.label,
                                    tint = tint,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${node.coordinate}:${node.declaredVersion}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = node.statusDetail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedDependencyInspectorCard(
    node: DependencyNode,
    incomingFrom: List<String>,
    outgoingTo: List<String>,
    onResolve: (DependencyNode) -> Unit
) {
    val accent = when (node.status) {
        DependencyNodeStatus.HEALTHY -> Color(0xFF10B981)
        DependencyNodeStatus.OUTDATED -> Color(0xFFD97706)
        DependencyNodeStatus.CONFLICT -> Color(0xFFEF4444)
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dep_graph_node_inspector")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = node.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = node.coordinate,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = accent.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = "${node.status.label} • v${node.declaredVersion}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = node.statusDetail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (incomingFrom.isNotEmpty()) {
                Text(
                    text = "Required by: ${incomingFrom.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (outgoingTo.isNotEmpty()) {
                Text(
                    text = "Pulls transitively: ${outgoingTo.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (node.status != DependencyNodeStatus.HEALTHY && node.fixSuggestion != null) {
                Button(
                    onClick = { onResolve(node) },
                    contentPadding = PaddingValues(vertical = 10.dp, horizontal = 14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dep_graph_resolve_node_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Resolve: ${node.fixSuggestion}")
                }
            }
        }
    }
}
