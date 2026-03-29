package com.dame.ungker.ungkeh

import android.Manifest
import android.app.KeyguardManager
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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─── Status pencocokan huruf Arab per karakter ────────────────────────────────
enum class CharMatchStatus {
    EXACT,    // huruf cocok persis          → hijau tua
    PHONETIC, // substitusi fonetik oleh STT → kuning
    SKIP,     // kata dilewati user (gap)    → merah
    MISS      // belum terdeteksi            → hitam
}

/**
 * Peta substitusi fonetik Arab yang umum terjadi di Google STT.
 * Kunci = huruf TARGET di mushaf
 * Nilai = huruf-huruf yang sering dikeluarkan STT sebagai pengganti
 *
 * Berdasarkan kemiripan artikulasi dan data empiris ASR bahasa Arab:
 * - Pasangan interdental:  ث ↔ ت ↔ ذ ↔ س
 * - Pasangan stop-emfatik: ض ↔ د ↔ ظ ↔ ط
 * - Pasangan sibilant:     ص ↔ س ↔ ز
 * - Pasangan uvular:       ق ↔ ك ↔ غ
 * - Pasangan faringal:     ع ↔ ا ↔ ه — ح ↔ ه ↔ خ
 */
val PHONETIC_SUBSTITUTIONS: Map<Char, Set<Char>> = mapOf(
    // Interdental
    'ث' to setOf('ت', 'س', 'ذ'),
    'ذ' to setOf('ز', 'د', 'ث', 'ظ'),
    'ظ' to setOf('ض', 'ذ', 'ز', 'ط'),
    // Stop / Emfatik
    'ض' to setOf('د', 'ظ', 'ط'),
    'ط' to setOf('ت', 'ض', 'ظ'),
    'ص' to setOf('س', 'ز'),
    // Uvular / Velar
    'ق' to setOf('ك', 'غ'),
    'غ' to setOf('ق', 'ك'),
    'خ' to setOf('ح', 'ك'),
    // Faringal / Glotal
    'ع' to setOf('ا', 'ه'),
    'ح' to setOf('ه', 'خ'),
    // Reverse (STT output → kemungkinan maksud user)
    'ت' to setOf('ث', 'ط'),
    'س' to setOf('ث', 'ص', 'ز'),
    'ز' to setOf('ذ', 'ظ', 'ص', 'س'),
    'د' to setOf('ذ', 'ض'),
    'ك' to setOf('ق', 'خ', 'غ'),
    'ه' to setOf('ح', 'ع'),
)
// ─────────────────────────────────────────────────────────────────────────────

class LockActivity : ComponentActivity() {

    private var targetPackage: String? = null
    private var creditOverrideMs: Long = -1L          // -1 = pakai setting normal
    private var startCooldownKey: String? = null       // key SharedPrefs untuk cooldown
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        targetPackage   = intent.getStringExtra("target_package")
        creditOverrideMs = intent.getLongExtra("credit_override_ms", -1L)
        startCooldownKey = intent.getStringExtra("start_cooldown_key")

        setupLockScreenFlags()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (targetPackage == null) {
                    finish()
                }
            }
        })

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
                        modifier = Modifier.fillMaxSize(),
                        color = if (isDarkMode) Color(0xFF0F172A) else Color(0xFFF8F9FA)
                    ) {
                        val context = LocalContext.current
                        var forceGameMode by remember { mutableStateOf(false) }
                        val isOffline = remember { !isOnline(context) }

                        if (isOffline || forceGameMode) {
                            SusunKataGame(showBackButton = targetPackage == null)
                        } else {
                            LayarPengunciSuara(
                                showBackButton = targetPackage == null,
                                onSwitchToGame = { forceGameMode = true }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun isOnline(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            else -> false
        }
    }

    private fun setupLockScreenFlags() {
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

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    fun SusunKataGame(showBackButton: Boolean) {
        val context = LocalContext.current
        val dark    = LocalIsDarkMode.current
        // Warna token dark/light untuk SusunKataGame
        val bgPage     = if (dark) Color(0xFF0F172A) else Color(0xFFF8F9FA)
        val cardBg     = if (dark) Color(0xFF1E293B) else Color.White
        val textPrim   = if (dark) Color(0xFFF1F5F9) else Color(0xFF4B4B4B)
        val textSub    = if (dark) Color(0xFF94A3B8) else Color(0xFF757575)
        val trackColor = if (dark) Color(0xFF334155) else Color(0xFFE5E5E5)
        val pillBg     = if (dark) Color(0xFF14532D) else Color(0xFFE8F5E9)
        val pillText   = if (dark) Color(0xFF4ADE80) else Color(0xFF2E7D32)
        val borderNorm = if (dark) Color(0xFF334155) else Color(0xFFE5E5E5)

        val dbHelper = remember { QuranDatabaseHelper(context) }
        val sharedPref =
            remember { context.getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE) }

        var isSuccess by remember { mutableStateOf(false) }

        val lastGameTime = sharedPref.getLong("last_game_success_time", 0L)
        val cooldownMillis = 2 * 60 * 1000L
        val isCoolingDown = !isSuccess && (System.currentTimeMillis() - lastGameTime < cooldownMillis)

        if (isCoolingDown) {
            var remainingSeconds by remember { mutableLongStateOf(0L) }
            LaunchedEffect(Unit) {
                while (true) {
                    val currentLastTime = sharedPref.getLong("last_game_success_time", 0L)
                    val diff = System.currentTimeMillis() - currentLastTime
                    if (diff >= cooldownMillis) {
                        (context as? ComponentActivity)?.recreate()
                        break
                    }
                    remainingSeconds = (cooldownMillis - diff) / 1000
                    delay(1000)
                }
            }
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                if (showBackButton) {
                    IconButton(
                        onClick = { (context as? ComponentActivity)?.finish() },
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back",
                            tint = textPrim)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⏳", fontSize = 60.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Fitur Game Sedang Cooldown",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        color = textPrim)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Tunggu $remainingSeconds detik lagi atau gunakan fitur 'Baca Suara'.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = textSub, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { (context as? ComponentActivity)?.recreate() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1CB0F6))
                    ) { Text("Coba Lagi") }
                }
            }
            return
        }

        var ayatTarget by remember { mutableStateOf(dbHelper.getRandomShortVerseJuz30()) }
        val correctWords  = remember(ayatTarget) { ayatTarget.second.split(" ").filter { it.isNotBlank() } }
        val shuffledWords = remember(ayatTarget) { correctWords.shuffled() }
        val selectedWords = remember { mutableStateListOf<String>() }
        val availableWords = remember(ayatTarget) { mutableStateListOf<String>().apply { addAll(shuffledWords) } }
        var isError by remember { mutableStateOf(false) }

        // ── AUTO-CHECK: ketika semua kata sudah ditempatkan ─────────────────
        // Cek setelah 600ms delay agar user sempat lihat progress bar penuh
        val selectedSize = selectedWords.size
        LaunchedEffect(selectedSize) {
            if (selectedSize == correctWords.size && correctWords.isNotEmpty() && !isSuccess) {
                delay(600)
                // Guard ulang setelah delay — user mungkin sudah cabut satu kata
                if (selectedWords.size == correctWords.size) {
                    if (selectedWords.toList() == correctWords) {
                        isSuccess = true
                        handleGameSuccess(context, isGameMode = true)
                    } else {
                        isError = true
                        Toast.makeText(context, "Susunan masih salah, coba lagi!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (showBackButton) {
                IconButton(
                    onClick = { (context as? ComponentActivity)?.finish() },
                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp).zIndex(1f)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back",
                        tint = textPrim)
                }
            }

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LinearProgressIndicator(
                                progress = { (selectedWords.size.toFloat() / correctWords.size.toFloat()) },
                                modifier = Modifier.weight(1f).height(12.dp).clip(CircleShape),
                                color = Color(0xFF58CC02),
                                trackColor = trackColor
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("🧩", fontSize = 24.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(40.dp))
                    Surface(color = pillBg, shape = CircleShape) {
                        Text(
                            text = "سورة ${ayatTarget.first}",
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.titleMedium,
                            color = pillText, fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text("Susunlah potongan ayat ini:",
                            style = MaterialTheme.typography.bodyLarge,
                            color = textSub, textAlign = TextAlign.Center)
                    }
                    Spacer(modifier = Modifier.height(30.dp))
                    // Kotak jawaban user
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 150.dp)
                            .border(2.dp,
                                if (isError) Color.Red else borderNorm,
                                RoundedCornerShape(16.dp))
                            .background(cardBg, RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            selectedWords.forEach { word ->
                                WordChip(word, isPrimary = true, dark = dark) {
                                    selectedWords.remove(word)
                                    availableWords.add(word)
                                    isError = false
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    // Kata-kata pilihan
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        availableWords.forEach { word ->
                            WordChip(word, isPrimary = false, dark = dark) {
                                selectedWords.add(word)
                                availableWords.remove(word)
                                isError = false
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(40.dp))
                    Button(
                        onClick = {
                            if (selectedWords.toList() == correctWords) {
                                isSuccess = true
                                handleGameSuccess(context, isGameMode = true)
                            } else {
                                isError = true
                                Toast.makeText(context, "Susunan masih salah, coba lagi!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSuccess) Color(0xFF58CC02) else Color(0xFF1CB0F6)
                        ),
                        enabled = selectedWords.size == correctWords.size && !isSuccess
                    ) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(if (isSuccess) "✅ أحسنت!" else "PERIKSA",
                                fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isSuccess,
            enter = fadeIn() + scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(Color(0xFF58CC02).copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🌟", fontSize = 80.sp)
                    Text("MANTAP!", color = Color.White,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black)
                    Text("+2 Menit Kredit Waktu", color = Color.White,
                        style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }

    @Composable
    fun WordChip(word: String, isPrimary: Boolean, dark: Boolean = false, onClick: () -> Unit) {
        val chipBg     = when {
            isPrimary && dark  -> Color(0xFF263348)
            isPrimary          -> Color.White
            dark               -> Color(0xFF1E293B)
            else               -> Color(0xFFF0F0F0)
        }
        val chipBorder = when {
            isPrimary && dark  -> Color(0xFF334155)
            isPrimary          -> Color(0xFFE5E5E5)
            else               -> Color.Transparent
        }
        val textColor  = if (dark) Color(0xFFF1F5F9) else Color(0xFF4B4B4B)

        Surface(
            modifier = Modifier.clickable { onClick() }.padding(2.dp),
            color = chipBg,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(2.dp, chipBorder),
            shadowElevation = if (dark) 0.dp else 2.dp
        ) {
            Text(
                text = word,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = textColor,
                textAlign = TextAlign.Center
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun handleGameSuccess(context: Context, isGameMode: Boolean = false) {
        val sharedPref = context.getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE)
        val currentCredit = sharedPref.getLong("remaining_credit", 0L)
        val maxCreditLimit = 3600000L

        // creditOverrideMs: dari beranda (tambah waktu) → tetap 5 menit
        // isGameMode: tebak ayat → 2 menit
        // default (baca suara): sesuai setting lock_duration_minutes
        val addMillis = when {
            creditOverrideMs > 0 -> creditOverrideMs
            isGameMode           -> 2L * 60 * 1000L
            else                 -> sharedPref.getInt("lock_duration_minutes", 5) * 60 * 1000L
        }
        val newCredit = minOf(maxCreditLimit, currentCredit + addMillis)

        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastDayStr = sharedPref.getString("last_verse_date", "") ?: ""
        val currentDaily = if (lastDayStr == todayStr) sharedPref.getInt("daily_verses", 0) else 0

        val lastFocusDate = sharedPref.getString("last_focus_date", "") ?: ""
        val currentFocusMs = if (lastFocusDate == todayStr)
            sharedPref.getLong("focus_time_today_ms", 0L) else 0L
        val newFocusMs = currentFocusMs + addMillis

        val editor = sharedPref.edit()
        editor.putLong("remaining_credit", newCredit)
        editor.putInt("total_verses", sharedPref.getInt("total_verses", 0) + 1)
        editor.putInt("daily_verses", currentDaily + 1)
        editor.putString("last_verse_date", todayStr)
        editor.putLong("focus_time_today_ms", newFocusMs)
        editor.putString("last_focus_date", todayStr)
        if (isGameMode) editor.putLong("last_game_success_time", System.currentTimeMillis())
        // Simpan timestamp cooldown untuk tombol "Tambah Waktu" di beranda
        startCooldownKey?.let { key ->
            editor.putLong(key, System.currentTimeMillis())
        }
        editor.apply()

        serviceScope.launch {
            delay(2000)
            val pm = context.packageManager
            val intentBack = targetPackage?.let { pm.getLaunchIntentForPackage(it) }
            if (intentBack != null) {
                intentBack.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intentBack)
            }
            finish()
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    fun LayarPengunciSuara(showBackButton: Boolean, onSwitchToGame: () -> Unit) {
        val context = LocalContext.current
        val dark    = LocalIsDarkMode.current
        // Token warna dark/light
        val textPrim    = if (dark) Color(0xFFF1F5F9) else Color(0xFF1A1A1A)
        val textSub     = if (dark) Color(0xFF94A3B8) else Color(0xFF757575)
        val cardBg      = if (dark) Color(0xFF1E293B) else Color.White
        val pillBg      = if (dark) Color(0xFF14532D) else Color(0xFFE8F5E9)
        val pillText    = if (dark) Color(0xFF4ADE80) else Color(0xFF2E7D32)
        val transcriptBg = if (dark) Color(0xFF1E293B) else Color(0xFFEEEEEE).copy(alpha = 0.5f)
        val transcriptText = if (dark) Color(0xFFCBD5E1) else Color(0xFF333333)
        val missColor   = if (dark) Color(0xFFE2E8F0) else Color(0xFF1A1A1A)

        val dbHelper = remember { QuranDatabaseHelper(context) }
        var daftarAyatTarget by remember { mutableStateOf<List<String>>(emptyList()) }
        var indexAyatSekarang by remember { mutableIntStateOf(0) }
        var namaSurah by remember { mutableStateOf("") }
        var ayatAktif by remember { mutableStateOf("") }
        var charStatusMap by remember { mutableStateOf(mapOf<Int, Map<Int, CharMatchStatus>>()) }
        var teksHasilSuara by remember { mutableStateOf("Siap mendengarkan...") }
        var sedangMerekam by remember { mutableStateOf(false) }
        var fullTranscript by remember { mutableStateOf("") }
        var percobaan by remember { mutableIntStateOf(0) }

        LaunchedEffect(Unit) {
            val (surah, ayatSet) = dbHelper.getRandomVerseSet()
            namaSurah = surah
            daftarAyatTarget = ayatSet
            ayatAktif = ayatSet[0]
        }

        val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
        val recIntent = remember {
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
        }

        fun updateProgress(hasilRaw: String) {
            if (hasilRaw.length <= 1) return

            // ── Normalisasi ──────────────────────────────────────────────
            val hasilNorm   = dbHelper.normalizeArabic(hasilRaw)
            val targetWords = ayatAktif.split(" ").filter { it.isNotBlank() }
            val userWords   = hasilNorm.split(" ").filter { it.isNotBlank() }
            if (userWords.isEmpty()) return

            // ── Prefiks Arab yang sering dihilangkan STT ─────────────────
            val prefixes = listOf("وال","فال","بال","كال","لل","ال","و","ف","ب","ل","ك")
            fun stripPrefix(w: String): String {
                for (p in prefixes) {
                    if (w.startsWith(p) && w.length > p.length) return w.substring(p.length)
                }
                return w
            }

            // ══════════════════════════════════════════════════════════════
            // EDIT DISTANCE FONETIK
            // Substitusi fonetik (ع→ا, ق→ك, dst) diberi cost 0.3 bukan 1.0
            // sehingga pelafalan STT yang fonetik tidak terlalu dihukum
            // ══════════════════════════════════════════════════════════════
            fun phoneticEditDistance(a: String, b: String): Float {
                val m = a.length; val n = b.length
                val dp = FloatArray(n + 1) { it.toFloat() }
                for (i in 1..m) {
                    var prev = dp[0]; dp[0] = i.toFloat()
                    for (j in 1..n) {
                        val temp = dp[j]
                        val cost = when {
                            a[i-1] == b[j-1] -> 0f
                            // Substitusi fonetik bilateral
                            PHONETIC_SUBSTITUTIONS[a[i-1]]?.contains(b[j-1]) == true ||
                                    PHONETIC_SUBSTITUTIONS[b[j-1]]?.contains(a[i-1]) == true -> 0.3f
                            else -> 1f
                        }
                        dp[j] = minOf(dp[j] + 1f, dp[j-1] + 1f, prev + cost)
                        prev = temp
                    }
                }
                return dp[n]
            }

            // ══════════════════════════════════════════════════════════════
            // WORD MATCH SCORE (0..1)
            // Makin tinggi = makin mirip
            // Anti-cheat: penalti keras untuk panjang kata yang beda jauh
            // ══════════════════════════════════════════════════════════════
            fun wordMatchScore(target: String, user: String): Float {
                if (target.isEmpty() || user.isEmpty()) return 0f
                if (target == user) return 1f
                val lenRatio = minOf(target.length, user.length).toFloat() /
                        maxOf(target.length, user.length)
                // Substring hanya valid jika panjang mirip (>=80%) — cegah "الملك" match "الملايكه"
                if ((target.contains(user) || user.contains(target)) && lenRatio >= 0.80f)
                    return 0.85f * lenRatio
                val ed     = phoneticEditDistance(target, user)
                val maxLen = maxOf(target.length, user.length).toFloat()
                val sim    = 1f - ed / maxLen
                // Penalti keras jika panjang berbeda >35% — cegah "يوم" match "يومنذ"
                return if (lenRatio < 0.65f) sim * 0.5f else sim
            }

            // ══════════════════════════════════════════════════════════════
            // THRESHOLD DINAMIS BERDASARKAN PANJANG KATA
            // SUPER LENIENT untuk STT Arabic yang tidak akurat
            // ══════════════════════════════════════════════════════════════
            fun matchThreshold(targetLen: Int): Float = when {
                targetLen <= 2 -> 0.20f  // هم، في، ما
                targetLen <= 3 -> 0.25f  // يوم، كان، بل
                targetLen <= 5 -> 0.30f  // تعرج، الف
                targetLen <= 7 -> 0.30f  // اكثrmereka، يسمعون
                else           -> 0.30f  // الملايكه، مقداره +
            }

            // ══════════════════════════════════════════════════════════════
            // SEQUENTIAL POSITIONAL ALIGNMENT
            // Setiap userWord hanya bisa dikonsumsi SEKALI secara berurutan.
            // Mencegah: kata "هم" di posisi belakang ter-match oleh "هم"
            // yang sudah dipakai untuk posisi depan.
            // ══════════════════════════════════════════════════════════════
            fun buildAlignment(
                targets: List<String>,
                users: List<String>
            ): Map<Int, String?> {
                val result  = mutableMapOf<Int, String?>()
                var userPtr = 0
                for (tIdx in targets.indices) {
                    val tNorm  = targets[tIdx]
                    val tStrip = stripPrefix(tNorm)
                    var best   = 0f
                    var bestUIdx = -1
                    val lookahead = minOf(userPtr + 4, users.size)
                    for (uIdx in userPtr until lookahead) {
                        val uNorm  = users[uIdx]
                        val uStrip = stripPrefix(uNorm)
                        val s = maxOf(
                            wordMatchScore(tNorm,  uNorm),
                            wordMatchScore(tStrip, uStrip)
                        )
                        if (s > best) { best = s; bestUIdx = uIdx }
                    }
                    if (best >= matchThreshold(tNorm.length) && bestUIdx >= 0) {
                        result[tIdx] = users[bestUIdx]
                        userPtr = bestUIdx + 1
                    } else {
                        result[tIdx] = null
                    }
                }
                return result
            }

            // ── Bandingkan karakter target word vs matched user word ──────
            fun charStatusForWord(
                targetNorm: String,
                matchedWord: String?
            ): Map<Int, CharMatchStatus> {
                if (matchedWord == null)
                    return targetNorm.indices.associateWith { CharMatchStatus.MISS }
                val userNorm = dbHelper.normalizeArabic(matchedWord)
                if (targetNorm == userNorm)
                    return targetNorm.indices.associateWith { CharMatchStatus.EXACT }

                // Hitung similarity score untuk menentukan warna
                val wordScore = maxOf(
                    wordMatchScore(targetNorm, userNorm),
                    wordMatchScore(stripPrefix(targetNorm), stripPrefix(userNorm))
                )

                // SUPER LENIENT: Jika similarity >= 30%, treat as exact match (hijau)
                if (wordScore >= 0.30f) {
                    return targetNorm.indices.associateWith { CharMatchStatus.EXACT }
                }

                // Jika similarity >= 10%, gunakan character-level matching (kuning)
                if (wordScore >= 0.10f) {
                    return targetNorm.mapIndexed { i, targetChar ->
                        val isExact = userNorm.contains(targetChar)
                        val isPhonetic = PHONETIC_SUBSTITUTIONS[targetChar]
                            ?.any { userNorm.contains(it) } == true
                        i to when {
                            isExact -> CharMatchStatus.EXACT
                            isPhonetic -> CharMatchStatus.PHONETIC
                            else -> CharMatchStatus.MISS
                        }
                    }.toMap()
                }

                // Low similarity - semua karakter MISS (hitam)
                return targetNorm.indices.associateWith { CharMatchStatus.MISS }
            }

            // ── Jalankan alignment ────────────────────────────────────────
            val targetNorms = targetWords.map { dbHelper.normalizeArabic(it) }
            val alignment   = buildAlignment(targetNorms, userWords)

            // ── Bangun charStatusMap baru + merge ─────────────────────────
            val newMap = mutableMapOf<Int, Map<Int, CharMatchStatus>>()
            targetNorms.forEachIndexed { wordIdx, targetNorm ->
                val freshMap = charStatusForWord(targetNorm, alignment[wordIdx])
                val prevMap  = charStatusMap[wordIdx] ?: emptyMap()
                val merged = targetNorm.indices.associateWith { i ->
                    val fresh = freshMap[i] ?: CharMatchStatus.MISS
                    val prev  = prevMap[i]  ?: CharMatchStatus.MISS
                    when {
                        // SKIP tidak bisa naik ke EXACT/PHONETIC (sudah diputuskan salah)
                        prev  == CharMatchStatus.SKIP     -> CharMatchStatus.SKIP
                        fresh == CharMatchStatus.EXACT    || prev == CharMatchStatus.EXACT    -> CharMatchStatus.EXACT
                        fresh == CharMatchStatus.PHONETIC || prev == CharMatchStatus.PHONETIC -> CharMatchStatus.PHONETIC
                        else -> CharMatchStatus.MISS
                    }
                }
                newMap[wordIdx] = merged
            }

            // ══════════════════════════════════════════════════════════════
            // GAP DETECTION: Hitam-Hijau-Hitam → Hitam-Hijau-MERAH
            //
            // Cari kata terakhir yang berhasil di-match (highestMatchedIdx).
            // Semua kata di index < highestMatchedIdx yang masih MISS
            // ditandai SKIP (merah) karena sudah "dilewati" bacaan user.
            // Mencegah user cheat dengan membaca sebagian lalu skip.
            // ══════════════════════════════════════════════════════════════
            fun wordIsPassed(wordIdx: Int): Boolean {
                val cmap = newMap[wordIdx] ?: return false
                val tNorm = targetNorms.getOrNull(wordIdx) ?: return false
                if (tNorm.isEmpty()) return false
                val matched = cmap.values.count { it == CharMatchStatus.EXACT || it == CharMatchStatus.PHONETIC }
                return matched.toFloat() / tNorm.length >= 0.6f
            }

            val highestMatchedIdx = targetNorms.indices.lastOrNull { wordIsPassed(it) } ?: -1

            if (highestMatchedIdx > 0) {
                for (wordIdx in 0 until highestMatchedIdx) {
                    if (!wordIsPassed(wordIdx)) {
                        // Kata ini di-skip → tandai semua hurufnya SKIP (merah)
                        val tNorm = targetNorms.getOrNull(wordIdx) ?: continue
                        // Jangan override SKIP dengan SKIP jika sudah ada
                        val prevMap = newMap[wordIdx] ?: emptyMap()
                        val allMiss = prevMap.values.all {
                            it == CharMatchStatus.MISS || it == CharMatchStatus.SKIP
                        }
                        if (allMiss) {
                            newMap[wordIdx] = tNorm.indices.associateWith { CharMatchStatus.SKIP }
                        }
                    }
                }
            }

            charStatusMap = newMap
        }

        val performCheck = {
            val targetWords = ayatAktif.split(" ").filter { it.isNotBlank() }
            val totalWords  = targetWords.size
            val isShortVerse = totalWords in 2..4   // ayat pendek = 2–4 kata

            // ── Hitung kata yang benar ─────────────────────────────────────
            // HITUNG: EXACT dan PHONETIC dianggap benar
            // Threshold 40% - lebih lenient untuk STT Arabic
            val matchedWordCount = targetWords.indices.count { wordIdx ->
                val tNorm   = dbHelper.normalizeArabic(targetWords[wordIdx])
                val cmap    = charStatusMap[wordIdx] ?: emptyMap()
                // EXACT & PHONETIC dianggap benar
                val correct = cmap.values.count {
                    it == CharMatchStatus.EXACT || it == CharMatchStatus.PHONETIC
                }
                // Threshold 40% - realistis untuk STT
                tNorm.isNotEmpty() && correct.toFloat() / tNorm.length >= 0.4f
            }

            // ── Threshold kelulusan ────────────────────────────────────────
            // Ayat pendek (2-4 kata): minimal 1 kata benar sudah cukup
            // Ayat panjang (5+ kata): minimal 50% kata benar
            val lulus = if (isShortVerse) {
                matchedWordCount >= 1
            } else {
                matchedWordCount.toFloat() / totalWords >= 0.50f
            }

            // ── Mercy system untuk ayat pendek ────────────────────────────
            // Jika user sudah gagal 3x pada ayat pendek (2-4 kata)
            // dan paling tidak 1 kata benar → tetap boleh lanjut
            val newAttempts = percobaan + 1
            val mercyKick = isShortVerse && newAttempts >= 3 && matchedWordCount >= 1

            if (lulus || mercyKick) {
                if (mercyKick && !lulus) {
                    teksHasilSuara = "💚 Semangat! Terus berlatih ya."
                }
                percobaan = 0  // reset counter untuk ayat berikutnya
                if (indexAyatSekarang < daftarAyatTarget.size - 1) {
                    indexAyatSekarang++
                } else {
                    teksHasilSuara = "✅ Alhamdulillah! Semua ayat selesai."
                    handleGameSuccess(context, isGameMode = false)
                }
            } else {
                percobaan = newAttempts
                val pct = if (totalWords > 0)
                    (matchedWordCount * 100 / totalWords) else 0
                val sisaPercobaan = if (isShortVerse) maxOf(0, 3 - newAttempts) else -1
                teksHasilSuara = if (isShortVerse && sisaPercobaan > 0) {
                    "❌ $matchedWordCount/$totalWords kata benar. Sisa $sisaPercobaan percobaan."
                } else {
                    "❌ Belum pas ($matchedWordCount/$totalWords kata). Coba ulangi."
                }
                Toast.makeText(context, "Ulangi ayat ini sampai hijau", Toast.LENGTH_SHORT).show()
            }
        }

        // ════════════════════════════════════════════════════════════════
        // SISTEM HIGHLIGHTING REALTIME + AUTO-CHECK
        //
        // Arsitektur:
        // • onPartialResults → highlighting REALTIME dengan debounce 80ms
        //   Setiap partial datang (~300-500ms), kita debounce 80ms sebelum
        //   memanggil updateProgress. Ini memberi realtime feel tanpa spam.
        //
        // • onResults → konfirmasi final, update akumulatif, lalu auto-check
        //
        // • updateProgress adalah fungsi BIASA (non-suspend) yang langsung
        //   memutakhirkan charStatusMap di Main thread. Tidak ada async gap
        //   sehingga checkAndMaybeAutoSubmit membaca state yang sudah benar.
        //
        // • Anti-flickering: merge logic di updateProgress sudah MONOTONE —
        //   status EXACT/PHONETIC tidak bisa turun kembali ke MISS/SKIP.
        //   Jadi tidak akan ada kata yang flip hijau-hitam-hijau.
        // ════════════════════════════════════════════════════════════════

        // Job untuk debounce partial results
        var partialDebounceJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
        // Job untuk auto-check ketika semua kata hijau
        var autoCheckJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

        // Transcript yang sudah dikonfirmasi (dari onResults)
        // Partial transcript hanya untuk preview teks, bukan akumulasi permanen
        var confirmedTranscript by remember { mutableStateOf("") }

        // ── checkAndMaybeAutoSubmit ───────────────────────────────────────
        // Dipanggil LANGSUNG (synchronous) setelah updateProgress() selesai.
        // Karena updateProgress() memutakhirkan charStatusMap SEBELUM fungsi ini
        // dipanggil, pembacaan charStatusMap di sini SELALU fresh.
        fun checkAndMaybeAutoSubmit() {
            if (!sedangMerekam) return
            val targetWords = ayatAktif.split(" ").filter { it.isNotBlank() }
            if (targetWords.isEmpty()) return
            val allGreen = targetWords.indices.all { wordIdx ->
                val tNorm = dbHelper.normalizeArabic(targetWords[wordIdx])
                val cmap  = charStatusMap[wordIdx] ?: emptyMap()
                if (tNorm.isEmpty()) return@all true
                val matched = cmap.values.count {
                    it == CharMatchStatus.EXACT || it == CharMatchStatus.PHONETIC
                }
                matched.toFloat() / tNorm.length >= 0.6f
            }
            if (allGreen) {
                autoCheckJob?.cancel()
                autoCheckJob = serviceScope.launch {
                    delay(800)   // tahan 800ms agar user lihat semua kata hijau
                    if (sedangMerekam) {
                        sedangMerekam = false
                        partialDebounceJob?.cancel()
                        try { speechRecognizer.stopListening() } catch (e: Exception) {}
                        performCheck()
                    }
                }
            }
        }

        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (confirmedTranscript.isEmpty()) {
                    teksHasilSuara = "Mendengarkan ayat ${indexAyatSekarang + 1}..."
                }
            }

            override fun onError(error: Int) {
                val ignorable = error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_CLIENT ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                val delayMs = if (ignorable) 150L else 500L
                if (sedangMerekam) {
                    serviceScope.launch {
                        delay(delayMs)
                        if (sedangMerekam) {
                            try { speechRecognizer.startListening(recIntent) } catch (e: Exception) {}
                        }
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val newText = matches[0]
                    if (newText.isNotBlank()) {
                        // Akumulasi: gabung confirmed + baru
                        val combined = if (confirmedTranscript.isBlank()) newText
                        else "$confirmedTranscript $newText"
                        confirmedTranscript = combined
                        fullTranscript      = combined
                        teksHasilSuara      = combined

                        // Cancel partial debounce yang mungkin masih pending
                        partialDebounceJob?.cancel()

                        // Update highlight LANGSUNG (non-async) lalu cek auto-submit
                        updateProgress(combined)
                        checkAndMaybeAutoSubmit()
                    }
                }
                // Restart untuk sesi berikutnya
                if (sedangMerekam) {
                    serviceScope.launch {
                        delay(200)
                        if (sedangMerekam) {
                            try { speechRecognizer.startListening(recIntent) } catch (e: Exception) {}
                        }
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                // ── REALTIME HIGHLIGHTING via debounce 80ms ───────────────
                // Partial result datang ~300-500ms sekali. Kita debounce 80ms:
                // • Kalau partial baru datang sebelum 80ms → cancel yang lama
                // • Kalau sudah 80ms tidak ada partial baru → jalankan update
                // Hasilnya: highlighting terasa realtime (max lag 80ms) tapi
                // tidak spam updateProgress setiap frame.
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches.isNullOrEmpty() || !sedangMerekam) return
                val partialText = matches[0]
                if (partialText.isBlank()) return

                // Update teks preview segera (tanpa debounce)
                val previewText = if (confirmedTranscript.isBlank()) partialText
                else "$confirmedTranscript $partialText"
                teksHasilSuara = previewText

                // Debounce highlighting 80ms
                partialDebounceJob?.cancel()
                partialDebounceJob = serviceScope.launch {
                    delay(80)
                    if (sedangMerekam) {
                        // Gabung confirmed + partial untuk highlighting
                        val transcriptForHighlight = if (confirmedTranscript.isBlank()) partialText
                        else "$confirmedTranscript $partialText"
                        updateProgress(transcriptForHighlight)
                        checkAndMaybeAutoSubmit()
                    }
                }
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }

        DisposableEffect(Unit) {
            speechRecognizer.setRecognitionListener(listener)
            onDispose { speechRecognizer.destroy() }
        }

        val permissionLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                if (it) {
                    fullTranscript      = ""
                    confirmedTranscript = ""
                    charStatusMap       = emptyMap()
                    partialDebounceJob?.cancel()
                    autoCheckJob?.cancel()
                    sedangMerekam = true
                    speechRecognizer.startListening(recIntent)
                }
            }

        Box(modifier = Modifier.fillMaxSize()) {
            if (showBackButton) {
                IconButton(
                    onClick = { (context as? ComponentActivity)?.finish() },
                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp).zIndex(1f)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back",
                        tint = textPrim)
                }
            }

            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier.padding(top = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Sesi Mengaji", color = Color(0xFF2E7D32),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold)
                    Text(
                        if (daftarAyatTarget.size > 1)
                            "Ayat ${indexAyatSekarang + 1} dari ${daftarAyatTarget.size}"
                        else "Bacalah ayat panjang ini",
                        style = MaterialTheme.typography.bodyMedium,
                        color = textSub
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                AnimatedContent(
                    targetState = indexAyatSekarang,
                    transitionSpec = {
                        (slideInHorizontally { it } + fadeIn()).togetherWith(
                            slideOutHorizontally { -it } + fadeOut())
                    },
                    label = "AyatTransition"
                ) { targetIndex ->
                    if (targetIndex < daftarAyatTarget.size) {
                        val currentVerse = daftarAyatTarget[targetIndex]
                        SideEffect {
                            ayatAktif           = currentVerse
                            charStatusMap       = emptyMap()
                            fullTranscript      = ""
                            confirmedTranscript = ""
                            percobaan           = 0
                            partialDebounceJob?.cancel()
                            autoCheckJob?.cancel()
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            shape = RoundedCornerShape(24.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Surface(color = pillBg, shape = CircleShape) {
                                    Text(
                                        text = "Surah $namaSurah",
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = pillText, fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                                val annotatedVerse = buildAnnotatedString {
                                    currentVerse.split(" ").forEachIndexed { wordIdx, word ->
                                        val wordNorm = dbHelper.normalizeArabic(word)
                                        val cmap     = charStatusMap[wordIdx] ?: emptyMap()
                                        val exactCount    = cmap.values.count { it == CharMatchStatus.EXACT }
                                        val phoneticCount = cmap.values.count { it == CharMatchStatus.PHONETIC }
                                        val skipCount     = cmap.values.count { it == CharMatchStatus.SKIP }
                                        val matchedCount  = exactCount + phoneticCount
                                        val wordRatio     = if (wordNorm.isNotEmpty())
                                            matchedCount.toFloat() / wordNorm.length else 0f
                                        val isSkipped     = skipCount > 0 && wordRatio < 0.4f
                                        when {
                                            // Hijau: >= 40% karakter cocok
                                            wordRatio >= 0.4f ->
                                                withStyle(SpanStyle(color = Color(0xFF2E7D32))) { append(word) }
                                            // Merah: dilewati/skipped
                                            isSkipped ->
                                                withStyle(SpanStyle(color = Color(0xFFE53935))) { append(word) }
                                            // Kuning: beberapa karakter cocok
                                            else -> word.forEachIndexed { charIdx, c ->
                                                val status = cmap[charIdx] ?: CharMatchStatus.MISS
                                                val color = when (status) {
                                                    CharMatchStatus.EXACT    -> Color(0xFF2E7D32)
                                                    CharMatchStatus.PHONETIC -> Color(0xFFF9A825)
                                                    CharMatchStatus.SKIP     -> Color(0xFFE53935)
                                                    CharMatchStatus.MISS     -> missColor
                                                }
                                                withStyle(SpanStyle(color = color)) { append(c) }
                                            }
                                        }
                                        append(" ")
                                    }
                                }
                                Text(
                                    text = annotatedVerse,
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        lineHeight = 48.sp, textAlign = TextAlign.Center
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = transcriptBg),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = teksHasilSuara,
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth()
                                .heightIn(min = 20.dp, max = 100.dp)
                                .verticalScroll(rememberScrollState()),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = transcriptText
                        )
                    }
                    Text(
                        text = "Tidak dapat berbicara sekarang?",
                        modifier = Modifier.clickable { onSwitchToGame() },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color(0xFF1CB0F6),
                            fontWeight = FontWeight.Bold,
                            textDecoration = TextDecoration.Underline
                        )
                    )
                    Button(
                        onClick = {
                            if (!sedangMerekam) {
                                if (ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    fullTranscript      = ""
                                    confirmedTranscript = ""
                                    charStatusMap       = emptyMap()
                                    partialDebounceJob?.cancel()
                                    autoCheckJob?.cancel()
                                    sedangMerekam = true
                                    speechRecognizer.startListening(recIntent)
                                } else {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            } else {
                                sedangMerekam = false
                                partialDebounceJob?.cancel()
                                autoCheckJob?.cancel()
                                speechRecognizer.stopListening()
                                performCheck()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (sedangMerekam) Color(0xFFFFA000) else Color(0xFF00C853)
                        ),
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            if (sedangMerekam) "✅ Selesai & Cek" else "▶️ Mulai Ngaji",
                            fontWeight = FontWeight.Bold, fontSize = 18.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}