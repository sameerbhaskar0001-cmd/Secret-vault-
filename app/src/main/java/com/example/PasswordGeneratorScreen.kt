package com.example

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalAppThemeColors
import java.security.SecureRandom

enum class PasswordStrength(val label: String, val color: Color, val score: Int) {
    WEAK("Weak", Color(0xFFEF5350), 1),
    MEDIUM("Medium", Color(0xFFFFB74D), 2),
    STRONG("Strong", Color(0xFF66BB6A), 3)
}

enum class PasswordMode {
    EASY, STRONG, ULTRA
}

fun generateWordBasedPassword(
    length: Int,
    includeUppercase: Boolean,
    includeLowercase: Boolean,
    includeNumbers: Boolean,
    includeSymbols: Boolean,
    excludeSimilar: Boolean
): String {
    val adjectives = listOf(
        "Red", "Blue", "Green", "Yellow", "Black", "White", "Gold", "Silver", "Royal", "Bright", "Dark", 
        "Wild", "Sweet", "Happy", "Brave", "Gentle", "Clear", "Warm", "Cool", "Super", "Magic", "Cosmic", 
        "Secret", "Quick", "Swift", "Smart", "Clever", "Solar", "Lunar", "Epic", "Neon", "Mega", "Alpha", 
        "Omega", "Cyber", "Pixel", "Quantum", "Grand", "True", "Noble", "Free", "Proud", "Stout", "Strong"
    )

    val nouns = listOf(
        "Tiger", "Lion", "Horse", "Deer", "Bear", "Eagle", "Falcon", "Hawk", "Wolf", "Fox", "Cat", "Dog", 
        "Rabbit", "Panda", "Koala", "Shark", "Dolphin", "Whale", "Dragon", "Phoenix", "Knight", "Hero", 
        "Wizard", "Spark", "Flame", "Storm", "Wave", "Wind", "Sky", "Star", "Moon", "Sun", "River", "Lake", 
        "Forest", "Mountain", "Valley", "Stone", "Gem", "Ocean", "Cloud", "Rain", "Snow", "Frost", "Fire", 
        "Light", "Shadow", "Ring", "Crown", "Shield", "Sword", "Heart", "Smile", "Dream", "Hope", "Wish", 
        "Song", "Bell", "Key", "Gate", "Door", "Ship", "Rocket", "Road", "Path", "Nest", "Leaf", "Tree"
    )

    val random = SecureRandom()
    
    // Select random adjective and noun
    var adj = adjectives[random.nextInt(adjectives.size)]
    var noun = nouns[random.nextInt(nouns.size)]

    fun String.filterSimilarChars(): String {
        val similar = setOf('I', 'l', '1', 'O', '0', 'o', 'S', '5', 'G', '6', 'B', '8', 'Z', '2', 't', 'f', 'i', 's')
        return this.filter { it !in similar }
    }

    if (excludeSimilar) {
        adj = adj.filterSimilarChars()
        noun = noun.filterSimilarChars()
    }

    fun formatWord(w: String): String {
        if (w.isEmpty()) return ""
        return when {
            includeUppercase && includeLowercase -> w.replaceFirstChar { it.uppercaseChar() }.substring(0, 1) + w.substring(1).lowercase()
            includeUppercase -> w.uppercase()
            includeLowercase -> w.lowercase()
            else -> w.lowercase()
        }
    }

    val fAdj = formatWord(adj)
    val fNoun = formatWord(noun)

    var digits = ""
    if (includeNumbers) {
        val numPool = if (excludeSimilar) "3479" else "0123456789"
        val d1 = numPool[random.nextInt(numPool.length)]
        val d2 = numPool[random.nextInt(numPool.length)]
        digits = "$d1$d2"
    }

    var symbol = ""
    if (includeSymbols) {
        val symbolPool = if (excludeSimilar) "!@#$%^&*()_+-=" else "!@#$%^&*()_+-=[]{}|;:,.<>?/`~"
        symbol = symbolPool[random.nextInt(symbolPool.length)].toString()
    }

    val base = fAdj + symbol + fNoun + digits

    if (base.length == length) {
        return base
    }

    if (base.length > length) {
        val remWordsLength = length - digits.length - symbol.length
        if (remWordsLength > 0) {
            val combinedWords = fAdj + fNoun
            val truncatedWords = combinedWords.take(remWordsLength)
            return truncatedWords + symbol + digits
        } else {
            return base.take(length)
        }
    }

    val needed = length - base.length
    val padBuilder = StringBuilder()
    val letterPool = StringBuilder()
    if (includeLowercase) {
        val pool = if (excludeSimilar) "abcdefghijkmnpqrswxyz" else "abcdefghijklmnopqrstuvwxyz"
        letterPool.append(pool)
    }
    if (includeUppercase) {
        val pool = if (excludeSimilar) "ACDEFHJKLMNPQRTUVWXY" else "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        letterPool.append(pool)
    }
    if (letterPool.isEmpty()) {
        letterPool.append(if (excludeSimilar) "abcdefghijkmnpqrswxyz" else "abcdefghijklmnopqrstuvwxyz")
    }

    for (i in 0 until needed) {
        padBuilder.append(letterPool[random.nextInt(letterPool.length)])
    }

    val suffix = symbol + digits
    val prefix = base.substring(0, base.length - suffix.length)
    return prefix + padBuilder.toString() + suffix
}

fun generatePassword(
    length: Int,
    includeUppercase: Boolean,
    includeLowercase: Boolean,
    includeNumbers: Boolean,
    includeSymbols: Boolean,
    excludeSimilar: Boolean,
    mode: PasswordMode
): String {
    return if (mode == PasswordMode.ULTRA) {
        val uppercaseCharsFull = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val lowercaseCharsFull = "abcdefghijklmnopqrstuvwxyz"
        val numberCharsFull = "0123456789"
        val symbolCharsFull = "!@#$%^&*()_+-=[]{}|;:,.<>?/\"'\\`~"

        fun String.filterSimilar(): String {
            val similar = setOf('I', 'l', '1', 'O', '0', 'o', 'S', '5', 'G', '6', 'B', '8', 'Z', '2', 't', 'f', 'i', 's')
            return this.filter { it !in similar }
        }

        val finalUpper = if (excludeSimilar) uppercaseCharsFull.filterSimilar() else uppercaseCharsFull
        val finalLower = if (excludeSimilar) lowercaseCharsFull.filterSimilar() else lowercaseCharsFull
        val finalNumbers = if (excludeSimilar) numberCharsFull.filterSimilar() else numberCharsFull
        val finalSymbols = if (excludeSimilar) symbolCharsFull.filterSimilar() else symbolCharsFull

        val pool = StringBuilder()
        val guaranteed = mutableListOf<Char>()
        val random = SecureRandom()

        if (includeUppercase && finalUpper.isNotEmpty()) {
            pool.append(finalUpper)
            guaranteed.add(finalUpper[random.nextInt(finalUpper.length)])
        }
        if (includeLowercase && finalLower.isNotEmpty()) {
            pool.append(finalLower)
            guaranteed.add(finalLower[random.nextInt(finalLower.length)])
        }
        if (includeNumbers && finalNumbers.isNotEmpty()) {
            pool.append(finalNumbers)
            guaranteed.add(finalNumbers[random.nextInt(finalNumbers.length)])
        }
        if (includeSymbols && finalSymbols.isNotEmpty()) {
            pool.append(finalSymbols)
            guaranteed.add(finalSymbols[random.nextInt(finalSymbols.length)])
        }

        if (pool.isEmpty()) {
            return ""
        }

        val password = StringBuilder()
        password.append(guaranteed.joinToString(""))

        val remaining = length - password.length
        for (i in 0 until remaining) {
            password.append(pool[random.nextInt(pool.length)])
        }

        val list = password.toList().shuffled(random)
        list.joinToString("")
    } else {
        generateWordBasedPassword(
            length = length,
            includeUppercase = includeUppercase,
            includeLowercase = includeLowercase,
            includeNumbers = includeNumbers,
            includeSymbols = includeSymbols,
            excludeSimilar = excludeSimilar
        )
    }
}

fun evaluateStrength(
    password: String,
    length: Int,
    includeUppercase: Boolean,
    includeLowercase: Boolean,
    includeNumbers: Boolean,
    includeSymbols: Boolean
): PasswordStrength {
    if (password.isEmpty()) return PasswordStrength.WEAK
    
    var score = 0
    if (length >= 14) score += 2
    else if (length >= 10) score += 1
    
    var typesCount = 0
    if (includeUppercase) typesCount++
    if (includeLowercase) typesCount++
    if (includeNumbers) typesCount++
    if (includeSymbols) typesCount++
    
    score += typesCount
    
    return when {
        score <= 3 || length < 8 -> PasswordStrength.WEAK
        score <= 4 -> PasswordStrength.MEDIUM
        else -> PasswordStrength.STRONG
    }
}

@Composable
fun PasswordGeneratorScreen(
    viewModel: CalculatorViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val themeColors = LocalAppThemeColors.current
    val ThemePurple = themeColors.themePurple
    val TextMedium = themeColors.textMedium
    val clipboardManager = LocalClipboardManager.current

    var selectedMode by remember { mutableStateOf(PasswordMode.STRONG) }
    var passwordLength by remember { mutableStateOf(14) } // Default for Strong Mode is 14
    var includeUppercase by remember { mutableStateOf(true) }
    var includeLowercase by remember { mutableStateOf(true) }
    var includeNumbers by remember { mutableStateOf(true) }
    var includeSymbols by remember { mutableStateOf(true) }
    var excludeSimilar by remember { mutableStateOf(false) }

    var generatedPassword by remember { mutableStateOf("") }
    var showCopiedToast by remember { mutableStateOf(false) }

    LaunchedEffect(passwordLength, includeUppercase, includeLowercase, includeNumbers, includeSymbols, excludeSimilar, selectedMode) {
        val isValid = includeUppercase || includeLowercase || includeNumbers || includeSymbols
        if (isValid) {
            generatedPassword = generatePassword(
                length = passwordLength,
                includeUppercase = includeUppercase,
                includeLowercase = includeLowercase,
                includeNumbers = includeNumbers,
                includeSymbols = includeSymbols,
                excludeSimilar = excludeSimilar,
                mode = selectedMode
            )
            val prefs = context.getSharedPreferences("exchange_calc_prefs", android.content.Context.MODE_PRIVATE)
            if (!prefs.getBoolean("first_password_generated", false)) {
                prefs.edit()
                    .putBoolean("first_password_generated", true)
                    .putLong("time_password_generated", System.currentTimeMillis())
                    .apply()
            }
        } else {
            generatedPassword = ""
        }
    }

    val isValidConfig = includeUppercase || includeLowercase || includeNumbers || includeSymbols

    val contentColorOnPurple = remember(ThemePurple) {
        val r = ThemePurple.red
        val g = ThemePurple.green
        val b = ThemePurple.blue
        val luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b
        if (luminance > 0.6) Color(0xFF111424) else Color.White
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF161B2B).copy(alpha = 0.95f))
                        .border(
                            width = 1.2.dp,
                            color = Color.White.copy(alpha = 0.35f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "LOCAL CRYPTOGRAPHY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ThemePurple.copy(alpha = 0.8f),
                        letterSpacing = 1.8.sp
                    )
                    Text(
                        text = "Password Generator",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                UnifiedGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    bgColor = Color(0xFF1B2031).copy(alpha = 0.95f),
                    elevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ThemePurple.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VpnKey,
                                    contentDescription = null,
                                    tint = ThemePurple,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Password Generator",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Secure & Offline",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(2.dp))
                        
                        Text(
                            text = "Generate strong, secure passwords locally on your device. Your keys are never saved or sent online.",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            lineHeight = 18.sp
                        )
                    }
                }

                // PASSWORD MODES SEGMENTED SELECTOR
                Text(
                    text = "PASSWORD MODE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMedium,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val modes = listOf(
                        Triple(PasswordMode.EASY, "Easy", Color(0xFF00E676)),
                        Triple(PasswordMode.STRONG, "Strong", Color(0xFFFFD600)),
                        Triple(PasswordMode.ULTRA, "Ultra Secure", Color(0xFFEF5350))
                    )

                    modes.forEach { (mode, label, activeColor) ->
                        val isSelected = selectedMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) activeColor.copy(alpha = 0.15f) 
                                    else Color.Transparent
                                )
                                .clickable {
                                    selectedMode = mode
                                    when (mode) {
                                        PasswordMode.EASY -> {
                                            passwordLength = 12
                                            includeUppercase = true
                                            includeLowercase = true
                                            includeNumbers = true
                                            includeSymbols = false
                                            excludeSimilar = false
                                        }
                                        PasswordMode.STRONG -> {
                                            passwordLength = 14
                                            includeUppercase = true
                                            includeLowercase = true
                                            includeNumbers = true
                                            includeSymbols = true
                                            excludeSimilar = false
                                        }
                                        PasswordMode.ULTRA -> {
                                            passwordLength = 16
                                            includeUppercase = true
                                            includeLowercase = true
                                            includeNumbers = true
                                            includeSymbols = true
                                            excludeSimilar = true
                                        }
                                    }
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(activeColor)
                                )
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                UnifiedGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    bgColor = Color(0xFF1B2031).copy(alpha = 0.95f),
                    elevation = 3.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "GENERATED PASSWORD",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ThemePurple
                            )
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color(0xFF00E676),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "100% Local",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00E676)
                                )
                            }
                        }
                        
                        Row(
                            modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Black.copy(alpha = 0.4f))
                                        .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = generatedPassword.ifEmpty { "Select at least one option" },
                                color = if (generatedPassword.isEmpty()) Color.White.copy(alpha = 0.3f) else Color.White,
                                fontSize = when {
                                    generatedPassword.length > 24 -> 12.sp
                                    generatedPassword.length > 16 -> 14.sp
                                    generatedPassword.length > 12 -> 15.sp
                                    else -> 17.sp
                                },
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            
                            if (generatedPassword.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        viewModel.copyToClipboard(context, "Generated Password", generatedPassword)
                                        showCopiedToast = true
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy Password",
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                
                                IconButton(
                                    onClick = {
                                        if (isValidConfig) {
                                            generatedPassword = generatePassword(
                                                length = passwordLength,
                                                includeUppercase = includeUppercase,
                                                includeLowercase = includeLowercase,
                                                includeNumbers = includeNumbers,
                                                includeSymbols = includeSymbols,
                                                excludeSimilar = excludeSimilar,
                                                mode = selectedMode
                                            )
                                        }
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Regenerate",
                                        tint = ThemePurple,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        if (generatedPassword.isNotEmpty()) {
                            val strength = evaluateStrength(
                                generatedPassword,
                                passwordLength,
                                includeUppercase,
                                includeLowercase,
                                includeNumbers,
                                includeSymbols
                            )
                            
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Strength: ${strength.label}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = strength.color
                                    )
                                    
                                    val entropy = (passwordLength * when {
                                        includeUppercase && includeLowercase && includeNumbers && includeSymbols -> 6.5
                                        includeUppercase && includeLowercase && includeNumbers -> 5.7
                                        else -> 4.5
                                    }).toInt()
                                    Text(
                                        text = "~$entropy bits entropy",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.4f)
                                    )
                                }
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val activeSegments = strength.score
                                    for (i in 1..3) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(
                                                    if (i <= activeSegments) strength.color 
                                                    else Color.White.copy(alpha = 0.08f)
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "PASSWORD CRITERIA",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMedium,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp)
                )

                UnifiedGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    bgColor = Color(0xFF1B2031).copy(alpha = 0.95f),
                    elevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Password Length",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Min: 6 | Max: 64 characters",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                            
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ThemePurple.copy(alpha = 0.15f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = passwordLength.toString(),
                                    color = ThemePurple,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        
                        Slider(
                            value = passwordLength.toFloat(),
                            onValueChange = { passwordLength = it.toInt() },
                            valueRange = 6f..64f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = ThemePurple,
                                inactiveTrackColor = Color.White.copy(alpha = 0.12f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                UnifiedGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    bgColor = Color(0xFF1B2031).copy(alpha = 0.95f),
                    elevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        @Composable
                        fun OptionRow(
                            title: String,
                            description: String,
                            checked: Boolean,
                            onCheckedChange: (Boolean) -> Unit,
                            icon: androidx.compose.ui.graphics.vector.ImageVector,
                            iconBg: Color,
                            iconTint: Color,
                            enabled: Boolean = true
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = enabled) { onCheckedChange(!checked) }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(iconBg.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = iconTint,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = title,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f)
                                        )
                                        Text(
                                            text = description,
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.5f),
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                                
                                Switch(
                                    checked = checked,
                                    onCheckedChange = if (enabled) onCheckedChange else null,
                                    enabled = enabled,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = contentColorOnPurple,
                                        checkedTrackColor = ThemePurple,
                                        uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                                        uncheckedTrackColor = Color.White.copy(alpha = 0.12f)
                                    )
                                )
                            }
                        }

                        OptionRow(
                            title = "Uppercase Letters",
                            description = "Include capital letters (A-Z)",
                            checked = includeUppercase,
                            onCheckedChange = { includeUppercase = it },
                            icon = Icons.Default.TextFields,
                            iconBg = Color(0xFF0EA5E9),
                            iconTint = Color(0xFF0EA5E9)
                        )
                        
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.05f)))

                        OptionRow(
                            title = "Lowercase Letters",
                            description = "Include small letters (a-z)",
                            checked = includeLowercase,
                            onCheckedChange = { includeLowercase = it },
                            icon = Icons.Default.Title,
                            iconBg = Color(0xFF3B82F6),
                            iconTint = Color(0xFF3B82F6)
                        )

                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.05f)))

                        OptionRow(
                            title = "Include Numbers",
                            description = "Include numeric digits (0-9)",
                            checked = includeNumbers,
                            onCheckedChange = { includeNumbers = it },
                            icon = Icons.Default.Numbers,
                            iconBg = Color(0xFF00E676),
                            iconTint = Color(0xFF00E676)
                        )

                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.05f)))

                        OptionRow(
                            title = "Include Symbols",
                            description = "Include special characters (!@#$%)",
                            checked = includeSymbols,
                            onCheckedChange = { includeSymbols = it },
                            icon = Icons.Default.AlternateEmail,
                            iconBg = Color(0xFFFF9100),
                            iconTint = Color(0xFFFF9100)
                        )

                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.05f)))

                        OptionRow(
                            title = "Exclude Similar Characters",
                            description = "Avoid confusion (e.g. O, 0, I, l)",
                            checked = excludeSimilar,
                            onCheckedChange = { excludeSimilar = it },
                            icon = Icons.Default.Difference,
                            iconBg = Color(0xFFEF5350),
                            iconTint = Color(0xFFEF5350)
                        )
                    }
                }

                Button(
                    onClick = {
                        if (isValidConfig) {
                            generatedPassword = generatePassword(
                                length = passwordLength,
                                includeUppercase = includeUppercase,
                                includeLowercase = includeLowercase,
                                includeNumbers = includeNumbers,
                                includeSymbols = includeSymbols,
                                excludeSimilar = excludeSimilar,
                                mode = selectedMode
                            )
                        }
                    },
                    enabled = isValidConfig,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4C1D95),
                        contentColor = Color.White,
                        disabledContainerColor = Color.White.copy(alpha = 0.08f),
                        disabledContentColor = Color.White.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = null,
                            tint = contentColorOnPurple,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Generate Password",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
        
        AnimatedVisibility(
            visible = showCopiedToast,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            LaunchedEffect(showCopiedToast) {
                if (showCopiedToast) {
                    kotlinx.coroutines.delay(2000)
                    showCopiedToast = false
                }
            }
            UnifiedGlassCard(
                shape = RoundedCornerShape(12.dp),
                bgColor = Color(0xFF00E676).copy(alpha = 0.9f),
                elevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Password copied to clipboard!",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
