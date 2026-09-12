package com.nutrix.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nutrix.app.ui.components.BannerTone
import com.nutrix.app.ui.components.InfoBanner
import com.nutrix.app.ui.components.NutrixCard
import com.nutrix.app.ui.components.SectionHeader
import com.nutrix.app.ui.rememberAppContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val container = rememberAppContainer()
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var anthropicKey by remember { mutableStateOf("") }
    var usdaKey by remember { mutableStateOf("") }
    var proxyUrl by remember(state.proxyUrl) { mutableStateOf(state.proxyUrl) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NutrixCard {
                SectionHeader(
                    "Claude",
                    subtitle = "Powers photo analysis, the chatbot and the goal review",
                )
                if (state.anthropicKeySet) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(0.dp))
                        Text(
                            "  A key is stored on this device.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = anthropicKey,
                    onValueChange = { anthropicKey = it },
                    label = { Text(if (state.anthropicKeySet) "Replace key" else "API key") },
                    placeholder = { Text("sk-ant-…") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            viewModel.saveAnthropicKey(anthropicKey)
                            anthropicKey = ""
                        },
                        enabled = anthropicKey.isNotBlank(),
                    ) { Text("Save key") }
                    if (state.anthropicKeySet) {
                        OutlinedButton(onClick = { viewModel.saveAnthropicKey("") }) { Text("Remove") }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Keys are encrypted with a key held in this phone's hardware-backed keystore " +
                        "and are excluded from backups. Get one at console.anthropic.com.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            NutrixCard {
                SectionHeader(
                    "Use your own backend instead",
                    subtitle = "Recommended for anything you ship to other people",
                )
                OutlinedTextField(
                    value = proxyUrl,
                    onValueChange = { proxyUrl = it },
                    label = { Text("Proxy base URL") },
                    placeholder = { Text("https://api.yourserver.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { viewModel.saveProxyUrl(proxyUrl) }) { Text("Save proxy") }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Nutrix will POST to {base}/v1/messages in the Anthropic Messages format and " +
                        "send no key — your server holds it. When a proxy is set it takes priority " +
                        "over any key above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            NutrixCard {
                SectionHeader(
                    "USDA FoodData Central",
                    subtitle = "Lab-measured ingredient data for the recipe builder",
                )
                if (state.usdaKeySet) {
                    Text("A key is stored on this device.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = usdaKey,
                    onValueChange = { usdaKey = it },
                    label = { Text(if (state.usdaKeySet) "Replace key" else "API key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            viewModel.saveUsdaKey(usdaKey)
                            usdaKey = ""
                        },
                        enabled = usdaKey.isNotBlank(),
                    ) { Text("Save key") }
                    if (state.usdaKeySet) {
                        OutlinedButton(onClick = { viewModel.saveUsdaKey("") }) { Text("Remove") }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Free from fdc.nal.usda.gov/api-key-signup.html. Without it, ingredient lookups " +
                        "fall back to Claude and then to the small table built into the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            NutrixCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("AI features", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Photo analysis, the chatbot and goal review. Turn this off and Nutrix " +
                                "still tracks, calculates and reminds — entirely on-device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = state.aiEnabled, onCheckedChange = viewModel::setAiEnabled)
                }
            }

            NutrixCard {
                SectionHeader("Your data")
                Text(
                    "Your diary, recipes, profile and water log never leave the phone. When you scan " +
                        "a photo or ask a question, that photo or question — plus a short summary of " +
                        "your profile and today's totals — is sent to Anthropic to be answered.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = viewModel::clearChatHistory) { Text("Delete conversation history") }
            }

            InfoBanner(
                title = "Not medical advice",
                text = "Nutrix estimates. A photograph cannot tell you exactly how much oil is in a " +
                    "curry, and reference intakes describe populations, not individuals. If you are " +
                    "managing a health condition, your clinician's numbers beat the app's.",
                tone = BannerTone.WARNING,
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}
