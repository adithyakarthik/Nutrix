package com.nutrix.app.ui.scan

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.nutrix.app.model.MealType
import com.nutrix.app.model.Nutrient
import com.nutrix.app.model.NutrientGroup
import com.nutrix.app.model.format
import com.nutrix.app.ui.components.BannerTone
import com.nutrix.app.ui.components.InfoBanner
import com.nutrix.app.ui.components.NutrixCard
import com.nutrix.app.ui.components.SectionHeader
import com.nutrix.app.ui.rememberAppContainer
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ScanScreen(onDone: () -> Unit) {
    val container = rememberAppContainer()
    val viewModel: ScanViewModel = viewModel(factory = ScanViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    var controller by remember { mutableStateOf<CameraController?>(null) }
    var captureError by remember { mutableStateOf<String?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { viewModel.analyze(context, it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.analysis == null) "Scan food" else state.analysis!!.title) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    if (state.analysis != null || state.imageUri != null) {
                        IconButton(onClick = viewModel::reset) { Icon(Icons.Default.Refresh, "Start over") }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        state.imageUri != null -> AsyncImage(
                            model = state.imageUri,
                            contentDescription = "Captured food",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        cameraPermission.status.isGranted -> CameraPreview(
                            onCaptureReady = { controller = it },
                        )
                        else -> Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp),
                        ) {
                            Icon(Icons.Default.Camera, contentDescription = null, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Nutrix needs the camera to read your plate.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { cameraPermission.launchPermissionRequest() }) {
                                Text("Allow camera")
                            }
                        }
                    }

                    if (state.isAnalyzing) {
                        Box(
                            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Reading your plate…",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Identifying the dish and checking food composition data.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.85f),
                                )
                                Spacer(Modifier.height(16.dp))
                                LinearProgressIndicator(modifier = Modifier.width(180.dp))
                            }
                        }
                    }
                }
            }

            if (state.analysis == null && !state.isAnalyzing) {
                item {
                    OutlinedTextField(
                        value = state.note,
                        onValueChange = viewModel::setNote,
                        label = { Text("Anything Nutrix can't see? (optional)") },
                        placeholder = { Text("e.g. cooked in 2 tbsp ghee, 200 g rice") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 1,
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                val active = controller
                                if (active == null) {
                                    captureError = "Camera is not ready yet."
                                } else {
                                    captureError = null
                                    active.capture { result ->
                                        result.fold(
                                            onSuccess = { viewModel.analyze(context, it) },
                                            onFailure = { captureError = "Capture failed: ${it.message}" },
                                        )
                                    }
                                }
                            },
                            enabled = cameraPermission.status.isGranted,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Camera, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Capture")
                        }
                        OutlinedButton(
                            onClick = {
                                galleryLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                                    ),
                                )
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Gallery")
                        }
                    }
                }
            }

            captureError?.let { message ->
                item { InfoBanner(text = message, tone = BannerTone.ERROR) }
            }

            state.error?.let { message ->
                item {
                    InfoBanner(
                        title = "Could not analyse that",
                        text = message,
                        tone = BannerTone.ERROR,
                        actionLabel = "Try again",
                        onAction = {
                            viewModel.dismissError()
                            state.imageUri?.let { viewModel.analyze(context, it) }
                        },
                    )
                }
            }

            state.scaled?.let { analysis ->
                item {
                    NutrixCard {
                        Text(analysis.title, style = MaterialTheme.typography.titleLarge)
                        if (analysis.summary.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                analysis.summary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${analysis.nutrients.amountOr0(Nutrient.ENERGY).roundToInt()} kcal",
                                    style = MaterialTheme.typography.headlineSmall,
                                )
                                Text(
                                    analysis.confidence.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            PortionEditor(
                                grams = state.portionGrams,
                                label = analysis.portionLabel,
                                onChange = viewModel::setPortion,
                            )
                        }
                    }
                }

                if (analysis.warnings.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            analysis.warnings.forEach { warning ->
                                InfoBanner(text = warning, tone = BannerTone.WARNING)
                            }
                        }
                    }
                }

                item {
                    NutrixCard {
                        SectionHeader("Macros")
                        Nutrient.macros.filter { analysis.nutrients.has(it) }.forEach { nutrient ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(nutrient.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    nutrient.format(analysis.nutrients.amountOr0(nutrient)),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }

                item {
                    NutrixCard {
                        SectionHeader("Vitamins & minerals", subtitle = "In this portion")
                        listOf(NutrientGroup.VITAMIN, NutrientGroup.MINERAL).forEach { group ->
                            val rows = analysis.nutrients.byGroup(group)
                            if (rows.isEmpty()) return@forEach
                            Text(
                                group.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                            )
                            rows.forEach { (nutrient, amount) ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                    Text(nutrient.label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                    Text(nutrient.format(amount), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                if (analysis.ingredients.isNotEmpty()) {
                    item {
                        NutrixCard {
                            SectionHeader("What Nutrix saw")
                            analysis.ingredients.forEach { ingredient ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                    Column(Modifier.weight(1f)) {
                                        Text(ingredient.name, style = MaterialTheme.typography.bodyMedium)
                                        if (ingredient.note.isNotBlank()) {
                                            Text(
                                                ingredient.note,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    Text(
                                        "${ingredient.grams.roundToInt()} g",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }
                }

                if (analysis.sources.isNotEmpty()) {
                    item {
                        NutrixCard {
                            SectionHeader("Sources", subtitle = "Where these numbers came from")
                            analysis.sources.take(6).forEach { source ->
                                Text(
                                    "• ${source.title}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 2.dp),
                                )
                            }
                        }
                    }
                }

                item {
                    NutrixCard {
                        SectionHeader("Log as")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MealType.entries.forEach { meal ->
                                FilterChip(
                                    selected = state.meal == meal,
                                    onClick = { viewModel.setMeal(meal) },
                                    label = { Text(meal.label) },
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.save(onDone) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Add to diary")
                        }
                        TextButton(onClick = viewModel::reset, modifier = Modifier.fillMaxWidth()) {
                            Text("Scan something else")
                        }
                    }
                }
            }
        }
    }
}

/** Portion is the single biggest source of error in a photo estimate, so it is editable up front. */
@Composable
private fun PortionEditor(grams: Double, label: String, onChange: (Double) -> Unit) {
    var text by remember(grams) { mutableStateOf(grams.roundToInt().toString()) }
    Column(horizontalAlignment = Alignment.End) {
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                text = input.filter { it.isDigit() }.take(5)
                text.toDoubleOrNull()?.let(onChange)
            },
            label = { Text("Portion (g)") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(140.dp),
        )
        if (label.isNotBlank()) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
