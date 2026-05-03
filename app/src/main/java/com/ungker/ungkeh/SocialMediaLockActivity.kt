package com.ungker.ungkeh

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.ungker.ungkeh.data.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class SocialMediaLockActivity : ComponentActivity() {

    private var targetPackage: String? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onRestart() {
        super.onRestart()
        UngkerService.isLockActivityVisible = true
    }

    override fun onStop() {
        super.onStop()
        UngkerService.isLockActivityVisible = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        targetPackage = intent.getStringExtra("target_package")
        val isWarning = intent.getBooleanExtra("is_warning", false)

        setupLockScreenFlags()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (targetPackage == null) {
                    finish()
                } else {
                    // Keluar ke Home agar aplikasi terlarang ditinggalkan
                    val intent = Intent(Intent.ACTION_MAIN)
                    intent.addCategory(Intent.CATEGORY_HOME)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    
                    // Beri jeda waktu agar tidak langsung dikunci oleh kunci lainnya (Grace Period)
                    val sp = getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE)
                    sp.edit { putLong("lock_dismissed_at", System.currentTimeMillis()) }
                    
                    finish()
                }
            }
        })

        UngkerService.isLockActivityVisible = true

        setContent {
            val sp = remember { getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE) }
            val isDarkMode = remember { sp.getBoolean("dark_mode", false) }
            val colorScheme = if (isDarkMode) {
                darkColorScheme(
                    background   = Color(0xFF0F172A),
                    surface      = Color(0xFF1E293B),
                    onSurface    = Color(0xFFF1F5F9),
                    onBackground = Color(0xFFF1F5F9),
                    primary      = Color(0xFF4ADE80),
                    onPrimary    = Color(0xFF052E16),
                )
            } else {
                lightColorScheme()
            }
            CompositionLocalProvider(LocalIsDarkMode provides isDarkMode) {
                MaterialTheme(colorScheme = colorScheme) {
                    Surface(
                        modifier = Modifier.fillMaxSize().systemBarsPadding(),
                        color = if (isDarkMode) Color(0xFF0F172A) else Color(0xFFF8F9FA)
                    ) {
                        SocialMediaLockScreen(
                            targetPackage = targetPackage,
                            isWarning = isWarning,
                            onDismiss = { finish() }
                        )
                    }
                }
            }
        }
    }

    private fun setupLockScreenFlags() {
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}

@Composable
fun SocialMediaLockScreen(targetPackage: String?, isWarning: Boolean = false, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val dark = LocalIsDarkMode.current
    
    val textPrim = if (dark) Color(0xFFF1F5F9) else Color(0xFF4B4B4B)
    val textSec = if (dark) Color(0xFF94A3B8) else Color(0xFF757575)
    val cardBg = if (dark) Color(0xFF1E293B) else Color.White
    val greenAccent = Color(0xFF2E7D32)
    val greenBg = if (dark) Color(0xFF14532D) else Color(0xFFE8F5E9)
    val blueAccent = Color(0xFF1CB0F6)
    val orangeAccent = Color(0xFFFF9800)
    
    // Get PreferenceManager
    val prefs = remember { PreferenceManager(context) }
    val userRole = remember { prefs.getUserRole() }
    val savedPassword = remember { prefs.getParentPassword() }

    // State management for the flow
    val isHardLockedInitially = remember { 
        prefs.isHardLockedUntilTomorrow() || (prefs.hasTakenTempUnlockToday() && !prefs.isSocialMediaTempUnlocked()) 
    }
    
    var currentState by rememberSaveable { 
        mutableStateOf(
            when {
                isWarning -> SocialMediaLockState.WARNING
                isHardLockedInitially -> SocialMediaLockState.HARD_LOCKED
                else -> SocialMediaLockState.INTRODUCTION
            }
        ) 
    }
    var pagesRead by rememberSaveable { mutableIntStateOf(0) }
    
    // Handler functions that need to capture context
    val navigateToHome: () -> Unit = {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
        
        // Catat waktu tutup agar servis tidak langsung mengunci lagi (Grace Period)
        val sp = context.getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE)
        sp.edit { putLong("lock_dismissed_at", System.currentTimeMillis()) }
        
        onDismiss()
    }

    val handleContinue: () -> Unit = {
        val sp = context.getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE)
        val limitMinutes = sp.getLong("social_media_limit_minutes", 60L)
        val limitMs = limitMinutes * 60 * 1000L
        
        val bonusMs = 30 * 60 * 1000L
        val newUsedMs = maxOf(0L, limitMs - bonusMs)
        
        val expiryTime = System.currentTimeMillis() + bonusMs
        sp.edit { 
            putLong("social_media_temp_unlock_expiry_ms", expiryTime)
            putLong("social_media_time_used_ms", newUsedMs)
            putBoolean("has_taken_temp_unlock_today", true)
            putLong("lock_dismissed_at", System.currentTimeMillis())
        }
        
        prefs.setIsInUnlockChallenge(false)
        prefs.setQuranPagesReadForUnlock(0)
        onDismiss()
    }
    
    val handleIkhlas: () -> Unit = {
        UnlockManager.activateHardLockUntilTomorrow(prefs, "ikhlas")
        prefs.setIsInUnlockChallenge(false)
        prefs.setQuranPagesReadForUnlock(0)
        
        // Gunakan fungsi navigasi ke Home
        navigateToHome()
    }
    
    when (currentState) {
        SocialMediaLockState.INTRODUCTION -> {
            IntroductionScreen(
                dark = dark,
                textPrim = textPrim,
                textSec = textSec,
                cardBg = cardBg,
                greenAccent = greenAccent,
                greenBg = greenBg,
                onStartChallenge = { 
                    prefs.setIsInUnlockChallenge(true)
                    currentState = SocialMediaLockState.READING_CHALLENGE
                },
                onExit = navigateToHome // Tambahkan opsi keluar
            )
        }
        
        SocialMediaLockState.READING_CHALLENGE -> {
            ReadingChallengeScreen(
                dark = dark,
                textPrim = textPrim,
                textSec = textSec,
                cardBg = cardBg,
                greenAccent = greenAccent,
                greenBg = greenBg,
                onReadMushaf = {
                    // Open mushaf
                    val intent = Intent(context, MainActivity::class.java).apply {
                        putExtra("open_mushaf", true)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                },
                onPagesUpdated = { newCount ->
                    pagesRead = newCount
                    if (newCount >= 5) {
                        if (userRole == "parent") {
                            currentState = SocialMediaLockState.PARENT_PASSWORD
                        } else {
                            currentState = SocialMediaLockState.CHOICE_SCREEN
                        }
                    }
                },
                onExit = navigateToHome
            )
        }
        
        SocialMediaLockState.CHOICE_SCREEN -> {
            ChoiceScreen(
                dark = dark,
                textPrim = textPrim,
                textSec = textSec,
                cardBg = cardBg,
                greenAccent = greenAccent,
                greenBg = greenBg,
                blueAccent = blueAccent,
                orangeAccent = orangeAccent,
                onChooseContinue = handleContinue,
                onChooseIkhlas = handleIkhlas
            )
        }

        SocialMediaLockState.PARENT_PASSWORD -> {
            ParentPasswordUnlockScreen(
                dark = dark,
                textPrim = textPrim,
                textSec = textSec,
                cardBg = cardBg,
                greenAccent = greenAccent,
                savedPassword = savedPassword,
                onUnlock = handleContinue
            )
        }

        SocialMediaLockState.HARD_LOCKED -> {
            HardLockedScreen(
                dark = dark,
                textPrim = textPrim,
                textSec = textSec,
                cardBg = cardBg,
                orangeAccent = orangeAccent,
                onDismiss = navigateToHome
            )
        }

        SocialMediaLockState.WARNING -> {
            WarningScreen(
                targetPackage = targetPackage,
                onExit = navigateToHome,
                onEnableProtection = {
                    // Logic to enable for this app
                    val sp = context.getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE)
                    val blocked = sp.getStringSet("blocked_apps", emptySet())?.toMutableSet() ?: mutableSetOf()
                    targetPackage?.let { 
                        blocked.add(it)
                        sp.edit { putStringSet("blocked_apps", blocked) }
                    }
                    onDismiss()
                }
            )
        }
    }
}

enum class SocialMediaLockState {
    INTRODUCTION,
    READING_CHALLENGE,
    CHOICE_SCREEN,
    PARENT_PASSWORD,
    HARD_LOCKED,
    WARNING
}

@Composable
fun WarningScreen(
    targetPackage: String?,
    onExit: () -> Unit,
    onEnableProtection: () -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val appName = remember {
        try {
            targetPackage?.let { pm.getApplicationLabel(pm.getApplicationInfo(it, 0)).toString() } ?: LocaleManager.L("social_lock_fallback_app")
        } catch (e: Exception) { LocaleManager.L("social_lock_fallback_app") }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF7F1D1D)) // Dark Red Background
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "⚠️", fontSize = 80.sp)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Pembatasan Belum Aktif!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "$appName terdeteksi belum masuk dalam daftar blokir Ungker. Kamu tidak bisa membypass sistem!",
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFFFECACA),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = onEnableProtection,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF7F1D1D))
        ) {
            Text(LocaleManager.L("social_lock_enable_now"), fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onExit,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) {
            Text(LocaleManager.L("social_lock_exit"))
        }
    }
}

@Composable
fun IntroductionScreen(
    dark: Boolean,
    textPrim: Color,
    textSec: Color,
    cardBg: Color,
    greenAccent: Color,
    greenBg: Color,
    onStartChallenge: () -> Unit,
    onExit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "📱",
            fontSize = 80.sp
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = LocaleManager.L("social_lock_title"),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = textPrim,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = LocaleManager.L("social_lock_time_desc"),
            style = MaterialTheme.typography.bodyLarge,
            color = textSec,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = greenBg),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = greenAccent,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Baca 5 Halaman Mushaf",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = greenAccent
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = LocaleManager.L("social_lock_desc"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = textSec,
                    textAlign = TextAlign.Center
                )
            }
        }
        
        Spacer(modifier = Modifier.height(40.dp))
        
        Button(
            onClick = onStartChallenge,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = greenAccent)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = LocaleManager.L("btn_start_reading"),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(
            onClick = onExit,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = LocaleManager.L("btn_exit_later"),
                color = textSec,
                style = MaterialTheme.typography.bodyMedium,
                textDecoration = TextDecoration.Underline
            )
        }
    }
}

@Composable
fun ReadingChallengeScreen(
    dark: Boolean,
    textPrim: Color,
    textSec: Color,
    cardBg: Color,
    greenAccent: Color,
    greenBg: Color,
    onReadMushaf: () -> Unit,
    onPagesUpdated: (Int) -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager(context) }
    
    // Use state to properly trigger recomposition
    var currentPages by remember { mutableIntStateOf(0) }
    
    // Monitor pages read
    LaunchedEffect(Unit) {
        while (true) {
            val pages = prefs.getQuranPagesReadForUnlock()
            currentPages = pages
            onPagesUpdated(pages)
            delay(1000)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "📖",
            fontSize = 60.sp
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Baca 5 Halaman Mushaf",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = textPrim,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Buka aplikasi Mushaf dan baca 5 halaman Al-Quran.",
            style = MaterialTheme.typography.bodyMedium,
            color = textSec,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Progress indicator
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Progres Membaca",
                        style = MaterialTheme.typography.titleSmall,
                        color = textSec
                    )
                    Text(
                        text = "$currentPages / 5 halaman",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = greenAccent
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                LinearProgressIndicator(
                    progress = { (currentPages.toFloat() / 5f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(CircleShape),
                    color = greenAccent,
                    trackColor = if (dark) Color(0xFF334155) else Color(0xFFE5E5E5)
                )
                
                if (currentPages >= 5) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = greenAccent,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Selesai! Lanjut ke langkah berikutnya",
                            style = MaterialTheme.typography.bodyMedium,
                            color = greenAccent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedButton(
            onClick = onReadMushaf,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(2.dp, greenAccent)
        ) {
            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = greenAccent)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = LocaleManager.L("btn_open_mushaf"),
                fontWeight = FontWeight.Bold,
                color = greenAccent
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = LocaleManager.L("social_lock_mushaf_hint"),
            style = MaterialTheme.typography.bodySmall,
            color = textSec,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        TextButton(
            onClick = onExit,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = LocaleManager.L("social_lock_exit_short"),
                color = textSec,
                style = MaterialTheme.typography.bodySmall,
                textDecoration = TextDecoration.Underline
            )
        }
    }
}

@Composable
fun ParentPasswordUnlockScreen(
    dark: Boolean,
    textPrim: Color,
    textSec: Color,
    cardBg: Color,
    greenAccent: Color,
    savedPassword: String,
    onUnlock: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        delay(500)
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🔐",
            fontSize = 60.sp
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Password Orang Tua",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = textPrim,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Masukkan password untuk membuka kunci aplikasi.",
            style = MaterialTheme.typography.bodyMedium,
            color = textSec,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = input,
            onValueChange = { 
                input = it
                isError = false
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            placeholder = { Text(LocaleManager.L("soc_lock_password_placeholder"), color = textSec.copy(alpha = 0.5f)) },
            singleLine = true,
            isError = isError,
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (input == savedPassword) {
                        keyboard?.hide()
                        onUnlock()
                    } else {
                        isError = true
                    }
                }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = greenAccent,
                unfocusedBorderColor = textSec.copy(alpha = 0.3f),
                focusedTextColor = textPrim,
                unfocusedTextColor = textPrim,
                errorBorderColor = Color.Red
            )
        )

        if (isError) {
            Text(
                text = "Password salah!",
                color = Color.Red,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                if (input == savedPassword) {
                    onUnlock()
                } else {
                    isError = true
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = greenAccent)
        ) {
            Text(
                text = LocaleManager.L("btn_unlock"),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
    }
}

@Composable
fun ChoiceScreen(
    dark: Boolean,
    textPrim: Color,
    textSec: Color,
    cardBg: Color,
    greenAccent: Color,
    greenBg: Color,
    blueAccent: Color,
    orangeAccent: Color,
    onChooseContinue: () -> Unit,
    onChooseIkhlas: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "✅",
            fontSize = 60.sp
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Alhamdulillah!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = greenAccent,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Kamu telah membaca 5 halaman mushaf.",
            style = MaterialTheme.typography.bodyLarge,
            color = textPrim,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Sekarang, bagaimana niatmu?",
            style = MaterialTheme.typography.titleMedium,
            color = textSec,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Choice 1: Continue playing
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onChooseContinue() },
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(blueAccent.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = blueAccent,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Lanjut Bermain",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = textPrim
                    )
                    Text(
                        text = "+ 30 menit waktu tambahan",
                        style = MaterialTheme.typography.bodySmall,
                        color = textSec
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = textSec
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Choice 2: Ikhlas
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onChooseIkhlas() },
            colors = CardDefaults.cardColors(containerColor = orangeAccent.copy(alpha = 0.1f)),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(orangeAccent.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = orangeAccent,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Ikhlas karena Allah (RECOMMENDED)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = orangeAccent
                    )
                    Text(
                        text = "Gunakan kembali media sosial esok hari.",
                        style = MaterialTheme.typography.bodySmall,
                        color = textSec
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = textSec
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Pilihlah dengan penuh kesadaran.",
            style = MaterialTheme.typography.bodySmall,
            color = textSec,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun HardLockedScreen(
    dark: Boolean,
    textPrim: Color,
    textSec: Color,
    cardBg: Color,
    orangeAccent: Color,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager(context) }
    val hardLockReason = remember { prefs.getHardLockReason() }
    
    val (titleText, messageText) = when (hardLockReason) {
        "ikhlas" -> LocaleManager.L("hard_lock_ikhlas_title") to LocaleManager.L("hard_lock_ikhlas_message")
        "limit_exceeded" -> LocaleManager.L("hard_lock_limit_title") to LocaleManager.L("hard_lock_limit_message")
        else -> LocaleManager.L("hard_lock_default_title") to LocaleManager.L("hard_lock_default_message")
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "👋🏼",
            fontSize = 80.sp
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = titleText,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = textPrim,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = messageText,
            style = MaterialTheme.typography.bodyLarge,
            color = textSec,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(40.dp))
        
        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = orangeAccent)
        ) {
            Text(
                text = LocaleManager.L("btn_close"),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
    }
}
