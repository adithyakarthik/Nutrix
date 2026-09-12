package com.nutrix.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.nutrix.app.AppContainer
import com.nutrix.app.NutrixApplication

/** The one place composables reach the dependency graph. */
@Composable
fun rememberAppContainer(): AppContainer {
    val context = LocalContext.current
    return remember(context) { (context.applicationContext as NutrixApplication).container }
}
