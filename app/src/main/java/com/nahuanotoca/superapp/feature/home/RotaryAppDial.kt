package com.nahuanotoca.superapp.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nahuanotoca.superapp.core.design.Jade
import com.nahuanotoca.superapp.core.model.Service
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Selector moderno de aplicaciones inspirado en los clásicos aros de teléfonos antiguos (disco giratorio).
 *
 * Combina la estética retro-vintage del disco telefónico con acabados modernos (gradientes metálicos,
 * elevación y glassmorphism) y permite seleccionar y abrir cada app tanto girando el disco con el dedo
 * como tocando los orificios del aro o el hub central.
 */
@Composable
fun RotaryAppDial(
    services: List<Service>,
    onOpenService: (Service) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (services.isEmpty()) return

    // Máximo 10 orificios para mantener la proporción clásica de los teléfonos de disco (1 al 0)
    val displayServices = remember(services) { services.take(10) }
    val count = displayServices.size
    val stepAngle = 360f / count

    var selectedIndex by remember { mutableIntStateOf(0) }
    val rotationAnim = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    // Ángulo de arrastre interactivo
    var startAngle by remember { mutableFloatStateOf(0f) }
    var currentDialAngle by remember { mutableFloatStateOf(0f) }

    fun selectIndex(index: Int) {
        val targetIndex = (index % count + count) % count
        selectedIndex = targetIndex
        val targetAngle = -targetIndex * stepAngle
        coroutineScope.launch {
            rotationAnim.animateTo(
                targetValue = targetAngle,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            )
            currentDialAngle = targetAngle
        }
    }

    val selectedService = displayServices[selectedIndex]

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Indicador de modo / instrucciones
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Dialpad,
                    contentDescription = null,
                    tint = Jade,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Aro Telefónico",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = Jade.copy(alpha = 0.12f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.TouchApp,
                        contentDescription = null,
                        tint = Jade,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Gira el aro o toca un orificio",
                        style = MaterialTheme.typography.labelSmall,
                        color = Jade,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Contenedor principal del aro telefónico
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val sizePx = constraints.maxWidth.toFloat().coerceAtMost(980f)
            val sizeDp = with(LocalDensity.current) { sizePx.toDp() }
            val radiusPx = sizePx / 2f
            val holeRadiusPx = radiusPx * 0.72f // Radio donde se sitúan los orificios

            Box(
                modifier = Modifier
                    .size(sizeDp)
                    .aspectRatio(1f)
                    .shadow(16.dp, CircleShape, spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    // Bisel exterior metálico
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surface,
                                Color(0xFF1E2630),
                            ),
                        ),
                    )
                    .border(
                        BorderStroke(
                            4.dp,
                            Brush.sweepGradient(
                                listOf(
                                    Color(0xFFE0E5EC),
                                    Color(0xFF8896A6),
                                    Color(0xFFFFFFFF),
                                    Color(0xFF637381),
                                    Color(0xFFE0E5EC),
                                ),
                            ),
                        ),
                        CircleShape,
                    )
                    .pointerInput(count) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val dx = offset.x - radiusPx
                                val dy = offset.y - radiusPx
                                startAngle = (atan2(dy, dx) * (180f / PI.toFloat())) - currentDialAngle
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val dx = change.position.x - radiusPx
                                val dy = change.position.y - radiusPx
                                val touchAngle = atan2(dy, dx) * (180f / PI.toFloat())
                                val newAngle = touchAngle - startAngle
                                currentDialAngle = newAngle
                                coroutineScope.launch {
                                    rotationAnim.snapTo(newAngle)
                                }
                            },
                            onDragEnd = {
                                // Snap al orificio más cercano
                                val norm = -currentDialAngle
                                val closestIndex = ((norm / stepAngle).roundToInt() % count + count) % count
                                selectIndex(closestIndex)
                            },
                            onDragCancel = {
                                val norm = -currentDialAngle
                                val closestIndex = ((norm / stepAngle).roundToInt() % count + count) % count
                                selectIndex(closestIndex)
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                // Disco giratorio con los orificios
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .rotate(rotationAnim.value),
                    contentAlignment = Alignment.Center,
                ) {
                    displayServices.forEachIndexed { index, service ->
                        val angleDeg = index * stepAngle - 90f // Comenzar arriba (12 en punto)
                        val angleRad = angleDeg * (PI / 180.0)
                        val holeX = (holeRadiusPx * cos(angleRad)).toFloat()
                        val holeY = (holeRadiusPx * sin(angleRad)).toFloat()

                        val isSelected = index == selectedIndex

                        // Orificio individual del disco telefónico
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(holeX.roundToInt(), holeY.roundToInt()) }
                                .size(50.dp)
                                .shadow(if (isSelected) 8.dp else 3.dp, CircleShape)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) {
                                        Brush.radialGradient(listOf(service.accent, service.accent.copy(alpha = 0.8f)))
                                    } else {
                                        Brush.radialGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.surface,
                                                MaterialTheme.colorScheme.surfaceVariant,
                                            ),
                                        )
                                    },
                                )
                                .border(
                                    width = if (isSelected) 3.dp else 1.5.dp,
                                    color = if (isSelected) Color.White else Color(0xFFBBC5D0),
                                    shape = CircleShape,
                                )
                                .clickable {
                                    selectIndex(index)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    imageVector = service.icon,
                                    contentDescription = service.name,
                                    tint = if (isSelected) Color.White else service.accent,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isSelected) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // Tope metálico clásico del aro telefónico (situado a las 12 en punto como indicador)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = 4.dp)
                        .size(width = 18.dp, height = 24.dp)
                        .shadow(4.dp, RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                        .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFFF5F7FA), Color(0xFF9AA5B1), Color(0xFF616E7C)),
                            ),
                        )
                        .border(1.dp, Color.White, RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)),
                )

                // Eje central (Hub) con la app seleccionada y botón de acción
                Surface(
                    onClick = { onOpenService(selectedService) },
                    modifier = Modifier
                        .size(144.dp)
                        .shadow(12.dp, CircleShape),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        width = 2.5.dp,
                        brush = Brush.sweepGradient(
                            listOf(
                                selectedService.accent,
                                Color(0xFFE0E5EC),
                                selectedService.accent,
                            ),
                        ),
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(selectedService.accent.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = selectedService.icon,
                                contentDescription = null,
                                tint = selectedService.accent,
                                modifier = Modifier.size(22.dp),
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        Text(
                            text = selectedService.name,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )

                        Text(
                            text = selectedService.tagline,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(Modifier.height(4.dp))

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = selectedService.accent,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Abrir",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                                Spacer(Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(10.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
