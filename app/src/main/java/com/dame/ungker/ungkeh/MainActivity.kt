package com.dame.ungker.ungkeh

import android.Manifest
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
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
import java.util.concurrent.TimeUnit
import kotlin.math.*

data class AppInfo(
    val name: String,
    val packageName: String,
    val category: String
)

// ── Dark Mode state — disimpan di level Activity, diteruskan sebagai parameter ──
// Menggunakan compositionLocalOf yang sudah tersedia via import androidx.compose.runtime.*
val LocalIsDarkMode = compositionLocalOf { false }

// ══════════════════════════════════════════════════════════════════════════════
// TOKEN WARNA DARK/LIGHT — satu sumber kebenaran untuk seluruh UI
// Semua composable mengambil warna dari sini, bukan hardcode Color(0xFF...)
// ══════════════════════════════════════════════════════════════════════════════
object DarkTheme {
    // Background layers
    val bgPage      = Color(0xFF0F172A)   // background halaman
    val bgCard      = Color(0xFF1E293B)   // card utama
    val bgCardAlt   = Color(0xFF263348)   // card secondary / icon background
    val bgInput     = Color(0xFF1E293B)   // text field background
    val bgTrack     = Color(0xFF334155)   // progress bar track

    // Teks
    val textPrimary = Color(0xFFF1F5F9)   // heading, nilai penting
    val textSecond  = Color(0xFF94A3B8)   // subtext, label
    val textMuted   = Color(0xFF64748B)   // placeholder, hint

    // Warna aksen (tetap sama di dark/light)
    val green       = Color(0xFF4ADE80)
    val greenDark   = Color(0xFF2E7D32)   // dipakai teks & border
    val greenBg     = Color(0xFF14532D)   // background pill hijau gelap
    val amber       = Color(0xFFFBBF24)
    val red         = Color(0xFFEF4444)

    // Border
    val border      = Color(0xFF334155)
    val divider     = Color(0xFF1E293B)

    // Canvas / arc
    val arcTrack    = Color(0xFF334155)
    val sunCircle   = Color(0xFF1E293B)
    val horizon     = Color(0xFF334155)
}

object LightTheme {
    val bgPage      = Color(0xFFF8F9FA)
    val bgCard      = Color.White
    val bgCardAlt   = Color(0xFFF0F0F0)
    val bgInput     = Color.White
    val bgTrack     = Color(0xFFF3F4F6)

    val textPrimary = Color(0xFF1A1A1A)
    val textSecond  = Color(0xFF6B7280)
    val textMuted   = Color(0xFF9CA3AF)

    val green       = Color(0xFF2E7D32)
    val greenDark   = Color(0xFF2E7D32)
    val greenBg     = Color(0xFFE8F5E9)
    val amber       = Color(0xFFFFA000)
    val red         = Color(0xFFE53935)

    val border      = Color(0xFFE0E0E0)
    val divider     = Color(0xFFEEEEEE)

    val arcTrack    = Color(0xFFEEEEEE)
    val sunCircle   = Color.White
    val horizon     = Color(0xFFE8E8E8)
}

/** Shortcut: ambil token warna sesuai mode saat ini */
@Composable
fun cardBg()      = if (LocalIsDarkMode.current) DarkTheme.bgCard      else LightTheme.bgCard
@Composable
fun cardAltBg()   = if (LocalIsDarkMode.current) DarkTheme.bgCardAlt   else LightTheme.bgCardAlt
@Composable
fun pageBg()      = if (LocalIsDarkMode.current) DarkTheme.bgPage      else LightTheme.bgPage
@Composable
fun textPrimC()   = if (LocalIsDarkMode.current) DarkTheme.textPrimary else LightTheme.textPrimary
@Composable
fun textSecC()    = if (LocalIsDarkMode.current) DarkTheme.textSecond  else LightTheme.textSecond
@Composable
fun textMutC()    = if (LocalIsDarkMode.current) DarkTheme.textMuted   else LightTheme.textMuted
@Composable
fun trackBg()     = if (LocalIsDarkMode.current) DarkTheme.bgTrack     else LightTheme.bgTrack
@Composable
fun greenAccent() = if (LocalIsDarkMode.current) DarkTheme.green       else LightTheme.green
@Composable
fun greenBgC()    = if (LocalIsDarkMode.current) DarkTheme.greenBg     else LightTheme.greenBg
@Composable
fun dividerC()    = if (LocalIsDarkMode.current) DarkTheme.divider     else LightTheme.divider
@Composable
fun borderC()     = if (LocalIsDarkMode.current) DarkTheme.border      else LightTheme.border

// ══════════════════════════════════════════════════════════════════════════════
// HISAB WAKTU SHOLAT — METODE KEMENAG RI
// Referensi: "Ilmu Falak Praktis" Kemenag RI
// Fajr: 20°, Isha: 18°, Ihtiyat: +2 mnt, Asar: mazhab Syafi'i
// ══════════════════════════════════════════════════════════════════════════════

data class PrayerTimes(
    val subuh: String,
    val syuruq: String,
    val dzuhur: String,
    val ashar: String,
    val maghrib: String,
    val isya: String,
    val syuruqDecimal: Double,   // untuk arc matahari
    val maghribDecimal: Double,  // untuk arc matahari
    val nowDecimal: Double,      // jam sekarang dalam desimal
)

data class CityLocation(val name: String, val lat: Double, val lng: Double, val tzOffset: Int, val province: String)

/** 43 kota besar Indonesia dengan koordinat akurat */
val INDONESIA_CITIES: List<CityLocation> = listOf(
    CityLocation("Banda Aceh",      5.5483,  95.3238, 7, "Aceh"),
    CityLocation("Medan",           3.5952,  98.6722, 7, "Sumatera Utara"),
    CityLocation("Padang",         -0.9471, 100.4172, 7, "Sumatera Barat"),
    CityLocation("Pekanbaru",       0.5335, 101.4474, 7, "Riau"),
    CityLocation("Batam",           1.0746, 104.0305, 7, "Kepulauan Riau"),
    CityLocation("Jambi",          -1.6102, 103.6131, 7, "Jambi"),
    CityLocation("Palembang",      -2.9761, 104.7754, 7, "Sumatera Selatan"),
    CityLocation("Bengkulu",       -3.7928, 102.2608, 7, "Bengkulu"),
    CityLocation("Bandar Lampung", -5.3971, 105.2668, 7, "Lampung"),
    CityLocation("Pangkalpinang",  -2.1337, 106.1164, 7, "Bangka Belitung"),
    CityLocation("Jakarta",        -6.2088, 106.8456, 7, "DKI Jakarta"),
    CityLocation("Bogor",          -6.5971, 106.8060, 7, "Jawa Barat"),
    CityLocation("Bandung",        -6.9175, 107.6191, 7, "Jawa Barat"),
    CityLocation("Bekasi",         -6.2383, 106.9756, 7, "Jawa Barat"),
    CityLocation("Depok",          -6.4025, 106.7942, 7, "Jawa Barat"),
    CityLocation("Tangerang",      -6.1783, 106.6319, 7, "Banten"),
    CityLocation("Serang",         -6.1204, 106.1503, 7, "Banten"),
    CityLocation("Semarang",       -6.9932, 110.4203, 7, "Jawa Tengah"),
    CityLocation("Solo",           -7.5642, 110.8317, 7, "Jawa Tengah"),
    CityLocation("Yogyakarta",     -7.7956, 110.3695, 7, "DI Yogyakarta"),
    CityLocation("Surabaya",       -7.2575, 112.7521, 7, "Jawa Timur"),
    CityLocation("Malang",         -7.9654, 112.6326, 7, "Jawa Timur"),
    CityLocation("Kediri",         -7.8480, 112.0178, 7, "Jawa Timur"),
    CityLocation("Denpasar",       -8.6705, 115.2126, 8, "Bali"),
    CityLocation("Mataram",        -8.5833, 116.1167, 8, "NTB"),
    CityLocation("Kupang",        -10.1789, 123.6070, 8, "NTT"),
    CityLocation("Pontianak",       0.0263, 109.3425, 7, "Kalimantan Barat"),
    CityLocation("Palangkaraya",   -2.2136, 113.9108, 7, "Kalimantan Tengah"),
    CityLocation("Banjarmasin",    -3.3194, 114.5908, 8, "Kalimantan Selatan"),
    CityLocation("Samarinda",      -0.5022, 117.1536, 8, "Kalimantan Timur"),
    CityLocation("Balikpapan",     -1.2654, 116.8312, 8, "Kalimantan Timur"),
    CityLocation("Tarakan",         3.3013, 117.5783, 8, "Kalimantan Utara"),
    CityLocation("Makassar",       -5.1477, 119.4327, 8, "Sulawesi Selatan"),
    CityLocation("Parepare",       -4.0135, 119.6298, 8, "Sulawesi Selatan"),
    CityLocation("Kendari",        -3.9985, 122.5127, 8, "Sulawesi Tenggara"),
    CityLocation("Palu",           -0.8917, 119.8707, 8, "Sulawesi Tengah"),
    CityLocation("Gorontalo",       0.5435, 123.0600, 8, "Gorontalo"),
    CityLocation("Manado",          1.4748, 124.8421, 8, "Sulawesi Utara"),
    CityLocation("Ternate",         0.7833, 127.3667, 9, "Maluku Utara"),
    CityLocation("Ambon",          -3.6954, 128.1814, 9, "Maluku"),
    CityLocation("Sorong",         -0.8833, 131.2500, 9, "Papua Barat"),
    CityLocation("Manokwari",      -0.8615, 134.0650, 9, "Papua Barat Daya"),
    CityLocation("Jayapura",       -2.5337, 140.7181, 9, "Papua"),
)

/**
 * Menghitung waktu sholat sesuai metode Kemenag RI.
 * @param year, month, day  — tanggal Masehi
 * @param lat, lng           — koordinat desimal (negatif = S/W)
 * @param tz                 — offset zona waktu dari UTC (WIB=7, WITA=8, WIT=9)
 * @param fajrAngle          — sudut Subuh (20° sesuai Kemenag)
 * @param ishaAngle          — sudut Isya  (18° sesuai Kemenag)
 * @param ihtiyat            — ihtiyat/kehati-hatian dalam menit (default 2)
 */
fun hitungWaktuSholat(
    year: Int, month: Int, day: Int,
    lat: Double, lng: Double, tz: Int,
    fajrAngle: Double = 20.0, ishaAngle: Double = 18.0, ihtiyat: Int = 2
): PrayerTimes {
    // ── 1. Julian Day Number ─────────────────────────────────────────────────
    val y = if (month <= 2) year - 1 else year
    val m = if (month <= 2) month + 12 else month
    val A = y / 100; val B = 2 - A + A / 4
    val JD = (365.25 * (y + 4716)).toLong() + (30.6001 * (m + 1)).toInt() + day + B - 1524.5

    // ── 2. Julian Century ────────────────────────────────────────────────────
    val T = (JD - 2451545.0) / 36525.0

    // ── 3. Posisi Matahari ───────────────────────────────────────────────────
    val L0  = ((280.46646 + 36000.76983 * T + 0.0003032 * T * T) % 360 + 360) % 360
    val Msr = ((357.52911 + 35999.05029 * T - 0.0001537 * T * T) % 360).toRadians()
    val C   = (1.914602 - 0.004817*T - 0.000014*T*T) * sin(Msr) +
            (0.019993 - 0.000101*T) * sin(2*Msr) + 0.000289 * sin(3*Msr)
    val sunLon  = L0 + C
    val omega   = (125.04 - 1934.136 * T).toRadians()
    val lam     = (sunLon - 0.00569 - 0.00478 * sin(omega)).toRadians()

    // ── 4. Deklinasi & Equation of Time ─────────────────────────────────────
    val eps0 = 23 + 26.0/60 + 21.448/3600 - (46.8150/3600)*T - (0.00059/3600)*T*T + (0.001813/3600)*T*T*T
    val eps  = (eps0 + 0.00256 * cos(omega)).toRadians()
    val dec  = asin(sin(eps) * sin(lam))   // deklinasi matahari (radian)

    val yy   = tan(eps / 2).pow(2)
    val L0r  = L0.toRadians()
    val e    = 0.016708634 - 0.000042037 * T
    val EqT  = 4 * Math.toDegrees(
        yy*sin(2*L0r) - 2*e*sin(Msr) + 4*e*yy*sin(Msr)*cos(2*L0r) -
                0.5*yy*yy*sin(4*L0r) - 1.25*e*e*sin(2*Msr)
    )  // dalam menit

    // ── 5. Transit (Dhuhur) ─────────────────────────────────────────────────
    val transit = 12.0 + tz - lng / 15.0 - EqT / 60.0

    val latR = lat.toRadians()

    fun hourAngle(elevDeg: Double): Double {
        val cosHA = (sin(elevDeg.toRadians()) - sin(latR)*sin(dec)) / (cos(latR)*cos(dec))
        return Math.toDegrees(acos(cosHA.coerceIn(-1.0, 1.0))) / 15.0
    }

    // ── 6. Waktu-waktu sholat ────────────────────────────────────────────────
    val tSubuh   = transit - hourAngle(-fajrAngle)
    val tSyuruq  = transit - hourAngle(-0.8333)
    val tDzuhur  = transit
    val asrElev  = Math.toDegrees(atan(1.0 / (1.0 + tan(abs(latR - dec)))))
    val tAshar   = transit + hourAngle(asrElev)
    val tMaghrib = transit + hourAngle(-0.8333)
    val tIsya    = transit + hourAngle(-ishaAngle)

    // ── 7. Format ke HH:MM dengan ihtiyat ───────────────────────────────────
    fun Double.toHHMM(addIhtiyat: Boolean = true): String {
        val total = this + if (addIhtiyat) ihtiyat / 60.0 else 0.0
        val h = total.toInt() % 24
        var mn = ((total % 1.0) * 60.0).let { kotlin.math.round(it).toInt() }
        val hFinal = if (mn == 60) { mn = 0; (h + 1) % 24 } else h
        return "%02d:%02d".format(hFinal, mn)
    }

    // Jam sekarang dalam desimal lokal
    val cal = Calendar.getInstance()
    val nowDecimal = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE)/60.0 + cal.get(Calendar.SECOND)/3600.0

    return PrayerTimes(
        subuh   = tSubuh.toHHMM(),
        syuruq  = tSyuruq.toHHMM(false),
        dzuhur  = tDzuhur.toHHMM(),
        ashar   = tAshar.toHHMM(),
        maghrib = tMaghrib.toHHMM(),
        isya    = tIsya.toHHMM(),
        syuruqDecimal  = tSyuruq + ihtiyat/60.0,
        maghribDecimal = tMaghrib + ihtiyat/60.0,
        nowDecimal     = nowDecimal,
    )
}

private fun Double.toRadians(): Double = this * PI / 180.0

/** Tentukan sholat berikutnya berdasarkan waktu sekarang */
fun getNextPrayer(pt: PrayerTimes): Pair<String, String> {
    fun toDouble(hhmm: String): Double {
        val parts = hhmm.split(":"); return parts[0].toDouble() + parts[1].toDouble() / 60.0
    }
    val now = pt.nowDecimal
    val prayers = listOf(
        "Subuh" to toDouble(pt.subuh), "Dzuhur" to toDouble(pt.dzuhur),
        "Ashar" to toDouble(pt.ashar), "Maghrib" to toDouble(pt.maghrib),
        "Isya" to toDouble(pt.isya),
    )
    val next = prayers.firstOrNull { it.second > now } ?: prayers.first()
    val diffMin = ((next.second - now + 24) % 24 * 60).toInt()
    val label = if (diffMin < 60) "Menuju ${next.first}" else "Menuju ${next.first}"
    return next.first to label
}

// ══════════════════════════════════════════════════════════════════════════════

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val serviceIntent = Intent(this, UngkerService::class.java)
        startForegroundService(serviceIntent)

        if (!android.provider.Settings.canDrawOverlays(this)) {
            val intent = Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            time - 1000 * 10,
            time
        )
        if (stats.isEmpty()) {
            startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        setContent {
            // isDarkMode di-hoist langsung di sini — setContent adalah @Composable context
            val sharedPref = remember {
                getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE)
            }
            var isDarkMode by remember {
                mutableStateOf(sharedPref.getBoolean("dark_mode", false))
            }

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
                        LayarUtama(
                            isDarkMode = isDarkMode,
                            onToggleDarkMode = {
                                val newVal = !isDarkMode
                                isDarkMode = newVal
                                sharedPref.edit().putBoolean("dark_mode", newVal).apply()
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Menghitung total waktu penggunaan untuk aplikasi yang tergolong Hiburan, Sosial Media, atau Game hari ini.
 */
fun getDistractionTimeToday(context: Context): String {
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val pm = context.packageManager
    val calendar = java.util.Calendar.getInstance()
    calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
    calendar.set(java.util.Calendar.MINUTE, 0)
    calendar.set(java.util.Calendar.SECOND, 0)
    val startTime = calendar.timeInMillis
    val endTime = System.currentTimeMillis()

    val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
    var totalMillis = 0L

    stats?.forEach { usageStats ->
        try {
            val appInfo = pm.getApplicationInfo(usageStats.packageName, 0)
            val packageName = usageStats.packageName.lowercase()

            var isDistraction = false

            // Cek Kategori Sistem (Android O+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (appInfo.category == ApplicationInfo.CATEGORY_SOCIAL ||
                    appInfo.category == ApplicationInfo.CATEGORY_GAME ||
                    appInfo.category == ApplicationInfo.CATEGORY_VIDEO) {
                    isDistraction = true
                }
            }

            // Cek Manual untuk aplikasi populer (Fallback)
            if (!isDistraction) {
                val targets = listOf("instagram", "tiktok", "youtube", "facebook", "twitter", "game", "mobile.legends", "whatsapp")
                if (targets.any { packageName.contains(it) }) {
                    isDistraction = true
                }
            }

            if (isDistraction) {
                totalMillis += usageStats.totalTimeInForeground
            }
        } catch (e: Exception) { }
    }

    val hours = TimeUnit.MILLISECONDS.toHours(totalMillis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(totalMillis) % 60
    return if (hours > 0) "${hours}j ${minutes}m" else "${minutes}m"
}

@Composable
fun LayarUtama(isDarkMode: Boolean = false, onToggleDarkMode: () -> Unit = {}) {
    val context = LocalContext.current
    val dark  = isDarkMode
    val navBg = if (dark) Color(0xFF1E293B) else Color.White
    val bg    = if (dark) Color(0xFF0F172A) else Color(0xFFF8F9FA)
    var selectedTab by remember { mutableIntStateOf(0) }
    var remainingTimeMillis by remember { mutableLongStateOf(0L) }
    var blockedCount by remember { mutableIntStateOf(0) }
    var hideNavBar by remember { mutableStateOf(false) }

    val maxCreditLimit = 3600000L // 1 Jam
    val READ_COOLDOWN_MS = 5L * 60 * 1000L  // 5 menit cooldown untuk tambah waktu
    val READ_CREDIT_MS   = 5L * 60 * 1000L  // kredit tetap 5 menit per sesi baca

    LaunchedEffect(Unit) {
        while (true) {
            withContext(Dispatchers.IO) {
                val sharedPref = context.getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE)
                val credit = sharedPref.getLong("remaining_credit", 0L)
                val count = sharedPref.getStringSet("blocked_apps", emptySet())?.size ?: 0
                withContext(Dispatchers.Main) {
                    remainingTimeMillis = credit
                    blockedCount = count
                }
            }
            delay(5000)
        }
    }

    Scaffold(
        containerColor = bg,
        bottomBar = {
            if (!hideNavBar) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = navBg,
                    // Shadow ke atas: gunakan drawBehind dengan gradient tipis
                    shadowElevation = 0.dp,
                    tonalElevation  = 0.dp
                ) {
                    Column {
                        // Garis shadow tipis di atas navbar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(
                                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = if (dark) 0.25f else 0.10f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                        NavigationBar(
                            containerColor = Color.Transparent,
                            tonalElevation = 0.dp
                        ) {
                            val items = listOf("🏠", "📱", "📖", "📊", "👤")
                            items.forEachIndexed { index, icon ->
                                NavigationBarItem(
                                    selected = selectedTab == index,
                                    onClick  = { selectedTab = index },
                                    icon = {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(icon, fontSize = 24.sp)
                                            if (selectedTab == index) {
                                                Spacer(Modifier.height(2.dp))
                                                Box(modifier = Modifier
                                                    .size(4.dp)
                                                    .background(Color(0xFF2E7D32), CircleShape))
                                            }
                                        }
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = Color.Transparent
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            androidx.compose.animation.AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    if (targetState > initialState) {
                        (androidx.compose.animation.slideInHorizontally { it / 3 } +
                                androidx.compose.animation.fadeIn()).togetherWith(
                            androidx.compose.animation.slideOutHorizontally { -it / 3 } +
                                    androidx.compose.animation.fadeOut()
                        )
                    } else {
                        (androidx.compose.animation.slideInHorizontally { -it / 3 } +
                                androidx.compose.animation.fadeIn()).togetherWith(
                            androidx.compose.animation.slideOutHorizontally { it / 3 } +
                                    androidx.compose.animation.fadeOut()
                        )
                    }.using(androidx.compose.animation.SizeTransform(clip = false))
                },
                label = "TabTransition"
            ) { tab ->
                when (tab) {
                    0 -> BerandaScreen(
                        remainingTimeMillis = remainingTimeMillis,
                        progress = if (remainingTimeMillis > 0) remainingTimeMillis.toFloat() / maxCreditLimit.toFloat() else 0f,
                        blockedCount = blockedCount,
                        isDarkMode = dark,
                        onToggleDarkMode = onToggleDarkMode,
                        onTambahWaktu = {
                            val sp = context.getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE)
                            val lastRead = sp.getLong("last_read_tambah_time", 0L)
                            val now = System.currentTimeMillis()
                            when {
                                remainingTimeMillis >= maxCreditLimit ->
                                    Toast.makeText(context, "Waktu sudah maksimal (1 Jam)!", Toast.LENGTH_SHORT).show()
                                now - lastRead < READ_COOLDOWN_MS -> {
                                    val sisaDetik = ((READ_COOLDOWN_MS - (now - lastRead)) / 1000).toInt()
                                    val mnt = sisaDetik / 60; val det = sisaDetik % 60
                                    Toast.makeText(context,
                                        "Cooldown aktif! Tunggu ${mnt}m ${det}d lagi",
                                        Toast.LENGTH_LONG).show()
                                }
                                else -> {
                                    // Buka LockActivity dengan mode "read_only" — kredit tetap 5 menit
                                    val intentLock = Intent(context, LockActivity::class.java).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        putExtra("credit_override_ms", READ_CREDIT_MS)
                                        putExtra("start_cooldown_key", "last_read_tambah_time")
                                    }
                                    context.startActivity(intentLock)
                                }
                            }
                        }
                    )
                    1 -> DaftarAplikasiScreen(isDarkMode = dark)
                    2 -> QuranScreen(onHideNavBar = { hideNavBar = it })
                    3 -> StatistikScreen(isDarkMode = dark)
                    else -> Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) { Text("Halaman Belum Tersedia") }
                }
            }
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// DATA JUZ AL-QUR'AN (Standar Mushaf Rasm Usmani 604 halaman)
// ─────────────────────────────────────────────────────────────────────────────

/** Halaman pertama setiap juz (index 0 = Juz 1, dst.) */
val JUZ_PAGE_START = intArrayOf(
    1, 22, 42, 62, 82, 102, 121, 142, 162, 182,
    202, 222, 242, 262, 282, 302, 322, 342, 362, 382,
    402, 422, 442, 462, 482, 502, 522, 542, 562, 582
)

/**
 * Surah ID yang menjadi penanda awal juz di daftar surat.
 * Kunci = surah ID, nilai = nomor juz yang mulai dari sekitar surah ini.
 * Catatan: beberapa juz dimulai di tengah surah — di sini dipakai surah terdekat sebagai pembatas visual.
 */
val SURAH_JUZ_START: Map<Int, Int> = mapOf(
    1 to 1,  2 to 2,  3 to 4,  4 to 5,  5 to 7,
    6 to 8,  7 to 9,  8 to 10, 9 to 11, 11 to 12,
    12 to 13, 15 to 14, 17 to 15, 18 to 16, 21 to 17,
    23 to 18, 25 to 19, 27 to 20, 29 to 21, 33 to 22,
    36 to 23, 39 to 24, 41 to 25, 46 to 26, 51 to 27,
    58 to 28, 67 to 29, 78 to 30
)

/** Kembalikan nomor juz (1-30) berdasarkan ID surah. */
fun getJuzOfSurah(surahId: Int): Int {
    var juz = 1
    for ((sid, j) in SURAH_JUZ_START.entries.sortedBy { it.key }) {
        if (surahId >= sid) juz = j else break
    }
    return juz
}

/** Kembalikan nomor juz (1-30) berdasarkan nomor halaman (1-604). */
fun getJuzOfPage(page: Int): Int {
    var juz = 1
    for (i in JUZ_PAGE_START.indices) {
        if (page >= JUZ_PAGE_START[i]) juz = i + 1 else break
    }
    return juz
}

// ─────────────────────────────────────────────────────────────────────────────

// ═══════════════════════════════════════════════════════════════════════════
// QURAN SCREEN — Level 1: Hub (Mushaf | Sambung Ayat)
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun QuranScreen(onHideNavBar: (Boolean) -> Unit = {}) {
    val context  = LocalContext.current
    val dbHelper = remember(context) { QuranDatabaseHelper(context) }

    var showMushaf      by remember { mutableStateOf(false) }
    var showSambungAyat by remember { mutableStateOf(false) }

    when {
        showMushaf      -> MushafScreen(dbHelper = dbHelper, onBack = { showMushaf = false }, onHideNavBar = onHideNavBar)
        showSambungAyat -> SambungAyatScreen(
            dbHelper     = dbHelper,
            onBack       = { showSambungAyat = false },
            onHideNavBar = onHideNavBar
        )
        else -> QuranHubScreen(
            onMushaf      = { showMushaf = true },
            onSambungAyat = { showSambungAyat = true }
        )
    }
}

// ── Level 1: Hub dengan 2 card ────────────────────────────────────────────

@Composable
fun QuranHubScreen(onMushaf: () -> Unit, onSambungAyat: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            "Al-Qur'an",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = textPrimC()
        )
        Text(
            "Pilih aktivitas yang ingin kamu lakukan",
            style = MaterialTheme.typography.bodyMedium,
            color = textSecC()
        )
        Spacer(modifier = Modifier.height(40.dp))

        // Card Mushaf
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onMushaf() },
            colors = CardDefaults.cardColors(containerColor = cardBg()),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier.padding(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(greenBgC(), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) { Text("📖", fontSize = 32.sp) }
                Spacer(modifier = Modifier.width(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Mushaf",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = textPrimC()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Baca Al-Qur'an per surat, halaman, atau juz",
                        style = MaterialTheme.typography.bodySmall,
                        color = textSecC()
                    )
                }
                Text("›", fontSize = 28.sp, color = greenAccent(), fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card Sambung Ayat
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSambungAyat() },
            colors = CardDefaults.cardColors(containerColor = cardBg()),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier.padding(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color(0xFFFFF3E0), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) { Text("🧩", fontSize = 32.sp) }
                Spacer(modifier = Modifier.width(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Sambung Ayat",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = textPrimC()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Susun potongan ayat dari surat pilihanmu",
                        style = MaterialTheme.typography.bodySmall,
                        color = textSecC()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = Color(0xFFFFF3E0),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "Bisa dimainkan offline  ✓",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFE65100),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text("›", fontSize = 28.sp, color = Color(0xFFE65100), fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// MUSHAF SCREEN — Per halaman, dengan bookmark, FAB mic/buku, progress juz
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun MushafScreen(
    dbHelper     : QuranDatabaseHelper,
    onBack       : () -> Unit,
    onHideNavBar : (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    var selectedPage   by remember { mutableIntStateOf(-1) }
    // Arah navigasi: true = maju (ke kanan layar), false = mundur (ke kiri layar)
    var navForward     by remember { mutableStateOf(true) }

    // Sembunyikan navbar saat membaca
    LaunchedEffect(selectedPage) { onHideNavBar(selectedPage != -1) }
    DisposableEffect(Unit) { onDispose { onHideNavBar(false) } }

    when {
        selectedPage != -1 -> AnimatedContent(
            targetState = selectedPage,
            transitionSpec = {
                if (navForward) {
                    // Maju → konten baru masuk dari kiri, lama keluar ke kanan
                    // (Quran: halaman berikutnya ada di sebelah kiri)
                    (slideInHorizontally(
                        initialOffsetX = { -it },
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(200))).togetherWith(
                        slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                        ) + fadeOut(tween(200))
                    )
                } else {
                    // Mundur → konten baru masuk dari kanan, lama keluar ke kiri
                    (slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(200))).togetherWith(
                        slideOutHorizontally(
                            targetOffsetX = { -it },
                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                        ) + fadeOut(tween(200))
                    )
                }.using(SizeTransform(clip = true))
            },
            label = "PageTransition"
        ) { animatedPage ->
            MushafPageReader(
                page     = animatedPage,
                dbHelper = dbHelper,
                onBack   = { selectedPage = -1 },
                onNav    = { newPage ->
                    navForward = newPage > selectedPage
                    selectedPage = newPage
                }
            )
        }
        else -> MushafPagePicker(
            dbHelper   = dbHelper,
            onBack     = onBack,
            onOpenPage = { selectedPage = it }
        )
    }
}

// ── Picker halaman (grid 4 kolom + bookmark) ──────────────────────────────

@Composable
fun MushafPagePicker(
    dbHelper   : QuranDatabaseHelper,
    onBack     : () -> Unit,
    onOpenPage : (Int) -> Unit
) {
    val context = LocalContext.current
    val sp = remember { context.getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE) }
    var bookmarks by remember {
        mutableStateOf(sp.getStringSet("mushaf_bookmarks", emptySet())?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet())
    }
    var searchQuery by remember { mutableStateOf("") }
    val dark = LocalIsDarkMode.current

    val filteredPages = remember(searchQuery) {
        val q = searchQuery.trim()
        if (q.isBlank()) (1..604).toList()
        else {
            val juzNum = Regex("^(?:juz|jz)\\s*(\\d+)$").find(q.lowercase())?.groupValues?.get(1)?.toIntOrNull()
            if (juzNum != null && juzNum in 1..30) {
                val s = JUZ_PAGE_START[juzNum - 1]
                val e = if (juzNum < 30) JUZ_PAGE_START[juzNum] - 1 else 604
                (s..e).toList()
            } else (1..604).filter { it.toString().startsWith(q) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 12.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = textPrimC())
            }
            Text("Mushaf", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, color = textPrimC(), modifier = Modifier.weight(1f))
            if (bookmarks.isNotEmpty()) {
                Surface(
                    onClick = { onOpenPage(bookmarks.max()) },
                    shape = RoundedCornerShape(10.dp),
                    color = greenBgC()
                ) {
                    Text("🔖 Lanjut", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium, color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Bold)
                }
            }
        }
        OutlinedTextField(
            value = searchQuery, onValueChange = { searchQuery = it.take(10) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Cari halaman (1–604) atau 'Juz 5'", color = textMutC()) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = textSecC()) },
            trailingIcon = { if (searchQuery.isNotEmpty()) IconButton({ searchQuery = "" }) { Text("✕", color = textSecC(), fontSize = 14.sp) } },
            singleLine = true, shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF2E7D32), unfocusedBorderColor = borderC(),
                focusedContainerColor = cardBg(), unfocusedContainerColor = cardBg(),
                focusedTextColor = textPrimC(), unfocusedTextColor = textPrimC())
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            var lastJuz = -1
            filteredPages.forEach { pageNum ->
                val pageJuz = getJuzOfPage(pageNum)
                if (searchQuery.isBlank() && pageJuz != lastJuz && JUZ_PAGE_START.contains(pageNum)) {
                    lastJuz = pageJuz
                    item(key = "sep_$pageJuz", span = { GridItemSpan(4) }) { JuzSeparator(pageJuz) }
                }
                val isBookmarked = bookmarks.contains(pageNum)
                item(key = "p_$pageNum") {
                    Card(
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clickable { onOpenPage(pageNum) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isBookmarked) Color(0xFFE8F5E9) else cardBg()),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(if (isBookmarked) 2.dp else 1.dp,
                            if (isBookmarked) Color(0xFF2E7D32) else borderC())
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (isBookmarked) Text("🔖", fontSize = 10.sp)
                                Text("Hal", fontSize = 10.sp, color = if (isBookmarked) Color(0xFF2E7D32) else Color.Gray)
                                Text(pageNum.toString(), fontWeight = FontWeight.Bold,
                                    color = if (isBookmarked) Color(0xFF2E7D32) else textPrimC())
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Page Reader — halaman penuh dengan semua fitur ────────────────────────

@Composable
fun MushafPageReader(
    page     : Int,
    dbHelper : QuranDatabaseHelper,
    onBack   : () -> Unit,
    onNav    : (Int) -> Unit
) {
    val context = LocalContext.current
    val sp = remember { context.getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE) }
    val dark = LocalIsDarkMode.current

    // Cek koneksi
    val isOnline = remember {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cap = cm.getNetworkCapabilities(cm.activeNetwork)
        cap?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
                cap?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
    }

    // ── Verses ──────────────────────────────────────────────────────────
    var pageVerses    by remember { mutableStateOf<List<Verse>>(emptyList()) }
    var isPageLoading by remember { mutableStateOf(true) }
    var pageError     by remember { mutableStateOf(false) }
    LaunchedEffect(page) {
        isPageLoading = true; pageError = false
        withContext(Dispatchers.IO) {
            val list = try { dbHelper.getVersesByPage(page) } catch (e: Exception) { null }
            withContext(Dispatchers.Main) {
                if (list == null) pageError = true else pageVerses = list
                isPageLoading = false
            }
        }
    }

    // ── Bookmark ────────────────────────────────────────────────────────
    var bookmarks by remember {
        mutableStateOf(sp.getStringSet("mushaf_bookmarks", emptySet())?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet())
    }
    val isBookmarked = bookmarks.contains(page)
    fun toggleBookmark() {
        val newSet = if (isBookmarked) bookmarks - page else bookmarks + page
        bookmarks = newSet
        sp.edit().putStringSet("mushaf_bookmarks", newSet.map { it.toString() }.toSet()).apply()
    }

    // ── STT State ───────────────────────────────────────────────────────
    var sedangMerekam   by remember { mutableStateOf(false) }
    var transcript      by remember { mutableStateOf("") }
    var matchedPositions by remember { mutableStateOf(setOf<Triple<Int,Int,Int>>()) }
    // Triple(surahId, ayahId, wordIdx) — unik per kata per ayat

    val speechScope = remember { CoroutineScope(Dispatchers.Main + Job()) }

    val speechRecognizer = remember {
        if (isOnline && SpeechRecognizer.isRecognitionAvailable(context))
            SpeechRecognizer.createSpeechRecognizer(context) else null
    }
    val recIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }

    fun norm(text: String) = text
        .replace(Regex("[\u064B-\u065F\u0670]"), "")
        .replace(Regex("[أإآٱ]"), "ا")
        .replace(Regex("[ىی]"), "ي")
        .replace("ة", "ه").trim()

    fun updateHighlight(raw: String) {
        val userNorms = raw.split(Regex("\\s+")).map { norm(it) }.filter { it.isNotEmpty() }
        val newMatched = mutableSetOf<Triple<Int,Int,Int>>()
        pageVerses.forEach { verse ->
            verse.text.split(Regex("\\s+")).forEachIndexed { wIdx, word ->
                val wNorm = norm(word)
                if (wNorm.isNotEmpty() && userNorms.any { u -> u == wNorm || u.contains(wNorm) || wNorm.contains(u) })
                    newMatched.add(Triple(verse.surahId, verse.ayahId, wIdx))
            }
        }
        matchedPositions = matchedPositions + newMatched

        // ── Auto-bookmark: simpan halaman ini saat mulai membaca ──────────
        if (newMatched.isNotEmpty() && !isBookmarked) {
            bookmarks = bookmarks + page
            sp.edit().putStringSet("mushaf_bookmarks", (bookmarks + page).map { it.toString() }.toSet()).apply()
        }

        // ── Auto-next: pindah halaman jika semua kata sudah di-highlight ──
        // Hitung total kata valid di halaman ini
        val totalWords = pageVerses.sumOf { verse ->
            verse.text.split(Regex("\\s+")).count { token ->
                token.any { c -> c.code in 0x0621..0x063A || c.code in 0x0641..0x064A || c.code in 0x0671..0x06D3 }
            }
        }
        val matchedCount = matchedPositions.size
        // Threshold: 80% kata terbaca → auto pindah ke halaman berikutnya
        if (totalWords > 0 && matchedCount >= totalWords * 0.80 && page < 604 && sedangMerekam) {
            speechScope.launch {
                delay(1500)  // jeda 1.5 detik agar user lihat semua hijau
                if (sedangMerekam) {
                    sedangMerekam = false
                    try { speechRecognizer?.stopListening() } catch (_: Exception) {}
                    onNav(page + 1)
                }
            }
        }
    }

    val sttListener = remember {
        object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(r: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(t: Int, p: Bundle?) {}
            override fun onError(error: Int) {
                val retry = error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                        error == SpeechRecognizer.ERROR_CLIENT
                if (sedangMerekam) speechScope.launch {
                    delay(if (retry) 200L else 500L)
                    if (sedangMerekam) try { speechRecognizer?.startListening(recIntent) } catch (_: Exception) {}
                }
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                if (text.isNotBlank()) {
                    transcript = if (transcript.isBlank()) text else "$transcript $text"
                    updateHighlight(transcript)
                }
                if (sedangMerekam) speechScope.launch {
                    delay(200)
                    if (sedangMerekam) try { speechRecognizer?.startListening(recIntent) } catch (_: Exception) {}
                }
            }
            override fun onPartialResults(partial: Bundle?) {
                val text = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                if (text.isNotBlank()) updateHighlight("$transcript $text")
            }
        }
    }

    DisposableEffect(page) {
        speechRecognizer?.setRecognitionListener(sttListener)
        onDispose {
            sedangMerekam = false
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechScope.cancel()
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            transcript = ""; matchedPositions = emptySet(); sedangMerekam = true
            try { speechRecognizer?.startListening(recIntent) } catch (_: Exception) {}
        }
    }

    // ── Timer baca (offline) ─────────────────────────────────────────────
    var readingSeconds by remember { mutableLongStateOf(0L) }
    var isReadingActive by remember { mutableStateOf(false) }
    LaunchedEffect(isReadingActive) {
        if (isReadingActive) {
            while (isReadingActive) {
                delay(1000)
                readingSeconds++
            }
        }
    }

    // ── Juz progress ────────────────────────────────────────────────────
    val currentJuz = getJuzOfPage(page)
    val juzStart   = JUZ_PAGE_START[currentJuz - 1]
    val juzEnd     = if (currentJuz < 30) JUZ_PAGE_START[currentJuz] - 1 else 604
    val juzProgress = (page - juzStart).toFloat() / (juzEnd - juzStart + 1).toFloat()

    // ── UI ───────────────────────────────────────────────────────────────
    Scaffold(
        containerColor = if (dark) Color(0xFF0F172A) else Color(0xFFF8F9FA),
        topBar = {
            Column {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        sedangMerekam = false; isReadingActive = false
                        speechRecognizer?.stopListening()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = textPrimC())
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Halaman $page", fontWeight = FontWeight.Bold, color = textPrimC())
                        Text("Juz $currentJuz", style = MaterialTheme.typography.bodySmall, color = Color(0xFF2E7D32))
                    }
                    // Navigasi halaman
                    IconButton(onClick = { if (page > 1) onNav(page - 1) }, enabled = page > 1) {
                        Text("◀", fontSize = 18.sp, color = if (page > 1) Color(0xFF2E7D32) else Color.LightGray)
                    }
                    IconButton(onClick = { if (page < 604) onNav(page + 1) }, enabled = page < 604) {
                        Text("▶", fontSize = 18.sp, color = if (page < 604) Color(0xFF2E7D32) else Color.LightGray)
                    }
                    // Bookmark
                    IconButton(onClick = { toggleBookmark() }) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = if (isBookmarked) "Hapus Bookmark" else "Tambah Bookmark",
                            tint = if (isBookmarked) Color(0xFF2E7D32) else textSecC().copy(alpha = 0.35f),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                HorizontalDivider(color = dividerC())
            }
        },
        bottomBar = {
            // ── Progress Bar Juz (menggantikan navbar) ────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (dark) Color(0xFF1E293B) else Color.White
            ) {
                Column {
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp)
                        .background(Brush.verticalGradient(listOf(
                            Color.Black.copy(if (dark) 0.25f else 0.10f), Color.Transparent))))
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            // Kiri: mode icon + info
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!isOnline) {
                                    // Offline: ikon buku + timer + halaman
                                    Text("📖", fontSize = 16.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Column {
                                        Text("Hal $page",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = textPrimC(), fontWeight = FontWeight.Bold)
                                        if (isReadingActive || readingSeconds > 0) {
                                            val m = readingSeconds / 60; val s = readingSeconds % 60
                                            Text(String.format(Locale.getDefault(), "%02d:%02d", m, s),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF2E7D32))
                                        }
                                    }
                                } else if (sedangMerekam) {
                                    Text("🎙️", fontSize = 16.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Mendengarkan...",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFE65100))
                                } else {
                                    Text("📖", fontSize = 16.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Hal $page",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = textSecC())
                                }
                            }
                            // Kanan: info juz
                            Text("Juz $currentJuz  •  Hal $juzStart–$juzEnd",
                                style = MaterialTheme.typography.labelSmall, color = textMutC())
                        }
                        Spacer(Modifier.height(6.dp))
                        // Progress bar juz
                        LinearProgressIndicator(
                            progress = { juzProgress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = Color(0xFF2E7D32),
                            trackColor = if (dark) Color(0xFF334155) else Color(0xFFE5E7EB)
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            // FAB: Mic (online) atau Buku (offline)
            if (isOnline && speechRecognizer != null) {
                FloatingActionButton(
                    onClick = {
                        if (!sedangMerekam) {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                                == PackageManager.PERMISSION_GRANTED) {
                                transcript = ""; matchedPositions = emptySet()
                                sedangMerekam = true; isReadingActive = true
                                try { speechRecognizer.startListening(recIntent) } catch (_: Exception) {}
                            } else permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            sedangMerekam = false
                            speechRecognizer.stopListening()
                        }
                    },
                    containerColor = if (sedangMerekam) Color(0xFFFFA000) else Color(0xFF2E7D32),
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Text(if (sedangMerekam) "⏹" else "🎙️", fontSize = 20.sp)
                }
            } else {
                // Offline: FAB buku untuk toggle sesi baca
                FloatingActionButton(
                    onClick = {
                        isReadingActive = !isReadingActive
                        if (!isReadingActive) readingSeconds = 0L
                    },
                    containerColor = if (isReadingActive) Color(0xFFE65100) else Color(0xFF2E7D32),
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Text(if (isReadingActive) "⏹" else "📖", fontSize = 20.sp)
                }
            }
        }
    ) { innerPadding ->
        when {
            isPageLoading -> Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF2E7D32))
            }
            pageError -> Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⚠️", fontSize = 40.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Gagal memuat halaman $page.", color = textSecC(), textAlign = TextAlign.Center)
                }
            }
            pageVerses.isEmpty() -> Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) {
                Text("Tidak ada ayat.", color = textSecC())
            }
            else -> {
                // State untuk swipe gesture
                var swipeDelta by remember { mutableStateOf(0f) }
                val swipeThreshold = 80f  // minimum px untuk trigger nav

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .pointerInput(page) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    when {
                                        // Geser kanan → halaman sebelumnya (seperti Quran fisik: balik ke kanan)
                                        swipeDelta > swipeThreshold && page > 1   -> onNav(page - 1)
                                        // Geser kiri → halaman berikutnya
                                        swipeDelta < -swipeThreshold && page < 604 -> onNav(page + 1)
                                    }
                                    swipeDelta = 0f
                                },
                                onDragCancel = { swipeDelta = 0f },
                                onHorizontalDrag = { _, dragAmount ->
                                    swipeDelta += dragAmount
                                }
                            )
                        },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    items(pageVerses, key = { "${it.surahId}_${it.ayahId}" }) { verse ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            val hasHighlight = matchedPositions.any { (sid, aid, _) -> sid == verse.surahId && aid == verse.ayahId }
                            if (!hasHighlight) {
                                Text(verse.text,
                                    style = MaterialTheme.typography.headlineMedium.copy(lineHeight = 50.sp),
                                    textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth(),
                                    color = textPrimC())
                            } else {
                                val annotated = buildAnnotatedString {
                                    verse.text.split(Regex("\\s+")).forEachIndexed { wIdx, word ->
                                        val matched = matchedPositions.contains(Triple(verse.surahId, verse.ayahId, wIdx))
                                        withStyle(SpanStyle(color = if (matched) Color(0xFF2E7D32) else textPrimC())) {
                                            append(word)
                                        }
                                        append(" ")
                                    }
                                }
                                Text(annotated,
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        lineHeight = 50.sp, textAlign = TextAlign.Right),
                                    modifier = Modifier.fillMaxWidth())
                            }
                            Spacer(Modifier.height(8.dp))
                            Surface(color = greenBgC(), shape = CircleShape) {
                                Text("${verse.surahName} : ${verse.ayahId}",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32))
                            }
                            HorizontalDivider(modifier = Modifier.padding(top = 16.dp), color = dividerC())
                        }
                    }
                }   // end LazyColumn
            }   // end else (swipe block)
        }
    }
}

private fun TabRowDefaults.tabIndicatorOffset(position: TabPosition): Modifier {return Modifier.tabIndicatorOffset(position)}

// ── Composable helpers ─────────────────────────────────────────────────────────

/** Pembatas visual juz — muncul di antara item surat/halaman */
@Composable
fun JuzSeparator(juzNum: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFDDDDDD))
        Surface(
            color = Color(0xFF2E7D32),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Text(
                text = "  Juz $juzNum  ",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold
            )
        }
        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFDDDDDD))
    }
}

/** Card satu juz di tab Juz */
@Composable
fun JuzCard(juzNum: Int, onOpenPage: (Int) -> Unit) {
    val startPage = JUZ_PAGE_START[juzNum - 1]
    val endPage   = if (juzNum < 30) JUZ_PAGE_START[juzNum] - 1 else 604
    val pageCount = endPage - startPage + 1

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onOpenPage(startPage) },
        colors = CardDefaults.cardColors(containerColor = cardBg()),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).background(Color(0xFFE8F5E9), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(juzNum.toString(), fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color(0xFF2E7D32))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Juz $juzNum", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = textPrimC())
                Text("Hal. $startPage – $endPage  •  $pageCount halaman",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFF757575))
            }
            Text("▶", fontSize = 16.sp, color = Color(0xFF2E7D32))
        }
    }
}


@Composable
fun ChapterItem(chapter: Chapter, onClick: () -> Unit) {
    val verseCount = remember(chapter.id) {
        chapter.content.split(Regex("\\[\\d+\\]")).count { it.isNotBlank() }
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = cardBg()),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).background(greenBgC(), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(chapter.id.toString(), fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(chapter.name, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = textPrimC())
                Text("$verseCount ayat", style = MaterialTheme.typography.bodySmall,
                    color = textSecC())
            }
            Text("📖", fontSize = 18.sp)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// SAMBUNG AYAT SCREEN
// ═══════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SambungAyatScreen(
    dbHelper     : QuranDatabaseHelper,
    onBack       : () -> Unit,
    onHideNavBar : (Boolean) -> Unit = {}
) {
    var selectedSurah by remember { mutableStateOf<Chapter?>(null) }
    var quizState     by remember { mutableStateOf<SambungQuizState?>(null) }

    // Sembunyikan navbar saat kuis aktif
    LaunchedEffect(quizState) {
        onHideNavBar(quizState != null)
    }

    when {
        quizState != null -> SambungQuizPlay(
            state    = quizState!!,
            dbHelper = dbHelper,
            onBack   = { quizState = null },
            onFinish = { quizState = null; selectedSurah = null }
        )
        selectedSurah != null -> SambungSurahConfig(
            surah    = selectedSurah!!,
            dbHelper = dbHelper,
            onBack   = { selectedSurah = null },
            onStart  = { state -> quizState = state }
        )
        else -> SambungSurahPicker(
            dbHelper      = dbHelper,
            onBack        = onBack,
            onSelectSurah = { selectedSurah = it }
        )
    }
}

// ── Data class untuk state kuis ──────────────────────────────────────────

data class SambungQuizState(
    val surahName : String,
    val verses    : List<String>,   // daftar ayat yang akan dikuis
    val totalQ    : Int             // total pertanyaan
)

// ── Picker surat ─────────────────────────────────────────────────────────

@Composable
fun SambungSurahPicker(
    dbHelper      : QuranDatabaseHelper,
    onBack        : () -> Unit,
    onSelectSurah : (Chapter) -> Unit
) {
    var surahList by remember { mutableStateOf<List<Chapter>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val list = try { dbHelper.getAllChapters() } catch (e: Exception) { emptyList() }
            withContext(Dispatchers.Main) { surahList = list; isLoading = false }
        }
    }

    val filtered = remember(surahList, searchQuery) {
        if (searchQuery.isBlank()) surahList
        else {
            val q = searchQuery.trim().lowercase()
            surahList.filter { ch ->
                ch.name.contains(q, ignoreCase = true) ||
                        ch.nameLatin.contains(q, ignoreCase = true) ||
                        ch.id.toString() == q
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 12.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = textPrimC())
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Sambung Ayat", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, color = textPrimC())
                Text("Pilih surat yang ingin diuji", style = MaterialTheme.typography.bodySmall,
                    color = textSecC())
            }
        }
        HorizontalDivider(color = dividerC())

        if (isLoading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF2E7D32))
            }
        } else {
            OutlinedTextField(
                value = searchQuery, onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                placeholder = { Text("Cari surat...", color = textMutC()) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = textSecC()) },
                trailingIcon = { if (searchQuery.isNotEmpty()) IconButton({ searchQuery = "" }) { Text("✕", color = textSecC(), fontSize = 14.sp) } },
                singleLine = true, shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF2E7D32), unfocusedBorderColor = borderC(),
                    focusedContainerColor = cardBg(), unfocusedContainerColor = cardBg(),
                    focusedTextColor = textPrimC(), unfocusedTextColor = textPrimC())
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp)
            ) {
                val showSep = searchQuery.isBlank()
                filtered.forEach { surah ->
                    if (showSep && SURAH_JUZ_START.containsKey(surah.id)) {
                        val j = SURAH_JUZ_START[surah.id]!!
                        item(key = "sep_$j") { JuzSeparator(j) }
                    }
                    item(key = "s_${surah.id}") {
                        SambungSurahItem(surah = surah, onClick = { onSelectSurah(surah) })
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SambungSurahItem(surah: Chapter, onClick: () -> Unit) {
    val juz       = remember(surah.id) { getJuzOfSurah(surah.id) }
    val isLong    = surah.id <= 77       // Juz 1–25 → surat panjang
    val verseCount = remember(surah.id) {
        surah.content.split(Regex("\\[\\d+\\]")).count { it.isNotBlank() }
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors   = CardDefaults.cardColors(containerColor = cardBg()),
        shape    = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).background(
                if (isLong) Color(0xFFE8F5E9) else Color(0xFFFFF3E0), CircleShape),
                contentAlignment = Alignment.Center) {
                Text(surah.id.toString(), fontWeight = FontWeight.Bold,
                    color = if (isLong) Color(0xFF2E7D32) else Color(0xFFE65100), fontSize = 13.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(surah.name, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = textPrimC())
                if (surah.nameLatin.isNotBlank())
                    Text(surah.nameLatin, style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF2E7D32))
                Text("$verseCount ayat  •  Juz $juz", style = MaterialTheme.typography.bodySmall,
                    color = textSecC())
            }
            Surface(color = if (isLong) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                shape = RoundedCornerShape(8.dp)) {
                Text(
                    if (isLong) "Pilih ayat" else "Acak Juz 30",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isLong) Color(0xFF2E7D32) else Color(0xFFE65100),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ── Konfigurasi kuis (untuk surat panjang Juz 1–25) ──────────────────────

@Composable
fun SambungSurahConfig(
    surah    : Chapter,
    dbHelper : QuranDatabaseHelper,
    onBack   : () -> Unit,
    onStart  : (SambungQuizState) -> Unit
) {
    val isLong     = surah.id <= 77
    val verseCount = remember(surah.id) {
        surah.content.split(Regex("\\[\\d+\\]")).count { it.isNotBlank() }
    }

    // Untuk surat pendek (Juz 26-30) langsung generate 10 soal acak
    if (!isLong) {
        var verses by remember { mutableStateOf<List<String>>(emptyList()) }
        var loading by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                val (_, v) = dbHelper.getQuizVerses(surah.id, maxCount = 10)
                withContext(Dispatchers.Main) { verses = v; loading = false }
            }
        }
        if (loading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Color(0xFF2E7D32)) }
        } else {
            // Auto-start
            LaunchedEffect(verses) {
                if (verses.isNotEmpty()) {
                    onStart(SambungQuizState(surah.name, verses, verses.size))
                }
            }
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF2E7D32))
                    Spacer(Modifier.height(12.dp))
                    Text("Menyiapkan soal...", color = textSecC())
                }
            }
        }
        return
    }

    // Surat panjang → tampilkan form konfigurasi
    var modeTab       by remember { mutableIntStateOf(0) }  // 0=per ayat, 1=per halaman
    var fromInput     by remember { mutableStateOf("1") }
    var toInput       by remember { mutableStateOf(verseCount.toString()) }
    var jumlahSoal    by remember { mutableIntStateOf(10) }
    var errorMsg      by remember { mutableStateOf("") }

    // Halaman surah
    val surahStartPage = remember(surah.id) {
        // Estimasi halaman awal surah berdasarkan DB (sederhana)
        (surah.id * 1).coerceIn(1, 604)
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = textPrimC())
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(surah.name, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, color = textPrimC())
                if (surah.nameLatin.isNotBlank())
                    Text(surah.nameLatin, style = MaterialTheme.typography.bodySmall, color = Color(0xFF2E7D32))
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = dividerC())
        Spacer(Modifier.height(24.dp))

        Text("⚙️  Pengaturan Kuis", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = textPrimC())
        Spacer(Modifier.height(16.dp))

        // Mode: per ayat atau per halaman
        Text("Tentukan range berdasarkan:", style = MaterialTheme.typography.bodyMedium,
            color = textSecC())
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("Nomor Ayat", "Nomor Halaman").forEachIndexed { idx, label ->
                val sel = modeTab == idx
                Surface(
                    onClick = { modeTab = idx; errorMsg = "" },
                    shape = RoundedCornerShape(12.dp),
                    color = if (sel) Color(0xFF2E7D32) else cardBg(),
                    border = BorderStroke(1.dp, if (sel) Color(0xFF2E7D32) else borderC()),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(label, modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center, fontWeight = FontWeight.Bold,
                        color = if (sel) Color.White else textPrimC(),
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        val rangeLabel = if (modeTab == 0) "ayat" else "halaman"
        val rangeMax   = if (modeTab == 0) verseCount else 604

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = fromInput, onValueChange = { fromInput = it.filter { c -> c.isDigit() }.take(4); errorMsg = "" },
                label = { Text("Dari $rangeLabel", color = textSecC()) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF2E7D32), unfocusedBorderColor = borderC(),
                    focusedContainerColor = cardBg(), unfocusedContainerColor = cardBg(),
                    focusedTextColor = textPrimC(), unfocusedTextColor = textPrimC(),
                    focusedLabelColor = Color(0xFF2E7D32))
            )
            OutlinedTextField(
                value = toInput, onValueChange = { toInput = it.filter { c -> c.isDigit() }.take(4); errorMsg = "" },
                label = { Text("Sampai $rangeLabel", color = textSecC()) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF2E7D32), unfocusedBorderColor = borderC(),
                    focusedContainerColor = cardBg(), unfocusedContainerColor = cardBg(),
                    focusedTextColor = textPrimC(), unfocusedTextColor = textPrimC(),
                    focusedLabelColor = Color(0xFF2E7D32))
            )
        }
        Spacer(Modifier.height(6.dp))
        Text("Range: 1 – $rangeMax $rangeLabel",
            style = MaterialTheme.typography.bodySmall, color = textMutC())

        if (errorMsg.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(errorMsg, style = MaterialTheme.typography.bodySmall, color = Color(0xFFE53935))
        }

        Spacer(Modifier.height(20.dp))
        // Hitung maksimal soal yang bisa dibuat dari range yang dipilih
        val fromVal  = fromInput.toIntOrNull()?.coerceIn(1, rangeMax) ?: 1
        val toVal    = toInput.toIntOrNull()?.coerceIn(fromVal, rangeMax) ?: rangeMax
        val rangeSize = (toVal - fromVal + 1).coerceAtLeast(1)
        // Pilihan jumlah soal: hanya tampilkan jika <= rangeSize
        // Minimal 1 soal, maksimal rangeSize (capped di 20)
        val maxSoal  = minOf(rangeSize, 20)
        // Auto-clamp jumlahSoal jika range berubah membuat pilihan tidak valid
        if (jumlahSoal > maxSoal) jumlahSoal = maxSoal

        val soalOptions = listOf(5, 10, 15, 20).filter { it <= maxSoal }
            .let { opts ->
                // Selalu ada minimal 1 pilihan: nilai maxSoal itu sendiri
                if (opts.isEmpty()) listOf(maxSoal)
                else if (!opts.contains(maxSoal) && maxSoal < 20) opts + maxSoal
                else opts
            }.sorted()

        Text("Jumlah soal:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold, color = textPrimC())
        if (maxSoal < 20) {
            Text("Maksimal $maxSoal soal untuk range ini",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFE65100))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            soalOptions.forEach { n ->
                val sel = jumlahSoal == n
                Surface(
                    onClick = { jumlahSoal = n },
                    shape = RoundedCornerShape(12.dp),
                    color = if (sel) Color(0xFF2E7D32) else cardBg(),
                    border = BorderStroke(1.dp, if (sel) Color(0xFF2E7D32) else borderC()),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("$n",
                        modifier = Modifier.padding(vertical = 12.dp),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        color = if (sel) Color.White else textPrimC())
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = {
                val from = fromInput.toIntOrNull() ?: 1
                val to   = toInput.toIntOrNull()   ?: rangeMax
                when {
                    from < 1 || from > rangeMax ->
                        errorMsg = "Dari $rangeLabel harus antara 1–$rangeMax"
                    to < from ->
                        errorMsg = "Sampai $rangeLabel harus ≥ dari $rangeLabel"
                    to > rangeMax ->
                        errorMsg = "Sampai $rangeLabel maksimal $rangeMax"
                    else -> {
                        // Konversi halaman → ayat jika mode halaman
                        // Untuk mode halaman, gunakan fromAyah=1 toAyah=verseCount
                        // dan filter berdasarkan page range (simplified)
                        val fromAyah = if (modeTab == 0) from else 1
                        val toAyah   = if (modeTab == 0) to   else verseCount
                        // Load async via coroutine di scope composable tidak bisa langsung,
                        // jadi kita kirim parameter ke QuizPlay yang akan load sendiri
                        onStart(SambungQuizState(
                            surahName = surah.name,
                            verses    = emptyList(),  // diisi oleh QuizPlay
                            totalQ    = jumlahSoal
                        ).copy(
                            // Encode config ke verses sebagai marker
                            verses = listOf(
                                "__CONFIG__",
                                surah.id.toString(),
                                fromAyah.toString(),
                                toAyah.toString(),
                                jumlahSoal.toString()
                            )
                        ))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
        ) {
            Text("Mulai Kuis $jumlahSoal Soal", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ── Quiz Play ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SambungQuizPlay(
    state    : SambungQuizState,
    dbHelper : QuranDatabaseHelper,
    onBack   : () -> Unit,
    onFinish : () -> Unit = onBack   // default: sama dengan onBack jika tidak diisi
) {
    // Jika verses berisi config marker, load dulu
    var verses   by remember { mutableStateOf(state.verses) }
    var isLoading by remember { mutableStateOf(verses.firstOrNull() == "__CONFIG__") }
    var totalQ   by remember { mutableIntStateOf(state.totalQ) }

    LaunchedEffect(Unit) {
        if (verses.firstOrNull() == "__CONFIG__") {
            val surahId  = verses.getOrNull(1)?.toIntOrNull() ?: 1
            val fromAyah = verses.getOrNull(2)?.toIntOrNull() ?: 1
            val toAyah   = verses.getOrNull(3)?.toIntOrNull() ?: Int.MAX_VALUE
            val count    = verses.getOrNull(4)?.toIntOrNull() ?: 10
            withContext(Dispatchers.IO) {
                val (_, v) = dbHelper.getQuizVerses(surahId, fromAyah, toAyah, count)
                withContext(Dispatchers.Main) {
                    verses   = v
                    totalQ   = count
                    isLoading = false
                }
            }
        }
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color(0xFF2E7D32))
                Spacer(Modifier.height(12.dp))
                Text("Menyiapkan soal...", color = textSecC())
            }
        }
        return
    }

    if (verses.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("😔", fontSize = 48.sp)
                Spacer(Modifier.height(12.dp))
                Text("Tidak ada ayat cocok untuk kuis ini.", color = textSecC(), textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))) {
                    Text("Kembali")
                }
            }
        }
        return
    }

    // ── State kuis ────────────────────────────────────────────────────────
    var currentIdx   by remember { mutableIntStateOf(0) }
    var score        by remember { mutableIntStateOf(0) }
    var isFinished   by remember { mutableStateOf(false) }
    var isSuccess    by remember { mutableStateOf(false) }
    var isError      by remember { mutableStateOf(false) }
    var showResult   by remember { mutableStateOf(false) }

    val currentVerse = verses.getOrNull(currentIdx) ?: ""
    val correctWords = remember(currentIdx) {
        // Filter token yang mengandung minimal 1 huruf Arab (U+0621–U+06FF)
        // Ini menghilangkan tanda waqf (ۗ ۖ ۚ ۛ) dan karakter non-huruf
        // yang menjadi penyebab ghost block
        currentVerse.split(Regex("\\s+"))
            .map { it.trim() }
            .filter { token ->
                // Hanya huruf Arab asli — exclude harakat (U+064B-065F),
                // tanda waqf (U+06D4-06ED), angka Arab, dan simbol non-huruf
                token.any { c ->
                    c.code in 0x0621..0x063A ||  // Huruf Arab dasar
                            c.code in 0x0641..0x064A ||  // Huruf Arab lanjutan
                            c.code in 0x0671..0x06D3     // Huruf Arab extended
                }
            }
    }
    val shuffledWords = remember(currentIdx) { correctWords.shuffled() }
    val selectedWords = remember(currentIdx) { mutableStateListOf<String>() }
    val availableWords = remember(currentIdx) {
        mutableStateListOf<String>().apply { addAll(shuffledWords) }
    }

    // Auto-check saat semua kata sudah ditempatkan
    val selectedSize = selectedWords.size
    LaunchedEffect(selectedSize) {
        if (selectedSize == correctWords.size && correctWords.isNotEmpty() && !isSuccess && !isError) {
            delay(500)
            if (selectedWords.size == correctWords.size) {
                if (selectedWords.toList() == correctWords) {
                    isSuccess = true
                    score++
                    delay(1200)
                    if (currentIdx < verses.size - 1) {
                        currentIdx++
                        isSuccess = false
                        isError   = false
                    } else {
                        isFinished = true
                    }
                } else {
                    isError = true
                }
            }
        }
    }

    if (isFinished) {
        // Layar hasil akhir
        Box(modifier = Modifier.fillMaxSize().background(
            if (score >= verses.size * 0.7) Color(0xFF2E7D32) else Color(0xFF1565C0)
        ), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                Text(if (score >= verses.size * 0.7) "🌟" else "📚", fontSize = 72.sp)
                Text(
                    if (score >= verses.size * 0.7) "Luar Biasa!" else "Terus Berlatih!",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black, color = Color.White
                )
                Text("$score / ${verses.size} ayat benar",
                    style = MaterialTheme.typography.titleLarge, color = Color.White.copy(0.9f))
                Text("Surah ${state.surahName}",
                    style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(0.7f))
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onFinish,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Selesai", fontWeight = FontWeight.Bold,
                        color = if (score >= verses.size * 0.7) Color(0xFF2E7D32) else Color(0xFF1565C0))
                }
            }
        }
        return
    }

    // ── UI kuis ───────────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {

            // Header
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = textPrimC())
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(state.surahName, fontWeight = FontWeight.Bold, color = textPrimC())
                    Text("Soal ${currentIdx + 1} dari ${verses.size}",
                        style = MaterialTheme.typography.bodySmall, color = textSecC())
                }
                Surface(color = greenBgC(), shape = RoundedCornerShape(12.dp)) {
                    Text("⭐ $score",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                }
            }

            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { (currentIdx.toFloat() + (if (isSuccess) 1f else 0f)) / verses.size },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                color = Color(0xFF2E7D32),
                trackColor = if (LocalIsDarkMode.current) Color(0xFF334155) else Color(0xFFE5E5E5)
            )

            Spacer(Modifier.height(24.dp))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text("Susunlah potongan ayat ini:",
                    style = MaterialTheme.typography.bodyLarge, color = textSecC())
            }
            Spacer(Modifier.height(16.dp))

            // ── Area Arab: seluruh wrapper RTL ───────────────────────────
            // RTL pada parent membuat FlowRow mengalir kanan→kiri di semua baris
            // Arrangement.Start dalam RTL = mulai dari kanan (bukan Center
            // yang membuat tiap baris dicentrer sendiri dan tidak nyambung)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {

                // Kotak jawaban
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp)
                        .border(2.dp,
                            when {
                                isSuccess -> Color(0xFF2E7D32)
                                isError   -> Color(0xFFE53935)
                                else      -> borderC()
                            },
                            RoundedCornerShape(16.dp))
                        .background(
                            when {
                                isSuccess -> Color(0xFFE8F5E9)
                                isError   -> Color(0xFFFFEBEE)
                                else      -> cardBg()
                            },
                            RoundedCornerShape(16.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    if (selectedWords.isEmpty()) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                "Ketuk kata di bawah untuk menyusun ayat",
                                color = textMutC(), textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        // Arrangement.Start dalam RTL = aliran kanan→kiri tiap baris
                        // Tidak ada AnimatedVisibility di sini (menyebabkan ghost item)
                        // Animasi sudah ada di SambungWordChip (scale bounce saat klik)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            selectedWords.forEachIndexed { idx, word ->
                                if (word.any { c -> c.code in 0x0621..0x063A || c.code in 0x0641..0x064A || c.code in 0x0671..0x06D3 }) {
                                    SambungWordChip(
                                        word = word,
                                        isPrimary = true,
                                        enabled = !isSuccess
                                    ) {
                                        if (!isSuccess) {
                                            selectedWords.removeAt(idx)
                                            availableWords.add(word)
                                            isError = false
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Feedback — balik LTR agar teks Latin terbaca benar
                AnimatedVisibility(visible = isSuccess || isError) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            if (isSuccess) "✅ Benar! Lanjut..." else "❌ Belum tepat, coba lagi",
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            color = if (isSuccess) Color(0xFF2E7D32) else Color(0xFFE53935),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                // Kata-kata pilihan — Center agar terlihat rapi di tengah
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    availableWords.forEach { word ->
                        if (word.any { c -> c.code in 0x0621..0x063A || c.code in 0x0641..0x064A || c.code in 0x0671..0x06D3 }) {
                            SambungWordChip(
                                word = word,
                                isPrimary = false,
                                enabled = !isSuccess
                            ) {
                                if (!isSuccess) {
                                    selectedWords.add(word)
                                    availableWords.remove(word)
                                    isError = false
                                }
                            }
                        }
                    }
                }

            } // end RTL

            Spacer(Modifier.height(20.dp))

            // Tombol reset jika salah
            if (isError && !isSuccess) {
                OutlinedButton(
                    onClick = {
                        val allWords = (selectedWords + availableWords).shuffled()
                        selectedWords.clear()
                        availableWords.clear()
                        availableWords.addAll(allWords)
                        isError = false
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, Color(0xFFE53935))
                ) {
                    Text("🔄 Susun Ulang", color = Color(0xFFE53935), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun SambungWordChip(word: String, isPrimary: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val bg     = if (isPrimary) cardBg() else if (LocalIsDarkMode.current) Color(0xFF1E293B) else Color(0xFFF0F0F0)
    val border = if (isPrimary) borderC() else Color.Transparent

    // Animasi tekan: scale bounce saat diklik
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMediumLow
        ),
        label = "chipScale"
    )

    Surface(
        onClick           = { if (enabled) onClick() },
        color             = bg,
        shape             = RoundedCornerShape(12.dp),
        border            = BorderStroke(2.dp, border),
        shadowElevation   = if (isPrimary) 2.dp else 0.dp,
        interactionSource = interactionSource,
        modifier          = Modifier
            .padding(2.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
    ) {
        Text(
            text       = word,
            modifier   = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            style      = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color      = textPrimC(),
            textAlign  = TextAlign.Center
        )
    }
}

@Composable
fun BerandaScreen(
    remainingTimeMillis: Long,
    progress: Float,
    blockedCount: Int,
    isDarkMode: Boolean = false,
    onToggleDarkMode: () -> Unit = {},
    onTambahWaktu: () -> Unit
) {
    val context = LocalContext.current
    val sharedPref = remember { context.getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE) }
    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }

    var totalVerses by remember { mutableIntStateOf(sharedPref.getInt("total_verses", 0)) }
    var dailyVerses by remember {
        // Cek apakah data daily masih dari hari ini
        val lastDate = sharedPref.getString("last_verse_date", "") ?: ""
        mutableIntStateOf(if (lastDate == todayStr) sharedPref.getInt("daily_verses", 0) else 0)
    }
    var distractionTime by remember { mutableStateOf("...") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val result = getDistractionTimeToday(context)
            val lastDate = sharedPref.getString("last_verse_date", "") ?: ""
            val tv = sharedPref.getInt("total_verses", 0)
            val dv = if (lastDate == todayStr) sharedPref.getInt("daily_verses", 0) else 0
            withContext(Dispatchers.Main) {
                distractionTime = result
                totalVerses     = tv
                dailyVerses     = dv
            }
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            withContext(Dispatchers.IO) {
                val lastDate = sharedPref.getString("last_verse_date", "") ?: ""
                val tv = sharedPref.getInt("total_verses", 0)
                val dv = if (lastDate == todayStr) sharedPref.getInt("daily_verses", 0) else 0
                withContext(Dispatchers.Main) {
                    totalVerses = tv
                    dailyVerses = dv
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp)
    ) {
        item {
            HeaderDashboard(isDarkMode = isDarkMode, onToggleDarkMode = onToggleDarkMode)
            Spacer(modifier = Modifier.height(24.dp))
            TimerCard(remainingTimeMillis, progress, onTambahWaktu)
            Spacer(modifier = Modifier.height(24.dp))
            MotivasiCard()
            Spacer(modifier = Modifier.height(24.dp))
            // ── Widget Jadwal Sholat ──────────────────────────────────────
            JadwalSholatCard()
            Spacer(modifier = Modifier.height(24.dp))
            // ─────────────────────────────────────────────────────────────
            Text(
                "Aktivitas Hari Ini",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = textPrimC()
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = "📱",
                    value = distractionTime,
                    label = "Akses Medsos & Game",
                    iconBg = if (isDarkMode) Color(0xFF1E3A5F) else Color(0xFFE3F2FD)
                )
                Spacer(modifier = Modifier.width(12.dp))
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = "🚫",
                    value = blockedCount.toString(),
                    label = "Aplikasi Dikunci",
                    iconBg = if (isDarkMode) Color(0xFF3B1F1F) else Color(0xFFFDE7E7)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            AyatReadCard(totalVerses, dailyVerses)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// WIDGET JADWAL SHOLAT
// ══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JadwalSholatCard() {
    val context = LocalContext.current
    val sharedPref = remember { context.getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE) }

    // ── State lokasi ─────────────────────────────────────────────────────────
    var locationLabel by remember { mutableStateOf(sharedPref.getString("sholat_city_name", "Jakarta") ?: "Jakarta") }
    var prayerTimes   by remember { mutableStateOf<PrayerTimes?>(null) }
    var isLoadingGps  by remember { mutableStateOf(false) }
    var showCityPicker by remember { mutableStateOf(false) }
    var cityExpanded  by remember { mutableStateOf(false) }
    var selectedCity  by remember {
        val savedName = sharedPref.getString("sholat_city_name", "Jakarta") ?: "Jakarta"
        mutableStateOf(INDONESIA_CITIES.find { it.name == savedName } ?: INDONESIA_CITIES[10])
    }
    // Latitude/longitude yang dipakai (bisa dari GPS atau pilihan kota)
    var usedLat by remember { mutableDoubleStateOf(sharedPref.getFloat("sholat_lat", selectedCity.lat.toFloat()).toDouble()) }
    var usedLng by remember { mutableDoubleStateOf(sharedPref.getFloat("sholat_lng", selectedCity.lng.toFloat()).toDouble()) }
    var usedTz  by remember { mutableIntStateOf(sharedPref.getInt("sholat_tz", selectedCity.tzOffset)) }
    var isGpsMode by remember { mutableStateOf(sharedPref.getBoolean("sholat_gps_mode", false)) }

    // ── Launcher izin lokasi ─────────────────────────────────────────────────
    val locationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            isLoadingGps = true
            getGpsLocation(context,
                onSuccess = { lat, lng, tz, label ->
                    usedLat = lat; usedLng = lng; usedTz = tz; locationLabel = label
                    isGpsMode = true; isLoadingGps = false
                    sharedPref.edit()
                        .putFloat("sholat_lat", lat.toFloat()).putFloat("sholat_lng", lng.toFloat())
                        .putInt("sholat_tz", tz).putString("sholat_city_name", label)
                        .putBoolean("sholat_gps_mode", true).apply()
                },
                onFail = {
                    isLoadingGps = false
                    Toast.makeText(context, "GPS tidak tersedia, pilih kota manual", Toast.LENGTH_SHORT).show()
                    showCityPicker = true
                }
            )
        } else {
            Toast.makeText(context, "Izin lokasi ditolak, pilih kota manual", Toast.LENGTH_SHORT).show()
            showCityPicker = true
        }
    }

    // ── Hitung waktu sholat ──────────────────────────────────────────────────
    LaunchedEffect(usedLat, usedLng, usedTz) {
        withContext(Dispatchers.Default) {
            val cal = Calendar.getInstance()
            val pt = hitungWaktuSholat(
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
                usedLat, usedLng, usedTz
            )
            withContext(Dispatchers.Main) { prayerTimes = pt }
        }
    }

    // ── Dialog pilih kota ────────────────────────────────────────────────────
    if (showCityPicker) {
        AlertDialog(
            onDismissRequest = { showCityPicker = false },
            title = { Text("Pilih Kota", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Pilih kota terdekat dengan lokasimu:", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF757575))
                    Spacer(modifier = Modifier.height(12.dp))
                    ExposedDropdownMenuBox(
                        expanded = cityExpanded,
                        onExpandedChange = { cityExpanded = !cityExpanded }
                    ) {
                        OutlinedTextField(
                            value = "${selectedCity.name} (${selectedCity.province})",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cityExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF2E7D32),
                                unfocusedBorderColor = Color(0xFFE0E0E0)
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = cityExpanded,
                            onDismissRequest = { cityExpanded = false },
                            modifier = Modifier.heightIn(max = 300.dp)
                        ) {
                            INDONESIA_CITIES.forEach { city ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(city.name, fontWeight = FontWeight.Medium)
                                            Text(city.province, fontSize = 11.sp, color = Color(0xFF9E9E9E))
                                        }
                                    },
                                    onClick = {
                                        selectedCity = city; cityExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        usedLat = selectedCity.lat; usedLng = selectedCity.lng
                        usedTz = selectedCity.tzOffset; locationLabel = selectedCity.name
                        isGpsMode = false; showCityPicker = false
                        sharedPref.edit()
                            .putFloat("sholat_lat", selectedCity.lat.toFloat())
                            .putFloat("sholat_lng", selectedCity.lng.toFloat())
                            .putInt("sholat_tz", selectedCity.tzOffset)
                            .putString("sholat_city_name", selectedCity.name)
                            .putBoolean("sholat_gps_mode", false).apply()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Gunakan Kota Ini") }
            },
            dismissButton = {
                TextButton(onClick = { showCityPicker = false }) { Text("Batal") }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // ── Tanya izin GPS saat pertama kali ─────────────────────────────────────
    LaunchedEffect(Unit) {
        val hasAsked = sharedPref.getBoolean("sholat_asked_gps", false)
        if (!hasAsked) {
            sharedPref.edit().putBoolean("sholat_asked_gps", true).apply()
            // Tunda sedikit agar UI sudah tampil
            delay(600)
            val hasFinePermission = ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (hasFinePermission) {
                isLoadingGps = true
                getGpsLocation(context,
                    onSuccess = { lat, lng, tz, label ->
                        usedLat = lat; usedLng = lng; usedTz = tz; locationLabel = label
                        isGpsMode = true; isLoadingGps = false
                        sharedPref.edit()
                            .putFloat("sholat_lat", lat.toFloat()).putFloat("sholat_lng", lng.toFloat())
                            .putInt("sholat_tz", tz).putString("sholat_city_name", label)
                            .putBoolean("sholat_gps_mode", true).apply()
                    },
                    onFail = { isLoadingGps = false }
                )
            } else {
                locationPermLauncher.launch(arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }
        }
    }

    // ── UI Card ──────────────────────────────────────────────────────────────
    val dark = LocalIsDarkMode.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardBg()),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {

            // Header: judul + badge sholat berikutnya + lokasi
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        "Jadwal Sholat",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = textPrimC()
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showCityPicker = true }
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            if (isGpsMode) "$locationLabel (GPS)" else locationLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = textSecC()
                        )
                    }
                }

                val pt = prayerTimes
                if (pt != null) {
                    val (_, nextLabel) = getNextPrayer(pt)
                    Surface(
                        color = greenBgC(),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🕌", fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                nextLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (dark) DarkTheme.green else LightTheme.greenDark,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else if (isLoadingGps) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color(0xFF2E7D32),
                        strokeWidth = 2.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Arc Matahari
            if (prayerTimes != null) {
                SunArcWidget(pt = prayerTimes!!)
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF2E7D32))
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Baris waktu sholat
            if (prayerTimes != null) {
                PrayerTimesRow(pt = prayerTimes!!)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Footer: tombol GPS & ganti kota
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isGpsMode) {
                    TextButton(
                        onClick = {
                            locationPermLauncher.launch(arrayOf(
                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                android.Manifest.permission.ACCESS_COARSE_LOCATION
                            ))
                        }
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null,
                            tint = Color(0xFF2E7D32), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Gunakan GPS", color = Color(0xFF2E7D32),
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                TextButton(onClick = { showCityPicker = true }) {
                    Text("Ganti Kota", color = textSecC(), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** Arc perjalanan matahari dari syuruq ke maghrib */
@Composable
fun SunArcWidget(pt: PrayerTimes) {
    val dark = LocalIsDarkMode.current
    val syuruq   = pt.syuruqDecimal
    val maghrib  = pt.maghribDecimal
    val now      = pt.nowDecimal
    val progress = ((now - syuruq) / (maghrib - syuruq)).coerceIn(0.0, 1.0).toFloat()

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().wrapContentHeight()
    ) {
        val canvasW    = maxWidth
        val arcPad     = 20.dp
        val arcDiam    = canvasW - arcPad * 2f
        val arcRadius  = arcDiam / 2f
        val centerX    = canvasW / 2f
        val centerY    = arcRadius
        val labelH     = 44.dp
        val canvasH    = arcRadius + labelH

        val sunAngleDeg   = 180.0 + 180.0 * progress
        val sunAngleRad   = Math.toRadians(sunAngleDeg)
        val sunX: Dp      = centerX + arcRadius * cos(sunAngleRad).toFloat()
        val sunY: Dp      = centerY + arcRadius * sin(sunAngleRad).toFloat()

        val circleRadius = 16.dp
        val emojiSize    = 20.sp
        val emojiHalfW   = 12.dp
        val emojiHalfH   = 11.dp

        val arcTrackColor  = if (dark) DarkTheme.arcTrack  else LightTheme.arcTrack
        val horizonColor   = if (dark) DarkTheme.horizon   else LightTheme.horizon
        val sunCircleColor = if (dark) DarkTheme.sunCircle else LightTheme.sunCircle
        val labelColor     = if (dark) DarkTheme.textSecond else Color(0xFF555555)

        Box(modifier = Modifier.fillMaxWidth().height(canvasH)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w   = size.width
                val padPx      = arcPad.toPx()
                val diamPx     = w - padPx * 2f
                val radiusPx   = diamPx / 2f
                val cxPx       = w / 2f
                val cyPx       = radiusPx
                val arcTopLeft = Offset(cxPx - radiusPx, cyPx - radiusPx)
                val arcSizePx  = Size(diamPx, diamPx)

                drawLine(
                    color = horizonColor,
                    start = Offset(0f, cyPx), end = Offset(w, cyPx),
                    strokeWidth = 1.dp.toPx()
                )
                drawArc(
                    color = arcTrackColor, startAngle = 180f, sweepAngle = 180f,
                    useCenter = false, topLeft = arcTopLeft, size = arcSizePx,
                    style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                )
                if (progress > 0.01f) {
                    drawArc(
                        color = Color(0xFFFFA726), startAngle = 180f,
                        sweepAngle = 180f * progress, useCenter = false,
                        topLeft = arcTopLeft, size = arcSizePx,
                        style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                val sxPx = cxPx + radiusPx * cos(sunAngleRad).toFloat()
                val syPx = cyPx + radiusPx * sin(sunAngleRad).toFloat()
                drawCircle(color = sunCircleColor, radius = circleRadius.toPx(), center = Offset(sxPx, syPx))
                drawCircle(
                    color = Color(0xFFFFA726), radius = circleRadius.toPx(),
                    center = Offset(sxPx, syPx), style = Stroke(width = 1.5.dp.toPx())
                )
            }

            Text(text = "☀️", fontSize = emojiSize,
                modifier = Modifier.absoluteOffset(x = sunX - emojiHalfW, y = sunY - emojiHalfH))

            Row(
                modifier = Modifier.fillMaxWidth()
                    .absoluteOffset(y = arcRadius + 16.dp)
                    .padding(horizontal = arcPad - 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🌅", fontSize = 18.sp)
                    Text(pt.syuruq, style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold, color = labelColor)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🌇", fontSize = 18.sp)
                    Text(pt.maghrib, style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold, color = labelColor)
                }
            }
        }
    }
}

/** Baris 5 waktu sholat utama dengan highlight sholat aktif */
@Composable
fun PrayerTimesRow(pt: PrayerTimes) {
    val dark = LocalIsDarkMode.current
    fun toDouble(hhmm: String): Double {
        val p = hhmm.split(":"); return p[0].toDouble() + p[1].toDouble() / 60.0
    }
    val now = pt.nowDecimal
    val prayers = listOf(
        "Subuh" to pt.subuh, "Dzuhur" to pt.dzuhur,
        "Ashar" to pt.ashar, "Maghrib" to pt.maghrib, "Isya" to pt.isya
    )
    val nextIdx = prayers.indexOfFirst { toDouble(it.second) > now }.let { if (it < 0) 0 else it }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
        prayers.forEachIndexed { idx, (name, time) ->
            val isNext = idx == nextIdx
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    name,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isNext) Color(0xFF2E7D32) else textMutC(),
                    fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (isNext) {
                    Surface(color = Color(0xFF2E7D32), shape = RoundedCornerShape(20.dp)) {
                        Text(time,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.size(5.dp).background(Color(0xFF2E7D32), CircleShape))
                } else {
                    Text(time,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = textPrimC())
                }
            }
        }
    }
}

/** Ambil lokasi GPS dari LocationManager */
fun getGpsLocation(
    context: Context,
    onSuccess: (lat: Double, lng: Double, tz: Int, label: String) -> Unit,
    onFail: () -> Unit
) {
    try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val hasFine = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) { onFail(); return }

        // Tentukan timezone dari longitude secara otomatis (untuk Indonesia)
        fun tzFromLng(lng: Double): Int = when {
            lng < 115.0 -> 7   // WIB
            lng < 135.0 -> 8   // WITA
            else        -> 9   // WIT
        }

        // Coba last known location dulu (sangat cepat, tidak perlu tunggu)
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        var bestLocation: Location? = null
        for (p in providers) {
            if (lm.isProviderEnabled(p)) {
                val loc = lm.getLastKnownLocation(p)
                if (loc != null && (bestLocation == null || loc.accuracy < bestLocation.accuracy)) {
                    bestLocation = loc
                }
            }
        }
        if (bestLocation != null) {
            val tz = tzFromLng(bestLocation.longitude)
            val label = findNearestCity(bestLocation.latitude, bestLocation.longitude)
            onSuccess(bestLocation.latitude, bestLocation.longitude, tz, label)
            return
        }

        // Fallback: minta update aktif
        val provider = when {
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER)     -> LocationManager.GPS_PROVIDER
            else -> null
        }
        if (provider == null) { onFail(); return }

        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                lm.removeUpdates(this)   // hapus langsung setelah dapat 1 lokasi
                val tz    = tzFromLng(loc.longitude)
                val label = findNearestCity(loc.latitude, loc.longitude)
                onSuccess(loc.latitude, loc.longitude, tz, label)
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
            override fun onProviderEnabled(p: String) {}
            override fun onProviderDisabled(p: String) { onFail() }
        }
        lm.requestLocationUpdates(provider, 0L, 0f, listener)
    } catch (e: SecurityException) { onFail() }
    catch (e: Exception)           { onFail() }
}

/** Cari nama kota terdekat dari koordinat GPS */
fun findNearestCity(lat: Double, lng: Double): String {
    var nearest = INDONESIA_CITIES[10]  // default Jakarta
    var minDist = Double.MAX_VALUE
    for (city in INDONESIA_CITIES) {
        val d = sqrt((lat - city.lat).pow(2) + (lng - city.lng).pow(2))
        if (d < minDist) { minDist = d; nearest = city }
    }
    return nearest.name
}

// ══════════════════════════════════════════════════════════════════════════════

// ══════════════════════════════════════════════════════════════════════════════
// HELPER: DATA STATISTIK
// ══════════════════════════════════════════════════════════════════════════════

data class AppUsageStat(val name: String, val packageName: String, val usageMs: Long)

/** Ambil total distraction time hari ini dalam ms */
fun getDistractionTodayMs(context: Context): Long {
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val pm  = context.packageManager
    val cal = Calendar.getInstance()
        .apply { set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }
    val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, cal.timeInMillis, System.currentTimeMillis())
    var total = 0L
    stats?.forEach { s ->
        try {
            val info = pm.getApplicationInfo(s.packageName, 0)
            val pkg  = s.packageName.lowercase()
            val isDistraction = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    (info.category == ApplicationInfo.CATEGORY_SOCIAL ||
                            info.category == ApplicationInfo.CATEGORY_GAME  ||
                            info.category == ApplicationInfo.CATEGORY_VIDEO)) ||
                    listOf("instagram","tiktok","youtube","facebook","twitter","game","mobile.legends","whatsapp","x.com","twitter")
                        .any { pkg.contains(it) }
            if (isDistraction) total += s.totalTimeInForeground
        } catch (e: Exception) { }
    }
    return total
}

/** Ambil distraction time per hari selama 7 hari terakhir (dalam ms), index 0=hari ini */
fun getWeeklyDistractionMs(context: Context): LongArray {
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val pm  = context.packageManager
    val result = LongArray(7)
    val now = System.currentTimeMillis()
    val DAY = 86_400_000L
    for (dayOffset in 0..6) {
        val end   = now - dayOffset * DAY
        val start = end - DAY
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
        var total = 0L
        stats?.forEach { s ->
            try {
                val info = pm.getApplicationInfo(s.packageName, 0)
                val pkg  = s.packageName.lowercase()
                val isD  = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                        (info.category == ApplicationInfo.CATEGORY_SOCIAL ||
                                info.category == ApplicationInfo.CATEGORY_GAME  ||
                                info.category == ApplicationInfo.CATEGORY_VIDEO)) ||
                        listOf("instagram","tiktok","youtube","facebook","twitter","game","mobile.legends","whatsapp")
                            .any { pkg.contains(it) }
                if (isD) total += s.totalTimeInForeground
            } catch (e: Exception) { }
        }
        result[dayOffset] = total
    }
    return result
}

/** Top 3 app distraction hari ini */
fun getTopDistractingApps(context: Context): List<AppUsageStat> {
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val pm  = context.packageManager
    val cal = Calendar.getInstance()
        .apply { set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }
    val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, cal.timeInMillis, System.currentTimeMillis())
    val list  = mutableListOf<AppUsageStat>()
    stats?.forEach { s ->
        try {
            val info = pm.getApplicationInfo(s.packageName, 0)
            val pkg  = s.packageName.lowercase()
            val isD  = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    (info.category == ApplicationInfo.CATEGORY_SOCIAL ||
                            info.category == ApplicationInfo.CATEGORY_GAME  ||
                            info.category == ApplicationInfo.CATEGORY_VIDEO)) ||
                    listOf("instagram","tiktok","youtube","facebook","twitter","game","mobile.legends","whatsapp")
                        .any { pkg.contains(it) }
            if (isD && s.totalTimeInForeground > 0) {
                val name = pm.getApplicationLabel(info).toString()
                list.add(AppUsageStat(name, s.packageName, s.totalTimeInForeground))
            }
        } catch (e: Exception) { }
    }
    return list.sortedByDescending { it.usageMs }.take(3)
}

fun Long.toHhMm(): String {
    val h = TimeUnit.MILLISECONDS.toHours(this)
    val m = TimeUnit.MILLISECONDS.toMinutes(this) % 60
    return if (h > 0) "${h}j ${m}m" else "${m}m"
}

// ══════════════════════════════════════════════════════════════════════════════
// LAYAR STATISTIK
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun StatistikScreen(isDarkMode: Boolean = false) {
    val context = LocalContext.current
    val sp = remember { context.getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE) }

    // ── State ──────────────────────────────────────────────────────────────
    var todayMs    by remember { mutableLongStateOf(0L) }
    var weeklyMs   by remember { mutableStateOf(LongArray(7)) }
    var topApps    by remember { mutableStateOf<List<AppUsageStat>>(emptyList()) }
    var totalVerse by remember { mutableIntStateOf(0) }
    var focusMs    by remember { mutableLongStateOf(0L) }
    var isLoading  by remember { mutableStateOf(true) }

    // Baca SharedPrefs dengan reset harian
    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val tMs  = getDistractionTodayMs(context)
            val wMs  = getWeeklyDistractionMs(context)
            val apps = getTopDistractingApps(context)
            val lastFocusDate = sp.getString("last_focus_date", "") ?: ""
            val fMs  = if (lastFocusDate == todayStr) sp.getLong("focus_time_today_ms", 0L) else 0L
            val tv   = sp.getInt("total_verses", 0)
            withContext(Dispatchers.Main) {
                todayMs   = tMs;  weeklyMs = wMs;  topApps = apps
                focusMs   = fMs;  totalVerse = tv;  isLoading = false
            }
        }
    }

    // ── BATAS HARIAN: 3 jam = normal, 3-5 jam = cukup, >5 jam = kelewat batas ─
    val normalLimitMs  = 3L * 3_600_000L   // 3 jam
    val warnLimitMs    = 5L * 3_600_000L   // 5 jam (batas kelewat)
    val progressNormal = (todayMs.toFloat() / normalLimitMs).coerceIn(0f, 1f)
    val progressWarn   = ((todayMs - normalLimitMs).toFloat() / (warnLimitMs - normalLimitMs)).coerceIn(0f, 1f)
    val statusLabel    = when {
        todayMs < normalLimitMs -> "WAJAR"
        todayMs < warnLimitMs   -> "CUKUP"
        else                    -> "KELEWAT BATAS"
    }
    val statusColor = when (statusLabel) {
        "WAJAR"         -> Color(0xFF43A047)
        "CUKUP"         -> Color(0xFFFFA000)
        else            -> Color(0xFFE53935)
    }
    val sisaMs = maxOf(0L, normalLimitMs - todayMs)

    // ── Renungan: estimasi juz jika waktu dipakai baca Qur'an ─────────────
    // 1 juz ≈ 35 menit = 2_100_000 ms
    val estimatedJuz = (todayMs / 2_100_000L).toInt()

    // Urutan hari untuk bar chart (index 6=hari paling lama lalu, 0=hari ini)
    val dayLabels = remember {
        val cal = Calendar.getInstance()
        val dayNames = arrayOf("Min","Sen","Sel","Rab","Kam","Jum","Sab")
        Array(7) { i ->
            val c = Calendar.getInstance()
            c.timeInMillis = cal.timeInMillis - i * 86_400_000L
            dayNames[c.get(Calendar.DAY_OF_WEEK) - 1]
        }.reversedArray()
    }
    val weekReversed = remember(weeklyMs) { weeklyMs.reversedArray() }
    val maxWeekMs    = remember(weeklyMs) { weeklyMs.maxOrNull()?.takeIf { it > 0 } ?: 1L }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBg()),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // ── HEADER ──────────────────────────────────────────────────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cardBg())
                    .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 20.dp)
            ) {
                Text("Statistik",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = textPrimC())
                Text("Pantau perkembangan fokusmu",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textSecC())
            }
        }

        // ── CARD: BATASAN HARIAN ─────────────────────────────────────────────
        item {
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape    = RoundedCornerShape(20.dp),
                colors   = CardDefaults.cardColors(containerColor = cardBg()),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Text("Batasan Harian",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold, color = textPrimC())
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Tersisa ", style = MaterialTheme.typography.bodySmall, color = textSecC())
                                Text(sisaMs.toHhMm(),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold, color = statusColor)
                                Text(" sebelum kelewat batas",
                                    style = MaterialTheme.typography.bodySmall, color = textSecC())
                            }
                        }
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape)
                                .background(statusColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) { Text("⏱", fontSize = 18.sp) }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("TERPAKAI: ${todayMs.toHhMm()}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold, color = textPrimC())
                        Text("BATAS: 3J 00M",
                            style = MaterialTheme.typography.labelSmall, color = textMutC())
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier.fillMaxWidth().height(14.dp)
                            .clip(RoundedCornerShape(7.dp)).background(trackBg())
                    ) {
                        val greenFrac = (todayMs.toFloat() / normalLimitMs).coerceIn(0f, 1f)
                        Box(Modifier.fillMaxWidth(greenFrac).fillMaxHeight()
                            .clip(RoundedCornerShape(7.dp))
                            .background(Brush.horizontalGradient(listOf(Color(0xFF43A047), Color(0xFFFFA000)))))
                        if (todayMs > normalLimitMs) {
                            val overFrac  = ((todayMs - normalLimitMs).toFloat() / (warnLimitMs - normalLimitMs)).coerceIn(0f, 1f)
                            val startFrac = normalLimitMs.toFloat() / warnLimitMs
                            Box(Modifier.fillMaxWidth(startFrac + overFrac * (1f - startFrac))
                                .fillMaxHeight().clip(RoundedCornerShape(7.dp))
                                .background(Color(0xFFEF5350).copy(alpha = 0.85f)))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("WAJAR", style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold, color = Color(0xFF43A047))
                        Text("CUKUP", style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold, color = Color(0xFFFFA000))
                        Text("KELEWAT BATAS", style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold, color = Color(0xFFE53935))
                    }
                }
            }
        }

        // ── ROW: TOTAL AYAT + WAKTU FOKUS ────────────────────────────────────
        item {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg()),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                            .background(greenBgC()), contentAlignment = Alignment.Center
                        ) { Text("📖", fontSize = 20.sp) }
                        Spacer(Modifier.height(12.dp))
                        val dailyV = run {
                            val lastDate = sp.getString("last_verse_date", "") ?: ""
                            val today2   = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                            if (lastDate == today2) sp.getInt("daily_verses", 0) else 0
                        }
                        Text("$dailyV", style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold, color = textPrimC())
                        Text("Total Ayat Dibaca", style = MaterialTheme.typography.bodySmall,
                            color = textSecC())
                    }
                }
                Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg()),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (LocalIsDarkMode.current) Color(0xFF1E3A5F) else Color(0xFFE3F2FD)),
                            contentAlignment = Alignment.Center
                        ) { Text("⏱", fontSize = 20.sp) }
                        Spacer(Modifier.height(12.dp))
                        val fH = TimeUnit.MILLISECONDS.toHours(focusMs)
                        val fM = TimeUnit.MILLISECONDS.toMinutes(focusMs) % 60
                        val focusLabel = if (fH > 0) "${fH}j ${fM}m" else "${fM}m"
                        Text(focusLabel, style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold, color = textPrimC())
                        Text("Waktu Fokus", style = MaterialTheme.typography.bodySmall,
                            color = textSecC())
                    }
                }
            }
        }

        // ── CARD: GRAFIK BATANG 7 HARI ───────────────────────────────────────
        item {
            Spacer(Modifier.height(12.dp))
            Card(
                modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape     = RoundedCornerShape(20.dp),
                colors    = CardDefaults.cardColors(containerColor = cardBg()),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Hiburan & Sosmed",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = textPrimC())
                    Text("Durasi (jam) 7 hari terakhir",
                        style = MaterialTheme.typography.bodySmall, color = textSecC())
                    Spacer(Modifier.height(20.dp))
                    if (isLoading) {
                        Box(Modifier.fillMaxWidth().height(120.dp), Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFFF05252), strokeWidth = 2.dp)
                        }
                    } else {
                        val barInactive = if (LocalIsDarkMode.current)
                            Brush.verticalGradient(listOf(Color(0xFF334155), Color(0xFF1E293B)))
                        else
                            Brush.verticalGradient(listOf(Color(0xFFE5E7EB), Color(0xFFD1D5DB)))
                        Row(Modifier.fillMaxWidth().height(140.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            weekReversed.forEachIndexed { idx, ms ->
                                val isToday = idx == 6
                                val frac = (ms.toFloat() / maxWeekMs).coerceIn(0f, 1f)
                                val barH = if (ms == 0L) 8.dp else 8.dp + (100.dp - 8.dp) * frac
                                val barColor = if (isToday || ms == maxWeekMs)
                                    Brush.verticalGradient(listOf(Color(0xFFFF6B6B), Color(0xFFF05252)))
                                else barInactive
                                Column(horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(Modifier.width(26.dp).height(barH)
                                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                        .background(barColor))
                                    Spacer(Modifier.height(6.dp))
                                    Text(dayLabels.getOrElse(idx) { "" },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isToday) Color(0xFFF05252) else textMutC(),
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── CARD: WAKTU LAYAR TERTINGGI ──────────────────────────────────────
        item {
            Spacer(Modifier.height(12.dp))
            Card(
                modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape     = RoundedCornerShape(20.dp),
                colors    = CardDefaults.cardColors(containerColor = cardBg()),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📱", fontSize = 18.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("Waktu Layar Tertinggi",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = textPrimC())
                    }
                    Spacer(Modifier.height(16.dp))
                    if (isLoading) {
                        repeat(3) {
                            Box(Modifier.fillMaxWidth().height(20.dp)
                                .clip(RoundedCornerShape(4.dp)).background(trackBg()))
                            Spacer(Modifier.height(16.dp))
                        }
                    } else if (topApps.isEmpty()) {
                        Text("Tidak ada data penggunaan hari ini.",
                            style = MaterialTheme.typography.bodySmall, color = textMutC())
                    } else {
                        val maxMs = topApps.maxOf { it.usageMs }
                        val appColors = listOf(Color(0xFFEC4899), Color(0xFF60A5FA), Color(0xFFEF4444))
                        topApps.forEachIndexed { idx, app ->
                            val frac  = (app.usageMs.toFloat() / maxMs).coerceIn(0f, 1f)
                            val color = appColors.getOrElse(idx) { Color(0xFF6B7280) }
                            Row(Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(app.name, style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold, color = textPrimC())
                                Text(app.usageMs.toHhMm(), style = MaterialTheme.typography.bodyMedium,
                                    color = textSecC())
                            }
                            Spacer(Modifier.height(6.dp))
                            Box(Modifier.fillMaxWidth().height(6.dp)
                                .clip(RoundedCornerShape(3.dp)).background(trackBg())
                            ) {
                                Box(Modifier.fillMaxWidth(frac).fillMaxHeight()
                                    .clip(RoundedCornerShape(3.dp)).background(color))
                            }
                            if (idx < topApps.size - 1) Spacer(Modifier.height(16.dp))
                        }
                    }
                }
            }
        }

        // ── CARD: RENUNGAN WAKTU ─────────────────────────────────────────────
        item {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFF4F46E5), Color(0xFF7C3AED))))
                    .padding(24.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("✦", fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f))
                        Spacer(Modifier.width(8.dp))
                        Text("Renungan Waktu", style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.85f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Seandainya Waktu Layarku Kupakai Untuk Membaca Qur'an",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold, color = Color.White, lineHeight = 26.sp)
                    Spacer(Modifier.height(16.dp))
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.15f)).padding(16.dp)
                    ) {
                        Column {
                            Text("Aku bakal dapat", style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.8f))
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text("$estimatedJuz", style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Black, color = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text("juz", style = MaterialTheme.typography.titleLarge,
                                    color = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.padding(bottom = 6.dp))
                            }
                            if (todayMs > 0) {
                                val totalMin = TimeUnit.MILLISECONDS.toMinutes(todayMs)
                                Text("dari $totalMin menit waktu layarmu hari ini",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.65f))
                                Text("(1 juz ≈ 35 menit membaca)", style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(top = 2.dp))
                            } else {
                                Text("Belum ada data penggunaan hari ini",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.65f))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun HeaderDashboard(isDarkMode: Boolean = false, onToggleDarkMode: () -> Unit = {}) {
    val dark     = isDarkMode
    val textPrim = if (dark) Color(0xFFF1F5F9) else Color(0xFF1A1A1A)
    val textSub  = if (dark) Color(0xFF94A3B8) else Color(0xFF757575)
    val iconBg   = if (dark) Color(0xFF1E293B) else Color.White
    val iconTint = if (dark) Color(0xFFCBD5E1) else Color(0xFF333333)

    var showSettingsMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("UNGKER",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = textPrim)
            Text("Penjaga anak muda untuk mengaji.",
                style = MaterialTheme.typography.bodyMedium,
                color = textSub)
        }
        Row {
            IconButton(onClick = { },
                modifier = Modifier.background(iconBg, CircleShape)) {
                Icon(Icons.Default.Notifications, contentDescription = null, tint = iconTint)
            }
            Spacer(modifier = Modifier.width(8.dp))
            // Settings dengan dropdown
            Box {
                IconButton(onClick = { showSettingsMenu = !showSettingsMenu },
                    modifier = Modifier.background(iconBg, CircleShape)) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = iconTint)
                }
                DropdownMenu(
                    expanded = showSettingsMenu,
                    onDismissRequest = { showSettingsMenu = false },
                ) {
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Gunakan Text emoji — tidak bergantung pada material-icons-extended
                                Text(
                                    if (isDarkMode) "☀️" else "🌙",
                                    fontSize = 18.sp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    if (isDarkMode) "Mode Terang" else "Mode Gelap",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        },
                        onClick = {
                            onToggleDarkMode()
                            showSettingsMenu = false
                        }
                    )
                }
            }
        }
    }
}

// Thread-safe icon cache menggunakan ConcurrentHashMap
private val iconCache = java.util.concurrent.ConcurrentHashMap<String, ImageBitmap>()

@Composable
fun DaftarAplikasiScreen(isDarkMode: Boolean = false) {
    val context = LocalContext.current
    val pm = context.packageManager
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    val sharedPref = context.getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE)
    val blockedApps = remember {
        mutableStateListOf<String>().apply {
            addAll(sharedPref.getStringSet("blocked_apps", emptySet()) ?: emptySet())
        }
    }

    var selectedDuration by remember {
        mutableIntStateOf(sharedPref.getInt("lock_duration_minutes", 5))
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
            val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                pm.queryIntentActivities(mainIntent, 0)
            }

            val appList = resolveInfos.mapNotNull { resolveInfo ->
                val app = resolveInfo.activityInfo.applicationInfo
                val name = resolveInfo.loadLabel(pm).toString()
                val packageName = app.packageName

                var categoryStr = "Lainnya"
                var isTarget = false

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    when (app.category) {
                        ApplicationInfo.CATEGORY_SOCIAL -> { categoryStr = "Sosial Media"; isTarget = true }
                        ApplicationInfo.CATEGORY_GAME -> { categoryStr = "Game"; isTarget = true }
                        ApplicationInfo.CATEGORY_VIDEO -> { categoryStr = "Hiburan"; isTarget = true }
                    }
                }

                // Extra check for common distractors
                val lowerPackage = packageName.lowercase()
                if (lowerPackage.contains("instagram") || lowerPackage.contains("tiktok") ||
                    lowerPackage.contains("youtube") || lowerPackage.contains("facebook") ||
                    lowerPackage.contains("twitter") || lowerPackage.contains("game") ||
                    lowerPackage.contains("mobile.legends")) {
                    isTarget = true
                    if (categoryStr == "Lainnya") {
                        categoryStr = if (lowerPackage.contains("game")) "Game" else "Hiburan"
                    }
                }

                if (isTarget) {
                    AppInfo(name, packageName, categoryStr)
                } else null
            }.distinctBy { it.packageName }.sortedBy { it.name }

            withContext(Dispatchers.Main) {
                apps = appList
                isLoading = false
            }
        }
    }

    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) apps
        else apps.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }
    val isAllSelected = filteredApps.isNotEmpty() && filteredApps.all { blockedApps.contains(it.packageName) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Daftar Aplikasi",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = textPrimC()
        )
        Text(
            "Pilih aplikasi dan durasi sesi kunci",
            style = MaterialTheme.typography.bodyMedium,
            color = textSecC()
        )

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF00C853)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    color = Color.White.copy(alpha = 0.2f),
                    shape = CircleShape
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("🛡️", fontSize = 20.sp)
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${blockedApps.size} Aplikasi",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        "Masuk daftar blokir",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp
                    )
                }

                TextButton(
                    onClick = {
                        if (isAllSelected) {
                            filteredApps.forEach { blockedApps.remove(it.packageName) }
                        } else {
                            filteredApps.forEach { if (!blockedApps.contains(it.packageName)) blockedApps.add(it.packageName) }
                        }
                        sharedPref.edit().putStringSet("blocked_apps", blockedApps.toSet()).apply()
                    }
                ) {
                    Text(
                        if (isAllSelected) "Batal Semua" else "Pilih Semua",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Durasi Kunci Selector
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🕒", fontSize = 18.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Durasi Kunci", fontWeight = FontWeight.Bold, color = textPrimC())
        }
        Spacer(modifier = Modifier.height(12.dp))
        val durations = listOf(5, 10, 15, 20)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(durations) { duration ->
                val isSelected = selectedDuration == duration
                Surface(
                    onClick = {
                        selectedDuration = duration
                        sharedPref.edit().putInt("lock_duration_minutes", duration).apply()
                    },
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) Color(0xFF00C853) else cardBg(),
                    border = if (isSelected) null else BorderStroke(1.dp, borderC()),
                    shadowElevation = if (isSelected) 4.dp else 0.dp
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "$duration Mnt",
                            color = if (isSelected) Color.White else textSecC(),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Cari aplikasi...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF00C853),
                unfocusedBorderColor = Color(0xFFE0E0E0)
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF00C853))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Tunggu sebentar, ya...", color = textSecC())
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    AppItem(
                        app = app,
                        isBlocked = blockedApps.contains(app.packageName),
                        onToggle = { isChecked ->
                            if (isChecked) {
                                blockedApps.add(app.packageName)
                            } else {
                                blockedApps.remove(app.packageName)
                            }
                            sharedPref.edit().putStringSet("blocked_apps", blockedApps.toSet()).apply()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AppItem(app: AppInfo, isBlocked: Boolean, onToggle: (Boolean) -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager
    var iconBitmap by remember(app.packageName) {
        mutableStateOf<ImageBitmap?>(iconCache[app.packageName])
    }

    LaunchedEffect(app.packageName) {
        if (iconBitmap == null) {
            withContext(Dispatchers.IO) {
                try {
                    val drawable = pm.getApplicationIcon(app.packageName)
                    // 72px cukup untuk tampilan 40dp @ 2x density, hemat memory & decode lebih cepat
                    val bitmap = drawable.toBitmap(72, 72).asImageBitmap()
                    iconCache[app.packageName] = bitmap
                    withContext(Dispatchers.Main) { iconBitmap = bitmap }
                } catch (_: Exception) {}
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardBg()),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.animation.AnimatedContent(
                targetState = iconBitmap,
                transitionSpec = { androidx.compose.animation.fadeIn() togetherWith androidx.compose.animation.fadeOut() },
                label = "IconFade"
            ) { bmp ->
                if (bmp != null) {
                    Image(bitmap = bmp, contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
                } else {
                    Box(modifier = Modifier.size(40.dp)
                        .background(cardAltBg(), RoundedCornerShape(8.dp)))
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(app.name, fontWeight = FontWeight.Bold, color = textPrimC())
                Text(app.category, fontSize = 12.sp, color = textSecC())
            }
            Switch(
                checked = isBlocked,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF00C853),
                    uncheckedThumbColor = Color(0xFFBDBDBD),
                    uncheckedTrackColor = if (LocalIsDarkMode.current) Color(0xFF334155) else Color(0xFFEEEEEE)
                )
            )
        }
    }
}

@Composable
fun TimerCard(remainingTimeMillis: Long, progress: Float, onTambahWaktu: () -> Unit) {
    val dark = LocalIsDarkMode.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardBg()),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                color = greenBgC(),
                shape = CircleShape
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🕒", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Sisa Waktu Penggunaan",
                        color = if (dark) DarkTheme.green else LightTheme.greenDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
                val trackColor = if (dark) DarkTheme.bgTrack else Color(0xFFF0F0F0)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawArc(
                        color = trackColor,
                        startAngle = 0f, sweepAngle = 360f, useCenter = false,
                        style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = Color(0xFF00C853),
                        startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                        style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingTimeMillis)
                    val seconds = TimeUnit.MILLISECONDS.toSeconds(remainingTimeMillis) % 60
                    Text(
                        text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = textPrimC()
                    )
                    Text(
                        "MENIT : DETIK",
                        style = MaterialTheme.typography.labelSmall,
                        color = textSecC(),
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onTambahWaktu,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📖", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tambah Waktu", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun MotivasiCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(Color(0xFF004D40), Color(0xFF00695C))))
                .padding(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📖", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "MOTIVASI HARI INI",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "فَإِنَّ مَعَ الْعُسْرِ يُسْرًا",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "\"Karena sesungguhnya sesudah kesulitan itu ada kemudahan.\"",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Light
                )
                Text(
                    "— QS. Al-Insyirah: 5",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    icon: String,
    value: String,
    label: String,
    iconBg: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = cardBg()),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = textPrimC()
            )
            Text(label, style = MaterialTheme.typography.bodySmall, color = textSecC())
        }
    }
}

@Composable
fun AyatReadCard(total: Int, daily: Int) {
    val dark = LocalIsDarkMode.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardBg()),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (dark) Color(0xFF3B2F00) else Color(0xFFFFF8E1)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📖", fontSize = 24.sp)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Jumlah Ayat Dibaca",
                        fontWeight = FontWeight.Bold,
                        color = textPrimC())
                    Text(
                        if (daily > 0) "+$daily ayat hari ini" else "Belum ada bacaan hari ini",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (daily > 0) Color(0xFF4ADE80) else textSecC()
                    )
                }
            }
            Text(
                total.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFA000)
            )
        }
    }
}