package com.example.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.data.CloudAiConfig

import com.example.model.BuildOptions
import androidx.compose.material.icons.filled.Tune

@Composable
fun ApiKeyValidationBottomSheet(
    onDismiss: () -> Unit,
    onProceedToBuild: () -> Unit,
    buildOptions: BuildOptions? = null,
    onConfigureOptions: (() -> Unit)? = null
) {
    val context = LocalContext.current
    
    val geminiKeyFromBuildConfig = remember {
        val key = runCatching { BuildConfig.GEMINI_API_KEY }.getOrDefault("").trim()
        if (key == "MY_GEMINI_API_KEY" || key == "null") "" else key
    }
    
    val openRouterKeySecrets = remember {
        val key = runCatching { BuildConfig.OPENROUTER_API_KEY }.getOrDefault("").trim()
        if (key == "MY_OPENROUTER_API_KEY" || key == "null") "" else key
    }

    val savedOpenRouterKey by CloudAiConfig.savedOpenRouterApiKey.collectAsStateWithLifecycle()
    
    var openRouterInput by remember(savedOpenRouterKey) {
        mutableStateOf(savedOpenRouterKey.ifBlank { openRouterKeySecrets })
    }
    var showKey by remember { mutableStateOf(false) }

    val hasGemini = geminiKeyFromBuildConfig.isNotEmpty()
    val hasOpenRouter = openRouterInput.isNotBlank() && openRouterInput != "MY_OPENROUTER_API_KEY"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.VpnKey,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = ".env API Key Validation",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Checking .env and BuildConfig configuration before triggering APK build:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Gemini API Key Status Card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (hasGemini)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (hasGemini) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = if (hasGemini) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "GEMINI_API_KEY (.env)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (hasGemini) "Configured in BuildConfig / .env" else "Not configured in .env (Optional for offline build)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }

                // OpenRouter API Key Input / Status
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "OPENROUTER_API_KEY (.env / Runtime)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = openRouterInput,
                        onValueChange = { newValue ->
                            openRouterInput = newValue
                            CloudAiConfig.setSavedOpenRouterApiKey(context, newValue)
                        },
                        label = { Text("OpenRouter API Key") },
                        singleLine = true,
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { showKey = !showKey }) {
                                Text(if (showKey) "Hide" else "Show")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("api_key_input_field")
                    )
                    Text(
                        text = if (hasOpenRouter) "✓ Key provided" else "Optional: Enter key if using OpenRouter models",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasOpenRouter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                if (buildOptions != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Build: ${buildOptions.buildVariant.uppercase()} • Minify: ${if (buildOptions.minifyEnabled) "ON" else "OFF"}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val tfliteText = if (buildOptions.bundledTfliteModels.isNotEmpty()) {
                                    " • ${buildOptions.bundledTfliteModels.size} TFLite"
                                } else {
                                    ""
                                }
                                Text(
                                    text = "Debuggable: ${if (buildOptions.includeDebuggable) "ON" else "OFF"} • API ${buildOptions.targetSdk}$tfliteText",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (onConfigureOptions != null) {
                                TextButton(
                                    onClick = onConfigureOptions,
                                    modifier = Modifier.testTag("dialog_configure_options_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Change")
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "You can proceed to build the APK regardless, but AI cloud features require valid API keys in .env.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onProceedToBuild()
                },
                modifier = Modifier.testTag("proceed_build_apk_button")
            ) {
                Text("Proceed to Build APK")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_validation_button")
            ) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}
