package com.example

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class RunnerGameState {
    NOT_STARTED,
    PLAYING,
    GAME_OVER
}

enum class ObstacleType {
    LOW_VAULT_BOX,         // Low crate/barrier -> Jump or Vault
    HIGH_OVERHEAD_LASER,   // Overhead firewall laser -> Must Slide under
    CYBER_SPIRE,           // Medium spiked barrier -> Jump/Double Jump
    WALL_STRUCTURE,        // Tall wall -> Double Jump or Wall Jump
    SECURITY_LASER_GATE    // Twin laser barrier -> Jump
}

enum class CollectibleType {
    SHIELD_TOKEN,
    PRIVACY_STAR,
    ENCRYPTED_CORE
}

data class RunnerObstacle(
    val id: Long,
    var x: Float,
    val yOffset: Float, // distance from ground
    val width: Float,
    val height: Float,
    val type: ObstacleType,
    var wallJumped: Boolean = false
)

data class RunnerCollectible(
    val id: Long,
    var x: Float,
    val y: Float, // height above ground
    val type: CollectibleType,
    val points: Int,
    var isCollected: Boolean = false
)

data class ScorePopup(
    val id: Long,
    val text: String,
    var x: Float,
    var y: Float,
    var alpha: Float = 1f,
    val color: Color = Color(0xFFFFD600)
)

data class RunnerParticle(
    val id: Long,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Color,
    var alpha: Float = 1f,
    val radius: Float = 3f,
    val maxLife: Float = 0.4f,
    var age: Float = 0f
)

@Composable
fun SecretRunnerGameView(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val isDark = isSystemInDarkTheme()

    // Preferences for Best Score
    val prefs = remember { context.getSharedPreferences("secret_runner_prefs", Context.MODE_PRIVATE) }
    var bestScore by remember { mutableStateOf(prefs.getInt("best_score", 0)) }

    // Intercept back button to return safely to browser
    BackHandler {
        onClose()
    }

    // High-performance sound effects engine
    val soundEffects = remember { GameSoundEffects(context) }
    DisposableEffect(Unit) {
        onDispose {
            soundEffects.release()
        }
    }

    var gameState by remember { mutableStateOf(RunnerGameState.NOT_STARTED) }
    var distanceScore by remember { mutableFloatStateOf(0f) }
    var bonusScore by remember { mutableIntStateOf(0) }
    val totalScore = (distanceScore.toInt() + bonusScore)
    var isNewRecord by remember { mutableStateOf(false) }

    // Runner physics & parkour state
    var runnerY by remember { mutableFloatStateOf(0f) } // 0 = ground, >0 = air
    var runnerVelocity by remember { mutableFloatStateOf(0f) }
    var isGrounded by remember { mutableStateOf(true) }
    var canDoubleJump by remember { mutableStateOf(true) }
    var isDoubleJumping by remember { mutableStateOf(false) }
    var isSliding by remember { mutableStateOf(false) }
    var slideTimer by remember { mutableFloatStateOf(0f) }
    var isWallJumping by remember { mutableStateOf(false) }
    var wallJumpTimer by remember { mutableFloatStateOf(0f) }
    var runAnimationPhase by remember { mutableFloatStateOf(0f) }

    // Visual effect & dynamic close-camera state
    var speedLineAlpha by remember { mutableFloatStateOf(0f) }
    var cameraOffsetY by remember { mutableFloatStateOf(0f) }

    // World entities
    val obstacles = remember { mutableStateListOf<RunnerObstacle>() }
    val collectibles = remember { mutableStateListOf<RunnerCollectible>() }
    val scorePopups = remember { mutableStateListOf<ScorePopup>() }
    val particles = remember { mutableStateListOf<RunnerParticle>() }

    var nextSpawnDistance by remember { mutableFloatStateOf(420f) }
    var nextEntityId by remember { mutableLongStateOf(1L) }
    var groundOffset by remember { mutableFloatStateOf(0f) }
    var midgroundOffset by remember { mutableFloatStateOf(0f) }
    var bgParallaxOffset by remember { mutableFloatStateOf(0f) }

    // Physics constants (tuned for ultra-responsive close-camera parkour flow)
    val gravity = -1920f
    val jumpVelocity = 725f
    val doubleJumpVelocity = 645f
    val wallJumpBoostY = 780f
    val baseSpeed = 370f
    val slideDuration = 0.46f
    var lastObstacleType by remember { mutableStateOf<ObstacleType?>(null) }

    // Particle spawn helper
    val spawnParticles = { x: Float, y: Float, count: Int, baseColor: Color, speedScale: Float ->
        for (i in 0 until count) {
            val angle = Random.nextFloat() * 2f * PI.toFloat()
            val speed = (Random.nextFloat() * 120f + 40f) * speedScale
            particles.add(
                RunnerParticle(
                    id = nextEntityId++,
                    x = x,
                    y = y,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    color = baseColor,
                    alpha = 1f,
                    radius = Random.nextFloat() * 2.5f + 1.5f,
                    maxLife = Random.nextFloat() * 0.35f + 0.2f
                )
            )
        }
    }

    val jumpAction = {
        if (gameState == RunnerGameState.NOT_STARTED) {
            gameState = RunnerGameState.PLAYING
            runnerVelocity = jumpVelocity
            isGrounded = false
            canDoubleJump = true
            isSliding = false
            isDoubleJumping = false
            soundEffects.playJump()
            spawnParticles(105f, 10f, 6, Color(0xFF00E5FF), 0.8f)
        } else if (gameState == RunnerGameState.PLAYING) {
            // Cancel slide if jumping
            if (isSliding) {
                isSliding = false
                slideTimer = 0f
            }

            // Check if adjacent to a tall wall for Parkour WALL JUMP
            val playerX = 90f
            var triggeredWallJump = false
            val nearbyWall = obstacles.find { obs ->
                obs.type == ObstacleType.WALL_STRUCTURE &&
                        !obs.wallJumped &&
                        abs(obs.x - playerX) < 60f &&
                        runnerY > 15f && runnerY < obs.height + 30f
            }

            if (nearbyWall != null) {
                nearbyWall.wallJumped = true
                runnerVelocity = wallJumpBoostY
                isGrounded = false
                canDoubleJump = true
                isWallJumping = true
                wallJumpTimer = 0.35f
                triggeredWallJump = true
                soundEffects.playJump()
                spawnParticles(playerX + 22f, runnerY + 22f, 12, Color(0xFFFF6A00), 1.2f)
                scorePopups.add(
                    ScorePopup(
                        id = nextEntityId++,
                        text = "WALL LEAP!",
                        x = playerX,
                        y = runnerY + 48f,
                        color = Color(0xFFFF6A00)
                    )
                )
            }

            if (!triggeredWallJump) {
                if (isGrounded) {
                    runnerVelocity = jumpVelocity
                    isGrounded = false
                    canDoubleJump = true
                    isDoubleJumping = false
                    soundEffects.playJump()
                    spawnParticles(105f, 10f, 6, Color(0xFF00E5FF), 0.8f)
                } else if (canDoubleJump) {
                    runnerVelocity = doubleJumpVelocity
                    canDoubleJump = false
                    isDoubleJumping = true
                    soundEffects.playDoubleJump()
                    spawnParticles(105f, runnerY + 16f, 10, Color(0xFF00E5FF), 1.1f)
                    scorePopups.add(
                        ScorePopup(
                            id = nextEntityId++,
                            text = "AIR VAULT!",
                            x = 95f,
                            y = runnerY + 45f,
                            color = Color(0xFF00E5FF)
                        )
                    )
                }
            }
        }
    }

    val slideAction = {
        if (gameState == RunnerGameState.PLAYING && !isSliding) {
            isSliding = true
            slideTimer = slideDuration
            if (!isGrounded) {
                // Fast dive if in mid-air
                runnerVelocity = -850f
            }
            spawnParticles(100f, 8f, 8, Color(0xFFFFB300), 0.9f)
        }
    }

    val restartGame = {
        obstacles.clear()
        collectibles.clear()
        scorePopups.clear()
        particles.clear()
        distanceScore = 0f
        bonusScore = 0
        runnerY = 0f
        runnerVelocity = 0f
        cameraOffsetY = 0f
        isGrounded = true
        canDoubleJump = true
        isDoubleJumping = false
        isSliding = false
        slideTimer = 0f
        isWallJumping = false
        wallJumpTimer = 0f
        runAnimationPhase = 0f
        nextSpawnDistance = 540f
        lastObstacleType = null
        isNewRecord = false
        gameState = RunnerGameState.PLAYING
    }

    // Dynamic environmental biome changing every 200-400 meters
    val currentBiome = RunnerEnvironmentManager.getBiome(distanceScore, isDark)
    val bgColorStart = currentBiome.skyTop
    val bgColorEnd = currentBiome.skyBottom
    val groundColor = currentBiome.groundDirtColor
    val groundLineColor = currentBiome.groundLineColor
    val playerSuitColor = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B)
    val playerVisorColor = currentBiome.accentColor
    val shieldGlowColor = Color(0xFF00E5FF)
    val starColor = Color(0xFFFFD600)
    val coreColor = Color(0xFF10B981)
    val cardBg = if (isDark) Color(0xFF141E33) else Color(0xFFFFFFFF)
    val cardBorder = if (isDark) Color(0xFF243452) else Color(0xFFE2E8F0)
    val textP = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val textS = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    // Game loop running with 60FPS target
    LaunchedEffect(gameState) {
        if (gameState != RunnerGameState.PLAYING) return@LaunchedEffect

        var lastFrameTimeNanos = 0L

        while (isActive && gameState == RunnerGameState.PLAYING) {
            withFrameNanos { frameTimeNanos ->
                if (lastFrameTimeNanos == 0L) {
                    lastFrameTimeNanos = frameTimeNanos
                    return@withFrameNanos
                }

                val dt = ((frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f).coerceIn(0.001f, 0.04f)
                lastFrameTimeNanos = frameTimeNanos

                // Natural, smooth progressive speed scaling over time and distance
                val currentSpeedMultiplier = 1f + (distanceScore / 320f).coerceAtMost(2.2f)
                val currentSpeed = baseSpeed * currentSpeedMultiplier

                speedLineAlpha = if (currentSpeedMultiplier > 1.35f) {
                    ((currentSpeedMultiplier - 1.35f) / 0.5f).coerceIn(0f, 0.35f)
                } else 0f

                // Advance distance score
                distanceScore += currentSpeed * dt * 0.1f
                val currentTotalScore = (distanceScore.toInt() + bonusScore)
                if (currentTotalScore > bestScore) {
                    if (!isNewRecord && bestScore > 0) {
                        soundEffects.playClapping()
                        scorePopups.add(
                            ScorePopup(
                                id = nextEntityId++,
                                text = "NEW RECORD!",
                                x = 100f,
                                y = 120f,
                                color = Color(0xFFFFD700)
                            )
                        )
                    }
                    bestScore = currentTotalScore
                    isNewRecord = true
                    prefs.edit().putInt("best_score", bestScore).apply()
                }

                // Slide Timer
                if (isSliding) {
                    slideTimer -= dt
                    if (slideTimer <= 0f) {
                        isSliding = false
                        slideTimer = 0f
                    }
                    if (isGrounded && Random.nextFloat() < 0.35f) {
                        spawnParticles(85f + Random.nextFloat() * 15f, 4f, 2, Color(0xFFFFB300), 0.7f)
                    }
                }

                // Wall Jump Timer
                if (isWallJumping) {
                    wallJumpTimer -= dt
                    if (wallJumpTimer <= 0f) {
                        isWallJumping = false
                    }
                }

                // Physics: Player Y & Velocity
                runnerVelocity += gravity * dt
                runnerY += runnerVelocity * dt
                if (runnerY <= 0f) {
                    val wasAirborne = !isGrounded
                    runnerY = 0f
                    runnerVelocity = 0f
                    isGrounded = true
                    canDoubleJump = true
                    isDoubleJumping = false
                    isWallJumping = false

                    if (wasAirborne) {
                        spawnParticles(105f, 4f, 5, Color(0xFF94A3B8), 0.6f)
                    }
                } else {
                    isGrounded = false
                }

                // Dynamic Smooth Camera Tracking on Jump/Leap
                val targetCameraY = if (runnerY > 55f) (runnerY - 55f) * 0.40f else 0f
                cameraOffsetY += (targetCameraY - cameraOffsetY) * (dt * 8.5f).coerceAtMost(1f)

                // Run leg stride animation
                if (isGrounded && !isSliding) {
                    runAnimationPhase += dt * (19f * currentSpeedMultiplier)
                }

                // Parallax offsets
                groundOffset = (groundOffset + currentSpeed * dt) % 40f
                midgroundOffset = (midgroundOffset + currentSpeed * 0.45f * dt) % 180f
                bgParallaxOffset = (bgParallaxOffset + currentSpeed * 0.18f * dt) % 240f

                // Spawning Obstacles & Collectibles (Generous reaction time & jump landing recovery)
                nextSpawnDistance -= currentSpeed * dt
                if (nextSpawnDistance <= 0f) {
                    val spawnX = 760f
                    val roll = Random.nextFloat()

                    if (roll < 0.58f) {
                        // Spawn Obstacle (avoid back-to-back tall obstacles)
                        val typeChoice = when {
                            distanceScore < 90f -> ObstacleType.LOW_VAULT_BOX
                            lastObstacleType == ObstacleType.WALL_STRUCTURE || lastObstacleType == ObstacleType.SECURITY_LASER_GATE -> {
                                if (Random.nextBoolean()) ObstacleType.LOW_VAULT_BOX else ObstacleType.HIGH_OVERHEAD_LASER
                            }
                            else -> ObstacleType.entries.random()
                        }
                        lastObstacleType = typeChoice

                        val (w, h, yOff) = when (typeChoice) {
                            ObstacleType.LOW_VAULT_BOX -> Triple(44f, 36f, 0f)
                            ObstacleType.HIGH_OVERHEAD_LASER -> Triple(58f, 26f, 44f) // Above ground -> must slide
                            ObstacleType.CYBER_SPIRE -> Triple(36f, 54f, 0f)
                            ObstacleType.WALL_STRUCTURE -> Triple(44f, 88f, 0f) // Tall wall -> wall jump or double jump
                            ObstacleType.SECURITY_LASER_GATE -> Triple(48f, 52f, 0f)
                        }

                        obstacles.add(
                            RunnerObstacle(
                                id = nextEntityId++,
                                x = spawnX,
                                yOffset = yOff,
                                width = w,
                                height = h,
                                type = typeChoice
                            )
                        )
                    } else {
                        // Spawn Collectible
                        lastObstacleType = null
                        val cRand = Random.nextFloat()
                        val (cType, pts, yPos) = when {
                            cRand < 0.50f -> Triple(CollectibleType.SHIELD_TOKEN, 25, if (Random.nextBoolean()) 22f else 72f)
                            cRand < 0.85f -> Triple(CollectibleType.PRIVACY_STAR, 50, if (Random.nextBoolean()) 38f else 92f)
                            else -> Triple(CollectibleType.ENCRYPTED_CORE, 100, 82f)
                        }
                        collectibles.add(
                            RunnerCollectible(
                                id = nextEntityId++,
                                x = spawnX,
                                y = yPos,
                                type = cType,
                                points = pts
                            )
                        )
                    }

                    // Generous interval: 520px-740px giving comfortable landing recovery time
                    val extraLandingBuffer = if (lastObstacleType == ObstacleType.WALL_STRUCTURE || lastObstacleType == ObstacleType.SECURITY_LASER_GATE) 160f else 0f
                    nextSpawnDistance = (520f * currentSpeedMultiplier) + Random.nextFloat() * 180f + extraLandingBuffer
                }

                // Update Obstacles & Collision Check
                val playerX = 90f
                val playerWidth = 32f
                val playerHeight = if (isSliding) 24f else 50f
                val playerBottom = runnerY
                val playerTop = runnerY + playerHeight

                val obsIterator = obstacles.iterator()
                while (obsIterator.hasNext()) {
                    val obs = obsIterator.next()
                    obs.x -= currentSpeed * dt

                    val obsLeft = obs.x + 4.5f
                    val obsRight = obs.x + obs.width - 4.5f
                    val obsBottom = obs.yOffset
                    val obsTop = obs.yOffset + obs.height

                    // AABB Collision check with fair padding
                    val isOverlapX = (playerX + playerWidth > obsLeft) && (playerX < obsRight)
                    val isOverlapY = (playerTop > obsBottom + 3.5f) && (playerBottom < obsTop - 3.5f)

                    if (isOverlapX && isOverlapY) {
                        gameState = RunnerGameState.GAME_OVER
                        soundEffects.playHit()
                        if (isNewRecord) {
                            soundEffects.playClapping()
                        } else {
                            soundEffects.playGameOver()
                        }
                        spawnParticles(playerX + 16f, playerBottom + 22f, 25, Color(0xFFFF5252), 1.4f)
                        break
                    }

                    if (obs.x < -120f) {
                        obsIterator.remove()
                    }
                }

                // Update Collectibles
                val colIterator = collectibles.iterator()
                while (colIterator.hasNext()) {
                    val col = colIterator.next()
                    col.x -= currentSpeed * dt

                    if (!col.isCollected) {
                        val colCenterX = col.x + 15f
                        val colCenterY = col.y + 15f
                        val playerCenterX = playerX + playerWidth / 2f
                        val playerCenterY = playerBottom + playerHeight / 2f

                        val dx = abs(colCenterX - playerCenterX)
                        val dy = abs(colCenterY - playerCenterY)

                        if (dx < 30f && dy < 36f) {
                            col.isCollected = true
                            soundEffects.playCollect()
                            bonusScore += col.points
                            val colColor = when (col.type) {
                                CollectibleType.SHIELD_TOKEN -> shieldGlowColor
                                CollectibleType.PRIVACY_STAR -> starColor
                                CollectibleType.ENCRYPTED_CORE -> coreColor
                            }
                            spawnParticles(colCenterX, colCenterY, 12, colColor, 1.2f)
                            scorePopups.add(
                                ScorePopup(
                                    id = nextEntityId++,
                                    text = "+${col.points}",
                                    x = col.x,
                                    y = col.y + 20f,
                                    color = colColor
                                )
                            )
                        }
                    }

                    if (col.x < -100f || (col.isCollected && col.x < playerX - 40f)) {
                        colIterator.remove()
                    }
                }

                // Update Floating Popups
                val popupIterator = scorePopups.iterator()
                while (popupIterator.hasNext()) {
                    val popup = popupIterator.next()
                    popup.y += dt * 55f
                    popup.alpha -= dt * 1.6f
                    if (popup.alpha <= 0f) {
                        popupIterator.remove()
                    }
                }

                // Update Particle Pool
                val particleIterator = particles.iterator()
                while (particleIterator.hasNext()) {
                    val p = particleIterator.next()
                    p.age += dt
                    p.x += p.vx * dt
                    p.y += p.vy * dt
                    p.alpha = (1f - (p.age / p.maxLife)).coerceIn(0f, 1f)
                    if (p.age >= p.maxLife) {
                        particleIterator.remove()
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(bgColorStart, bgColorEnd)
                )
            )
            .pointerInput(gameState) {
                detectTapGestures { offset ->
                    // Tap right/upper 60% = Jump / Wall Jump, tap lower-left = Slide
                    if (offset.y > size.height * 0.70f && offset.x < size.width * 0.45f) {
                        slideAction()
                    } else {
                        jumpAction()
                    }
                }
            }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = constraints.maxWidth.toFloat()
            val canvasHeight = constraints.maxHeight.toFloat()
            val groundY = canvasHeight * 0.68f

            // Game Graphics Canvas with Close-Camera Parkour Scaling
            Canvas(modifier = Modifier.fillMaxSize()) {
                translate(top = cameraOffsetY) {
                    scale(
                        scaleX = 2.05f,
                        scaleY = 2.05f,
                        pivot = Offset(110f, groundY - 35f)
                    ) {
                        // 1. Layer 1: Dynamic Parallax Distant Skyline & Celestial Body
                        drawDynamicSkyline(
                            width = canvasWidth,
                            groundY = groundY,
                            offset = bgParallaxOffset,
                            biome = currentBiome,
                            isDark = isDark
                        )

                        // 2. Layer 2: Dynamic Midground Facility Corridor
                        drawDynamicMidground(
                            width = canvasWidth,
                            groundY = groundY,
                            offset = midgroundOffset,
                            biome = currentBiome
                        )

                        // 3. Layer 3: Speed lines at high velocity
                        if (speedLineAlpha > 0f) {
                            drawSpeedStreaks(
                                width = canvasWidth,
                                groundY = groundY,
                                alpha = speedLineAlpha,
                                isDark = isDark
                            )
                        }

                        // 4. Ground Foundation: Dynamic Biome Ground with glowing track
                        drawDynamicGround(
                            canvasWidth = canvasWidth,
                            canvasHeight = canvasHeight,
                            groundY = groundY,
                            offset = groundOffset,
                            biome = currentBiome
                        )

                        // 5. Draw Obstacles: High-polish beautiful cyber obstacles
                        obstacles.forEach { obs ->
                            drawBeautifulObstacle(
                                obstacle = obs,
                                groundY = groundY,
                                biome = currentBiome,
                                isDark = isDark
                            )
                        }

                        // 6. Draw Collectibles: Kenney gem_blue, coin_gold, key_blue
                        collectibles.forEach { col ->
                            if (!col.isCollected) {
                                drawKenneyCollectible(
                                    collectible = col,
                                    groundY = groundY
                                )
                            }
                        }

                        // 7. Draw Active Particles
                        particles.forEach { p ->
                            drawCircle(
                                color = p.color.copy(alpha = p.alpha),
                                radius = p.radius,
                                center = Offset(p.x, groundY - p.y)
                            )
                        }

                        // 8. Draw Kenney Purple Character (All 7 animation states connected)
                        drawKenneyPurpleCharacter(
                            playerX = 90f,
                            groundY = groundY,
                            runnerY = runnerY,
                            gameState = gameState,
                            isGrounded = isGrounded,
                            isSliding = isSliding,
                            isDoubleJumping = isDoubleJumping,
                            isWallJumping = isWallJumping,
                            phase = runAnimationPhase,
                            shieldColor = shieldGlowColor,
                            isDark = isDark
                        )
                    }
                }
            }

            // Top HUD Bar - safely padded below status bar & camera notch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Return to Browser
                Surface(
                    onClick = onClose,
                    shape = RoundedCornerShape(12.dp),
                    color = cardBg.copy(alpha = 0.92f),
                    border = BorderStroke(1.dp, cardBorder),
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Browser",
                            tint = textP,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Back",
                            color = textP,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Scores Display
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Best Score Pill
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = cardBg.copy(alpha = 0.88f),
                        border = BorderStroke(1.dp, cardBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = "Best Score",
                                tint = Color(0xFFFFB300),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "BEST $bestScore",
                                color = textS,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Live Score Pill
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = playerVisorColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.5.dp, playerVisorColor)
                    ) {
                        Text(
                            text = "SCORE $totalScore",
                            color = if (isDark) Color.White else Color(0xFFD84315),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // On-Screen Parkour Touch Action Controls (Ergonomic, Transparent)
            if (gameState == RunnerGameState.PLAYING) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Slide Action Pill
                    Surface(
                        onClick = { slideAction() },
                        shape = RoundedCornerShape(16.dp),
                        color = (if (isSliding) playerVisorColor else cardBg).copy(alpha = 0.85f),
                        border = BorderStroke(1.5.dp, if (isSliding) playerVisorColor else cardBorder),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "👟 SLIDE",
                                color = if (isSliding) Color.White else textP,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    // Jump / Wall Leap Pill
                    Surface(
                        onClick = { jumpAction() },
                        shape = RoundedCornerShape(16.dp),
                        color = (if (!isGrounded) Color(0xFF00E5FF) else playerVisorColor).copy(alpha = 0.88f),
                        border = BorderStroke(1.5.dp, if (!isGrounded) Color(0xFF00E5FF) else playerVisorColor),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 22.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (!isGrounded && canDoubleJump) "⚡ DOUBLE JUMP" else "🚀 JUMP",
                                color = if (!isGrounded) Color(0xFF090D16) else Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }

            // Start Screen Overlay
            if (gameState == RunnerGameState.NOT_STARTED) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = cardBg.copy(alpha = 0.96f),
                        border = BorderStroke(1.5.dp, cardBorder),
                        shadowElevation = 10.dp,
                        modifier = Modifier.widthIn(max = 390.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(26.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(playerVisorColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = "Secret Runner",
                                    tint = playerVisorColor,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "SECRET RUNNER",
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Black,
                                color = textP,
                                letterSpacing = 1.2.sp
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "PARKOUR ESCAPE",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = playerVisorColor,
                                letterSpacing = 2.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "RUN. ESCAPE. STAY PRIVATE.",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textS,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Parkour Moves Guide Card
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isDark) Color(0xFF0F172A) else Color(0xFFF1F5F9),
                                border = BorderStroke(1.dp, cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "• Tap JUMP for jump & mid-air double jump",
                                        color = textP,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "• Tap JUMP near tall walls for a WALL LEAP",
                                        color = textP,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "• Tap SLIDE to duck under overhead lasers",
                                        color = textP,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Button(
                                onClick = { jumpAction() },
                                colors = ButtonDefaults.buttonColors(containerColor = playerVisorColor),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "START ESCAPE",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            // Game Over Overlay
            if (gameState == RunnerGameState.GAME_OVER) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = cardBg,
                        border = BorderStroke(1.5.dp, cardBorder),
                        shadowElevation = 14.dp,
                        modifier = Modifier.widthIn(max = 380.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(26.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Run Over!",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                color = textP
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Nice try, Buddy.",
                                fontSize = 14.sp,
                                color = textS
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            // Score Breakdown Card
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isDark) Color(0xFF0F172A) else Color(0xFFF1F5F9),
                                border = BorderStroke(1.dp, cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    if (isNewRecord) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFFFFB300).copy(alpha = 0.2f),
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        ) {
                                            Text(
                                                text = "✨ NEW BEST SCORE! ✨",
                                                color = Color(0xFFFFB300),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Score", color = textS, fontSize = 13.sp)
                                        Text(
                                            "$totalScore",
                                            color = textP,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Best Record", color = textS, fontSize = 13.sp)
                                        Text(
                                            "$bestScore",
                                            color = Color(0xFFFFB300),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Action Buttons
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = { restartGame() },
                                    colors = ButtonDefaults.buttonColors(containerColor = playerVisorColor),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "RUN AGAIN",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }

                                OutlinedButton(
                                    onClick = onClose,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, cardBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    Text(
                                        text = "BACK TO BROWSER",
                                        color = textP,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 1. Parallax Distant Cyber Skyline & Hills (Kenney Backgrounds)
private fun DrawScope.drawCyberSkyline(
    width: Float,
    groundY: Float,
    offset: Float,
    isDark: Boolean
) {
    val hillColor = if (isDark) Color(0xFF101728) else Color(0xFFD8E1ED)
    val cloudColor = if (isDark) Color(0xFF1A263D).copy(alpha = 0.5f) else Color(0xFFE2E8F0).copy(alpha = 0.65f)

    // Floating Kenney clouds
    val cloudSpacing = 160f
    var cx = -offset * 0.5f
    var cIdx = 0
    while (cx < width + cloudSpacing) {
        val cy = groundY - 140f - ((cIdx * 29) % 55)
        val cSize = 44f + ((cIdx * 17) % 24)
        drawCircle(
            color = cloudColor,
            radius = cSize * 0.35f,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = cloudColor,
            radius = cSize * 0.45f,
            center = Offset(cx + cSize * 0.3f, cy - 4f)
        )
        drawCircle(
            color = cloudColor,
            radius = cSize * 0.35f,
            center = Offset(cx + cSize * 0.6f, cy)
        )
        cx += cloudSpacing
        cIdx++
    }

    // Kenney rolling background hills
    val hillWidth = 90f
    val spacing = 75f
    var x = -offset
    var index = 0
    while (x < width + spacing) {
        val hillHeight = ((index * 37) % 95 + 40).toFloat()
        val path = Path().apply {
            moveTo(x - 20f, groundY)
            quadraticTo(x + hillWidth * 0.5f, groundY - hillHeight, x + hillWidth + 20f, groundY)
            close()
        }
        drawPath(path, hillColor)
        x += spacing
        index++
    }
}

// 2. Midground Facility Corridor & Platformer Pillars
private fun DrawScope.drawMidgroundCorridor(
    width: Float,
    groundY: Float,
    offset: Float,
    isDark: Boolean
) {
    val pillarColor = if (isDark) Color(0xFF16233B) else Color(0xFFCBD6E4)
    val beaconColor = if (isDark) Color(0xFF00E5FF).copy(alpha = 0.35f) else Color(0xFF0284C7).copy(alpha = 0.3f)

    val pWidth = 24f
    val spacing = 160f
    var x = -offset
    var idx = 0
    while (x < width + spacing) {
        val pH = 110f
        drawRoundRect(
            color = pillarColor,
            topLeft = Offset(x, groundY - pH),
            size = Size(pWidth, pH),
            cornerRadius = CornerRadius(4f, 4f)
        )
        // Neon beacon dot
        drawCircle(
            color = beaconColor,
            radius = 4f,
            center = Offset(x + pWidth / 2f, groundY - pH + 12f)
        )
        x += spacing
        idx++
    }
}

// 3. High speed streaks
private fun DrawScope.drawSpeedStreaks(
    width: Float,
    groundY: Float,
    alpha: Float,
    isDark: Boolean
) {
    val streakColor = (if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7)).copy(alpha = alpha)
    for (i in 0..4) {
        val y = groundY - 40f - (i * 35f)
        val startX = (i * 90f) % width
        val length = 120f + (i * 20f)
        drawLine(
            color = streakColor,
            start = Offset(startX, y),
            end = Offset(startX + length, y),
            strokeWidth = 1.5f
        )
    }
}

// 4. Ground Foundation: Kenney Terrain Grass & Stone Blocks
private fun DrawScope.drawKenneyTerrainGround(
    canvasWidth: Float,
    canvasHeight: Float,
    groundY: Float,
    offset: Float,
    isDark: Boolean
) {
    val dirtColor = if (isDark) Color(0xFF1E2638) else Color(0xFF8D6E63)
    val grassTopColor = if (isDark) Color(0xFF10B981) else Color(0xFF4CAF50)
    val grassEdgeColor = if (isDark) Color(0xFF059669) else Color(0xFF388E3C)
    val stoneColor = if (isDark) Color(0xFF151C2C) else Color(0xFF6D4C41)

    // Base earth dirt mass
    drawRect(
        color = dirtColor,
        topLeft = Offset(-100f, groundY),
        size = Size(canvasWidth + 200f, canvasHeight - groundY + 100f)
    )

    // Sub-surface stone strata line
    drawRect(
        color = stoneColor,
        topLeft = Offset(-100f, groundY + 22f),
        size = Size(canvasWidth + 200f, canvasHeight - groundY + 80f)
    )

    // Kenney grass block top cap
    drawRect(
        color = grassTopColor,
        topLeft = Offset(-100f, groundY),
        size = Size(canvasWidth + 200f, 9f)
    )
    // Darker grass trim
    drawLine(
        color = grassEdgeColor,
        start = Offset(-100f, groundY + 9f),
        end = Offset(canvasWidth + 200f, groundY + 9f),
        strokeWidth = 2f
    )

    // Kenney scalloped grass fringe tufts
    var tuftX = -offset - 100f
    while (tuftX < canvasWidth + 140f) {
        val tuftPath = Path().apply {
            moveTo(tuftX, groundY + 9f)
            lineTo(tuftX + 7f, groundY + 16f)
            lineTo(tuftX + 14f, groundY + 9f)
            close()
        }
        drawPath(tuftPath, grassEdgeColor)
        tuftX += 24f
    }
}

// 5. Draw Kenney Obstacles (block_spikes, saw_a/b, door_closed, spikes)
private fun DrawScope.drawKenneyObstacle(
    obstacle: RunnerObstacle,
    groundY: Float,
    accentColor: Color,
    isDark: Boolean
) {
    val obsBottom = groundY - obstacle.yOffset
    val obsTop = obsBottom - obstacle.height

    when (obstacle.type) {
        ObstacleType.LOW_VAULT_BOX -> {
            // Kenney block_spikes: Stone crate block with sharp metal spikes on top
            val blockTop = obsTop + 14f
            val blockHeight = obstacle.height - 14f

            // Stone base block
            drawRoundRect(
                color = if (isDark) Color(0xFF334155) else Color(0xFF64748B),
                topLeft = Offset(obstacle.x, blockTop),
                size = Size(obstacle.width, blockHeight),
                cornerRadius = CornerRadius(4f, 4f)
            )
            // Block bevel inner
            drawRoundRect(
                color = if (isDark) Color(0xFF1E293B) else Color(0xFF475569),
                topLeft = Offset(obstacle.x + 3f, blockTop + 3f),
                size = Size(obstacle.width - 6f, blockHeight - 6f),
                cornerRadius = CornerRadius(2f, 2f)
            )

            // 3 Sharp silver steel spikes on top (Kenney style)
            val spikeCount = 3
            val spikeW = obstacle.width / spikeCount
            for (i in 0 until spikeCount) {
                val sx = obstacle.x + i * spikeW
                val spikePath = Path().apply {
                    moveTo(sx, blockTop)
                    lineTo(sx + spikeW * 0.5f, obsTop)
                    lineTo(sx + spikeW, blockTop)
                    close()
                }
                drawPath(spikePath, Color(0xFFCBD5E1))
                // Spike shadow / highlight facet
                val highlightPath = Path().apply {
                    moveTo(sx, blockTop)
                    lineTo(sx + spikeW * 0.5f, obsTop)
                    lineTo(sx + spikeW * 0.5f, blockTop)
                    close()
                }
                drawPath(highlightPath, Color(0xFFFFFFFF).copy(alpha = 0.6f))
                drawPath(spikePath, Color(0xFF475569), style = Stroke(width = 1f))
            }
        }

        ObstacleType.HIGH_OVERHEAD_LASER -> {
            // Kenney saw_a / saw_b: Rotating circular steel saw with security hazard beam
            val centerX = obstacle.x + obstacle.width * 0.5f
            val centerY = obsTop + obstacle.height * 0.5f
            val radius = obstacle.height * 0.5f

            // Rotating saw blade teeth (Kenney saw_a & saw_b)
            val toothCount = 8
            val angleOffset = (obstacle.x * 0.08f) % (2f * PI.toFloat())
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

            drawPath(sawPath, Color(0xFFE2E8F0))
            drawPath(sawPath, Color(0xFF64748B), style = Stroke(width = 1.2f))

            // Center steel hub and red indicator dot
            drawCircle(color = Color(0xFF475569), radius = radius * 0.38f, center = Offset(centerX, centerY))
            drawCircle(color = Color(0xFFFF1744), radius = radius * 0.18f, center = Offset(centerX, centerY))

            // Overhead mounting beam
            drawLine(
                color = if (isDark) Color(0xFF334155) else Color(0xFF94A3B8),
                start = Offset(centerX, obsTop - 15f),
                end = Offset(centerX, obsTop),
                strokeWidth = 3f
            )
        }

        ObstacleType.CYBER_SPIRE -> {
            // Kenney ground spikes: Triple metallic ground spikes
            val spikeCount = 2
            val spikeW = obstacle.width / spikeCount
            for (i in 0 until spikeCount) {
                val sx = obstacle.x + i * spikeW
                val path = Path().apply {
                    moveTo(sx, obsBottom)
                    lineTo(sx + spikeW * 0.5f, obsTop)
                    lineTo(sx + spikeW, obsBottom)
                    close()
                }
                drawPath(path, Color(0xFFE2E8F0))
                // Facet bevel
                val facetPath = Path().apply {
                    moveTo(sx, obsBottom)
                    lineTo(sx + spikeW * 0.5f, obsTop)
                    lineTo(sx + spikeW * 0.5f, obsBottom)
                    close()
                }
                drawPath(facetPath, Color(0xFFFFFFFF).copy(alpha = 0.5f))
                drawPath(path, Color(0xFF475569), style = Stroke(width = 1.2f))
            }
        }

        ObstacleType.WALL_STRUCTURE -> {
            // Kenney door_closed & stone wall block: Wooden platformer door with iron studs
            drawRoundRect(
                color = if (isDark) Color(0xFF1E293B) else Color(0xFF475569),
                topLeft = Offset(obstacle.x, obsTop),
                size = Size(obstacle.width, obstacle.height),
                cornerRadius = CornerRadius(4f, 4f)
            )
            // Wooden door inlay
            drawRoundRect(
                color = Color(0xFF795548),
                topLeft = Offset(obstacle.x + 4f, obsTop + 6f),
                size = Size(obstacle.width - 8f, obstacle.height - 6f),
                cornerRadius = CornerRadius(3f, 3f)
            )
            // Iron reinforcement bands
            drawLine(
                color = Color(0xFF37474F),
                start = Offset(obstacle.x + 4f, obsTop + 18f),
                end = Offset(obstacle.x + obstacle.width - 4f, obsTop + 18f),
                strokeWidth = 3.5f
            )
            drawLine(
                color = Color(0xFF37474F),
                start = Offset(obstacle.x + 4f, obsTop + 44f),
                end = Offset(obstacle.x + obstacle.width - 4f, obsTop + 44f),
                strokeWidth = 3.5f
            )
            // Keyhole / handle
            drawCircle(
                color = Color(0xFFFFD600),
                radius = 2.5f,
                center = Offset(obstacle.x + obstacle.width - 9f, obsTop + 32f)
            )
            // Parkour neon grip ledge
            drawLine(
                color = accentColor,
                start = Offset(obstacle.x + 2f, obsTop + 2f),
                end = Offset(obstacle.x + obstacle.width - 2f, obsTop + 2f),
                strokeWidth = 3f
            )
        }

        ObstacleType.SECURITY_LASER_GATE -> {
            // Security laser gate with rotating mini hazard blade
            drawRoundRect(
                color = if (isDark) Color(0xFF334155) else Color(0xFF64748B),
                topLeft = Offset(obstacle.x, obsTop),
                size = Size(7f, obstacle.height),
                cornerRadius = CornerRadius(3f, 3f)
            )
            drawRoundRect(
                color = if (isDark) Color(0xFF334155) else Color(0xFF64748B),
                topLeft = Offset(obstacle.x + obstacle.width - 7f, obsTop),
                size = Size(7f, obstacle.height),
                cornerRadius = CornerRadius(3f, 3f)
            )
            // Red laser beams
            drawLine(
                color = Color(0xFFFF1744),
                start = Offset(obstacle.x + 7f, obsTop + 12f),
                end = Offset(obstacle.x + obstacle.width - 7f, obsTop + 12f),
                strokeWidth = 3.5f
            )
            drawLine(
                color = Color(0xFFFF1744),
                start = Offset(obstacle.x + 7f, obsTop + 30f),
                end = Offset(obstacle.x + obstacle.width - 7f, obsTop + 30f),
                strokeWidth = 3f
            )
        }
    }
}

// 6. Draw Kenney Collectibles (gem_blue, coin_gold, key_blue)
private fun DrawScope.drawKenneyCollectible(
    collectible: RunnerCollectible,
    groundY: Float
) {
    val center = Offset(collectible.x + 14f, groundY - collectible.y - 14f)

    when (collectible.type) {
        CollectibleType.SHIELD_TOKEN -> {
            // Kenney gem_blue.svg: Faceted blue crystal diamond
            drawCircle(
                color = Color(0xFF00E5FF).copy(alpha = 0.25f),
                radius = 16f,
                center = center
            )
            val gemPath = Path().apply {
                moveTo(center.x, center.y - 12f)
                lineTo(center.x + 11f, center.y - 4f)
                lineTo(center.x + 9f, center.y + 7f)
                lineTo(center.x, center.y + 12f)
                lineTo(center.x - 9f, center.y + 7f)
                lineTo(center.x - 11f, center.y - 4f)
                close()
            }
            drawPath(gemPath, Color(0xFF0288D1))

            // Upper facet highlight
            val topFacet = Path().apply {
                moveTo(center.x, center.y - 12f)
                lineTo(center.x + 11f, center.y - 4f)
                lineTo(center.x, center.y)
                lineTo(center.x - 11f, center.y - 4f)
                close()
            }
            drawPath(topFacet, Color(0xFF4FC3F7))

            // Diamond shine sparkle
            drawCircle(color = Color.White, radius = 2f, center = Offset(center.x - 3f, center.y - 5f))
            drawPath(gemPath, Color(0xFF01579B), style = Stroke(width = 1.2f))
        }

        CollectibleType.PRIVACY_STAR -> {
            // Kenney coin_gold.svg: Gold coin with inner star emblem
            drawCircle(
                color = Color(0xFFFFD600).copy(alpha = 0.3f),
                radius = 15f,
                center = center
            )
            // Outer golden disc
            drawCircle(color = Color(0xFFF59E0B), radius = 12f, center = center)
            // Inner golden rim
            drawCircle(color = Color(0xFFFBBF24), radius = 9.5f, center = center)

            // Inner star
            val starPath = Path().apply {
                moveTo(center.x, center.y - 6f)
                lineTo(center.x + 2f, center.y - 2f)
                lineTo(center.x + 6f, center.y - 2f)
                lineTo(center.x + 3f, center.y + 1f)
                lineTo(center.x + 4f, center.y + 5f)
                lineTo(center.x, center.y + 2.5f)
                lineTo(center.x - 4f, center.y + 5f)
                lineTo(center.x - 3f, center.y + 1f)
                lineTo(center.x - 6f, center.y - 2f)
                lineTo(center.x - 2f, center.y - 2f)
                close()
            }
            drawPath(starPath, Color(0xFFFFFBEB))
            drawCircle(color = Color(0xFFD97706), radius = 12f, style = Stroke(width = 1.2f))
        }

        CollectibleType.ENCRYPTED_CORE -> {
            // Kenney key_blue.svg: Classic blue security key
            drawCircle(
                color = Color(0xFF38BDF8).copy(alpha = 0.25f),
                radius = 16f,
                center = center
            )
            // Circular Bow Head
            drawCircle(
                color = Color(0xFF0284C7),
                radius = 7.5f,
                center = Offset(center.x - 5f, center.y - 5f)
            )
            drawCircle(
                color = Color.White,
                radius = 3f,
                center = Offset(center.x - 5f, center.y - 5f)
            )
            // Key shaft
            drawLine(
                color = Color(0xFF0284C7),
                start = Offset(center.x - 2f, center.y - 2f),
                end = Offset(center.x + 9f, center.y + 9f),
                strokeWidth = 3.5f
            )
            // Key teeth
            drawLine(
                color = Color(0xFF0284C7),
                start = Offset(center.x + 6f, center.y + 6f),
                end = Offset(center.x + 9f, center.y + 3f),
                strokeWidth = 2.5f
            )
            drawLine(
                color = Color(0xFF0284C7),
                start = Offset(center.x + 8.5f, center.y + 8.5f),
                end = Offset(center.x + 11.5f, center.y + 5.5f),
                strokeWidth = 2.5f
            )
        }
    }
}

// 7. Draw Kenney Purple Character (Faithful 7 Animation States: idle, walk_a, walk_b, jump, double jump, duck/slide, climb_a/b, hit)
private fun DrawScope.drawKenneyPurpleCharacter(
    playerX: Float,
    groundY: Float,
    runnerY: Float,
    gameState: RunnerGameState,
    isGrounded: Boolean,
    isSliding: Boolean,
    isDoubleJumping: Boolean,
    isWallJumping: Boolean,
    phase: Float,
    shieldColor: Color,
    isDark: Boolean
) {
    // Kenney Purple Character Palette:
    val purpleBody = Color(0xFF8E44AD)
    val purpleDarkOutline = Color(0xFF512E5F)
    val purpleBelly = Color(0xFFBB8FCE)
    val pinkAntennaeTip = Color(0xFFF48FB1)
    val darkShoe = Color(0xFF2C3E50)
    val shoeSole = Color(0xFFE2E8F0)

    // A. STATE: character_purple_duck (SLIDE / DUCK)
    if (isSliding) {
        val slideTop = groundY - 24f
        val slideW = 44f
        val slideH = 20f

        // Flattened purple body
        drawRoundRect(
            color = purpleBody,
            topLeft = Offset(playerX - 8f, slideTop + 4f),
            size = Size(slideW, slideH),
            cornerRadius = CornerRadius(9f, 9f)
        )
        // Belly patch
        drawRoundRect(
            color = purpleBelly,
            topLeft = Offset(playerX + 2f, slideTop + 7f),
            size = Size(20f, 10f),
            cornerRadius = CornerRadius(4f, 4f)
        )
        // Head tilted forward
        drawCircle(
            color = purpleBody,
            radius = 9f,
            center = Offset(playerX + 32f, slideTop + 10f)
        )
        // Determined squinting eye
        drawLine(
            color = Color.White,
            start = Offset(playerX + 30f, slideTop + 9f),
            end = Offset(playerX + 36f, slideTop + 9f),
            strokeWidth = 3f
        )
        drawCircle(
            color = darkShoe,
            radius = 1.5f,
            center = Offset(playerX + 34f, slideTop + 9f)
        )
        // Horns tilted back
        drawCircle(
            color = pinkAntennaeTip,
            radius = 3f,
            center = Offset(playerX + 24f, slideTop + 3f)
        )
        // Trailing slide shoe
        drawRoundRect(
            color = darkShoe,
            topLeft = Offset(playerX - 12f, slideTop + 13f),
            size = Size(10f, 6f),
            cornerRadius = CornerRadius(2.5f, 2.5f)
        )
        // Body outline
        drawRoundRect(
            color = purpleDarkOutline,
            topLeft = Offset(playerX - 8f, slideTop + 4f),
            size = Size(slideW, slideH),
            cornerRadius = CornerRadius(9f, 9f),
            style = Stroke(width = 1.5f)
        )
        return
    }

    val playerY = groundY - runnerY - 50f
    val centerX = playerX + 16f
    val centerY = playerY + 22f

    // B. STATE: character_purple_hit (GAME OVER)
    if (gameState == RunnerGameState.GAME_OVER) {
        // Tumbled backward pose with dizzy X eyes and stars
        val hitY = playerY + 8f

        // Body tilted back
        drawRoundRect(
            color = purpleBody,
            topLeft = Offset(playerX + 2f, hitY + 12f),
            size = Size(24f, 28f),
            cornerRadius = CornerRadius(10f, 10f)
        )
        // Head
        drawCircle(
            color = purpleBody,
            radius = 12f,
            center = Offset(centerX + 2f, hitY + 10f)
        )
        // Dizzy X eyes
        val eyeX = centerX + 4f
        val eyeY = hitY + 9f
        drawLine(color = Color(0xFFFF1744), start = Offset(eyeX - 4f, eyeY - 4f), end = Offset(eyeX + 4f, eyeY + 4f), strokeWidth = 2.5f)
        drawLine(color = Color(0xFFFF1744), start = Offset(eyeX - 4f, eyeY + 4f), end = Offset(eyeX + 4f, eyeY - 4f), strokeWidth = 2.5f)

        // Dizzy wobbly mouth
        val mouthPath = Path().apply {
            moveTo(eyeX - 4f, eyeY + 8f)
            lineTo(eyeX, eyeY + 6f)
            lineTo(eyeX + 4f, eyeY + 8f)
        }
        drawPath(mouthPath, purpleDarkOutline, style = Stroke(width = 2f))

        // Impact dizziness stars
        drawCircle(color = Color(0xFFFFD600), radius = 2.5f, center = Offset(centerX - 10f, hitY - 2f))
        drawCircle(color = Color(0xFFFFD600), radius = 3.5f, center = Offset(centerX + 12f, hitY - 6f))

        // Outline
        drawCircle(color = purpleDarkOutline, radius = 12f, center = Offset(centerX + 2f, hitY + 10f), style = Stroke(width = 1.5f))
        return
    }

    // C. GENERAL BODY / HEAD / EARS FOR IDLE, WALK A/B, JUMP, CLIMB
    val isWalkA = isGrounded && (sin(phase) >= 0f)

    // 1. Cute Antennae / Horns with Pink Tips (Kenney Mascot Feature)
    val horn1X = centerX - 6f
    val horn2X = centerX + 6f
    val hornY = playerY + 2f

    drawLine(color = purpleBody, start = Offset(centerX - 4f, playerY + 8f), end = Offset(horn1X, hornY), strokeWidth = 3.5f)
    drawLine(color = purpleBody, start = Offset(centerX + 4f, playerY + 8f), end = Offset(horn2X, hornY), strokeWidth = 3.5f)
    drawCircle(color = pinkAntennaeTip, radius = 3f, center = Offset(horn1X, hornY))
    drawCircle(color = pinkAntennaeTip, radius = 3f, center = Offset(horn2X, hornY))

    // 2. Character Body & Head (Kenney Oval Capsule)
    drawRoundRect(
        color = purpleBody,
        topLeft = Offset(playerX + 4f, playerY + 8f),
        size = Size(24f, 32f),
        cornerRadius = CornerRadius(11f, 11f)
    )
    // Cute lighter purple belly
    drawRoundRect(
        color = purpleBelly,
        topLeft = Offset(playerX + 8f, playerY + 18f),
        size = Size(16f, 18f),
        cornerRadius = CornerRadius(7f, 7f)
    )

    // 3. Expressive Big Cartoon Eyes
    val eyeCenterX = centerX + 4f
    val eyeCenterY = playerY + 15f
    drawCircle(color = Color.White, radius = 5.5f, center = Offset(eyeCenterX, eyeCenterY))
    drawCircle(color = darkShoe, radius = 3f, center = Offset(eyeCenterX + 1.2f, eyeCenterY))
    // Glossy eye reflection catchlight
    drawCircle(color = Color.White, radius = 1.2f, center = Offset(eyeCenterX + 0.5f, eyeCenterY - 1f))

    // 4. Character Mouth State
    if (!isGrounded) {
        // Open excited "O" mouth for jump
        drawCircle(color = purpleDarkOutline, radius = 2.5f, center = Offset(eyeCenterX, eyeCenterY + 7f))
    } else {
        // Cheerful smile
        val smilePath = Path().apply {
            moveTo(eyeCenterX - 3f, eyeCenterY + 6f)
            quadraticTo(eyeCenterX, eyeCenterY + 8.5f, eyeCenterX + 3f, eyeCenterY + 6f)
        }
        drawPath(smilePath, purpleDarkOutline, style = Stroke(width = 1.5f))
    }

    // 5. Double Jump Halo / Sparkle Ring
    if (isDoubleJumping && !isGrounded) {
        drawCircle(
            color = shieldColor.copy(alpha = 0.6f),
            radius = 20f,
            center = Offset(centerX, centerY),
            style = Stroke(width = 2.5f)
        )
        drawCircle(color = Color.White, radius = 2f, center = Offset(centerX + 18f, centerY - 8f))
        drawCircle(color = Color.White, radius = 2f, center = Offset(centerX - 16f, centerY + 10f))
    }

    // 6. Arms & Hands Animation
    if (isWallJumping) {
        // character_purple_climb: Arms reaching high gripping wall
        drawLine(color = purpleBody, start = Offset(centerX + 8f, centerY), end = Offset(centerX + 16f, centerY - 12f), strokeWidth = 4f)
        drawCircle(color = purpleBody, radius = 3f, center = Offset(centerX + 16f, centerY - 12f))
    } else if (!isGrounded) {
        // character_purple_jump: Both arms joyfully raised up
        drawLine(color = purpleBody, start = Offset(centerX - 8f, centerY), end = Offset(centerX - 14f, centerY - 10f), strokeWidth = 4f)
        drawLine(color = purpleBody, start = Offset(centerX + 8f, centerY), end = Offset(centerX + 14f, centerY - 10f), strokeWidth = 4f)
        drawCircle(color = purpleBody, radius = 3f, center = Offset(centerX - 14f, centerY - 10f))
        drawCircle(color = purpleBody, radius = 3f, center = Offset(centerX + 14f, centerY - 10f))
    } else if (gameState == RunnerGameState.NOT_STARTED) {
        // character_purple_idle: Arms resting naturally at sides
        drawLine(color = purpleBody, start = Offset(centerX - 8f, centerY - 2f), end = Offset(centerX - 10f, centerY + 8f), strokeWidth = 3.5f)
        drawLine(color = purpleBody, start = Offset(centerX + 8f, centerY - 2f), end = Offset(centerX + 10f, centerY + 8f), strokeWidth = 3.5f)
    } else {
        // character_purple_walk_a / walk_b: Running arm swing
        val armSwing = if (isWalkA) 8f else -8f
        drawLine(color = purpleBody, start = Offset(centerX - 6f, centerY), end = Offset(centerX - 6f - armSwing, centerY + 6f), strokeWidth = 3.5f)
        drawLine(color = purpleBody, start = Offset(centerX + 6f, centerY), end = Offset(centerX + 6f + armSwing, centerY + 6f), strokeWidth = 3.5f)
    }

    // 7. Legs & Shoes (character_purple_walk_a vs walk_b stride / jump tuck / idle)
    val legY = playerY + 36f
    if (isWallJumping) {
        // Wall climb kick off
        drawRoundRect(color = darkShoe, topLeft = Offset(centerX + 4f, legY + 4f), size = Size(10f, 6f), cornerRadius = CornerRadius(2f, 2f))
        drawRoundRect(color = darkShoe, topLeft = Offset(centerX - 10f, legY + 8f), size = Size(9f, 6f), cornerRadius = CornerRadius(2f, 2f))
    } else if (!isGrounded) {
        // Jump tucked legs
        drawRoundRect(color = darkShoe, topLeft = Offset(centerX - 8f, legY + 2f), size = Size(8f, 6f), cornerRadius = CornerRadius(2f, 2f))
        drawRoundRect(color = darkShoe, topLeft = Offset(centerX + 2f, legY + 2f), size = Size(8f, 6f), cornerRadius = CornerRadius(2f, 2f))
    } else if (gameState == RunnerGameState.NOT_STARTED) {
        // Idle stance feet
        drawRoundRect(color = darkShoe, topLeft = Offset(centerX - 8f, legY + 6f), size = Size(8f, 6f), cornerRadius = CornerRadius(2f, 2f))
        drawRoundRect(color = darkShoe, topLeft = Offset(centerX + 2f, legY + 6f), size = Size(8f, 6f), cornerRadius = CornerRadius(2f, 2f))
    } else {
        // Dynamic Walk A / Walk B Stride
        val frontFootX = if (isWalkA) centerX + 6f else centerX - 6f
        val backFootX = if (isWalkA) centerX - 8f else centerX + 4f
        val frontFootY = legY + 6f
        val backFootY = legY + 4f

        drawRoundRect(color = darkShoe, topLeft = Offset(frontFootX, frontFootY), size = Size(9f, 6f), cornerRadius = CornerRadius(2f, 2f))
        drawRoundRect(color = darkShoe, topLeft = Offset(backFootX, backFootY), size = Size(8f, 6f), cornerRadius = CornerRadius(2f, 2f))
    }

    // 8. Body Dark Outline
    drawRoundRect(
        color = purpleDarkOutline,
        topLeft = Offset(playerX + 4f, playerY + 8f),
        size = Size(24f, 32f),
        cornerRadius = CornerRadius(11f, 11f),
        style = Stroke(width = 1.5f)
    )
}

