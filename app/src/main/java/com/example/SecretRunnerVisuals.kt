package com.example

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Runner Biome definition for dynamic environment transitions every 200-400 meters.
 */
data class RunnerBiome(
    val sectorName: String,
    val skyTop: Color,
    val skyBottom: Color,
    val celestialColor: Color,
    val mountainColor: Color,
    val mountainAccent: Color,
    val midgroundColor: Color,
    val beaconColor: Color,
    val groundDirtColor: Color,
    val groundStoneColor: Color,
    val groundLineColor: Color,
    val groundFringeColor: Color,
    val accentColor: Color
)

private fun lerpColor(c1: Color, c2: Color, t: Float): Color {
    val f = t.coerceIn(0f, 1f)
    return Color(
        red = c1.red + f * (c2.red - c1.red),
        green = c1.green + f * (c2.green - c1.green),
        blue = c1.blue + f * (c2.blue - c1.blue),
        alpha = c1.alpha + f * (c2.alpha - c1.alpha)
    )
}

fun lerpBiome(b1: RunnerBiome, b2: RunnerBiome, t: Float): RunnerBiome {
    return RunnerBiome(
        sectorName = if (t < 0.5f) b1.sectorName else b2.sectorName,
        skyTop = lerpColor(b1.skyTop, b2.skyTop, t),
        skyBottom = lerpColor(b1.skyBottom, b2.skyBottom, t),
        celestialColor = lerpColor(b1.celestialColor, b2.celestialColor, t),
        mountainColor = lerpColor(b1.mountainColor, b2.mountainColor, t),
        mountainAccent = lerpColor(b1.mountainAccent, b2.mountainAccent, t),
        midgroundColor = lerpColor(b1.midgroundColor, b2.midgroundColor, t),
        beaconColor = lerpColor(b1.beaconColor, b2.beaconColor, t),
        groundDirtColor = lerpColor(b1.groundDirtColor, b2.groundDirtColor, t),
        groundStoneColor = lerpColor(b1.groundStoneColor, b2.groundStoneColor, t),
        groundLineColor = lerpColor(b1.groundLineColor, b2.groundLineColor, t),
        groundFringeColor = lerpColor(b1.groundFringeColor, b2.groundFringeColor, t),
        accentColor = lerpColor(b1.accentColor, b2.accentColor, t)
    )
}

object RunnerEnvironmentManager {
    // 5 Unique visual biomes cycling every 300 meters
    private val darkBiomes = listOf(
        // Sector 0: Cyber Metropolis (0 - 300m)
        RunnerBiome(
            sectorName = "CYBER CORE",
            skyTop = Color(0xFF070B14),
            skyBottom = Color(0xFF0F172A),
            celestialColor = Color(0xFF00E5FF).copy(alpha = 0.35f),
            mountainColor = Color(0xFF111C33),
            mountainAccent = Color(0xFF00E5FF),
            midgroundColor = Color(0xFF1A2A47),
            beaconColor = Color(0xFF00E5FF),
            groundDirtColor = Color(0xFF121B2B),
            groundStoneColor = Color(0xFF0C131F),
            groundLineColor = Color(0xFF00E5FF),
            groundFringeColor = Color(0xFF0284C7),
            accentColor = Color(0xFF00E5FF)
        ),
        // Sector 1: Sunset Synthwave (300 - 600m)
        RunnerBiome(
            sectorName = "SYNTH SUNSET",
            skyTop = Color(0xFF1F0A2E),
            skyBottom = Color(0xFF3F134A),
            celestialColor = Color(0xFFFF5722),
            mountainColor = Color(0xFF280E38),
            mountainAccent = Color(0xFFFF4081),
            midgroundColor = Color(0xFF36154D),
            beaconColor = Color(0xFFFF9100),
            groundDirtColor = Color(0xFF250D36),
            groundStoneColor = Color(0xFF180824),
            groundLineColor = Color(0xFFFF007F),
            groundFringeColor = Color(0xFFFF5252),
            accentColor = Color(0xFFFF5252)
        ),
        // Sector 2: Matrix Grid (600 - 900m)
        RunnerBiome(
            sectorName = "MATRIX VAULT",
            skyTop = Color(0xFF020E06),
            skyBottom = Color(0xFF071E10),
            celestialColor = Color(0xFF00E676).copy(alpha = 0.35f),
            mountainColor = Color(0xFF092815),
            mountainAccent = Color(0xFF00FF66),
            midgroundColor = Color(0xFF0D361D),
            beaconColor = Color(0xFF00E676),
            groundDirtColor = Color(0xFF071C0E),
            groundStoneColor = Color(0xFF030F07),
            groundLineColor = Color(0xFF00FF66),
            groundFringeColor = Color(0xFF10B981),
            accentColor = Color(0xFF00FF66)
        ),
        // Sector 3: Deep Nebula (900 - 1200m)
        RunnerBiome(
            sectorName = "NEBULA VOID",
            skyTop = Color(0xFF0B001F),
            skyBottom = Color(0xFF1C063D),
            celestialColor = Color(0xFFE040FB).copy(alpha = 0.4f),
            mountainColor = Color(0xFF1D093B),
            mountainAccent = Color(0xFF7C4DFF),
            midgroundColor = Color(0xFF2A1052),
            beaconColor = Color(0xFFE040FB),
            groundDirtColor = Color(0xFF1A0738),
            groundStoneColor = Color(0xFF100324),
            groundLineColor = Color(0xFFD500F9),
            groundFringeColor = Color(0xFF651FFF),
            accentColor = Color(0xFFE040FB)
        ),
        // Sector 4: Hyper Aurora (1200 - 1500m+)
        RunnerBiome(
            sectorName = "AURORA VAULT",
            skyTop = Color(0xFF021B1C),
            skyBottom = Color(0xFF052B29),
            celestialColor = Color(0xFF1DE9B6).copy(alpha = 0.45f),
            mountainColor = Color(0xFF073836),
            mountainAccent = Color(0xFF64FFDA),
            midgroundColor = Color(0xFF0B4744),
            beaconColor = Color(0xFF1DE9B6),
            groundDirtColor = Color(0xFF062A29),
            groundStoneColor = Color(0xFF031918),
            groundLineColor = Color(0xFF1DE9B6),
            groundFringeColor = Color(0xFF00BFA5),
            accentColor = Color(0xFF1DE9B6)
        )
    )

    private val lightBiomes = listOf(
        RunnerBiome(
            sectorName = "CYBER CORE",
            skyTop = Color(0xFFE2E8F0),
            skyBottom = Color(0xFFF1F5F9),
            celestialColor = Color(0xFF0284C7).copy(alpha = 0.35f),
            mountainColor = Color(0xFFCBD5E1),
            mountainAccent = Color(0xFF0284C7),
            midgroundColor = Color(0xFF94A3B8),
            beaconColor = Color(0xFF0284C7),
            groundDirtColor = Color(0xFF94A3B8),
            groundStoneColor = Color(0xFF64748B),
            groundLineColor = Color(0xFF0284C7),
            groundFringeColor = Color(0xFF0369A1),
            accentColor = Color(0xFF0284C7)
        ),
        RunnerBiome(
            sectorName = "SYNTH SUNSET",
            skyTop = Color(0xFFFFEDE8),
            skyBottom = Color(0xFFFFD6CC),
            celestialColor = Color(0xFFFF6F00).copy(alpha = 0.5f),
            mountainColor = Color(0xFFFFAB91),
            mountainAccent = Color(0xFFFF3D00),
            midgroundColor = Color(0xFFFF8A65),
            beaconColor = Color(0xFFFF3D00),
            groundDirtColor = Color(0xFFD7CCC8),
            groundStoneColor = Color(0xFFA1887F),
            groundLineColor = Color(0xFFFF3D00),
            groundFringeColor = Color(0xFFDD2C00),
            accentColor = Color(0xFFFF3D00)
        ),
        RunnerBiome(
            sectorName = "MATRIX VAULT",
            skyTop = Color(0xFFE8F5E9),
            skyBottom = Color(0xFFC8E6C9),
            celestialColor = Color(0xFF2E7D32).copy(alpha = 0.4f),
            mountainColor = Color(0xFFA5D6A7),
            mountainAccent = Color(0xFF2E7D32),
            midgroundColor = Color(0xFF81C784),
            beaconColor = Color(0xFF00C853),
            groundDirtColor = Color(0xFFB0BEC5),
            groundStoneColor = Color(0xFF78909C),
            groundLineColor = Color(0xFF00C853),
            groundFringeColor = Color(0xFF2E7D32),
            accentColor = Color(0xFF00C853)
        ),
        RunnerBiome(
            sectorName = "NEBULA VOID",
            skyTop = Color(0xFFF3E5F5),
            skyBottom = Color(0xFFE1BEE7),
            celestialColor = Color(0xFF8E24AA).copy(alpha = 0.4f),
            mountainColor = Color(0xFFCE93D8),
            mountainAccent = Color(0xFF8E24AA),
            midgroundColor = Color(0xFFBA68C8),
            beaconColor = Color(0xFFAA00FF),
            groundDirtColor = Color(0xFFCFD8DC),
            groundStoneColor = Color(0xFF90A4AE),
            groundLineColor = Color(0xFFAA00FF),
            groundFringeColor = Color(0xFF6A1B9A),
            accentColor = Color(0xFFAA00FF)
        ),
        RunnerBiome(
            sectorName = "AURORA VAULT",
            skyTop = Color(0xFFE0F2F1),
            skyBottom = Color(0xFFB2DFDB),
            celestialColor = Color(0xFF00897B).copy(alpha = 0.4f),
            mountainColor = Color(0xFF80CBC4),
            mountainAccent = Color(0xFF00897B),
            midgroundColor = Color(0xFF4DB6AC),
            beaconColor = Color(0xFF00BFA5),
            groundDirtColor = Color(0xFFB2DFDB),
            groundStoneColor = Color(0xFF80CBC4),
            groundLineColor = Color(0xFF00BFA5),
            groundFringeColor = Color(0xFF004D40),
            accentColor = Color(0xFF00BFA5)
        )
    )

    fun getBiome(distance: Float, isDark: Boolean): RunnerBiome {
        val list = if (isDark) darkBiomes else lightBiomes
        val biomeCycleDistance = 280f // Changes every 280 meters
        val rawIdx = (distance / biomeCycleDistance).toInt()
        val currentIdx = rawIdx % list.size
        val nextIdx = (currentIdx + 1) % list.size

        val offsetInCycle = distance % biomeCycleDistance
        // Transition starts at 200m in the cycle, lasting 80m until seamless switch
        val transitionProgress = if (offsetInCycle > 200f) {
            ((offsetInCycle - 200f) / 80f).coerceIn(0f, 1f)
        } else {
            0f
        }

        return if (transitionProgress > 0f) {
            lerpBiome(list[currentIdx], list[nextIdx], transitionProgress)
        } else {
            list[currentIdx]
        }
    }
}

/**
 * 1. Parallax Distant Skyline with Dynamic Biome styling & Celestial Body
 */
fun DrawScope.drawDynamicSkyline(
    width: Float,
    groundY: Float,
    offset: Float,
    biome: RunnerBiome,
    isDark: Boolean
) {
    // 1. Distant Celestial Body (Sun/Matrix Core/Nebula Moon)
    val sunRadius = 46f
    val sunCenterX = width * 0.72f
    val sunCenterY = groundY - 145f

    // Soft celestial glow aura
    drawCircle(
        color = biome.celestialColor.copy(alpha = 0.18f),
        radius = sunRadius * 1.75f,
        center = Offset(sunCenterX, sunCenterY)
    )
    drawCircle(
        brush = Brush.radialGradient(
            listOf(biome.celestialColor.copy(alpha = 0.85f), biome.celestialColor.copy(alpha = 0.1f)),
            center = Offset(sunCenterX, sunCenterY),
            radius = sunRadius
        ),
        radius = sunRadius,
        center = Offset(sunCenterX, sunCenterY)
    )

    // Synth horizontal scanlines through the celestial body
    for (lineIdx in 0..4) {
        val y = sunCenterY + (lineIdx * 7f) - 6f
        val halfW = kotlin.math.sqrt((sunRadius * sunRadius - (y - sunCenterY) * (y - sunCenterY)).coerceAtLeast(0f))
        if (halfW > 4f) {
            drawLine(
                color = biome.skyTop.copy(alpha = 0.8f),
                start = Offset(sunCenterX - halfW, y),
                end = Offset(sunCenterX + halfW, y),
                strokeWidth = 2f
            )
        }
    }

    // 2. Distant Cyber Skyline & Horizon Silhouettes
    val hillWidth = 95f
    val spacing = 80f
    var x = -offset
    var index = 0
    while (x < width + spacing) {
        val hillHeight = ((index * 41) % 95 + 45).toFloat()
        val path = Path().apply {
            moveTo(x - 20f, groundY)
            // Futuristic angular / curved silhouettes
            if (index % 2 == 0) {
                lineTo(x + hillWidth * 0.3f, groundY - hillHeight)
                lineTo(x + hillWidth * 0.7f, groundY - hillHeight)
            } else {
                quadraticTo(x + hillWidth * 0.5f, groundY - hillHeight, x + hillWidth + 20f, groundY)
            }
            lineTo(x + hillWidth + 20f, groundY)
            close()
        }
        drawPath(path, biome.mountainColor)

        // Subtle glowing accent crest on prominent spires
        if (index % 3 == 0) {
            drawLine(
                color = biome.mountainAccent.copy(alpha = 0.45f),
                start = Offset(x + hillWidth * 0.35f, groundY - hillHeight),
                end = Offset(x + hillWidth * 0.65f, groundY - hillHeight),
                strokeWidth = 2f
            )
        }
        x += spacing
        index++
    }
}

/**
 * 2. Midground Facility Corridor with Dynamic Biome Lights
 */
fun DrawScope.drawDynamicMidground(
    width: Float,
    groundY: Float,
    offset: Float,
    biome: RunnerBiome
) {
    val pWidth = 26f
    val spacing = 170f
    var x = -offset
    var idx = 0
    while (x < width + spacing) {
        val pH = 115f
        // Pillar structure
        drawRoundRect(
            color = biome.midgroundColor,
            topLeft = Offset(x, groundY - pH),
            size = Size(pWidth, pH),
            cornerRadius = CornerRadius(5f, 5f)
        )
        // Glowing vertical data channel
        drawLine(
            color = biome.beaconColor.copy(alpha = 0.55f),
            start = Offset(x + pWidth / 2f, groundY - pH + 8f),
            end = Offset(x + pWidth / 2f, groundY - 6f),
            strokeWidth = 2.5f
        )
        // Pulsing top emitter beacon
        drawCircle(
            color = biome.beaconColor,
            radius = 3.5f,
            center = Offset(x + pWidth / 2f, groundY - pH + 10f)
        )
        x += spacing
        idx++
    }
}

/**
 * 3. Ground Foundation with Dynamic Biome Textures
 */
fun DrawScope.drawDynamicGround(
    canvasWidth: Float,
    canvasHeight: Float,
    groundY: Float,
    offset: Float,
    biome: RunnerBiome
) {
    // Base subterranean dirt
    drawRect(
        color = biome.groundDirtColor,
        topLeft = Offset(-100f, groundY),
        size = Size(canvasWidth + 200f, canvasHeight - groundY + 100f)
    )

    // Deep stone layer
    drawRect(
        color = biome.groundStoneColor,
        topLeft = Offset(-100f, groundY + 22f),
        size = Size(canvasWidth + 200f, canvasHeight - groundY + 80f)
    )

    // Glowing cyber neon runner track line
    drawRect(
        color = biome.groundLineColor,
        topLeft = Offset(-100f, groundY),
        size = Size(canvasWidth + 200f, 6f)
    )

    // Running track fringe tufts with dynamic biome color
    var tuftX = -offset - 100f
    while (tuftX < canvasWidth + 140f) {
        val tuftPath = Path().apply {
            moveTo(tuftX, groundY + 6f)
            lineTo(tuftX + 7f, groundY + 14f)
            lineTo(tuftX + 14f, groundY + 6f)
            close()
        }
        drawPath(tuftPath, biome.groundFringeColor)
        tuftX += 24f
    }
}

/**
 * 4. Beautiful, High-Polish Futuristic Obstacles
 */
fun DrawScope.drawBeautifulObstacle(
    obstacle: RunnerObstacle,
    groundY: Float,
    biome: RunnerBiome,
    isDark: Boolean
) {
    val obsBottom = groundY - obstacle.yOffset
    val obsTop = obsBottom - obstacle.height

    when (obstacle.type) {
        ObstacleType.LOW_VAULT_BOX -> {
            // High-Tech Cyber Security Vault Barrier
            val blockTop = obsTop + 12f
            val blockHeight = obstacle.height - 12f

            // Outer bevel chassis
            drawRoundRect(
                color = if (isDark) Color(0xFF1E293B) else Color(0xFF475569),
                topLeft = Offset(obstacle.x, blockTop),
                size = Size(obstacle.width, blockHeight),
                cornerRadius = CornerRadius(6f, 6f)
            )
            // High-tech core insert
            drawRoundRect(
                color = if (isDark) Color(0xFF0F172A) else Color(0xFF334155),
                topLeft = Offset(obstacle.x + 3f, blockTop + 3f),
                size = Size(obstacle.width - 6f, blockHeight - 6f),
                cornerRadius = CornerRadius(4f, 4f)
            )

            // Neon glowing hazard stripe
            drawLine(
                color = biome.accentColor,
                start = Offset(obstacle.x + 6f, blockTop + blockHeight * 0.5f),
                end = Offset(obstacle.x + obstacle.width - 6f, blockTop + blockHeight * 0.5f),
                strokeWidth = 3f
            )

            // Glowing Crystal Spikes on top
            val spikeCount = 3
            val spikeW = obstacle.width / spikeCount
            for (i in 0 until spikeCount) {
                val sx = obstacle.x + i * spikeW
                val spikePath = Path().apply {
                    moveTo(sx + 1f, blockTop)
                    lineTo(sx + spikeW * 0.5f, obsTop)
                    lineTo(sx + spikeW - 1f, blockTop)
                    close()
                }
                // Radiant spike gradient
                drawPath(spikePath, biome.accentColor)
                // Bright white highlight edge
                val edgePath = Path().apply {
                    moveTo(sx + spikeW * 0.5f, obsTop)
                    lineTo(sx + spikeW * 0.5f, blockTop)
                }
                drawPath(edgePath, Color.White, style = Stroke(width = 1.5f))
            }
        }

        ObstacleType.HIGH_OVERHEAD_LASER -> {
            // Futuristic Sentry Drone with Overhead Laser Emitter
            val centerX = obstacle.x + obstacle.width * 0.5f
            val centerY = obsTop + obstacle.height * 0.5f
            val radius = obstacle.height * 0.5f

            // Rotating plasma teeth ring
            val toothCount = 8
            val angleOffset = (obstacle.x * 0.09f) % (2f * PI.toFloat())
            val sawPath = Path()
            for (i in 0 until toothCount) {
                val a1 = angleOffset + i * (2f * PI.toFloat() / toothCount)
                val a2 = a1 + (PI.toFloat() / toothCount)
                val outerX = centerX + cos(a1) * radius
                val outerY = centerY + sin(a1) * radius
                val innerX = centerX + cos(a2) * (radius * 0.65f)
                val innerY = centerY + sin(a2) * (radius * 0.65f)

                if (i == 0) sawPath.moveTo(outerX, outerY) else sawPath.lineTo(outerX, outerY)
                sawPath.lineTo(innerX, innerY)
            }
            sawPath.close()

            // Outer blade glow
            drawPath(sawPath, biome.accentColor)
            drawPath(sawPath, Color.White.copy(alpha = 0.8f), style = Stroke(width = 1.5f))

            // Center titanium rotor hub
            drawCircle(
                color = if (isDark) Color(0xFF0F172A) else Color(0xFF334155),
                radius = radius * 0.42f,
                center = Offset(centerX, centerY)
            )
            // Intense core laser diode
            drawCircle(
                color = Color(0xFFFF1744),
                radius = radius * 0.22f,
                center = Offset(centerX, centerY)
            )

            // Overhead stabilizer rod
            drawLine(
                color = if (isDark) Color(0xFF475569) else Color(0xFF94A3B8),
                start = Offset(centerX, obsTop - 18f),
                end = Offset(centerX, obsTop),
                strokeWidth = 3f
            )
        }

        ObstacleType.CYBER_SPIRE -> {
            // Prismatic Neon Ground Spikes with Crystal Facets
            val spikeCount = 2
            val spikeW = obstacle.width / spikeCount
            for (i in 0 until spikeCount) {
                val sx = obstacle.x + i * spikeW
                val mainPath = Path().apply {
                    moveTo(sx, obsBottom)
                    lineTo(sx + spikeW * 0.5f, obsTop)
                    lineTo(sx + spikeW, obsBottom)
                    close()
                }
                // Gradient crystal fill
                drawPath(
                    mainPath,
                    Brush.verticalGradient(
                        listOf(biome.accentColor, if (isDark) Color(0xFF1E293B) else Color(0xFF475569)),
                        startY = obsTop,
                        endY = obsBottom
                    )
                )

                // Beveled light facet
                val facetPath = Path().apply {
                    moveTo(sx, obsBottom)
                    lineTo(sx + spikeW * 0.5f, obsTop)
                    lineTo(sx + spikeW * 0.5f, obsBottom)
                    close()
                }
                drawPath(facetPath, Color.White.copy(alpha = 0.45f))
                drawPath(mainPath, biome.accentColor, style = Stroke(width = 1.5f))
            }
        }

        ObstacleType.WALL_STRUCTURE -> {
            // High-Tech Cyber Security Firewall Tower
            drawRoundRect(
                color = if (isDark) Color(0xFF0F172A) else Color(0xFF1E293B),
                topLeft = Offset(obstacle.x, obsTop),
                size = Size(obstacle.width, obstacle.height),
                cornerRadius = CornerRadius(6f, 6f)
            )
            // Inner panel grid
            drawRoundRect(
                color = if (isDark) Color(0xFF1E293B) else Color(0xFF334155),
                topLeft = Offset(obstacle.x + 4f, obsTop + 6f),
                size = Size(obstacle.width - 8f, obstacle.height - 12f),
                cornerRadius = CornerRadius(4f, 4f)
            )

            // Vertical neon energy conduits
            drawLine(
                color = biome.accentColor.copy(alpha = 0.7f),
                start = Offset(obstacle.x + obstacle.width * 0.3f, obsTop + 10f),
                end = Offset(obstacle.x + obstacle.width * 0.3f, obsBottom - 10f),
                strokeWidth = 2f
            )
            drawLine(
                color = biome.accentColor.copy(alpha = 0.7f),
                start = Offset(obstacle.x + obstacle.width * 0.7f, obsTop + 10f),
                end = Offset(obstacle.x + obstacle.width * 0.7f, obsBottom - 10f),
                strokeWidth = 2f
            )

            // Glowing security emblem
            drawCircle(
                color = biome.accentColor,
                radius = 4f,
                center = Offset(obstacle.x + obstacle.width * 0.5f, obsTop + obstacle.height * 0.5f)
            )

            // Neon Top Parkour Grip Ledge (Highlights wall-jump ability)
            drawRoundRect(
                color = biome.accentColor,
                topLeft = Offset(obstacle.x + 1f, obsTop),
                size = Size(obstacle.width - 2f, 5f),
                cornerRadius = CornerRadius(2f, 2f)
            )
        }

        ObstacleType.SECURITY_LASER_GATE -> {
            // Twin High-Tech Emitter Pylons with Pulsing Plasma Beam
            val pylonW = 8f
            // Left Pylon
            drawRoundRect(
                color = if (isDark) Color(0xFF1E293B) else Color(0xFF475569),
                topLeft = Offset(obstacle.x, obsTop),
                size = Size(pylonW, obstacle.height),
                cornerRadius = CornerRadius(3f, 3f)
            )
            // Right Pylon
            drawRoundRect(
                color = if (isDark) Color(0xFF1E293B) else Color(0xFF475569),
                topLeft = Offset(obstacle.x + obstacle.width - pylonW, obsTop),
                size = Size(pylonW, obstacle.height),
                cornerRadius = CornerRadius(3f, 3f)
            )

            // Pylon glowing indicator diodes
            drawCircle(
                color = biome.accentColor,
                radius = 2.5f,
                center = Offset(obstacle.x + pylonW / 2f, obsTop + 8f)
            )
            drawCircle(
                color = biome.accentColor,
                radius = 2.5f,
                center = Offset(obstacle.x + obstacle.width - pylonW / 2f, obsTop + 8f)
            )

            // Double high-intensity plasma laser beams
            val laserY1 = obsTop + 14f
            val laserY2 = obsTop + 32f

            // Outer laser bloom
            drawLine(
                color = Color(0xFFFF1744).copy(alpha = 0.45f),
                start = Offset(obstacle.x + pylonW, laserY1),
                end = Offset(obstacle.x + obstacle.width - pylonW, laserY1),
                strokeWidth = 6f
            )
            // Core laser beam
            drawLine(
                color = Color(0xFFFF1744),
                start = Offset(obstacle.x + pylonW, laserY1),
                end = Offset(obstacle.x + obstacle.width - pylonW, laserY1),
                strokeWidth = 2.5f
            )

            drawLine(
                color = Color(0xFFFF1744).copy(alpha = 0.45f),
                start = Offset(obstacle.x + pylonW, laserY2),
                end = Offset(obstacle.x + obstacle.width - pylonW, laserY2),
                strokeWidth = 6f
            )
            drawLine(
                color = Color(0xFFFF1744),
                start = Offset(obstacle.x + pylonW, laserY2),
                end = Offset(obstacle.x + obstacle.width - pylonW, laserY2),
                strokeWidth = 2.5f
            )
        }
    }
}
