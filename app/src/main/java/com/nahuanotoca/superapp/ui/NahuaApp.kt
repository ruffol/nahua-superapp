package com.nahuanotoca.superapp.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.nahuanotoca.superapp.core.AppContainer
import com.nahuanotoca.superapp.core.design.Jade
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
import com.nahuanotoca.superapp.feature.rag.RagScreen
import com.nahuanotoca.superapp.feature.rag.RagViewModel
import com.nahuanotoca.superapp.feature.profile.ProfileViewModel
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
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = if (chromeless) 24.dp else 88.dp),
            )
        },
        bottomBar = {
            if (!chromeless) {
                NahuaBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = ::navigateTab,
                    onScan = { navController.navigate(Routes.SCAN) },
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
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onScan = { navController.navigate(Routes.SCAN) },
                    onMessage = showMessage,
                )
            }

            composable(Routes.SERVICES) {
                val viewModel = rememberAppViewModel { ServicesViewModel(it) }
                ServicesScreen(
                    viewModel = viewModel,
                    onOpenService = ::openService,
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

            composable(Routes.RAG) {
                val viewModel = rememberAppViewModel { RagViewModel(it) }
                RagScreen(
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

private data class BottomDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val bottomDestinations = listOf(
    BottomDestination(Routes.HOME, "Inicio", Icons.Outlined.Home),
    BottomDestination(Routes.SERVICES, "Servicios", Icons.Outlined.GridView),
    BottomDestination(Routes.WALLET, "Cartera", Icons.Outlined.AccountBalanceWallet),
    BottomDestination(Routes.PROFILE, "Perfil", Icons.Outlined.Person),
)

@Composable
private fun NahuaBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onScan: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 16.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(66.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BottomBarItem(bottomDestinations[0], currentRoute, onNavigate, Modifier.weight(1f))
            BottomBarItem(bottomDestinations[1], currentRoute, onNavigate, Modifier.weight(1f))
            ScanButton(onScan, Modifier.weight(1f))
            BottomBarItem(bottomDestinations[2], currentRoute, onNavigate, Modifier.weight(1f))
            BottomBarItem(bottomDestinations[3], currentRoute, onNavigate, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BottomBarItem(
    destination: BottomDestination,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = currentRoute == destination.route
    val tint = if (selected) Jade else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onNavigate(destination.route) }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = destination.label,
            tint = tint,
            modifier = Modifier.size(23.dp),
        )
        Text(
            text = destination.label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
private fun ScanButton(onScan: () -> Unit, modifier: Modifier = Modifier) {
    val scale by animateFloatAsState(targetValue = 1f, animationSpec = tween(200), label = "scanScale")
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .offset(y = (-10).dp)
                .scale(scale)
                .size(56.dp)
                .clip(CircleShape)
                .background(Jade)
                .clickable(onClick = onScan),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.QrCodeScanner,
                contentDescription = "Escanear",
                tint = Color.White,
                modifier = Modifier.size(27.dp),
            )
        }
    }
}