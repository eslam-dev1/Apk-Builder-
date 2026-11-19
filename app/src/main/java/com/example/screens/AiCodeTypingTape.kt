package com.example.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlinx.coroutines.delay

@Composable
fun AiCodeTypingTape(
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val sampleSnippets = remember {
        listOf(
            "package com.example.generated\n\nimport androidx.compose.runtime.*\nimport androidx.compose.material3.*",
            "class GeneratedAppScreen : ComponentActivity() {\n    @Composable\n    fun Content() {\n        Scaffold(topBar = { TopAppBar(title = { Text(\"AI App Studio\") }) }) { inner ->",
            "            LazyColumn(modifier = Modifier.padding(inner).fillMaxSize()) {\n                items(10) { idx ->\n                    Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {\n                        Text(\"Item #\$idx\", style = MaterialTheme.typography.bodyLarge)\n                    }\n                }\n            }\n        }\n    }\n}",
            "    @Composable\n    fun ActionButton(text: String, onClick: () -> Unit) {\n        Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {\n            Text(text)\n        }\n    }\n}"
        )
    }

    var snippetIndex by remember { mutableIntStateOf(0) }
    var typedCharCount by remember { mutableIntStateOf(0) }

    val currentSnippet = sampleSnippets[snippetIndex]

    LaunchedEffect(snippetIndex) {
        typedCharCount = 0
        while (typedCharCount < currentSnippet.length) {
            delay(28L)
            typedCharCount++
        }
        delay(1400L)
        snippetIndex = (snippetIndex + 1) % sampleSnippets.size
    }

    val visibleText = remember(currentSnippet, typedCharCount) {
        currentSnippet.take(typedCharCount)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "cursor_blink")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_alpha"
    )

    Card(
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0D1117)
        ),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("ai_code_typing_tape")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF161B22))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = Color(0xFF58A6FF),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "AI Typing Tape • Code Stream",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFC9D1D9)
                    )
                }

                Surface(
                    color = Color(0xFF238636).copy(alpha = 0.25f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "LIVE STREAM",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF3FB950),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 140.dp)
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Row {
                    Text(
                        text = visibleText,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = Color(0xFFE6EDF3)
                    )
                    Text(
                        text = "█",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 12.sp,
                        color = Color(0xFF58A6FF),
                        modifier = Modifier.alpha(cursorAlpha)
                    )
                }
            }
        }
    }
}
