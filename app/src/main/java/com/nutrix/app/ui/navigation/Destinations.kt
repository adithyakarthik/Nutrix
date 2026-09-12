package com.nutrix.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val SCAN = "scan"
    const val DIARY = "diary"
    const val RECIPES = "recipes"
    const val CHAT = "chat"
    const val WATER = "water"
    const val GOALS = "goals"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"

    const val RECIPE_EDITOR = "recipe_editor"
    const val RECIPE_EDITOR_ARG = "recipeId"
    const val RECIPE_EDITOR_ROUTE = "$RECIPE_EDITOR/{$RECIPE_EDITOR_ARG}"

    fun recipeEditor(recipeId: Long) = "$RECIPE_EDITOR/$recipeId"
}

data class BottomDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
)

val bottomDestinations = listOf(
    BottomDestination(Routes.HOME, "Today", Icons.Filled.Restaurant, Icons.Outlined.Restaurant),
    BottomDestination(Routes.DIARY, "Diary", Icons.Filled.Timeline, Icons.Outlined.Timeline),
    BottomDestination(Routes.SCAN, "Scan", Icons.Filled.PhotoCamera, Icons.Filled.PhotoCamera),
    BottomDestination(Routes.RECIPES, "Recipes", Icons.Filled.MenuBook, Icons.Outlined.MenuBook),
    BottomDestination(Routes.CHAT, "Ask", Icons.Filled.Chat, Icons.Outlined.Chat),
)
