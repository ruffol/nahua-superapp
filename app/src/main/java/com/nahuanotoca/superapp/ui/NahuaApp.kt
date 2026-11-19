package com.nahuanotoca.superapp.ui

import android.content.Context
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nahuanotoca.superapp.core.AppContainer
import com.nahuanotoca.superapp.core.design.PixelBgDeep
import com.nahuanotoca.superapp.core.design.PixelSnackbar
import com.nahuanotoca.superapp.core.model.Service
import com.nahuanotoca.superapp.core.model.ServiceTarget
import com.nahuanotoca.superapp.core.navigation.Routes
import com.nahuanotoca.superapp.feature.home.HomeScreen
import com.nahuanotoca.superapp.feature.home.HomeViewModel
import com.nahuanotoca.superapp.feature.inbox.InboxScreen
import com.nahuanotoca.superapp.feature.inbox.InboxViewModel
import com.nahuanotoca.superapp.feature.miniapp.MiniAppScreen
import com.nahuanotoca.superapp.feature.miniapp.MiniAppViewModel
import com.nahuanotoca.superapp.feature.profile.ProfileScreen
import com.nahuanotoca.superapp.feature.profile.ProfileViewModel
import com.nahuanotoca.superapp.feature.rag.StudioScreen
import com.nahuanotoca.superapp.feature.rag.StudioViewModel
import com.nahuanotoca.superapp.feature.scan.ScanScreen
import com.nahuanotoca.superapp.feature.scan.ScanViewModel
import com.nahuanotoca.superapp.feature.search.SearchScreen
import com.nahuanotoca.superapp.feature.services.ServicesScreen
import com.nahuanotoca.superapp.feature.services.ServicesViewModel
import com.nahuanotoca.superapp.feature.wallet.WalletScreen
import com.nahuanotoca.superapp.feature.wallet.WalletViewModel
import kotlinx.coroutines.launch

@Composable
fun NahuaApp(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message = message) }
    }

    val chromeless = currentRoute == Routes.SCAN || currentRoute?.startsWith("miniapp/") == true

    // Toda la app es oscura en pixel art, así que los iconos del sistema van siempre en claro.
    val view = LocalView.current
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val activity = context.findActivity() ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(activity.window, view)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
    }

    fun openService(service: Service) {
        when (val target = service.target) {
            is ServiceTarget.MiniApp -> navController.navigate(Routes.miniApp(target.appId))
            is ServiceTarget.Native -> navController.navigate(target.route)
            ServiceTarget.ComingSoon -> showMessage(service.name + " llega muy pronto")
        }
    }

    fun navigateTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        containerColor = PixelBgDeep,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = if (chromeless) 24.dp else 104.dp),
            ) { data ->
                PixelSnackbar(text = data.visuals.message)
            }
        },
        bottomBar = {
            if (!chromeless) {
                PixelBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = ::navigateTab,
                    onOpenPet = { navController.navigate(Routes.miniApp("yolcatl")) },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
        ) {
            composable(Routes.HOME) {
                val viewModel = rememberAppViewModel { HomeViewModel(it) }
                HomeScreen(
                    viewModel = viewModel,
                    onOpenService = ::openService,
                    onSeeAllServices = { navigateTab(Routes.SERVICES) },
                    onOpenWallet = { navigateTab(Routes.WALLET) },
                    onOpenInbox = { navController.navigate(Routes.INBOX) },
                    onScan = { navController.navigate(Routes.SCAN) },
                    onMessage = showMessage,
                )
            }

            composable(Routes.SERVICES) {
                val viewModel = rememberAppViewModel { ServicesViewModel(it) }
                ServicesScreen(
                    viewModel = viewModel,
                    onOpenService = ::openService,
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onMessage = showMessage,
                )
            }

            composable(Routes.WALLET) {
                val viewModel = rememberAppViewModel { WalletViewModel(it) }
                WalletScreen(viewModel = viewModel, onMessage = showMessage)
            }

            composable(Routes.PROFILE) {
                val viewModel = rememberAppViewModel { ProfileViewModel(it) }
                ProfileScreen(
                    viewModel = viewModel,
                    onOpenInbox = { navController.navigate(Routes.INBOX) },
                    onOpenWallet = { navigateTab(Routes.WALLET) },
                    onMessage = showMessage,
                )
            }

            composable(Routes.INBOX) {
                val viewModel = rememberAppViewModel { InboxViewModel(it) }
                InboxScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.STUDIO) {
                val viewModel = rememberAppViewModel { StudioViewModel(it) }
                StudioScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onMessage = showMessage,
                )
            }

            composable(Routes.SEARCH) {
                val viewModel = rememberAppViewModel { ServicesViewModel(it) }
                SearchScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenService = ::openService,
                )
            }

            composable(Routes.SCAN) {
                val viewModel = rememberAppViewModel { ScanViewModel(it) }
                ScanScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenWallet = { navigateTab(Routes.WALLET) },
                    onMessage = showMessage,
                )
            }

            composable(
                route = Routes.MINI_APP_PATTERN,
                arguments = listOf(navArgument(Routes.MINI_APP_ARG) { type = NavType.StringType }),
            ) { entry ->
                val appId = entry.arguments?.getString(Routes.MINI_APP_ARG).orEmpty()
                val viewModel = rememberAppViewModel(key = appId) { MiniAppViewModel(it, appId) }
                MiniAppScreen(
                    viewModel = viewModel,
                    onClose = { navController.popBackStack() },
                    onMessage = showMessage,
                )
            }
        }
    }
}
