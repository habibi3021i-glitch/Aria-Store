package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.StoreViewModel

class MainActivity : ComponentActivity() {

    private val storeViewModel: StoreViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Supports unified, full-bleed edge-to-edge drawing
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                
                // Track current active navigation stack entry of router
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                // Hide bottom navigation shelf on detail pages and admin views for focus immersion
                val shouldShowBottomBar = currentRoute in listOf("home", "search", "wishlist", "account")

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing, // Avoid camera notches clipping top borders
                    bottomBar = {
                        if (shouldShowBottomBar) {
                            NavigationBar(
                                modifier = Modifier.testTag("app_bottom_navigation"),
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                tonalElevation = 0.dp
                            ) {
                                val tabs = listOf(
                                    NavTabItem("Games", "home", Icons.Default.Home, "home_tab"),
                                    NavTabItem("Search", "search", Icons.Default.Search, "search_tab"),
                                    NavTabItem("Wishlist", "wishlist", Icons.Default.Favorite, "wishlist_tab"),
                                    NavTabItem("Account", "account", Icons.Default.Person, "account_tab")
                                )

                                tabs.forEach { tab ->
                                    val isSelected = currentRoute == tab.route
                                    NavigationBarItem(
                                        selected = isSelected,
                                        onClick = {
                                            if (currentRoute != tab.route) {
                                                navController.navigate(tab.route) {
                                                    // Pop up to the start destination of the graph to
                                                    // avoid building up a large stack of destinations
                                                    popUpTo("home") { saveState = true }
                                                    // Avoid multiple copies of the same destination when
                                                    // reselecting the same item
                                                    launchSingleTop = true
                                                    // Restore state when reselecting a previously selected item
                                                    restoreState = true
                                                }
                                            }
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = tab.icon,
                                                contentDescription = tab.label
                                            )
                                        },
                                        label = {
                                            Text(text = tab.label, style = MaterialTheme.typography.labelMedium)
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.onSecondary,
                                            selectedTextColor = MaterialTheme.colorScheme.onSecondary,
                                            indicatorColor = MaterialTheme.colorScheme.secondary,
                                            unselectedIconColor = Color.Gray,
                                            unselectedTextColor = Color.Gray
                                        ),
                                        modifier = Modifier.testTag(tab.testTag)
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        // 1. Home Feed Page
                        composable("home") {
                            HomeFeedScreen(
                                viewModel = storeViewModel,
                                onAppClick = { appId ->
                                    if (appId.isNotEmpty()) navController.navigate("detail/$appId")
                                },
                                onCategorySelected = { categoryName ->
                                    // Categories trigger query filtrations inside Search Tab router
                                    navController.navigate("search") {
                                        popUpTo("home") { saveState = true }
                                        launchSingleTop = true
                                    }
                                    storeViewModel.performSearch(categoryName)
                                }
                            )
                        }

                        // 2. Search Page with realtime filtrations
                        composable("search") {
                            SearchScreen(
                                viewModel = storeViewModel,
                                onAppClick = { appId ->
                                    if (appId.isNotEmpty()) navController.navigate("detail/$appId")
                                }
                            )
                        }

                        // 3. Simulated bookmarks list
                        composable("wishlist") {
                            WishlistScreen(
                                viewModel = storeViewModel,
                                onAppClick = { appId ->
                                    if (appId.isNotEmpty()) navController.navigate("detail/$appId")
                                }
                            )
                        }

                        // 4. Accounts configuration manager
                        composable("account") {
                            AccountScreen(
                                viewModel = storeViewModel,
                                onAdminClick = {
                                    navController.navigate("admin")
                                }
                            )
                        }

                        // 5. High-fidelity application detailed sub-page
                        composable(
                            route = "detail/{appId}",
                            arguments = listOf(navArgument("appId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val appId = backStackEntry.arguments?.getString("appId") ?: ""
                            AppDetailScreen(
                                appId = appId,
                                viewModel = storeViewModel,
                                onBack = { navController.popBackStack() },
                                onRelatedAppClick = { relatedId ->
                                    if (relatedId.isNotEmpty()) {
                                        navController.navigate("detail/$relatedId") {
                                            popUpTo("home")
                                        }
                                    }
                                }
                            )
                        }

                        // 6. Developer Admin Form doc seeding Console
                        composable("admin") {
                            AdminPortalScreen(
                                viewModel = storeViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}

data class NavTabItem(
    val label: String,
    val route: String,
    val icon: ImageVector,
    val testTag: String
)
