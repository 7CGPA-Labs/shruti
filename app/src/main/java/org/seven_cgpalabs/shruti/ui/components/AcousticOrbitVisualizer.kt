package org.seven_cgpalabs.shruti.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

enum class VisualizerState {
    IDLE,
    PASSIVE_LISTENING,
    VULKAN_DEBRIEF_SYNTHESIS
}

@Composable
fun AcousticOrbitVisualizer(
    state: VisualizerState,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "OrbitTransition")

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "OrbitRotation"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state == VisualizerState.VULKAN_DEBRIEF_SYNTHESIS) 900 else 1800,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "OrbitPulse"
    )

    val primaryColor = when (state) {
        VisualizerState.IDLE -> Color(0xFF00E5FF) // Electric Cyan
        VisualizerState.PASSIVE_LISTENING -> Color(0xFF7C4DFF) // Purple Pulse
        VisualizerState.VULKAN_DEBRIEF_SYNTHESIS -> Color(0xFFFF9100) // Vulkan Compute Amber
    }

    val secondaryColor = when (state) {
        VisualizerState.IDLE -> Color(0xFF1DE9B6) // Teal
        VisualizerState.PASSIVE_LISTENING -> Color(0xFF00E5FF) // Cyan
        VisualizerState.VULKAN_DEBRIEF_SYNTHESIS -> Color(0xFFFF3D00) // Deep Orange
    }

    Box(
        modifier = modifier.size(180.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(170.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = (size.minDimension / 2f) * 0.7f * pulseScale

            // Outer Harmonic Orbit Ring
            drawCircle(
                brush = Brush.sweepGradient(listOf(primaryColor, secondaryColor, primaryColor)),
                radius = baseRadius,
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )

            // Inner Elliptical Orbit Ring
            val radAngle = Math.toRadians(rotationAngle.toDouble())
            val orbitCount = 4
            for (i in 0 until orbitCount) {
                val phase = radAngle + (i * Math.PI * 2 / orbitCount)
                val orbitX = center.x + (baseRadius * 0.65f * cos(phase)).toFloat()
                val orbitY = center.y + (baseRadius * 0.65f * sin(phase)).toFloat()

                // Floating harmonic satellite sphere
                drawCircle(
                    color = primaryColor.copy(alpha = 0.85f),
                    radius = 5.dp.toPx() * pulseScale,
                    center = Offset(orbitX, orbitY)
                )
            }

            // Core Energy Sphere
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.45f),
                        secondaryColor.copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 0.5f
                ),
                radius = baseRadius * 0.5f,
                center = center
            )
        }
    }
}
