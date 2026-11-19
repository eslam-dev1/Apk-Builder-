package com.example.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DependencyNode
import com.example.data.DependencyNodeStatus
import com.example.data.GradleDependencyGraph
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

internal fun computeDependencyNodeCenters(
    nodes: List<DependencyNode>,
    canvasSize: Size
): Map<String, Offset> {
    if (nodes.isEmpty() || canvasSize.width <= 0f || canvasSize.height <= 0f) return emptyMap()
    val positions = linkedMapOf<String, Offset>()
    val byLayer = nodes.groupBy { it.layer }

    val layer0 = byLayer[0].orEmpty()
    val layer1 = byLayer[1].orEmpty()
    val layer2 = byLayer[2].orEmpty()

    fun distributeRow(rowNodes: List<DependencyNode>, yFrac: Float) {
        if (rowNodes.isEmpty()) return
        val marginX = canvasSize.width * 0.12f
        val usableW = (canvasSize.width - marginX * 2f).coerceAtLeast(100f)
        val y = canvasSize.height * yFrac
        rowNodes.forEachIndexed { idx, node ->
            val x = if (rowNodes.size == 1) {
                canvasSize.width * 0.5f
            } else {
                marginX + usableW * (idx.toFloat() / (rowNodes.size - 1).toFloat())
            }
            positions[node.id] = Offset(x, y)
        }
    }

    distributeRow(layer0, 0.13f)

    if (layer1.size <= 4) {
        distributeRow(layer1, if (layer2.isEmpty()) 0.65f else 0.48f)
    } else {
        val mid = (layer1.size + 1) / 2
        distributeRow(layer1.take(mid), 0.36f)
        distributeRow(layer1.drop(mid), 0.58f)
    }

    distributeRow(layer2, 0.84f)
    return positions
}

@Composable
fun DependencyGraphCanvas(
    graph: GradleDependencyGraph,
    selectedNodeId: String?,
    statusFilter: DependencyNodeStatus?,
    onSelectNode: (DependencyNode) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    var zoom by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    val colorHealthy = Color(0xFF10B981)
    val colorOutdated = Color(0xFFD97706)
    val colorConflict = Color(0xFFEF4444)
    val colorSurface = MaterialTheme.colorScheme.surface
    val colorOnSurface = MaterialTheme.colorScheme.onSurface
    val colorPrimary = MaterialTheme.colorScheme.primary
    val colorGrid = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)

    fun statusColor(status: DependencyNodeStatus): Color = when (status) {
        DependencyNodeStatus.HEALTHY -> colorHealthy
        DependencyNodeStatus.OUTDATED -> colorOutdated
        DependencyNodeStatus.CONFLICT -> colorConflict
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        modifier = modifier
            .fillMaxWidth()
            .height(360.dp)
            .testTag("dep_graph_canvas_container")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { viewportSize = it }
                    .pointerInput(graph, zoom, panOffset) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            panOffset += dragAmount
                        }
                    }
                    .pointerInput(graph, zoom, panOffset, viewportSize) {
                        detectTapGestures { tapPos ->
                            val sizeF = Size(viewportSize.width.toFloat(), viewportSize.height.toFloat())
                            val centers = computeDependencyNodeCenters(graph.nodes, sizeF)
                            val centerPivot = Offset(sizeF.width / 2f, sizeF.height / 2f)
                            val worldTap = (tapPos - panOffset - centerPivot) / zoom + centerPivot
                            val hitNode = graph.nodes.minByOrNull { node ->
                                val c = centers[node.id] ?: Offset(-9999f, -9999f)
                                hypot((worldTap.x - c.x).toDouble(), (worldTap.y - c.y).toDouble())
                            }
                            if (hitNode != null) {
                                val c = centers[hitNode.id]
                                if (c != null && hypot((worldTap.x - c.x).toDouble(), (worldTap.y - c.y).toDouble()) <= 95.0) {
                                    onSelectNode(hitNode)
                                }
                            }
                        }
                    }
                    .testTag("dep_graph_canvas")
            ) {
                val centers = computeDependencyNodeCenters(graph.nodes, size)
                val pivot = Offset(size.width / 2f, size.height / 2f)

                withTransform({
                    translate(panOffset.x + pivot.x, panOffset.y + pivot.y)
                    scale(zoom, zoom, Offset.Zero)
                    translate(-pivot.x, -pivot.y)
                }) {
                    // Subtle architectural dot grid
                    val step = 36.dp.toPx()
                    var gx = 0f
                    while (gx < size.width) {
                        var gy = 0f
                        while (gy < size.height) {
                            drawCircle(color = colorGrid, radius = 1.4.dp.toPx(), center = Offset(gx, gy))
                            gy += step
                        }
                        gx += step
                    }

                    // Draw edges with Bezier curves & directional arrowheads
                    graph.edges.forEach { edge ->
                        val fromCenter = centers[edge.fromId] ?: return@forEach
                        val toCenter = centers[edge.toId] ?: return@forEach
                        val targetNode = graph.nodes.firstOrNull { it.id == edge.toId }
                        val sourceNode = graph.nodes.firstOrNull { it.id == edge.fromId }
                        val matchesFilter = statusFilter == null ||
                            targetNode?.status == statusFilter ||
                            sourceNode?.status == statusFilter
                        val alpha = if (matchesFilter) 0.9f else 0.16f

                        val edgeColor = when {
                            edge.isConflict -> colorConflict.copy(alpha = alpha)
                            edge.isOutdated -> colorOutdated.copy(alpha = alpha)
                            else -> colorPrimary.copy(alpha = alpha * 0.55f)
                        }

                        val startPt = Offset(fromCenter.x, fromCenter.y + 20.dp.toPx())
                        val endPt = Offset(toCenter.x, toCenter.y - 22.dp.toPx())
                        val midY = (startPt.y + endPt.y) * 0.5f

                        val path = Path().apply {
                            moveTo(startPt.x, startPt.y)
                            cubicTo(
                                startPt.x, midY,
                                endPt.x, midY,
                                endPt.x, endPt.y
                            )
                        }
                        val strokeWidth = if (edge.isConflict || edge.isOutdated) 2.5.dp.toPx() else 1.7.dp.toPx()
                        val effect = if (edge.isConflict) {
                            PathEffect.dashPathEffect(floatArrayOf(10f, 7f), 0f)
                        } else null

                        drawPath(
                            path = path,
                            color = edgeColor,
                            style = Stroke(width = strokeWidth, pathEffect = effect)
                        )

                        // Arrowhead
                        val angle = atan2((endPt.y - midY).toDouble(), 0.0)
                        val arrowLen = 7.dp.toPx()
                        val wingAngle = 0.52
                        val p1 = Offset(
                            endPt.x - (arrowLen * cos(angle - wingAngle)).toFloat(),
                            endPt.y - (arrowLen * sin(angle - wingAngle)).toFloat()
                        )
                        val p2 = Offset(
                            endPt.x - (arrowLen * cos(angle + wingAngle)).toFloat(),
                            endPt.y - (arrowLen * sin(angle + wingAngle)).toFloat()
                        )
                        drawLine(edgeColor, endPt, p1, strokeWidth = strokeWidth)
                        drawLine(edgeColor, endPt, p2, strokeWidth = strokeWidth)
                    }

                    // Draw nodes
                    graph.nodes.forEach { node ->
                        val center = centers[node.id] ?: return@forEach
                        val isSelected = node.id == selectedNodeId
                        val matchesFilter = statusFilter == null || node.status == statusFilter || node.layer == 0
                        val nodeAlpha = if (matchesFilter) 1f else 0.25f

                        val accent = if (node.layer == 0) colorPrimary else statusColor(node.status)
                        val pillW = 104.dp.toPx()
                        val pillH = 42.dp.toPx()
                        val topLeft = Offset(center.x - pillW / 2f, center.y - pillH / 2f)

                        if (isSelected) {
                            drawRoundRect(
                                color = accent.copy(alpha = 0.24f),
                                topLeft = Offset(topLeft.x - 5.dp.toPx(), topLeft.y - 5.dp.toPx()),
                                size = Size(pillW + 10.dp.toPx(), pillH + 10.dp.toPx()),
                                cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                            )
                        }

                        drawRoundRect(
                            color = colorSurface.copy(alpha = nodeAlpha),
                            topLeft = topLeft,
                            size = Size(pillW, pillH),
                            cornerRadius = CornerRadius(11.dp.toPx(), 11.dp.toPx())
                        )
                        drawRoundRect(
                            color = accent.copy(alpha = nodeAlpha),
                            topLeft = topLeft,
                            size = Size(pillW, pillH),
                            cornerRadius = CornerRadius(11.dp.toPx(), 11.dp.toPx()),
                            style = Stroke(width = if (isSelected) 2.6.dp.toPx() else 1.5.dp.toPx())
                        )

                        // Status dot
                        drawCircle(
                            color = accent.copy(alpha = nodeAlpha),
                            radius = 4.dp.toPx(),
                            center = Offset(topLeft.x + 10.dp.toPx(), topLeft.y + 13.dp.toPx())
                        )

                        val titleLayout = textMeasurer.measure(
                            text = node.label.take(14),
                            style = TextStyle(
                                color = colorOnSurface.copy(alpha = nodeAlpha),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                        drawText(
                            textLayoutResult = titleLayout,
                            topLeft = Offset(topLeft.x + 18.dp.toPx(), topLeft.y + 6.dp.toPx())
                        )

                        val versionLayout = textMeasurer.measure(
                            text = "v${node.declaredVersion}".take(13),
                            style = TextStyle(
                                color = accent.copy(alpha = nodeAlpha),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                        drawText(
                            textLayoutResult = versionLayout,
                            topLeft = Offset(topLeft.x + 18.dp.toPx(), topLeft.y + 22.dp.toPx())
                        )
                    }
                }
            }

            // Zoom & Reset Overlay Controls
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { zoom = (zoom - 0.2f).coerceAtLeast(0.6f) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("dep_graph_zoom_out")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Zoom Out",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            zoom = 1f
                            panOffset = Offset.Zero
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("dep_graph_reset_view")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CenterFocusStrong,
                            contentDescription = "Reset Graph View",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { zoom = (zoom + 0.2f).coerceAtMost(2.2f) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("dep_graph_zoom_in")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Zoom In",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
