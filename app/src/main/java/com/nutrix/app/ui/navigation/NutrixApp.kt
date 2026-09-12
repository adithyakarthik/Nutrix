package com.nutrix.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nutrix.app.ui.chat.ChatScreen
import com.nutrix.app.ui.diary.DiaryScreen
import com.nutrix.app.ui.goals.GoalsScreen
import com.nutrix.app.ui.home.HomeScreen
import com.nutrix.app.ui.lookup.LookupScreen
import com.nutrix.app.ui.profile.OnboardingScreen
import com.nutrix.app.ui.profile.ProfileScreen
import com.nutrix.app.ui.recipes.RecipeEditorScreen
import com.nutrix.app.ui.recipes.RecipeListScreen
import com.nutrix.app.ui.scan.ScanScreen
import com.nutrix.app.ui.settings.SettingsScreen
import com.nutrix.app.ui.water.WaterScreen

@Composable
fun NutrixApp(
    startDestination: String,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomDestinations.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomDestinations.forEach { destination ->
                        val selected = currentRoute == destination.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    navController.navigate(destination.route) {
                                        popUpTo(Routes.HOME) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    if (selected) destination.selectedIcon else destination.icon,
                                    contentDescription = destination.label,
                                )
                            },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn(tween(180)) },
            exitTransition = { fadeOut(tween(180)) },
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onFinished = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.HOME) {
                HomeScreen(
                    onScan = { navController.navigate(Routes.LOOKUP) },
                    onOpenWater = { navController.navigate(Routes.WATER) },
                    onOpenGoals = { navController.navigate(Routes.GOALS) },
                    onOpenProfile = { navController.navigate(Routes.PROFILE) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenDiary = { navController.navigate(Routes.DIARY) },
                    onOpenChat = { navController.navigate(Routes.CHAT) },
                )
            }
            composable(Routes.LOOKUP) {
                LookupScreen(
                    onDone = { navController.popBackStack() },
                    onOpenPhotoScan = { navController.navigate(Routes.SCAN) },
                )
            }
            composable(Routes.SCAN) {
                ScanScreen(onDone = { navController.popBackStack() })
            }
            composable(Routes.DIARY) {
                DiaryScreen(onScan = { navController.navigate(Routes.LOOKUP) })
            }
            composable(Routes.RECIPES) {
                RecipeListScreen(
                    onCreate = { navController.navigate(Routes.recipeEditor(0L)) },
                    onOpen = { id -> navController.navigate(Routes.recipeEditor(id)) },
                )
            }
            composable(
                route = Routes.RECIPE_EDITOR_ROUTE,
                arguments = listOf(navArgument(Routes.RECIPE_EDITOR_ARG) { type = NavType.LongType }),
            ) { entry ->
                RecipeEditorScreen(
                    recipeId = entry.arguments?.getLong(Routes.RECIPE_EDITOR_ARG) ?: 0L,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.CHAT) { ChatScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.WATER) { WaterScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.GOALS) { GoalsScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.PROFILE) { ProfileScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.SETTINGS) { SettingsScreen(onBack = { navController.popBackStack() }) }
        }
    }
}
