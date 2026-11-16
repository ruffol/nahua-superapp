package com.nahuanotoca.superapp.feature.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.nahuanotoca.superapp.core.design.Jade
import com.nahuanotoca.superapp.core.design.JadeSoft
import com.nahuanotoca.superapp.core.design.NahuaCard
import com.nahuanotoca.superapp.core.design.SectionHeader
import com.nahuanotoca.superapp.core.design.Sky
import com.nahuanotoca.superapp.core.location.LocationFix
import com.nahuanotoca.superapp.core.miniapp.MiniAppBridge
import com.nahuanotoca.superapp.core.model.Service
import kotlinx.coroutines.launch

/**
 * Tarjeta interactiva del mapa offline y ubicación actual en el menú principal (HomeScreen).
 * Muestra el mapa embebido con las teselas locales empaquetadas, el punto de ubicación GPS
 * en tiempo real, accesos rápidos a municipios y tianguis de Colima.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HomeOfflineMapCard(
    viewModel: HomeViewModel,
    onOpenFullMap: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locationFix by viewModel.currentLocation.collectAsStateWithLifecycle()
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val bridge = remember {
        MiniAppBridge(
            sessionJson = { "{}" },
            onPayRequest = {},
            onClose = onOpenFullMap,
            onToast = onMessage,
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.refreshLocation()
            onMessage("Ubicación GPS actualizada")
        } else {
            onMessage("Centrado en Colima Centro (modo local)")
        }
        webViewRef?.evaluateJavascript("if (typeof locateUser === 'function') locateUser();", null)
    }

    bridge.onLocationRequest = { requestId ->
        scope.launch {
            val fix = locationFix ?: LocationFix(19.2433, -103.7250, 30f)
            bridge.resolveLocation(requestId, fix)
        }
    }

    val assetLoader = remember(context) {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        SectionHeader(
            title = "Mapa Offline & Tu Ubicación",
            actionLabel = "Ver pantalla completa",
            onAction = onOpenFullMap,
        )

        Spacer(Modifier.height(6.dp))

        NahuaCard(
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                // Barra de estado de ubicación actual
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Sky.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NearMe,
                            contentDescription = null,
                            tint = Sky,
                            modifier = Modifier.size(16.dp),
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ubicación actual",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val locText = if (locationFix != null) {
                            "${String.format("%.4f", locationFix!!.latitude)}° N, ${String.format("%.4f", locationFix!!.longitude)}° O · Colima"
                        } else {
                            "19.2433° N, -103.7250° O · Colima Centro"
                        }
                        Text(
                            text = locText,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // Botón para centrar en GPS
                    Surface(
                        onClick = {
                            if (viewModel.hasLocationPermission()) {
                                viewModel.refreshLocation()
                                webViewRef?.evaluateJavascript("if (typeof locateUser === 'function') locateUser();", null)
                            } else {
                                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        },
                        shape = RoundedCornerShape(50),
                        color = Jade,
                        shadowElevation = 2.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MyLocation,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Centrar",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                    }
                }

                // Chips rápidos de municipios
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val towns = listOf(
                        "Colima" to 0,
                        "Villa de Álvarez" to 1,
                        "Comala" to 2,
                        "Tecomán" to 3,
                        "Manzanillo" to 4,
                        "Armería" to 5,
                    )
                    towns.forEach { (name, idx) ->
                        Surface(
                            onClick = {
                                webViewRef?.evaluateJavascript(
                                    "if (window.MUNICIPIOS && window.selectCity) selectCity(window.MUNICIPIOS[$idx]);",
                                    null,
                                )
                            },
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Place,
                                    contentDescription = null,
                                    tint = Jade,
                                    modifier = Modifier.size(12.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }

                // Contenedor del WebView interactivo del mapa offline
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)),
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { webViewContext ->
                            WebView(webViewContext).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.allowContentAccess = false
                                settings.allowFileAccess = false
                                settings.cacheMode = WebSettings.LOAD_DEFAULT
                                setBackgroundColor(android.graphics.Color.parseColor("#E8EDF4"))

                                webViewClient = object : WebViewClientCompat() {
                                    override fun shouldInterceptRequest(
                                        view: WebView,
                                        request: WebResourceRequest,
                                    ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

                                    override fun onPageFinished(view: WebView, url: String) {
                                        evaluateJavascript("if (typeof triggerResize === 'function') triggerResize();", null)
                                    }
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean = true
                                }

                                addJavascriptInterface(bridge, "NahuaNative")
                                bridge.sendToWeb = { script -> post { evaluateJavascript(script, null) } }

                                loadUrl("https://appassets.androidplatform.net/assets/miniapps/mapa/index.html?mode=compact")
                                webViewRef = this
                            }
                        },
                    )

                    // Botón flotante para expandir al mapa completo
                    Surface(
                        onClick = onOpenFullMap,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .shadow(6.dp, RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.95f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Explore,
                                contentDescription = null,
                                tint = Jade,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Abrir mapa",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}
