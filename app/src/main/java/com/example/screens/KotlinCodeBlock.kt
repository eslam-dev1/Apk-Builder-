package com.example.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.design.KotlinSyntaxHighlighter
import com.example.design.KotlinSyntaxTheme
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlinx.coroutines.delay

@Composable
fun KotlinCodeBlock(
    code: String,
    modifier: Modifier = Modifier,
    fileName: String = "GeneratedScreen.kt",
    codeTestTag: String? = null,
    showLineNumbers: Boolean = true,
    fontSize: TextUnit = 12.sp,
    lineHeight: TextUnit = 18.sp,
    borderColor: Color? = null,
    initiallyExpanded: Boolean = true,
    collapsedMaxHeight: Dp? = null
) {
    val context = LocalContext.current
    var useLightSyntaxTheme by remember { mutableStateOf(false) }
    var isExpanded by remember(initiallyExpanded) { mutableStateOf(initiallyExpanded) }
    var copiedRecently by remember { mutableStateOf(false) }

    LaunchedEffect(copiedRecently) {
        if (copiedRecently) {
            delay(1800L)
            copiedRecently = false
        }
    }

    val syntaxTheme = if (useLightSyntaxTheme) KotlinSyntaxTheme.Light else KotlinSyntaxTheme.Dark
    val highlightedCode = remember(code, syntaxTheme) {
        KotlinSyntaxHighlighter.highlight(code, syntaxTheme)
    }

    val lineCount = remember(code) {
        if (code.isEmpty()) 1 else code.count { it == '\n' } + 1
    }
    val lineNumbersText = remember(lineCount) {
        (1..lineCount).joinToString("\n")
    }
    val canCollapse = collapsedMaxHeight != null && lineCount > 10

    Surface(
        color = syntaxTheme.background,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, borderColor ?: syntaxTheme.border),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Code block header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(syntaxTheme.headerBackground)
                    .padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = syntaxTheme.keyword.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                tint = syntaxTheme.keyword,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "KT",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = syntaxTheme.keyword
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = fileName,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = syntaxTheme.defaultText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$lineCount lines",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = syntaxTheme.lineNumber,
                            maxLines = 1
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = { useLightSyntaxTheme = !useLightSyntaxTheme },
                        modifier = Modifier
                            .size(48.dp)
                            .let { base ->
                                if (codeTestTag != null) base.testTag("${codeTestTag}_theme_toggle") else base
                            }
                    ) {
                        Icon(
                            imageVector = if (useLightSyntaxTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = if (useLightSyntaxTheme) "Switch to dark code theme" else "Switch to light code theme",
                            tint = syntaxTheme.lineNumber,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    if (canCollapse) {
                        IconButton(
                            onClick = { isExpanded = !isExpanded },
                            modifier = Modifier
                                .size(48.dp)
                                .let { base ->
                                    if (codeTestTag != null) base.testTag("${codeTestTag}_expand_toggle") else base
                                }
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                                contentDescription = if (isExpanded) "Collapse code block" else "Expand code block",
                                tint = syntaxTheme.lineNumber,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText(fileName, code))
                            copiedRecently = true
                            Toast.makeText(context, "Copied $fileName", Toast.LENGTH_SHORT).show()
                        },
                        color = if (copiedRecently) {
                            syntaxTheme.string.copy(alpha = 0.16f)
                        } else {
                            syntaxTheme.keyword.copy(alpha = 0.14f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (copiedRecently) {
                                syntaxTheme.string.copy(alpha = 0.6f)
                            } else {
                                syntaxTheme.border
                            }
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .let { base ->
                                if (codeTestTag != null) base.testTag("${codeTestTag}_copy_button") else base
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = if (copiedRecently) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = if (copiedRecently) "Copied" else "Copy Kotlin code",
                                tint = if (copiedRecently) syntaxTheme.string else syntaxTheme.defaultText,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = if (copiedRecently) "Copied" else "Copy",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (copiedRecently) syntaxTheme.string else syntaxTheme.defaultText,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = syntaxTheme.border)

            // Code body with optional vertical viewport cap + aligned line numbers + horizontal scroll
            val viewportModifier = if (!isExpanded && collapsedMaxHeight != null) {
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = collapsedMaxHeight)
                    .verticalScroll(rememberScrollState())
            } else {
                Modifier.fillMaxWidth()
            }

            Box(modifier = viewportModifier) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                ) {
                    if (showLineNumbers) {
                        Text(
                            text = lineNumbersText,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            color = syntaxTheme.lineNumber,
                            textAlign = TextAlign.End,
                            modifier = Modifier
                                .padding(start = 10.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)
                        )
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(syntaxTheme.border.copy(alpha = 0.7f))
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        SelectionContainer {
                            Text(
                                text = highlightedCode,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = fontSize,
                                lineHeight = lineHeight,
                                color = syntaxTheme.defaultText,
                                softWrap = false,
                                modifier = if (codeTestTag != null) {
                                    Modifier.testTag(codeTestTag)
                                } else {
                                    Modifier
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
