package com.dame.ungker.ungkeh

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

const val PLEDGE_CREDIT_MS    = 2L * 60 * 1000        // 2 menit kredit setelah janji
const val SHOLAT_WINDOW_MS    = 10L * 60 * 1000       // window adzan = 10 menit
const val PLEDGE_PREFS         = "UNGKER_PREF"

/** Cek apakah sekarang sedang dalam window waktu sholat (adzan sampai +10 menit) */
fun isWithinPrayerWindow(context: Context): Pair<Boolean, String> {
    val sp = context.getSharedPreferences(PLEDGE_PREFS, Context.MODE_PRIVATE)
    val lat = sp.getFloat("sholat_lat", -6.2088f).toDouble()
    val lng = sp.getFloat("sholat_lng", 106.8456f).toDouble()
    val tz  = sp.getInt("sholat_tz", 7)

    val cal = Calendar.getInstance()
    val pt  = try {
        hitungWaktuSholat(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
            lat, lng, tz
        )
    } catch (_: Exception) { return false to "" }

    // Jam sekarang dalam desimal
    val nowH = cal.get(Calendar.HOUR_OF_DAY)
    val nowM = cal.get(Calendar.MINUTE)
    val nowDecimal = nowH + nowM / 60.0

    fun toDecimal(hhmm: String): Double {
        val p = hhmm.split(":"); return p[0].toDouble() + p[1].toDouble() / 60.0
    }

    // Nama & waktu 5 sholat wajib (bukan syuruq)
    val prayers = listOf(
        "Subuh"   to toDecimal(pt.subuh),
        "Dzuhur"  to toDecimal(pt.dzuhur),
        "Ashar"   to toDecimal(pt.ashar),
        "Maghrib" to toDecimal(pt.maghrib),
        "Isya"    to toDecimal(pt.isya),
    )

    val windowDecimal = SHOLAT_WINDOW_MS / 3_600_000.0  // 10 menit dalam desimal

    for ((name, prayerTime) in prayers) {
        if (nowDecimal >= prayerTime && nowDecimal <= prayerTime + windowDecimal) {
            return true to name
        }
    }
    return false to ""
}

/** Apakah kredit janji masih berlaku (2 menit belum habis)? */
fun hasPledgeCredit(context: Context): Boolean {
    val sp = context.getSharedPreferences(PLEDGE_PREFS, Context.MODE_PRIVATE)
    val expiry = sp.getLong("sholat_pledge_credit_expiry", 0L)
    return System.currentTimeMillis() < expiry
}

/**
 * Apakah user SUDAH pernah berjanji di window sholat YANG SEDANG AKTIF SEKARANG
 * DAN kredit 2 menitnya sudah habis?
 *
 * BUG FIX: sebelumnya cek expiry > 0 saja — ini salah karena expiry dari
 * sholat kemarin/sesi lama dianggap valid. Sekarang kita simpan
 * "sholat_pledge_session_key" berupa "{tanggal}_{nama_sholat}" sehingga
 * hanya expired di window sholat yang SAMA yang dianggap valid.
 */
fun pledgeWasUsedAndExpired(context: Context): Boolean {
    val sp = context.getSharedPreferences(PLEDGE_PREFS, Context.MODE_PRIVATE)
    val expiry     = sp.getLong("sholat_pledge_credit_expiry", 0L)
    val sessionKey = sp.getString("sholat_pledge_session_key", "") ?: ""

    // Kredit belum pernah dibuat
    if (expiry == 0L || sessionKey.isEmpty()) return false

    // Kredit masih berlaku → bukan expired
    if (System.currentTimeMillis() < expiry) return false

    // Cek apakah session key cocok dengan window sholat YANG SEDANG AKTIF
    val currentKey = getCurrentPrayerSessionKey(context)
    if (currentKey.isEmpty()) return false   // Tidak dalam window sholat → tidak berlaku

    // Hanya expired jika dari window sholat yang SAMA dengan sekarang
    return sessionKey == currentKey
}

/**
 * Buat session key unik untuk window sholat yang sedang aktif.
 * Format: "2026-03-09_Ashar" — berubah setiap hari dan setiap waktu sholat.
 * Return string kosong jika tidak sedang dalam window sholat.
 */
fun getCurrentPrayerSessionKey(context: Context): String {
    val sp  = context.getSharedPreferences(PLEDGE_PREFS, Context.MODE_PRIVATE)
    val lat = sp.getFloat("sholat_lat", -6.2088f).toDouble()
    val lng = sp.getFloat("sholat_lng", 106.8456f).toDouble()
    val tz  = sp.getInt("sholat_tz", 7)
    val cal = Calendar.getInstance()
    val pt  = try {
        hitungWaktuSholat(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH), lat, lng, tz
        )
    } catch (_: Exception) { return "" }

    fun toSec(hhmm: String): Long {
        val p = hhmm.split(":"); return p[0].toLong() * 3600 + p[1].toLong() * 60
    }
    val nowSec  = cal.get(Calendar.HOUR_OF_DAY) * 3600L +
            cal.get(Calendar.MINUTE) * 60L +
            cal.get(Calendar.SECOND)
    val prayers = listOf(
        "Subuh"   to toSec(pt.subuh),
        "Dzuhur"  to toSec(pt.dzuhur),
        "Ashar"   to toSec(pt.ashar),
        "Maghrib" to toSec(pt.maghrib),
        "Isya"    to toSec(pt.isya),
    )
    val dateStr = "%04d-%02d-%02d".format(
        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
    )
    for ((name, pSec) in prayers) {
        if (nowSec in pSec..(pSec + 600L)) return "${dateStr}_$name"
    }
    return ""
}

/** Simpan kredit janji baru, dikaitkan dengan session window sholat saat ini */
fun savePledgeCredit(context: Context) {
    val sp         = context.getSharedPreferences(PLEDGE_PREFS, Context.MODE_PRIVATE)
    val sessionKey = getCurrentPrayerSessionKey(context)
    sp.edit()
        .putLong("sholat_pledge_credit_expiry", System.currentTimeMillis() + PLEDGE_CREDIT_MS)
        .putString("sholat_pledge_session_key", sessionKey)
        .apply()
}

// ────────────────────────────────────────────────────────────────────────────

class SholatLockActivity : ComponentActivity() {

    private var targetPackage: String? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetPackage = intent.getStringExtra("target_package")
        setupLockFlags()

        // Blokir tombol back sepenuhnya — di kedua mode
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* tidak melakukan apa-apa */ }
        })

        // Tentukan mode layar saat Activity dibuat:
        // isExpiredMode = true  → kredit sudah habis → layar "final lock"
        // isExpiredMode = false → belum pernah janji  → form janji
        val isExpiredMode = pledgeWasUsedAndExpired(this)

        setContent {
            val sharedPref = remember { getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE) }
            val isDarkMode = remember { sharedPref.getBoolean("dark_mode", false) }

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
                        modifier = Modifier.fillMaxSize(),
                        color = if (isDarkMode) Color(0xFF0F172A) else Color(0xFFF8F9FA)
                    ) {
                        SholatLockScreen(
                            isExpiredMode = isExpiredMode,
                            onUnlocked    = { launchTargetAndFinish() }
                        )
                    }
                }
            }
        }
    }

    private fun setupLockFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(false)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    private fun launchTargetAndFinish() {
        scope.launch {
            savePledgeCredit(this@SholatLockActivity)
            delay(800)  // beri waktu animasi sukses
            val pm = packageManager
            val intentBack = targetPackage?.let { pm.getLaunchIntentForPackage(it) }
            if (intentBack != null) {
                intentBack.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intentBack)
            }
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

// ─── UI Layar Kunci ──────────────────────────────────────────────────────────

@Composable
fun SholatLockScreen(isExpiredMode: Boolean, onUnlocked: () -> Unit) {
    val context = LocalContext.current

    // Pantau expiry secara realtime — jika kredit habis SAAT layar terbuka,
    // transisi ke mode final lock tanpa perlu restart Activity
    var creditExpiredNow by remember { mutableStateOf(isExpiredMode) }

    LaunchedEffect(Unit) {
        while (!creditExpiredNow) {
            delay(1000)
            if (pledgeWasUsedAndExpired(context)) {
                creditExpiredNow = true
            }
        }
    }

    AnimatedContent(
        targetState = creditExpiredNow,
        transitionSpec = {
            fadeIn(tween(600)) togetherWith fadeOut(tween(300))
        },
        label = "lockModeTransition"
    ) { expired ->
        if (expired) {
            // ── Mode B: Kredit habis — layar final, tidak bisa dibuka ──────
            SholatFinalLockScreen()
        } else {
            // ── Mode A: Belum pernah janji — tampilkan form janji ──────────
            SholatPledgeScreen(onUnlocked = onUnlocked)
        }
    }
}

// ─── Mode A: Layar Form Janji ────────────────────────────────────────────────

@Composable
fun SholatPledgeScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    var remainingWindowSec by remember { mutableLongStateOf(0L) }
    var activePrayerName   by remember { mutableStateOf("") }

    // Hitung sisa waktu window adzan setiap detik
    LaunchedEffect(Unit) {
        while (true) {
            val sp  = context.getSharedPreferences(PLEDGE_PREFS, Context.MODE_PRIVATE)
            val lat = sp.getFloat("sholat_lat", -6.2088f).toDouble()
            val lng = sp.getFloat("sholat_lng", 106.8456f).toDouble()
            val tz  = sp.getInt("sholat_tz", 7)
            val cal = Calendar.getInstance()
            val pt  = try {
                hitungWaktuSholat(
                    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1,
                    cal.get(Calendar.DAY_OF_MONTH), lat, lng, tz
                )
            } catch (_: Exception) { null }

            if (pt != null) {
                fun toSec(hhmm: String): Long {
                    val p = hhmm.split(":")
                    return p[0].toLong() * 3600 + p[1].toLong() * 60
                }
                val cal2 = Calendar.getInstance()
                val nowSec = cal2.get(Calendar.HOUR_OF_DAY) * 3600L +
                        cal2.get(Calendar.MINUTE) * 60L +
                        cal2.get(Calendar.SECOND)
                val prayers = listOf(
                    "Subuh"   to toSec(pt.subuh),
                    "Dzuhur"  to toSec(pt.dzuhur),
                    "Ashar"   to toSec(pt.ashar),
                    "Maghrib" to toSec(pt.maghrib),
                    "Isya"    to toSec(pt.isya),
                )
                for ((name, pSec) in prayers) {
                    val endSec = pSec + 600L
                    if (nowSec in pSec..endSec) {
                        activePrayerName   = name
                        remainingWindowSec = endSec - nowSec
                        break
                    }
                }
            }
            delay(1000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0D1B2A), Color(0xFF1B3A5C), Color(0xFF0D1B2A))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            MosqueAnimatedHeader(activePrayerName, remainingWindowSec)

            Text(
                "Untuk melanjutkan, ketikkan janji di bawah ini dengan tepat:",
                style     = MaterialTheme.typography.bodyMedium,
                color     = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center
            )

            PledgeTargetBox()

            PledgeInputField(onUnlocked = onUnlocked)

            Spacer(modifier = Modifier.height(8.dp))

            // Ayat An-Nisa 103
            Surface(
                color = Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "إِنَّ الصَّلَاةَ كَانَتْ عَلَى الْمُؤْمِنِينَ كِتَابًا مَّوْقُوتًا",
                        style      = MaterialTheme.typography.titleMedium,
                        color      = Color(0xFFFFD700),
                        textAlign  = TextAlign.Center,
                        lineHeight = 32.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "\"Sesungguhnya shalat itu adalah kewajiban yang ditentukan waktunya atas orang-orang mukmin.\"",
                        style     = MaterialTheme.typography.bodySmall,
                        color     = Color.White.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        fontStyle = FontStyle.Italic
                    )
                    Text(
                        "— QS. An-Nisa: 103",
                        style    = MaterialTheme.typography.labelSmall,
                        color    = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

// ─── Mode B: Layar Final Lock — kredit habis, tidak bisa dibuka ──────────────

@Composable
fun SholatFinalLockScreen() {
    val context = LocalContext.current
    var activePrayerName   by remember { mutableStateOf("") }
    var remainingWindowSec by remember { mutableLongStateOf(0L) }

    // Tetap hitung sisa waktu agar user tahu kapan kunci ini berakhir
    LaunchedEffect(Unit) {
        while (true) {
            val sp  = context.getSharedPreferences(PLEDGE_PREFS, Context.MODE_PRIVATE)
            val lat = sp.getFloat("sholat_lat", -6.2088f).toDouble()
            val lng = sp.getFloat("sholat_lng", 106.8456f).toDouble()
            val tz  = sp.getInt("sholat_tz", 7)
            val cal = Calendar.getInstance()
            val pt  = try {
                hitungWaktuSholat(
                    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1,
                    cal.get(Calendar.DAY_OF_MONTH), lat, lng, tz
                )
            } catch (_: Exception) { null }

            if (pt != null) {
                fun toSec(hhmm: String): Long {
                    val p = hhmm.split(":")
                    return p[0].toLong() * 3600 + p[1].toLong() * 60
                }
                val cal2    = Calendar.getInstance()
                val nowSec  = cal2.get(Calendar.HOUR_OF_DAY) * 3600L +
                        cal2.get(Calendar.MINUTE) * 60L +
                        cal2.get(Calendar.SECOND)
                val prayers = listOf(
                    "Subuh"   to toSec(pt.subuh),
                    "Dzuhur"  to toSec(pt.dzuhur),
                    "Ashar"   to toSec(pt.ashar),
                    "Maghrib" to toSec(pt.maghrib),
                    "Isya"    to toSec(pt.isya),
                )
                for ((name, pSec) in prayers) {
                    val endSec = pSec + 600L
                    if (nowSec in pSec..endSec) {
                        activePrayerName   = name
                        remainingWindowSec = endSec - nowSec
                        break
                    }
                }
            }
            delay(1000)
        }
    }

    // Animasi pulsa pada ikon
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseScale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.5f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "glowAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0A0F1E), Color(0xFF0D2137), Color(0xFF0A0F1E))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            // ── Ikon masjid berpendar ────────────────────────────────────
            Box(contentAlignment = Alignment.Center) {
                // Cahaya luar
                Box(
                    modifier = Modifier
                        .size((120 * pulseScale).dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1565C0).copy(alpha = glowAlpha * 0.4f))
                )
                // Lingkaran dalam
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1A3A6B).copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🕌", fontSize = 46.sp)
                }
            }

            // ── Pesan utama ───────────────────────────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    if (activePrayerName.isNotEmpty()) "Waktu $activePrayerName" else "Waktu Sholat",
                    style      = MaterialTheme.typography.titleMedium,
                    color      = Color(0xFFFFA726),
                    fontWeight = FontWeight.Bold,
                    textAlign  = TextAlign.Center
                )
                Text(
                    "Kamu sudah menggunakan aplikasi yang kamu mau selama 2 menit.",
                    style      = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color      = Color.White,
                    textAlign  = TextAlign.Center,
                    lineHeight = 32.sp
                )
                Text(
                    "Saatnya memenuhi panggilan sang pencipta kawan.",
                    style      = MaterialTheme.typography.titleMedium,
                    color      = Color(0xFFFFD700),
                    fontWeight = FontWeight.Medium,
                    textAlign  = TextAlign.Center,
                    fontStyle  = FontStyle.Italic
                )
            }

            // ── Divider bintang ───────────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) {
                    Text("✦", fontSize = 10.sp, color = Color(0xFFFFA726).copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.width(6.dp))
                }
            }

            // ── Ayat pengingat ────────────────────────────────────────────
            Surface(
                color = Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "حَافِظُوا عَلَى الصَّلَوَاتِ وَالصَّلَاةِ الْوُسْطَىٰ",
                        style      = MaterialTheme.typography.titleLarge,
                        color      = Color(0xFFFFD700),
                        textAlign  = TextAlign.Center,
                        lineHeight = 38.sp
                    )
                    Text(
                        "\"Peliharalah semua shalat dan shalat wustha (Ashar). Dan laksanakanlah (shalat) karena Allah dengan khusyuk.\"",
                        style     = MaterialTheme.typography.bodySmall,
                        color     = Color.White.copy(alpha = 0.55f),
                        textAlign = TextAlign.Center,
                        fontStyle = FontStyle.Italic,
                        lineHeight = 20.sp
                    )
                    Text(
                        "— QS. Al-Baqarah: 238",
                        style    = MaterialTheme.typography.labelSmall,
                        color    = Color.White.copy(alpha = 0.35f)
                    )
                }
            }

            // ── Sisa waktu kunci ini aktif ────────────────────────────────
            if (remainingWindowSec > 0) {
                val min = remainingWindowSec / 60
                val sec = remainingWindowSec % 60
                Surface(
                    color = Color(0xFFEF5350).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("🔒", fontSize = 14.sp)
                        Text(
                            "Terkunci %02d:%02d lagi".format(min, sec),
                            style      = MaterialTheme.typography.labelMedium,
                            color      = Color(0xFFEF9A9A),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                // Window sudah lewat — tampilkan info
                Surface(
                    color = Color(0xFF2E7D32).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        "✅ Waktu sholat sudah berlalu",
                        modifier  = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style     = MaterialTheme.typography.labelMedium,
                        color     = Color(0xFF81C784),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun MosqueAnimatedHeader(prayerName: String, remainingSec: Long) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "glowAlpha"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Ikon masjid berpendar
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(Color(0xFF1565C0).copy(alpha = glowAlpha * 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Text("🕌", fontSize = 52.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            if (prayerName.isNotEmpty()) "Waktu $prayerName Telah Tiba" else "Waktu Sholat",
            style      = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color      = Color.White,
            textAlign  = TextAlign.Center
        )

        if (remainingSec > 0) {
            val min = remainingSec / 60
            val sec = remainingSec % 60
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                color = Color(0xFFFFA726).copy(alpha = 0.15f),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    "⏱ Layar ini aktif selama %02d:%02d lagi".format(min, sec),
                    modifier  = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    style     = MaterialTheme.typography.labelMedium,
                    color     = Color(0xFFFFA726),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ── Kotak Teks Target ────────────────────────────────────────────────────────

@Composable
fun PledgeTargetBox() {
    Surface(
        color = Color.White.copy(alpha = 0.08f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text       = PLEDGE_TEXT,
            modifier   = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            style      = MaterialTheme.typography.bodyLarge,
            color      = Color.White.copy(alpha = 0.9f),
            textAlign  = TextAlign.Center,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ── Input Janji dengan Highlight Karakter ────────────────────────────────────

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun PledgeInputField(onUnlocked: () -> Unit) {
    var inputValue by remember { mutableStateOf(TextFieldValue("")) }
    var isSuccess  by remember { mutableStateOf(false) }
    var isError    by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(300)
        focusRequester.requestFocus()
    }

    // Hitung progress kecocokan karakter
    val userText   = inputValue.text
    val matchCount = userText.zip(PLEDGE_TEXT).count { (u, t) -> u == t }
    val progress   = if (PLEDGE_TEXT.isNotEmpty()) matchCount.toFloat() / PLEDGE_TEXT.length else 0f

    // Validasi: teks harus PERSIS sama
    val isExactMatch = userText == PLEDGE_TEXT

    // Cek apakah input sudah mulai menyimpang
    val hasError = userText.isNotEmpty() && !PLEDGE_TEXT.startsWith(userText) &&
            !userText.startsWith(PLEDGE_TEXT.take(userText.length))

    // Effect ketika berhasil
    LaunchedEffect(isExactMatch) {
        if (isExactMatch && !isSuccess) {
            isSuccess = true
            delay(600)
            onUnlocked()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // ── Progress bar ─────────────────────────────────────────────────
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${(progress * 100).toInt()}% selesai",
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        isSuccess  -> Color(0xFF66BB6A)
                        hasError   -> Color(0xFFEF5350)
                        else       -> Color.White.copy(alpha = 0.5f)
                    }
                )
                Text(
                    "$matchCount / ${PLEDGE_TEXT.length} karakter",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.4f)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.15f))
            ) {
                val animProg by animateFloatAsState(
                    targetValue = progress,
                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                    label = "progress"
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animProg)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            when {
                                isSuccess -> Color(0xFF66BB6A)
                                hasError  -> Color(0xFFEF5350)
                                else      -> Color(0xFFFFA726)
                            }
                        )
                )
            }
        }

        // ── TextField utama ───────────────────────────────────────────────
        val borderColor = when {
            isSuccess -> Color(0xFF66BB6A)
            hasError  -> Color(0xFFEF5350).copy(alpha = 0.6f)
            else      -> Color.White.copy(alpha = 0.2f)
        }

        OutlinedTextField(
            value         = if (isSuccess) inputValue.copy(text = PLEDGE_TEXT) else inputValue,
            onValueChange = { newVal ->
                if (!isSuccess) {
                    // Batasi agar tidak melebihi panjang target
                    val trimmed = if (newVal.text.length > PLEDGE_TEXT.length)
                        newVal.copy(text = newVal.text.take(PLEDGE_TEXT.length),
                            selection = TextRange(PLEDGE_TEXT.length))
                    else newVal
                    inputValue = trimmed
                    isError = hasError
                }
            },
            modifier       = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            placeholder    = {
                Text(
                    "Ketik janji di sini...",
                    color = Color.White.copy(alpha = 0.3f)
                )
            },
            minLines       = 4,
            maxLines       = 6,
            shape          = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType    = KeyboardType.Text,
                capitalization  = KeyboardCapitalization.Sentences,
                imeAction       = ImeAction.Done
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor      = Color.White,
                unfocusedTextColor    = Color.White,
                focusedBorderColor    = borderColor,
                unfocusedBorderColor  = borderColor,
                cursorColor           = Color(0xFFFFA726),
                focusedContainerColor = Color.White.copy(alpha = 0.07f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
            ),
            trailingIcon = if (isSuccess) ({
                Text("✅", fontSize = 20.sp, modifier = Modifier.padding(8.dp))
            }) else null
        )

        // ── Pesan status ──────────────────────────────────────────────────
        AnimatedVisibility(visible = hasError && !isSuccess) {
            Text(
                "⚠️ Pastikan teks sesuai dengan yang tertulis di atas",
                style     = MaterialTheme.typography.labelMedium,
                color     = Color(0xFFEF5350),
                textAlign = TextAlign.Center,
                modifier  = Modifier.fillMaxWidth()
            )
        }

        AnimatedVisibility(visible = isSuccess) {
            Surface(
                color = Color(0xFF2E7D32).copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text("✅", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Aamiin! Kamu diberi 2 menit. Sholat dulu ya 🙏",
                        style      = MaterialTheme.typography.bodyMedium,
                        color      = Color(0xFF66BB6A),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ── Petunjuk ──────────────────────────────────────────────────────
        if (!isSuccess && !hasError) {
            Text(
                "Ketik persis seperti teks di atas, termasuk tanda baca",
                style     = MaterialTheme.typography.labelSmall,
                color     = Color.White.copy(alpha = 0.35f),
                textAlign = TextAlign.Center,
                modifier  = Modifier.fillMaxWidth()
            )
        }
    }
}