package com.nutrix.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.navigation.compose.rememberNavController
import com.nutrix.app.ui.navigation.NutrixApp
import com.nutrix.app.ui.navigation.Routes
import com.nutrix.app.ui.theme.NutrixTheme
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as NutrixApplication).container
        val deepLink = intent?.getStringExtra(EXTRA_DESTINATION)

        setContent {
            NutrixTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Null while the preference is still loading — showing the wrong start
                    // destination for one frame would flash onboarding at existing users.
                    val onboarded by produceState<Boolean?>(initialValue = null) {
                        value = container.preferences.onboardingComplete.first()
                    }
                    val navController = rememberNavController()

                    onboarded?.let { complete ->
                        NutrixApp(
                            startDestination = if (complete) Routes.HOME else Routes.ONBOARDING,
                            navController = navController,
                        )
                        if (complete && deepLink == DESTINATION_WATER) {
                            androidx.compose.runtime.LaunchedEffect(Unit) {
                                navController.navigate(Routes.WATER)
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_DESTINATION = "nutrix_destination"
        const val DESTINATION_WATER = "water"
    }
}
