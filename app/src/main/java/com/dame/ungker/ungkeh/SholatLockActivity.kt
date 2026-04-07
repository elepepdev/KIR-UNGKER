package com.dame.ungker.ungkeh

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
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) { setShowWhenLocked(true); setTurnScreenOn(true); (getSystemService(KEYGUARD_SERVICE) as KeyguardManager).requestDismissKeyguard(this, null) }
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
        if (nm.isNotificationPolicyAccessGranted && !dndApplied) { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE); dndApplied = true }
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

    override fun onDestroy() { super.onDestroy(); UngkerService.isLockActivityVisible = false; removeTotalLockOverlay(); scope.cancel() }
}

@Composable
fun SholatLockScreen(isExpiredMode: Boolean, onUnlocked: () -> Unit) {
    val context = LocalContext.current
    var creditExpiredNow by remember { mutableStateOf(isExpiredMode) }
    LaunchedEffect(Unit) { while (!creditExpiredNow) { delay(PLEDGE_CHECK_DELAY); if (pledgeWasUsedAndExpired(context)) creditExpiredNow = true } }
    AnimatedContent(targetState = creditExpiredNow, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "mode") { expired -> if (expired) SholatFinalLockScreen() else SholatPledgeScreen(onUnlocked = onUnlocked) }
}

private val NavyDeep   = Color(0xFF060D1F); private val NavyMid    = Color(0xFF0C1A3A); private val NavyLight  = Color(0xFF112244); private val Gold       = Color(0xFFD4A847); private val GoldLight  = Color(0xFFF0CC6A); private val GoldDim    = Gold.copy(alpha = 0.25f); private val WhiteSoft  = Color(0xFFF0EDE6); private val WhiteDim   = Color(0xFFB8B4AC)

@Composable
private fun StarryBackground() {
    val stars = remember { List(60) { Triple(Math.random().toFloat(), Math.random().toFloat(), Math.random().toFloat()) } }
    val inf = rememberInfiniteTransition(label = "stars"); val twinkle by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Reverse), label = "twinkle")
    Canvas(modifier = Modifier.fillMaxSize()) { stars.forEach { (x, y, r) -> val alpha = ((twinkle + r) % 1f).let { if (it < 0.5f) it * 2f else (1f - it) * 2f }; drawCircle(color = Color.White.copy(alpha = 0.15f + alpha * 0.5f), radius = (1.5f + r * 2f), center = Offset(x * size.width, y * size.height * 0.6f)) } }
}

@Composable
private fun CrescentMoonOrb(prayerName: String, remainingSec: Long) {
    val inf = rememberInfiniteTransition(label = "glow"); val glowAlpha by inf.animateFloat(0.3f, 0.7f, infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glowA")
    Box(contentAlignment = Alignment.Center) { Box(modifier = Modifier.size(180.dp).background(Brush.radialGradient(listOf(Gold.copy(alpha = glowAlpha * 0.3f), Color.Transparent)), CircleShape)); Box(modifier = Modifier.size(140.dp).background(Brush.radialGradient(listOf(NavyLight, NavyDeep)), CircleShape).border(1.5.dp, Brush.sweepGradient(listOf(Gold, GoldLight, Gold, NavyMid, Gold)), CircleShape), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("🕌", fontSize = 42.sp); Text(prayerName.ifEmpty { "Sholat" }, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = GoldLight, letterSpacing = 2.sp) } } }
    Spacer(Modifier.height(16.dp))
    if (remainingSec > 0) { Box(modifier = Modifier.background(GoldDim, RoundedCornerShape(20.dp)).border(1.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 6.dp)) { val mnt = (remainingSec / 60).coerceAtLeast(0); val det = (remainingSec % 60).coerceAtLeast(0); Text("⏱ Sisa ${if (mnt > 0) "${mnt}m " else ""}${det}d", fontSize = 12.sp, color = GoldLight, fontWeight = FontWeight.SemiBold) } }
}

@Composable
fun SholatPledgeScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current; var remainingWindowSec by remember { mutableLongStateOf(0L) }; var activePrayerName by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val (inWindow, name) = isWithinPrayerWindow(context)
            if (inWindow) {
                activePrayerName = name; val sp = context.getSharedPreferences(PLEDGE_PREFS, Context.MODE_PRIVATE); val lat = sp.getFloat("sholat_lat", -6.2088f).toDouble(); val lng = sp.getFloat("sholat_lng", 106.8456f).toDouble(); val tz = sp.getInt("sholat_tz", 7); val cal = Calendar.getInstance(); val pt = hitungWaktuSholat(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH), lat, lng, tz); val pStr = when (name) { "Subuh" -> pt.subuh; "Dzuhur" -> pt.dzuhur; "Ashar" -> pt.ashar; "Maghrib" -> pt.maghrib; else -> pt.isya }; val pSec = pStr.split(":").let { it[0].toLong() * 3600 + it[1].toLong() * 60 }; val nowSec = cal.get(Calendar.HOUR_OF_DAY) * 3600L + cal.get(Calendar.MINUTE) * 60L + cal.get(Calendar.SECOND)
                remainingWindowSec = maxOf(0L, (pSec + SHOLAT_WINDOW_MS / 1000) - nowSec)
            }
            delay(WINDOW_UPDATE_DELAY)
        }
    }
    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(NavyDeep, NavyMid, Color(0xFF0A1628), NavyDeep)))) {
        StarryBackground()
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(40.dp)); Text("WAKTU ${activePrayerName.uppercase().ifEmpty { "SHOLAT" }}", fontSize = 11.sp, letterSpacing = 4.sp, color = Gold.copy(alpha = 0.7f), fontWeight = FontWeight.Bold); Spacer(Modifier.height(20.dp)); CrescentMoonOrb(activePrayerName, remainingWindowSec); Spacer(Modifier.height(24.dp)); Box(modifier = Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(GoldDim, Color(0x10D4A847), GoldDim)), RoundedCornerShape(16.dp)).border(1.dp, Gold.copy(0.2f), RoundedCornerShape(16.dp)).padding(horizontal = 20.dp, vertical = 16.dp)) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("إِنَّ الصَّلَاةَ كَانَتْ عَلَى الْمُؤْمِنِينَ كِتَابًا مَّوْقُوتًا", color = GoldLight, textAlign = TextAlign.Center, fontSize = 16.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium); Spacer(Modifier.height(8.dp)); Text(
                """Sesungguhnya shalat itu adalah kewajiban yang ditentukan waktunya
atas orang-orang mukmin.""", // Using raw string for multiline text
                fontSize = 11.sp,
                color = WhiteDim,
                textAlign = TextAlign.Center,
                fontStyle = FontStyle.Italic,
                lineHeight = 18.sp
            ) } }; Spacer(Modifier.height(24.dp)); Box(modifier = Modifier.fillMaxWidth().background(Color.White.copy(0.04f), RoundedCornerShape(14.dp)).border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(14.dp)).padding(16.dp)) { Text(PLEDGE_TEXT, color = WhiteSoft.copy(0.9f), textAlign = TextAlign.Center, fontSize = 13.sp, lineHeight = 22.sp) }; Spacer(Modifier.height(16.dp)); PledgeInputField(onUnlocked = onUnlocked); Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
fun SholatFinalLockScreen() {
    val inf = rememberInfiniteTransition(label = "final"); val pulse by inf.animateFloat(0.85f, 1f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF030610), Color(0xFF07112A), Color(0xFF030610)))), contentAlignment = Alignment.Center) { StarryBackground(); Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 32.dp)) { Box(contentAlignment = Alignment.Center) { Box(modifier = Modifier.size((200 * pulse).dp).background(Brush.radialGradient(listOf(Gold.copy(0.12f), Color.Transparent)), CircleShape)); Box(modifier = Modifier.size(130.dp).background(Brush.radialGradient(listOf(Color(0xFF0D1E40), Color(0xFF060D1F))), CircleShape).border(1.dp, Brush.sweepGradient(listOf(Gold, GoldLight, Gold, Color(0xFF0D1E40), Gold)), CircleShape), contentAlignment = Alignment.Center) { Text("🕌", fontSize = 52.sp) } }; Spacer(Modifier.height(32.dp)); Text("WAKTU SHOLAT", fontSize = 11.sp, letterSpacing = 5.sp, color = Gold.copy(0.7f), fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text(
        """Saatnya Menghadap
Allah ﷻ""", // Using raw string for multiline text
        fontSize = 28.sp,
        fontWeight = FontWeight.ExtraBold,
        color = WhiteSoft,
        textAlign = TextAlign.Center,
        lineHeight = 36.sp
    ) } }
}

@Composable
fun PledgeInputField(onUnlocked: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var success by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        delay(500)
        try { focus.requestFocus() } catch (_: Exception) {}
    }

    val isCorrect = input.trim().equals(PLEDGE_TEXT, ignoreCase = true)
    val progress = (input.length.toFloat() / PLEDGE_TEXT.length).coerceIn(0f, 1f)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = if (isCorrect) Color(0xFF4ADE80) else Gold,
            trackColor = Color.White.copy(0.1f)
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
            placeholder = { Text("Ketik janji di sini...", color = Color.White.copy(0.3f), fontSize = 14.sp) },
            minLines = 3,
            enabled = !success,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done,
                capitalization = KeyboardCapitalization.Sentences,
                keyboardType = KeyboardType.Text
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = WhiteSoft,
                unfocusedTextColor = WhiteSoft,
                disabledTextColor = Color(0xFF4ADE80),
                focusedBorderColor = if (isCorrect) Color(0xFF4ADE80) else Gold.copy(0.6f),
                unfocusedBorderColor = Color.White.copy(0.12f),
                disabledBorderColor = Color(0xFF4ADE80)
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
                    color = Color(0xFF4ADE80),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}