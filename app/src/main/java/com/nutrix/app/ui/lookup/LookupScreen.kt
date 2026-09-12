package com.nutrix.app.ui.lookup

import android.Manifest
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.nutrix.app.data.repository.FoodSearchResult
import com.nutrix.app.data.repository.FoodSource
import com.nutrix.app.model.MealType
import com.nutrix.app.model.Nutrient
import com.nutrix.app.model.NutrientGroup
import com.nutrix.app.model.format
import com.nutrix.app.ui.components.BannerTone
import com.nutrix.app.ui.components.InfoBanner
import com.nutrix.app.ui.components.NutrixCard
import com.nutrix.app.ui.components.SectionHeader
import com.nutrix.app.ui.rememberAppContainer
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * The free way into the diary: scan a barcode or search by name.
 *
 * Both paths cost nothing to run — Open Food Facts needs no key at all, the USDA key is free,
 * and the barcode reader is on-device. For anything with a label on it this beats estimating
 * from a photograph, because it is the manufacturer's own panel rather than a guess.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun LookupScreen(onDone: () -> Unit, onOpenPhotoScan: () -> Unit) {
    val container = rememberAppContainer()
    val viewModel: LookupViewModel = viewModel(factory = LookupViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add food") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Default.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.selected == null) {
                item {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::setQuery,
                        label = { Text("Search any food") },
                        placeholder = { Text("e.g. amul dahi, chicken breast, oats") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (state.query.isBlank()) {
                    item {
                        NutrixCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Scan a barcode", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Reads the packet's own nutrition panel. Free, works offline, " +
                                            "and more accurate than any photo estimate.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(4f / 3f)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                contentAlignment = Alignment.Center,
                            ) {
                                when {
                                    !cameraPermission.status.isGranted -> Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(20.dp),
                                    ) {
                                        Text(
                                            "Nutrix needs the camera to read barcodes.",
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        Spacer(Modifier.height(10.dp))
                                        Button(onClick = { cameraPermission.launchPermissionRequest() }) {
                                            Text("Allow camera")
                                        }
                                    }
                                    state.scanning -> BarcodeScannerView(onBarcode = viewModel::onBarcodeScanned)
                                    else -> OutlinedButton(onClick = { viewModel.setScanning(true) }) {
                                        Text("Scan again")
                                    }
                                }
                                if (state.isSearching) {
                                    Box(
                                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text("Looking it up…", color = Color.White)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        NutrixCard {
                            SectionHeader("Common foods")
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                state.suggestions.take(8).chunked(2).forEach { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        row.forEach { suggestion ->
                                            AssistChip(
                                                onClick = { viewModel.setQuery(suggestion.substringBefore(",")) },
                                                label = { Text(suggestion.substringBefore(","), maxLines = 1) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        NutrixCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Estimate from a photo", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "For home-cooked food with no barcode. Needs a paid Claude API " +
                                            "key — everything else in Nutrix is free.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                OutlinedButton(onClick = onOpenPhotoScan) { Text("Open") }
                            }
                        }
                    }
                }

                state.message?.let { message ->
                    item {
                        InfoBanner(
                            text = message,
                            tone = BannerTone.WARNING,
                            actionLabel = "Dismiss",
                            onAction = viewModel::dismissMessage,
                        )
                    }
                }

                if (state.isSearching && state.query.isNotBlank()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Searching free food databases…")
                        }
                    }
                }

                items(state.results) { result ->
                    ResultRow(result = result, onClick = { viewModel.select(result) })
                }
            }

            state.selected?.let { result ->
                item {
                    NutrixCard {
                        Text(result.name, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(2.dp))
                        SourceBadge(result)
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            value = state.portionGrams,
                            onValueChange = viewModel::setPortion,
                            label = { Text("Portion (g)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(160.dp),
                        )
                        result.servingGrams?.let { serving ->
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "One serving on the pack is ${serving.roundToInt()} g",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                val grams = state.portionGrams.toDoubleOrNull() ?: 0.0
                val scaled = result.per100g * (grams / 100.0)

                item {
                    NutrixCard {
                        SectionHeader("In this portion")
                        Nutrient.macros.plus(Nutrient.ENERGY).distinct()
                            .filter { scaled.has(it) }
                            .forEach { nutrient ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Text(nutrient.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Text(nutrient.format(scaled.amountOr0(nutrient)), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        listOf(NutrientGroup.VITAMIN, NutrientGroup.MINERAL).forEach { group ->
                            val rows = scaled.byGroup(group)
                            if (rows.isEmpty()) return@forEach
                            Text(
                                group.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                            )
                            rows.forEach { (nutrient, amount) ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                    Text(nutrient.label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                    Text(nutrient.format(amount), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        if (scaled.byGroup(NutrientGroup.VITAMIN).isEmpty() &&
                            scaled.byGroup(NutrientGroup.MINERAL).isEmpty()
                        ) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "This label only declares macros. Micronutrients stay blank rather " +
                                    "than being invented.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = { viewModel.log(onDone) },
                            enabled = grams > 0,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Add to diary") }
                        OutlinedButton(
                            onClick = viewModel::clearSelection,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) { Text("Pick something else") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(result: FoodSearchResult, onClick: () -> Unit) {
    NutrixCard {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(result.name, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(2.dp))
                SourceBadge(result)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${result.per100g.amountOr0(Nutrient.ENERGY).roundToInt()} kcal · " +
                        "P ${result.per100g.amountOr0(Nutrient.PROTEIN).roundToInt()} g · " +
                        "C ${result.per100g.amountOr0(Nutrient.CARBS).roundToInt()} g · " +
                        "F ${result.per100g.amountOr0(Nutrient.FAT).roundToInt()} g per 100 g",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SourceBadge(result: FoodSearchResult) {
    val color = when (result.source) {
        FoodSource.LABEL -> MaterialTheme.colorScheme.primary
        FoodSource.LAB -> MaterialTheme.colorScheme.tertiary
        FoodSource.BUILT_IN -> MaterialTheme.colorScheme.outline
    }
    Text(
        "${result.source.label} · ${result.sourceLabel}",
        style = MaterialTheme.typography.labelSmall,
        color = color,
    )
}

/** CameraX preview wired to the on-device barcode reader. */
@Composable
private fun BarcodeScannerView(onBarcode: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(executor, BarcodeAnalyzer(onBarcode)) }
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            runCatching { future.get().unbindAll() }
            executor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}
