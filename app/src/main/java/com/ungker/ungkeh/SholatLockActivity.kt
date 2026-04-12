package com.ungker.ungkeh

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

// ─── Konstanta ──────────────────────────────────────────────────────────────
const val PLEDGE_TEXT =
    "Saya berjanji akan pergi sholat setelah melakukan sesuatu dengan aplikasi ini, dan sesungguhnya Allah maha melihat"

const val PLEDGE_CREDIT_MS    = 2L * 60 * 1000
const val SHOLAT_WINDOW_MS     = 10L * 60 * 1000
const val PLEDGE_PREFS         = "UNGKER_PREF"
const val GRACE_DISMISS_MS     = 8_000L
const val SHOLAT_CHECK_DELAY   = 5_000L
const val PLEDGE_CHECK_DELAY   = 2_000L
const val WINDOW_UPDATE_DELAY  = 1_000L
const val UNLOCK_DELAY_MS      = 800L

fun isWithinPrayerWindow(context: Context): Pair<Boolean, String> {
    val sp = context.getSharedPreferences(PLEDGE_PREFS, Context.MODE_PRIVATE)
    val lat = sp.getFloat("sholat_lat", -6.2088f).toDouble()
    val lng = sp.getFloat("sholat_lng", 106.8456f).toDouble()
    val tz  = sp.getInt("sholat_tz", 7)
    val cal = Calendar.getInstance()
    val pt  = try { hitungWaktuSholat(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH), lat, lng, tz) } catch (_: Exception) { return false to "" }
    val nowDecimal = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60.0
    fun toDecimal(hhmm: String): Double { val p = hhmm.split(":"); return p[0].toDouble() + p[1].toDouble() / 60.0 }
    val prayers = listOf("Subuh" to toDecimal(pt.subuh), "Dzuhur" to toDecimal(pt.dzuhur), "Ashar" to toDecimal(pt.ashar), "Maghrib" to toDecimal(pt.maghrib), "Isya" to toDecimal(pt.isya))
    val windowDecimal = 10.0 / 60.0
    for ((name, prayerTime) in prayers) { if (nowDecimal >= prayerTime && nowDecimal <= prayerTime + windowDecimal) return true to name }
    return false to ""
}

fun hasPledgeCredit(context: Context): Boolean {
    val sp = context.getSharedPreferences(PLEDGE_PREFS, Context.MODE_PRIVATE)
    return System.currentTimeMillis() < sp.getLong("sholat_pledge_credit_expiry", 0L)
}

fun pledgeWasUsedAndExpired(context: Context): Boolean {
    val sp = context.getSharedPreferences(PLEDGE_PREFS, Context.MODE_PRIVATE)
    val expiry = sp.getLong("sholat_pledge_credit_expiry", 0L)
    val sessionKey = sp.getString("sholat_pledge_session_key", "") ?: ""
    if (expiry == 0L || sessionKey.isEmpty()) return false
    if (System.currentTimeMillis() < expiry) return false
    return sessionKey == getCurrentPrayerSessionKey(context)
}

fun getCurrentPrayerSessionKey(context: Context): String {
    val sp  = context.getSharedPreferences(PLEDGE_PREFS, Context.MODE_PRIVATE)
    val lat = sp.getFloat("sholat_lat", -6.2088f).toDouble()
    val lng = sp.getFloat("sholat_lng", 106.8456f).toDouble()
    val tz  = sp.getInt("sholat_tz", 7)
    val cal = Calendar.getInstance()
    val pt  = try { hitungWaktuSholat(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH), lat, lng, tz) } catch (_: Exception) { return "" }
    fun toMin(hhmm: String): Int { val p = hhmm.split(":"); return p[0].toInt() * 60 + p[1].toInt() }
    val nowSec = cal.get(Calendar.HOUR_OF_DAY) * 3600 + cal.get(Calendar.MINUTE) * 60 + cal.get(Calendar.SECOND)
    val prayers = listOf("Subuh" to toMin(pt.subuh), "Dzuhur" to toMin(pt.dzuhur), "Ashar" to toMin(pt.ashar), "Maghrib" to toMin(pt.maghrib), "Isya" to toMin(pt.isya))
    val dateStr = "%04d-%02d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    for ((name, pMin) in prayers) { if (nowSec in (pMin * 60)..(pMin * 60 + SHOLAT_WINDOW_MS.toInt() / 1000)) return "${dateStr}_$name" }
    return ""
}

fun savePledgeCredit(context: Context) {
    val sp = context.getSharedPreferences(PLEDGE_PREFS, Context.MODE_PRIVATE)
    val now = System.currentTimeMillis()
    sp.edit(commit = true) {
        putLong("sholat_pledge_credit_expiry", now + PLEDGE_CREDIT_MS)
        putString("sholat_pledge_session_key", getCurrentPrayerSessionKey(context))
        putLong("lock_dismissed_at", now)
    }
}

class SholatLockActivity : ComponentActivity() {
    private var targetPackage: String? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var dndApplied = false
    private var originalDndFilter = NotificationManager.INTERRUPTION_FILTER_ALL
    private var fullOverlay: View? = null

    override fun onResume() {
        super.onResume()
        UngkerService.isLockActivityVisible = true
        moveTaskToFront()
    }

    private fun moveTaskToFront() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        try { am.moveTaskToFront(taskId, 0) } catch (_: Exception) {}
    }

    override fun onPause() {
        super.onPause()
        if (!isFinishing) checkAndRelaunch()
    }

    private fun checkAndRelaunch() {
        scope.launch {
            delay(300)
            if (!isFinishing && isWithinPrayerWindow(this@SholatLockActivity).first && !hasPledgeCredit(this@SholatLockActivity)) {
                val intent = Intent(this@SholatLockActivity, SholatLockActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    targetPackage?.let { putExtra("target_package", it) }
                }
                startActivity(intent)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetPackage = intent.getStringExtra("target_package")
        UngkerService.isLockActivityVisible = true
        applyDndBlock()
        setupLockFlags()
        addTotalLockOverlay()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() {} })
        val isExpiredMode = pledgeWasUsedAndExpired(this)
        scope.launch {
            while (true) { // Changed from while(isActive) to a safer while(true) with try-catch for CancellationException
                try {
                    delay(SHOLAT_CHECK_DELAY)
                    if (!isWithinPrayerWindow(this@SholatLockActivity).first) { finish(); break }
                } catch (e: CancellationException) {
                    // Coroutine was cancelled, exit loop gracefully
                    break
                }
            }
        }
        setContent { SideEffect { applyImmersiveMode() }; MaterialTheme { SholatLockScreen(isExpiredMode = isExpiredMode, onUnlocked = { launchTargetAndFinish() }) } }
    }

    @Suppress("DEPRECATION")
    private fun setupLockFlags() {
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_SECURE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        }
    }

    private fun applyImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun addTotalLockOverlay() {
        if (!android.provider.Settings.canDrawOverlays(this)) return
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_ERROR,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP
        val overlay = View(this).apply { setBackgroundColor(android.graphics.Color.TRANSPARENT) }
        try { wm.addView(overlay, params); fullOverlay = overlay } catch (e: Exception) {}
    }

    private fun removeTotalLockOverlay() {
        fullOverlay?.let { val wm = getSystemService(WINDOW_SERVICE) as WindowManager; try { wm.removeView(it) } catch (_: Exception) {}; fullOverlay = null }
    }

    override fun onUserLeaveHint() { super.onUserLeaveHint(); if (!hasPledgeCredit(this)) checkAndRelaunch() }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) { UngkerService.isLockActivityVisible = true; applyImmersiveMode() }
        else { if (!isFinishing) checkAndRelaunch() }
    }

    private fun applyDndBlock() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.isNotificationPolicyAccessGranted && !dndApplied) {
            originalDndFilter = nm.currentInterruptionFilter
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
            dndApplied = true
        }
    }

    private fun launchTargetAndFinish() {
        scope.launch {
            UngkerService.isLockActivityVisible = false
            removeTotalLockOverlay()
            delay(300L)
            val intentBack = targetPackage?.let { packageManager.getLaunchIntentForPackage(it) }
            intentBack?.let { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(it) }
            finish()
        }
    }

    // Added dispatchKeyEvent to intercept Home and Recents buttons
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Block Home button
        if (event.keyCode == KeyEvent.KEYCODE_HOME) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                return true // Consume the event
            }
        }
        // Block Recents (App Switch) button
        if (event.keyCode == KeyEvent.KEYCODE_APP_SWITCH) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                return true // Consume the event
            }
        }
        // Allow other key events to be handled normally
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        if (dndApplied) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            try {
                nm.setInterruptionFilter(originalDndFilter)
            } catch (_: Exception) {}
        }
        super.onDestroy()
        UngkerService.isLockActivityVisible = false
        removeTotalLockOverlay()
        scope.cancel()
    }
}

@Composable
fun SholatLockScreen(isExpiredMode: Boolean, onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val sp = remember { context.getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE) }
    val isDarkMode = remember { sp.getBoolean("dark_mode", false) }

    CompositionLocalProvider(LocalIsDarkMode provides isDarkMode) {
        var creditExpiredNow by remember { mutableStateOf(isExpiredMode) }
        LaunchedEffect(Unit) {
            while (!creditExpiredNow) {
                delay(PLEDGE_CHECK_DELAY)
                if (pledgeWasUsedAndExpired(context)) creditExpiredNow = true
            }
        }
        
        // Auto refresh location if GPS mode is on
        LaunchedEffect(Unit) {
            if (sp.getBoolean("sholat_gps_mode", false)) {
                getGpsLocation(context, onSuccess = { lat, lng, tz, label ->
                    sp.edit(commit = true) {
                        putFloat("sholat_lat", lat.toFloat())
                        putFloat("sholat_lng", lng.toFloat())
                        putInt("sholat_tz", tz)
                        putString("sholat_city_name", label)
                    }
                }, onFail = {})
            }
        }

        MaterialTheme(
            colorScheme = if (isDarkMode) darkColorScheme(
                background = DarkTheme.bgPage,
                surface = DarkTheme.bgCard,
                onSurface = DarkTheme.textPrimary,
                primary = DarkTheme.green
            ) else lightColorScheme(
                background = LightTheme.bgPage,
                surface = LightTheme.bgCard,
                onSurface = LightTheme.textPrimary,
                primary = LightTheme.green
            )
        ) {
            AnimatedContent(
                targetState = creditExpiredNow,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "mode"
            ) { expired ->
                if (expired) SholatFinalLockScreen() else SholatPledgeScreen(onUnlocked = onUnlocked)
            }
        }
    }
}

@Composable
private fun StarryBackground() {
    val isDark = LocalIsDarkMode.current
    val stars = remember { List(60) { Triple(Math.random().toFloat(), Math.random().toFloat(), Math.random().toFloat()) } }
    val inf = rememberInfiniteTransition(label = "stars")
    val twinkle by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Reverse), label = "twinkle")
    
    val starColor = if (isDark) Color.White else DarkTheme.bgPage
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        stars.forEach { (x, y, r) ->
            val alpha = ((twinkle + r) % 1f).let { if (it < 0.5f) it * 2f else (1f - it) * 2f }
            drawCircle(
                color = starColor.copy(alpha = 0.15f + alpha * 0.4f),
                radius = (1.5f + r * 2f),
                center = Offset(x * size.width, y * size.height * 0.7f)
            )
        }
    }
}

@Composable
private fun CrescentMoonOrb(prayerName: String, remainingSec: Long) {
    val isDark = LocalIsDarkMode.current
    val accentColor = greenAccent()
    val textPrimary = textPrimC()
    
    val inf = rememberInfiniteTransition(label = "glow")
    val glowAlpha by inf.animateFloat(0.3f, 0.7f, infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glowA")
    
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(180.dp)
                .background(Brush.radialGradient(listOf(accentColor.copy(alpha = glowAlpha * 0.3f), Color.Transparent)), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(140.dp)
                .background(Brush.radialGradient(listOf(cardAltBg(), cardBg())), CircleShape)
                .border(2.dp, Brush.sweepGradient(listOf(accentColor, accentColor.copy(0.4f), accentColor)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🕌", fontSize = 42.sp)
                Text(
                    prayerName.ifEmpty { "Sholat" }.uppercase(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = accentColor,
                    letterSpacing = 2.sp
                )
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    if (remainingSec > 0) {
        Box(
            modifier = Modifier
                .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            val mnt = (remainingSec / 60).coerceAtLeast(0)
            val det = (remainingSec % 60).coerceAtLeast(0)
            Text(
                "⏱ Sisa ${if (mnt > 0) "${mnt}m " else ""}${det}d",
                fontSize = 12.sp,
                color = accentColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SholatPledgeScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val sp = remember { context.getSharedPreferences(PLEDGE_PREFS, Context.MODE_PRIVATE) }
    val userRole = remember { sp.getString("user_role", "personal") ?: "personal" }
    val parentPassword = remember { sp.getString("parent_password", "") ?: "" }
    
    val isDark = LocalIsDarkMode.current
    val accentColor = greenAccent()
    val textPrimary = textPrimC()
    val textSecondary = textSecC()
    
    var remainingWindowSec by remember { mutableLongStateOf(0L) }
    var activePrayerName by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        while (true) {
            val (inWindow, name) = isWithinPrayerWindow(context)
            if (inWindow) {
                activePrayerName = name
                val lat = sp.getFloat("sholat_lat", -6.2088f).toDouble()
                val lng = sp.getFloat("sholat_lng", 106.8456f).toDouble()
                val tz = sp.getInt("sholat_tz", 7)
                val cal = Calendar.getInstance()
                val pt = hitungWaktuSholat(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH), lat, lng, tz)
                val pStr = when (name) {
                    "Subuh" -> pt.subuh
                    "Dzuhur" -> pt.dzuhur
                    "Ashar" -> pt.ashar
                    "Maghrib" -> pt.maghrib
                    else -> pt.isya
                }
                val pSec = pStr.split(":").let { it[0].toLong() * 3600 + it[1].toLong() * 60 }
                val nowSec = cal.get(Calendar.HOUR_OF_DAY) * 3600L + cal.get(Calendar.MINUTE) * 60L + cal.get(Calendar.SECOND)
                remainingWindowSec = maxOf(0L, (pSec + SHOLAT_WINDOW_MS / 1000) - nowSec)
            }
            delay(WINDOW_UPDATE_DELAY)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(pageBg())) {
        StarryBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(40.dp))
            Text(
                "WAKTU ${activePrayerName.uppercase().ifEmpty { "SHOLAT" }}",
                fontSize = 12.sp,
                letterSpacing = 4.sp,
                color = textSecondary,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(24.dp))
            CrescentMoonOrb(activePrayerName, remainingWindowSec)
            Spacer(Modifier.height(32.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(accentColor.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                    .border(1.dp, accentColor.copy(0.2f), RoundedCornerShape(24.dp))
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "إِنَّ الصَّلَاةَ كَانَتْ عَلَى الْمُؤْمِنِينَ كِتَابًا مَّوْقُوتًا",
                        color = textPrimary,
                        textAlign = TextAlign.Center,
                        fontSize = 18.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "\"Sesungguhnya shalat itu adalah kewajiban yang ditentukan waktunya atas orang-orang mukmin.\"",
                        fontSize = 12.sp,
                        color = textSecondary,
                        textAlign = TextAlign.Center,
                        fontStyle = FontStyle.Italic,
                        lineHeight = 20.sp
                    )
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            if (userRole == "parent") {
                ParentPasswordInputField(savedPassword = parentPassword, onUnlocked = onUnlocked)
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(cardBg(), RoundedCornerShape(16.dp))
                        .border(1.dp, dividerC(), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        PLEDGE_TEXT,
                        color = textPrimary,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Spacer(Modifier.height(20.dp))
                PledgeInputField(onUnlocked = onUnlocked)
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
fun ParentPasswordInputField(savedPassword: String, onUnlocked: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var success by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val accentColor = greenAccent()
    val textPrimary = textPrimC()

    LaunchedEffect(Unit) {
        delay(600)
        try { focus.requestFocus() } catch (_: Exception) {}
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Masukkan Password Orang Tua",
            color = textSecC(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        OutlinedTextField(
            value = input,
            onValueChange = { 
                if (success) return@OutlinedTextField
                input = it
                isError = false
                if (it == savedPassword) {
                    success = true
                    savePledgeCredit(context)
                    scope.launch {
                        delay(UNLOCK_DELAY_MS)
                        onUnlocked()
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus),
            placeholder = { Text("Password Orang Tua", color = textMutC(), fontSize = 14.sp) },
            singleLine = true,
            enabled = !success,
            isError = isError,
            shape = RoundedCornerShape(16.dp),
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done,
                keyboardType = KeyboardType.Password
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = textPrimary,
                unfocusedTextColor = textPrimary,
                disabledTextColor = Color(0xFF4ADE80),
                focusedBorderColor = accentColor,
                unfocusedBorderColor = dividerC(),
                errorBorderColor = Color.Red,
                disabledBorderColor = Color(0xFF4ADE80),
                focusedContainerColor = cardBg(),
                unfocusedContainerColor = cardBg(),
                disabledContainerColor = cardBg()
            )
        )

        if (isError) {
            Text(
                "Password salah!",
                color = Color.Red,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        AnimatedVisibility(visible = success) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "✅ Password Benar! Membuka kunci...",
                    color = Color(0xFF2D6A4F),
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
fun SholatFinalLockScreen() {
    val accentColor = greenAccent()
    val textPrimary = textPrimC()
    val textSecondary = textSecC()
    
    val inf = rememberInfiniteTransition(label = "final")
    val pulse by inf.animateFloat(0.9f, 1.05f, infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    
    Box(
        modifier = Modifier.fillMaxSize().background(pageBg()),
        contentAlignment = Alignment.Center
    ) {
        StarryBackground()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size((200 * pulse).dp)
                        .background(Brush.radialGradient(listOf(accentColor.copy(0.15f), Color.Transparent)), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .background(Brush.radialGradient(listOf(cardAltBg(), cardBg())), CircleShape)
                        .border(2.dp, accentColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🕌", fontSize = 56.sp)
                }
            }
            Spacer(Modifier.height(40.dp))
            Text(
                "WAKTU SHOLAT",
                fontSize = 12.sp,
                letterSpacing = 6.sp,
                color = accentColor,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Saatnya Menghadap\nAllah ﷻ",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = textPrimary,
                textAlign = TextAlign.Center,
                lineHeight = 38.sp
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "Layar terkunci hingga waktu sholat selesai.",
                fontSize = 14.sp,
                color = textSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun PledgeInputField(onUnlocked: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var success by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val accentColor = greenAccent()
    val textPrimary = textPrimC()

    LaunchedEffect(Unit) {
        delay(600)
        try { focus.requestFocus() } catch (_: Exception) {}
    }

    val isCorrect = input.trim().equals(PLEDGE_TEXT, ignoreCase = true)
    val progress = (input.length.toFloat() / PLEDGE_TEXT.length).coerceIn(0f, 1f)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape),
            color = if (isCorrect) Color(0xFF4ADE80) else accentColor,
            trackColor = trackBg()
        )

        OutlinedTextField(
            value = input,
            onValueChange = { new ->
                if (success) return@OutlinedTextField
                input = new
                if (new.trim().equals(PLEDGE_TEXT, ignoreCase = true)) {
                    success = true
                    savePledgeCredit(context)
                    scope.launch {
                        delay(UNLOCK_DELAY_MS)
                        onUnlocked()
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus),
            placeholder = { Text("Ketik janji di sini...", color = textMutC(), fontSize = 14.sp) },
            minLines = 3,
            enabled = !success,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done,
                capitalization = KeyboardCapitalization.Sentences,
                keyboardType = KeyboardType.Text
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = textPrimary,
                unfocusedTextColor = textPrimary,
                disabledTextColor = Color(0xFF4ADE80),
                focusedBorderColor = if (isCorrect) Color(0xFF4ADE80) else accentColor,
                unfocusedBorderColor = dividerC(),
                disabledBorderColor = Color(0xFF4ADE80),
                focusedContainerColor = cardBg(),
                unfocusedContainerColor = cardBg(),
                disabledContainerColor = cardBg()
            )
        )

        AnimatedVisibility(visible = success) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "✅ Alhamdulillah! Membuka kunci...",
                    color = Color(0xFF2D6A4F),
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp
                )
            }
        }
    }
}