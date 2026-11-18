package com.example.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.example.design.AppDesign
import com.example.design.ScreenIcons
import com.example.design.ScreenSpecs
import com.example.design.designFor
import com.example.model.AppProject
import com.example.model.ScreenSpec

@Composable
fun LivePreviewScreen(
    project: AppProject?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFullScreen by remember { mutableStateOf(false) }

    BackHandler {
        if (isFullScreen) {
            isFullScreen = false
        } else {
            onClose()
        }
    }

    if (project == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Project not found for Live Preview.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Button(onClick = onClose) {
                    Text("Close")
                }
            }
        }
        return
    }

    val context = LocalContext.current
    val appDesign = remember(project) { designFor(project) }
    var isDarkPreview by remember(project.id) { mutableStateOf(appDesign.isDarkBackground) }
    var selectedTabIndex by remember(project.id) { mutableIntStateOf(0) }

    val previewSpecs = remember(project) {
        ScreenSpecs.of(project)
    }

    val primaryColor = Color(appDesign.primaryColor)
    val secondaryColor = Color(appDesign.secondaryColor)
    val baseBgColor = if (isDarkPreview == appDesign.isDarkBackground) {
        Color(appDesign.backgroundColor)
    } else if (isDarkPreview) {
        Color(0xFF121212)
    } else {
        Color(0xFFFFFFFF)
    }

    val cardBgColor = if (isDarkPreview) {
        Color(0xFF1E222B)
    } else {
        if (appDesign.backgroundColor == 0xFFFFF8E1L) Color(0xFFFFFFFF) else Color(0xFFF8FAFC)
    }
    val onBgColor = if (isDarkPreview) Color(0xFFF8FAFC) else Color(0xFF0F172A)

    val activeColorScheme = if (isDarkPreview) {
        darkColorScheme(
            primary = primaryColor,
            onPrimary = Color.White,
            secondary = secondaryColor,
            onSecondary = Color.White,
            background = baseBgColor,
            onBackground = onBgColor,
            surface = cardBgColor,
            onSurface = onBgColor,
            outline = primaryColor.copy(alpha = 0.4f)
        )
    } else {
        lightColorScheme(
            primary = primaryColor,
            onPrimary = Color.White,
            secondary = secondaryColor,
            onSecondary = Color.White,
            background = baseBgColor,
            onBackground = onBgColor,
            surface = cardBgColor,
            onSurface = onBgColor,
            outline = primaryColor.copy(alpha = 0.35f)
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Ai App Studio Top Bar (hidden when "Open in Full Screen" is active)
        if (!isFullScreen) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("live_preview_top_bar")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Smartphone,
                                contentDescription = null,
                                tint = primaryColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = project.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("live_preview_project_title")
                                )
                                Text(
                                    text = "${appDesign.designStyle} • ${appDesign.layoutType} • ${appDesign.cardStyle}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = primaryColor
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onClose,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("live_preview_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Close")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = { isDarkPreview = !isDarkPreview },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("theme_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (isDarkPreview) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isDarkPreview) "Dark / Light (Dark)" else "Dark / Light (Light)",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        FilledTonalButton(
                            onClick = { isFullScreen = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_full_screen_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Open in Full Screen",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }

        // Dynamic Previewed App Environment
        MaterialTheme(colorScheme = activeColorScheme) {
            val safeTabIndex = if (previewSpecs.isEmpty()) 0 else selectedTabIndex.coerceIn(0, previewSpecs.lastIndex)
            val activeSpec = previewSpecs.getOrNull(safeTabIndex)
                ?: ScreenSpec(
                    name = "MainScreen",
                    label = project.name.ifBlank { "App" },
                    icon = "home",
                    purpose = project.description
                )
            val currentScreenLabel = activeSpec.label

            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("live_preview_scaffold"),
                containerColor = activeColorScheme.background,
                topBar = {
                    Surface(
                        color = primaryColor,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = project.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "$currentScreenLabel (${appDesign.layoutType})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }

                            if (isFullScreen) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TextButton(
                                        onClick = { isDarkPreview = !isDarkPreview },
                                        modifier = Modifier.testTag("fullscreen_theme_toggle")
                                    ) {
                                        Text("Dark / Light", color = Color.White)
                                    }
                                    TextButton(
                                        onClick = { isFullScreen = false },
                                        modifier = Modifier.testTag("exit_full_screen_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FullscreenExit,
                                            contentDescription = "Exit Full Screen",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Exit", color = Color.White)
                                    }
                                    TextButton(
                                        onClick = onClose,
                                        modifier = Modifier.testTag("fullscreen_close_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Close",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                bottomBar = {
                    if (previewSpecs.size in 2..5) {
                        NavigationBar(
                            containerColor = activeColorScheme.surface,
                            modifier = Modifier.testTag("live_preview_bottom_nav")
                        ) {
                            previewSpecs.forEachIndexed { index, spec ->
                                NavigationBarItem(
                                    selected = safeTabIndex == index,
                                    onClick = { selectedTabIndex = index },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color.White,
                                        selectedTextColor = primaryColor,
                                        indicatorColor = primaryColor
                                    ),
                                    icon = {
                                        Icon(
                                            imageVector = ScreenIcons.vector(spec.icon),
                                            contentDescription = spec.label
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = spec.label,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    modifier = Modifier.testTag("preview_tab_$index")
                                )
                            }
                        }
                    } else if (previewSpecs.size > 5) {
                        Surface(
                            color = activeColorScheme.surface,
                            tonalElevation = 3.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("live_preview_bottom_nav")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                previewSpecs.forEachIndexed { index, spec ->
                                    val isTabSelected = safeTabIndex == index
                                    Surface(
                                        color = if (isTabSelected) primaryColor else Color.Transparent,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .clickable { selectedTabIndex = index }
                                            .testTag("preview_tab_$index")
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Icon(
                                                imageVector = ScreenIcons.vector(spec.icon),
                                                contentDescription = spec.label,
                                                tint = if (isTabSelected) Color.White else onBgColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = spec.label,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isTabSelected) Color.White else onBgColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.TopCenter
                ) {
                    DynamicDesignedScreenContent(
                        project = project,
                        appDesign = appDesign,
                        spec = activeSpec,
                        isPrimaryTab = selectedTabIndex == 0,
                        primaryColor = primaryColor,
                        secondaryColor = secondaryColor,
                        cardBgColor = activeColorScheme.surface,
                        onFeatureClick = { feature ->
                            Toast.makeText(context, "Opening $feature", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DynamicDesignedScreenContent(
    project: AppProject,
    appDesign: AppDesign,
    spec: ScreenSpec,
    isPrimaryTab: Boolean,
    primaryColor: Color,
    secondaryColor: Color,
    cardBgColor: Color,
    onFeatureClick: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 600.dp)
            .padding(16.dp)
    ) {
        when {
            appDesign.layoutType == "GridProducts" && isPrimaryTab -> {
                GridProductsLayoutPreview(
                    project = project,
                    spec = spec,
                    primaryColor = primaryColor,
                    cardBgColor = cardBgColor,
                    onFeatureClick = onFeatureClick
                )
            }

            appDesign.layoutType == "ChatList" && isPrimaryTab -> {
                ChatBubblesLayoutPreview(
                    project = project,
                    spec = spec,
                    primaryColor = primaryColor,
                    secondaryColor = secondaryColor,
                    cardBgColor = cardBgColor
                )
            }

            appDesign.layoutType == "Dashboard" && isPrimaryTab -> {
                FinanceDashboardLayoutPreview(
                    project = project,
                    spec = spec,
                    primaryColor = primaryColor,
                    secondaryColor = secondaryColor,
                    cardBgColor = cardBgColor,
                    onFeatureClick = onFeatureClick
                )
            }

            appDesign.layoutType == "ProgressList" && isPrimaryTab -> {
                ProgressListLayoutPreview(
                    project = project,
                    spec = spec,
                    primaryColor = primaryColor,
                    cardBgColor = cardBgColor,
                    onFeatureClick = onFeatureClick
                )
            }

            appDesign.layoutType == "TravelGrid" && isPrimaryTab -> {
                GridProductsLayoutPreview(
                    project = project,
                    spec = spec,
                    primaryColor = primaryColor,
                    cardBgColor = cardBgColor,
                    onFeatureClick = onFeatureClick,
                    actionLabel = "Explore"
                )
            }

            else -> {
                SpecScreenPreview(
                    spec = spec,
                    project = project,
                    primaryColor = primaryColor,
                    cardBgColor = cardBgColor,
                    onItemClick = onFeatureClick
                )
            }
        }
    }
}

@Composable
private fun GridProductsLayoutPreview(
    project: AppProject,
    spec: ScreenSpec,
    primaryColor: Color,
    cardBgColor: Color,
    onFeatureClick: (String) -> Unit,
    actionLabel: String = "Add to Cart"
) {
    var cartCount by remember(project.id) { mutableIntStateOf(0) }
    val itemsList = remember(project, spec) {
        val baseFeatures = spec.sampleItems.ifEmpty {
            project.features.ifEmpty {
                listOf("Featured Item 1", "Featured Item 2", "Featured Item 3", "Featured Item 4")
            }
        }
        baseFeatures.mapIndexed { index, raw ->
            val parts = raw.split(" • ", limit = 2)
            val title = parts.first().trim()
            val meta = parts.getOrNull(1)?.trim() ?: "$${(index + 1) * 24 + 15}.00"
            title to meta
        }
    }
    val itemIcon = if (actionLabel == "Add to Cart") {
        Icons.Default.ShoppingBag
    } else {
        ScreenIcons.vector(spec.icon)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("preview_grid_products"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = cardBgColor),
            border = BorderStroke(1.5.dp, primaryColor),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("preview_description_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${project.name} • ${spec.label}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor
                    )
                    Text(
                        text = spec.purpose.ifBlank { project.description },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = primaryColor,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = if (actionLabel == "Add to Cart") "Cart: $cartCount" else "Saved: $cartCount",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        if (spec.components.isNotEmpty()) {
            PreviewComponentStrip(components = spec.components, primaryColor = primaryColor)
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(itemsList) { (itemTitle, subtitleOrPrice) ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
                    border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onFeatureClick(itemTitle) }
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(68.dp)
                                .background(
                                    primaryColor.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = itemIcon,
                                contentDescription = null,
                                tint = primaryColor,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Text(
                            text = itemTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = subtitleOrPrice,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Button(
                            onClick = {
                                cartCount++
                                onFeatureClick(itemTitle)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = primaryColor,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(vertical = 6.dp, horizontal = 8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(actionLabel, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubblesLayoutPreview(
    project: AppProject,
    spec: ScreenSpec,
    primaryColor: Color,
    secondaryColor: Color,
    cardBgColor: Color
) {
    var draft by remember { mutableStateOf("") }
    var messages by remember(project.id, spec.sampleItems) {
        val seed = spec.sampleItems.ifEmpty {
            project.features.ifEmpty {
                listOf(
                    "Welcome to ${project.name}!",
                    "Tap any message or send a reply below.",
                    "Real-time chat layout active."
                )
            }
        }
        mutableStateOf(
            seed.mapIndexed { index, text -> (index % 2 == 1) to text }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("preview_chat_bubbles"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages) { (isOutgoing, text) ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Surface(
                        color = if (isOutgoing) primaryColor else cardBgColor,
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isOutgoing) 16.dp else 4.dp,
                            bottomEnd = if (isOutgoing) 4.dp else 16.dp
                        ),
                        border = if (isOutgoing) null else BorderStroke(1.dp, secondaryColor.copy(alpha = 0.5f)),
                        modifier = Modifier.widthIn(max = 280.dp)
                    ) {
                        Text(
                            text = text,
                            color = if (isOutgoing) Color.White else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Message ${project.name}...") },
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    if (draft.isNotBlank()) {
                        messages = messages + (true to draft)
                        draft = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryColor,
                    contentColor = Color.White
                )
            ) {
                Text("Send")
            }
        }
    }
}

@Composable
private fun FinanceDashboardLayoutPreview(
    project: AppProject,
    spec: ScreenSpec,
    primaryColor: Color,
    secondaryColor: Color,
    cardBgColor: Color,
    onFeatureClick: (String) -> Unit
) {
    val features = spec.sampleItems.ifEmpty {
        project.features.ifEmpty {
            listOf("Monthly Budget Tracker", "Income vs Expense Ring", "Savings Goal Vault")
        }
    }
    val headlineAmount = features.firstOrNull()
        ?.split(" • ")
        ?.getOrNull(1)
        ?.takeIf { it.contains("$") }
        ?: "$14,820.50"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("preview_finance_dashboard"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = primaryColor),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("preview_balance_card")
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${project.name} • ${spec.label}",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    Text(
                        text = headlineAmount,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = spec.purpose.ifBlank { "Monthly Budget Used: 62%" },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    LinearProgressIndicator(
                        progress = { 0.62f },
                        color = Color.White,
                        trackColor = secondaryColor,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        if (spec.components.isNotEmpty()) {
            item {
                PreviewComponentStrip(components = spec.components, primaryColor = primaryColor)
            }
        }

        itemsIndexed(features) { index, feature ->
            val parts = feature.split(" • ", limit = 2)
            val title = parts.first().trim()
            val detail = parts.getOrNull(1)?.trim() ?: "+$${(index + 1) * 120}"
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onFeatureClick(feature) }
                    .testTag("preview_feature_card_$index")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressListLayoutPreview(
    project: AppProject,
    spec: ScreenSpec,
    primaryColor: Color,
    cardBgColor: Color,
    onFeatureClick: (String) -> Unit
) {
    val rows = remember(project.id, spec) {
        spec.sampleItems.ifEmpty {
            project.features.ifEmpty {
                listOf("HIIT Circuit Session", "Strength Progression", "Daily Macro Goal")
            }
        }
    }
    var completedIndices by remember(project.id, spec.name) {
        mutableStateOf(setOf(0))
    }
    val progress = if (rows.isEmpty()) 0f else {
        (completedIndices.size.toFloat() / rows.size.toFloat()).coerceIn(0.15f, 1f)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("preview_progress_list"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = cardBgColor),
            border = BorderStroke(1.5.dp, primaryColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${project.name} • ${spec.label} (${(progress * 100).toInt()}%)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = primaryColor
                )
                if (spec.purpose.isNotBlank()) {
                    Text(
                        text = spec.purpose,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                LinearProgressIndicator(
                    progress = { progress },
                    color = primaryColor,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        val nextIdx = (0 until rows.size).firstOrNull { it !in completedIndices }
                        completedIndices = if (nextIdx != null) {
                            completedIndices + nextIdx
                        } else {
                            setOf(0)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryColor,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (completedIndices.size < rows.size) "Log Progress" else "Reset Session"
                    )
                }
            }
        }

        if (spec.components.isNotEmpty()) {
            PreviewComponentStrip(components = spec.components, primaryColor = primaryColor)
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(rows) { index, itemText ->
                val isDone = index in completedIndices
                val parts = itemText.split(" • ", limit = 2)
                val title = parts.first().trim()
                val subtitle = parts.getOrNull(1)?.trim().orEmpty()
                Card(
                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
                    border = BorderStroke(
                        1.dp,
                        if (isDone) primaryColor else primaryColor.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            completedIndices = if (isDone) {
                                completedIndices - index
                            } else {
                                completedIndices + index
                            }
                            onFeatureClick(itemText)
                        }
                        .testTag("preview_feature_card_$index")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isDone) {
                                Icons.Default.CheckCircle
                            } else {
                                Icons.Default.RadioButtonUnchecked
                            },
                            contentDescription = if (isDone) "Completed" else "Pending",
                            tint = if (isDone) primaryColor else primaryColor.copy(alpha = 0.55f)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (subtitle.isNotEmpty()) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = primaryColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Text(
                            text = if (isDone) "Done" else "Tap",
                            style = MaterialTheme.typography.labelLarge,
                            color = primaryColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewComponentStrip(
    components: List<String>,
    primaryColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        components.forEach { component ->
            Surface(
                color = primaryColor.copy(alpha = 0.12f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.55f))
            ) {
                Text(
                    text = component,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
