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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.content.edit
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

// ─── Status pencocokan huruf Arab per karakter ────────────────────────────────
enum class CharMatchStatus {
    EXACT,    // huruf cocok persis          → hijau tua
    PHONETIC, // substitusi fonetik oleh STT → kuning
    SKIP,     // kata dilewati user (gap)    → merah
    MISS      // belum terdeteksi            → hitam
}

object TimerConstants {
    const val GAME_COOLDOWN_MS      = 2L * 60 * 1000   // 2 menit cooldown game
    const val GAME_CREDIT_MS        = 2L * 60 * 1000   // +2 menit credit dari game
    const val VOICE_CREDIT_MS       = 5L * 60 * 1000   // +5 menit credit dari suara
    const val AUTOCHECK_DELAY_MS     = 600L             // delay cek otomatis setelah semua kata dipilih
    const val FINISH_DELAY_MS        = 2_000L          // delay sebelum finish setelah sukses
    const val AUTOSUBMIT_DELAY_MS    = 800L             // auto-submit setelah speaking benar
    const val STT_ERROR_SHORT_DELAY  = 150L             // retry delay untuk error ringan
    const val STT_ERROR_LONG_DELAY   = 1000L            // retry delay untuk error berat
    const val STT_RESTART_DELAY      = 800L             // restart STT setelah results
    const val PARTIAL_DEBOUNCE_MS    = 150L             // debounce partial speech results
}

val PHONETIC_SUBSTITUTIONS: Map<Char, Set<Char>> = mapOf(
    'ث' to setOf('ت', 'س', 'ذ'),
    'ذ' to setOf('ز', 'د', 'ث', 'ظ'),
    'ظ' to setOf('ض', 'ذ', 'ز', 'ط'),
    'ض' to setOf('د', 'ظ', 'ط'),
    'ط' to setOf('ت', 'ض', 'ظ'),
    'ص' to setOf('س', 'ز'),
    'ق' to setOf('ك', 'غ'),
    'غ' to setOf('ق', 'ك'),
    'خ' to setOf('ح', 'ك'),
    'ع' to setOf('ا', 'ه'),
    'ح' to setOf('ه', 'خ'),
    'ت' to setOf('ث', 'ط'),
    'س' to setOf('ث', 'ص', 'ز'),
    'ز' to setOf('ذ', 'ظ', 'ص', 'س'),
    'د' to setOf('ذ', 'ض'),
    'ك' to setOf('ق', 'خ', 'غ'),
    'ه' to setOf('ح', 'ع'),
)

class LockActivity : ComponentActivity() {

    private var targetPackage: String? = null
    private var creditOverrideMs: Long = -1L
    private var startCooldownKey: String? = null
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

        targetPackage   = intent.getStringExtra("target_package")
        creditOverrideMs = intent.getLongExtra("credit_override_ms", -1L)
        startCooldownKey = intent.getStringExtra("start_cooldown_key")

        setupLockScreenFlags()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (targetPackage == null) {
                    finish()
                } else {
                    Toast.makeText(this@LockActivity, "Selesaikan tantangan untuk membuka kunci", Toast.LENGTH_SHORT).show()
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
                        modifier = Modifier.fillMaxSize(),
                        color = if (isDarkMode) Color(0xFF0F172A) else Color(0xFFF8F9FA)
                    ) {
                        val context = LocalContext.current
                        var forceGameMode by remember { mutableStateOf(false) }
                        
                        var isOffline by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            isOffline = !isOnline(context)
                        }

                        if (isOffline || forceGameMode) {
                            SusunKataGame(showBackButton = targetPackage == null)
                        } else {
                            LayarPengunciSuara(
                                showBackButton = targetPackage == null,
                                onSwitchToGame = { forceGameMode = true },
                                onOfflineRedirect = { forceGameMode = true }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun isOnline(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            else -> false
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

    @Composable
    fun SusunKataGame(showBackButton: Boolean) {
        val context = LocalContext.current
        val dark    = LocalIsDarkMode.current
        val cardBg     = if (dark) Color(0xFF1E293B) else Color.White
        val textPrim   = if (dark) Color(0xFFF1F5F9) else Color(0xFF4B4B4B)
        val textSub    = if (dark) Color(0xFF94A3B8) else Color(0xFF757575)
        val trackColor = if (dark) Color(0xFF334155) else Color(0xFFE5E5E5)
        val pillBg     = if (dark) Color(0xFF14532D) else Color(0xFFE8F5E9)
        val pillText   = if (dark) Color(0xFF4ADE80) else Color(0xFF2E7D32)
        val borderNorm = if (dark) Color(0xFF334155) else Color(0xFFE5E5E5)

        val dbHelper = remember { QuranDatabaseHelper(context) }
        val sharedPref = remember { context.getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE) }

        var isSuccess by remember { mutableStateOf(false) }
        val lastGameTime = sharedPref.getLong("last_game_success_time", 0L)
        val cooldownMillis = TimerConstants.GAME_COOLDOWN_MS
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
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                if (showBackButton) {
                    IconButton(onClick = { (context as? ComponentActivity)?.finish() }, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = textPrim)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⏳", fontSize = 60.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Fitur Game Sedang Cooldown", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = textPrim)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Tunggu $remainingSeconds detik lagi atau gunakan fitur 'Baca Suara'.", style = MaterialTheme.typography.bodyMedium, color = textSub, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { (context as? ComponentActivity)?.recreate() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1CB0F6))) { Text("Coba Lagi") }
                }
            }
            return
        }

        var daftarAyatTarget by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
        var indexAyatSekarang by remember { mutableIntStateOf(0) }

        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                val list = mutableListOf<Pair<String, String>>()
                repeat(3) {
                    try {
                        list.add(dbHelper.getRandomShortVerseJuz30())
                    } catch (e: Exception) {
                        list.add(Pair("Al-Fatihah", "الحمد لله رب العالمين"))
                    }
                }
                withContext(Dispatchers.Main) { daftarAyatTarget = list }
            }
        }

        if (daftarAyatTarget.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Color(0xFF2E7D32)) }
            return
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = indexAyatSekarang,
                transitionSpec = {
                    (fadeIn(tween(400)) + scaleIn(initialScale = 0.95f)).togetherWith(fadeOut(tween(400)))
                },
                label = "VerseTransition"
            ) { targetIdx ->
                val currentTarget = daftarAyatTarget[targetIdx]
                val currentCorrectWords = remember(currentTarget) { 
                    dbHelper.cleanVerseForQuiz(currentTarget.second).split(Regex("\\s+")).filter { it.isNotBlank() } 
                }
                val currentShuffledWords = remember(currentTarget) { currentCorrectWords.shuffled() }
                
                val currentSelectedWords = remember(currentTarget) { mutableStateListOf<String>() }
                val currentAvailableWords = remember(currentTarget) { mutableStateListOf<String>().apply { addAll(currentShuffledWords) } }
                var currentIsError by remember(currentTarget) { mutableStateOf(false) }

                LaunchedEffect(currentSelectedWords.size) {
                    if (currentSelectedWords.size == currentCorrectWords.size && currentCorrectWords.isNotEmpty() && !isSuccess && !currentIsError) {
                        delay(TimerConstants.AUTOCHECK_DELAY_MS)
                        if (targetIdx == indexAyatSekarang && currentSelectedWords.size == currentCorrectWords.size && !isSuccess) {
                            if (currentSelectedWords.toList() == currentCorrectWords) {
                                if (indexAyatSekarang < daftarAyatTarget.size - 1) {
                                    indexAyatSekarang++
                                } else {
                                    isSuccess = true
                                    handleGameSuccess(context, isGameMode = true, verseCount = daftarAyatTarget.size)
                                }
                            } else {
                                currentIsError = true
                                Toast.makeText(context, "Susunan masih salah, coba lagi!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                // Animasi staggered untuk chip saat ayat baru muncul
                var chipsVisible by remember(currentTarget) { mutableStateOf(false) }
                LaunchedEffect(currentTarget) {
                    delay(100)
                    chipsVisible = true
                }

                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (showBackButton) {
                            IconButton(
                                onClick = { (context as? ComponentActivity)?.finish() },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(if (dark) Color.White.copy(0.1f) else Color.Black.copy(0.05f), CircleShape)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = textPrim,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                        }

                        val progress = (targetIdx.toFloat() + (if (isSuccess) 1f else 0f)) / daftarAyatTarget.size.toFloat()
                        val animatedProgress by animateFloatAsState(targetValue = progress, animationSpec = spring(), label = "progress")
                        
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.weight(1f).height(10.dp).clip(CircleShape),
                            color = Color(0xFF58CC02),
                            trackColor = trackColor
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("🧩 ${targetIdx + 1}/${daftarAyatTarget.size}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textPrim)
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                    Surface(color = pillBg, shape = CircleShape) {
                        Text(text = "سورة ${currentTarget.first}", modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium, color = pillText, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Susunlah potongan ayat ini:", style = MaterialTheme.typography.bodyLarge, color = textSub, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(30.dp))
                    Box(modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp)
                        .border(2.dp, if (currentIsError) Color.Red else borderNorm, RoundedCornerShape(16.dp))
                        .background(cardBg, RoundedCornerShape(16.dp))
                        .animateContentSize()
                        .padding(12.dp), contentAlignment = Alignment.Center) {
                        StableFlowLayout(
                            modifier = Modifier.fillMaxWidth(),
                            arabRtl  = true,
                            centered = false,
                            hSpacing = 6.dp,
                            vSpacing = 8.dp
                        ) {
                            currentSelectedWords.forEach { word ->
                                WordChip(word, isPrimary = true, dark = dark) {
                                    currentSelectedWords.remove(word)
                                    currentAvailableWords.add(word)
                                    currentIsError = false
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    StableFlowLayout(
                        modifier = Modifier.fillMaxWidth(),
                        arabRtl  = false,
                        centered = true,
                        hSpacing = 6.dp,
                        vSpacing = 10.dp
                    ) {
                        currentAvailableWords.forEachIndexed { idx, word ->
                            AnimatedVisibility(
                                visible = chipsVisible,
                                enter = fadeIn(tween(400, delayMillis = idx * 50)) + scaleIn(initialScale = 0.8f, animationSpec = tween(400, delayMillis = idx * 50))
                            ) {
                                WordChip(word, isPrimary = false, dark = dark) {
                                    currentSelectedWords.add(word)
                                    currentAvailableWords.remove(word)
                                    currentIsError = false
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(40.dp))
                    Button(onClick = {
                        if (targetIdx != indexAyatSekarang) return@Button
                        if (currentSelectedWords.toList() == currentCorrectWords) {
                            if (indexAyatSekarang < daftarAyatTarget.size - 1) {
                                indexAyatSekarang++
                            } else {
                                isSuccess = true
                                handleGameSuccess(context, isGameMode = true, verseCount = daftarAyatTarget.size)
                            }
                        } else {
                            currentIsError = true
                            Toast.makeText(context, "Susunan masih salah, coba lagi!", Toast.LENGTH_SHORT).show()
                        }
                    }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = if (isSuccess) Color(0xFF58CC02) else Color(0xFF1CB0F6)), enabled = currentSelectedWords.size == currentCorrectWords.size && !isSuccess) {
                        Text(if (isSuccess) "✅ أحسنت!" else if (indexAyatSekarang < daftarAyatTarget.size - 1) "LANJUT" else "PERIKSA", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    }
                }
            }
        }

        AnimatedVisibility(visible = isSuccess, enter = fadeIn() + scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF58CC02).copy(alpha = 0.9f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🌟", fontSize = 80.sp)
                    Text("MANTAP!", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                    Text("+2 Menit Kredit Waktu", color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }

    @Composable
    fun WordChip(word: String, isPrimary: Boolean, dark: Boolean = false, onClick: () -> Unit) {
        val chipBg = when {
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
        val textColor = if (dark) Color(0xFFF1F5F9) else Color(0xFF4B4B4B)
        Surface(modifier = Modifier.clickable { onClick() }.padding(2.dp), color = chipBg, shape = RoundedCornerShape(12.dp), border = BorderStroke(2.dp, chipBorder), shadowElevation = if (dark) 0.dp else 2.dp) {
            Text(text = word, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = textColor, textAlign = TextAlign.Center)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun handleGameSuccess(context: Context, isGameMode: Boolean = false, verseCount: Int = 1) {
        val sharedPref = context.getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE)
        val currentCredit = sharedPref.getLong("remaining_credit", 0L)
        val maxCreditLimit = 3_600_000L
        val addMillis = if (creditOverrideMs > 0) {
            creditOverrideMs
        } else if (isGameMode) {
            TimerConstants.GAME_CREDIT_MS
        } else {
            TimerConstants.VOICE_CREDIT_MS
        }
        val newCredit = minOf(maxCreditLimit, currentCredit + addMillis)
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val hour     = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val lastDayStr = sharedPref.getString("last_verse_date", "") ?: ""
        val currentDaily = if (lastDayStr == todayStr) sharedPref.getInt("daily_verses", 0) else 0
        val lastFocusDate = sharedPref.getString("last_focus_date", "") ?: ""
        val currentFocusMs = if (lastFocusDate == todayStr) sharedPref.getLong("focus_time_today_ms", 0L) else 0L
        val newFocusMs = currentFocusMs + addMillis
        sharedPref.edit {
            putLong("remaining_credit", newCredit)
            putInt("total_verses", sharedPref.getInt("total_verses", 0) + verseCount)
            putInt("daily_verses", currentDaily + verseCount)
            putString("last_verse_date", todayStr)
            putLong("focus_time_today_ms", newFocusMs)
            putString("last_focus_date", todayStr)
            
            val sessions = sharedPref.getStringSet("reading_sessions", emptySet())?.toMutableSet() ?: mutableSetOf()
            val type = if (isGameMode) "game" else "voice"
            sessions.add("${todayStr}_${hour}_${type}")
            putStringSet("reading_sessions", sessions)

            if (isGameMode) putLong("last_game_success_time", System.currentTimeMillis())
            startCooldownKey?.let { key -> putLong(key, System.currentTimeMillis()) }
            // Tulis timestamp dismiss agar UngkerService masuk grace period (8 detik)
            // dan tidak langsung menampilkan LockActivity lagi sebelum kredit terbaca
            putLong("lock_dismissed_at", System.currentTimeMillis())
        }
        serviceScope.launch {
            delay(TimerConstants.FINISH_DELAY_MS)
            val pm = context.packageManager
            val intentBack = targetPackage?.let { pm.getLaunchIntentForPackage(it) }
            if (intentBack != null) {
                intentBack.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intentBack)
            }
            finish()
        }
    }

    @Composable
    fun LayarPengunciSuara(showBackButton: Boolean, onSwitchToGame: () -> Unit, onOfflineRedirect: (() -> Unit)? = null) {
        val context = LocalContext.current
        val dark    = LocalIsDarkMode.current
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
        var percobaan by remember { mutableIntStateOf(0) }
        var sttUnavailable by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                sttUnavailable = true
                teksHasilSuara = "Voice recognition tidak tersedia di perangkat ini"
                return@LaunchedEffect
            }
            withContext(Dispatchers.IO) {
                val (surah, ayatSet) = dbHelper.getRandomVerseSet()
                withContext(Dispatchers.Main) {
                    namaSurah = surah
                    daftarAyatTarget = ayatSet
                    if (ayatSet.isNotEmpty()) ayatAktif = ayatSet[0]
                }
            }
        }

        val speechRecognizer = remember(sttUnavailable) { 
            if (sttUnavailable) null else SpeechRecognizer.createSpeechRecognizer(context) 
        }
        val recIntent = remember {
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
        }

        fun updateProgress(hasilRaw: String) {
            if (hasilRaw.length <= 1) return
            val hasilNorm   = dbHelper.normalizeArabic(hasilRaw)
            val targetWords = dbHelper.cleanVerseForQuiz(ayatAktif).split(Regex("\\s+")).filter { it.isNotBlank() }
            val userWords   = hasilNorm.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (userWords.isEmpty()) return

            val prefixes = listOf("وال","فال","بال","كال","لل","ال","و","ف","ب","ل","ك")
            fun stripPrefix(w: String): String {
                for (p in prefixes) { if (w.startsWith(p) && w.length > p.length) return w.substring(p.length) }
                return w
            }

            fun phoneticEditDistance(a: String, b: String): Float {
                val m = a.length; val n = b.length
                val dp = FloatArray(n + 1) { it.toFloat() }
                for (i in 1..m) {
                    var prev = dp[0]; dp[0] = i.toFloat()
                    for (j in 1..n) {
                        val temp = dp[j]
                        val cost = when {
                            a[i-1] == b[j-1] -> 0f
                            PHONETIC_SUBSTITUTIONS[a[i-1]]?.contains(b[j-1]) == true || PHONETIC_SUBSTITUTIONS[b[j-1]]?.contains(a[i-1]) == true -> 0.3f
                            else -> 1f
                        }
                        dp[j] = minOf(dp[j] + 1f, dp[j-1] + 1f, prev + cost)
                        prev = temp
                    }
                }
                return dp[n]
            }

            fun wordMatchScore(target: String, user: String): Float {
                if (target.isEmpty() || user.isEmpty()) return 0f
                if (target == user) return 1f
                val lenRatio = minOf(target.length, user.length).toFloat() / maxOf(target.length, user.length)
                if ((target.contains(user) || user.contains(target)) && lenRatio >= 0.80f) return 0.85f * lenRatio
                val ed     = phoneticEditDistance(target, user)
                val maxLen = maxOf(target.length, user.length).toFloat()
                val sim    = 1f - ed / maxLen
                return if (lenRatio < 0.65f) sim * 0.5f else sim
            }

            fun buildAlignment(targets: List<String>, users: List<String>): Map<Int, String?> {
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
                        val s = maxOf(wordMatchScore(tNorm, uNorm), wordMatchScore(tStrip, uStrip))
                        if (s > best) { best = s; bestUIdx = uIdx }
                    }
                    if (best >= 0.30f && bestUIdx >= 0) {
                        result[tIdx] = users[bestUIdx]
                        userPtr = bestUIdx + 1
                    } else { result[tIdx] = null }
                }
                return result
            }

            fun charStatusForWord(targetNorm: String, matchedWord: String?): Map<Int, CharMatchStatus> {
                if (matchedWord == null) return targetNorm.indices.associateWith { CharMatchStatus.MISS }
                val userNorm = dbHelper.normalizeArabic(matchedWord)
                if (targetNorm == userNorm) return targetNorm.indices.associateWith { CharMatchStatus.EXACT }
                val wordScore = maxOf(wordMatchScore(targetNorm, userNorm), wordMatchScore(stripPrefix(targetNorm), stripPrefix(userNorm)))
                if (wordScore >= 0.30f) return targetNorm.indices.associateWith { CharMatchStatus.EXACT }
                if (wordScore >= 0.10f) {
                    return targetNorm.mapIndexed { i, targetChar ->
                        val isExact = userNorm.contains(targetChar)
                        val isPhonetic = PHONETIC_SUBSTITUTIONS[targetChar]?.any { userNorm.contains(it) } == true
                        i to when { isExact -> CharMatchStatus.EXACT; isPhonetic -> CharMatchStatus.PHONETIC; else -> CharMatchStatus.MISS }
                    }.toMap()
                }
                return targetNorm.indices.associateWith { CharMatchStatus.MISS }
            }

            val targetNorms = targetWords.map { dbHelper.normalizeArabic(it) }
            val alignment   = buildAlignment(targetNorms, userWords)
            val newMap = mutableMapOf<Int, Map<Int, CharMatchStatus>>()
            targetNorms.forEachIndexed { wordIdx, targetNorm ->
                val freshMap = charStatusForWord(targetNorm, alignment[wordIdx])
                val prevMap  = charStatusMap[wordIdx] ?: emptyMap()
                newMap[wordIdx] = targetNorm.indices.associateWith { i ->
                    val fresh = freshMap[i] ?: CharMatchStatus.MISS
                    val prev  = prevMap[i]  ?: CharMatchStatus.MISS
                    when {
                        prev  == CharMatchStatus.SKIP     -> CharMatchStatus.SKIP
                        fresh == CharMatchStatus.EXACT    || prev == CharMatchStatus.EXACT    -> CharMatchStatus.EXACT
                        fresh == CharMatchStatus.PHONETIC || prev == CharMatchStatus.PHONETIC -> CharMatchStatus.PHONETIC
                        else -> CharMatchStatus.MISS
                    }
                }
            }

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
                        val tNorm = targetNorms.getOrNull(wordIdx) ?: continue
                        val prevMap = newMap[wordIdx] ?: emptyMap()
                        if (prevMap.values.all { it == CharMatchStatus.MISS || it == CharMatchStatus.SKIP }) {
                            newMap[wordIdx] = tNorm.indices.associateWith { CharMatchStatus.SKIP }
                        }
                    }
                }
            }
            charStatusMap = newMap
        }

        val performCheck = {
            val targetWords = dbHelper.cleanVerseForQuiz(ayatAktif).split(Regex("\\s+")).filter { it.isNotBlank() }
            val totalWords  = targetWords.size
            val isShortVerse = totalWords in 2..4
            val matchedWordCount = targetWords.indices.count { wordIdx ->
                val tNorm   = dbHelper.normalizeArabic(targetWords[wordIdx])
                val cmap    = charStatusMap[wordIdx] ?: emptyMap()
                val correct = cmap.values.count { it == CharMatchStatus.EXACT || it == CharMatchStatus.PHONETIC }
                tNorm.isNotEmpty() && correct.toFloat() / tNorm.length >= 0.4f
            }
            val lulus = if (isShortVerse) matchedWordCount >= 1 else matchedWordCount.toFloat() / totalWords >= 0.50f
            val newAttempts = percobaan + 1
            val mercyKick = isShortVerse && newAttempts >= 3 && matchedWordCount >= 1
            if (lulus || mercyKick) {
                if (mercyKick && !lulus) teksHasilSuara = "💚 Semangat! Terus berlatih ya."
                percobaan = 0
                if (indexAyatSekarang < daftarAyatTarget.size - 1) { indexAyatSekarang++ }
                else { teksHasilSuara = "✅ Alhamdulillah! Semua ayat selesai."; handleGameSuccess(context, isGameMode = false, verseCount = daftarAyatTarget.size) }
            } else {
                percobaan = newAttempts
                teksHasilSuara = "❌ Belum pas ($matchedWordCount/$totalWords kata). Coba ulangi."
                Toast.makeText(context, "Ulangi ayat ini sampai hijau", Toast.LENGTH_SHORT).show()
            }
        }

        var partialDebounceJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
        var autoCheckJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
        var confirmedTranscript by remember { mutableStateOf("") }

        fun checkAndMaybeAutoSubmit() {
            if (!sedangMerekam) return
            val targetWords = dbHelper.cleanVerseForQuiz(ayatAktif).split(Regex("\\s+")).filter { it.isNotBlank() }
            if (targetWords.isEmpty()) return
            val allGreen = targetWords.indices.all { wordIdx ->
                val tNorm = dbHelper.normalizeArabic(targetWords[wordIdx])
                val cmap  = charStatusMap[wordIdx] ?: emptyMap()
                if (tNorm.isEmpty()) return@all true
                val matched = cmap.values.count { it == CharMatchStatus.EXACT || it == CharMatchStatus.PHONETIC }
                matched.toFloat() / tNorm.length >= 0.6f
            }
                if (allGreen) {
                autoCheckJob?.cancel()
                autoCheckJob = serviceScope.launch {
                    delay(TimerConstants.AUTOSUBMIT_DELAY_MS)
                    if (sedangMerekam) {
                        sedangMerekam = false
                        partialDebounceJob?.cancel()
                        speechRecognizer?.stopListening()
                        performCheck()
                    }
                }
            }
        }

        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { if (confirmedTranscript.isEmpty()) teksHasilSuara = "Mendengarkan ayat ${indexAyatSekarang + 1}..." }
            override fun onError(error: Int) {
                if (!isOnline(context)) {
                    teksHasilSuara = "Koneksi terputus! Mengalihkan ke mode kuis..."
                    sedangMerekam = false
                    onOfflineRedirect?.invoke()
                    return
                }
                val ignorable = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                val delayMs = if (ignorable) TimerConstants.STT_ERROR_SHORT_DELAY else TimerConstants.STT_ERROR_LONG_DELAY
                if (sedangMerekam) { serviceScope.launch { delay(delayMs); if (sedangMerekam) { speechRecognizer?.startListening(recIntent) } } }
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val newText = matches[0]
                    if (newText.isNotBlank()) {
                        val combined = if (confirmedTranscript.isBlank()) newText else "$confirmedTranscript $newText"
                        confirmedTranscript = combined; teksHasilSuara = combined
                        partialDebounceJob?.cancel(); updateProgress(combined); checkAndMaybeAutoSubmit()
                    }
                }
                if (sedangMerekam) { serviceScope.launch { delay(TimerConstants.STT_RESTART_DELAY); if (sedangMerekam) { speechRecognizer?.startListening(recIntent) } } }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches.isNullOrEmpty() || !sedangMerekam) return
                val partialText = matches[0]
                if (partialText.isBlank()) return
                val previewText = if (confirmedTranscript.isBlank()) partialText else "$confirmedTranscript $partialText"
                teksHasilSuara = previewText
                partialDebounceJob?.cancel()
                partialDebounceJob = serviceScope.launch { delay(TimerConstants.PARTIAL_DEBOUNCE_MS); if (sedangMerekam) { updateProgress(previewText); checkAndMaybeAutoSubmit() } }
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }

        DisposableEffect(speechRecognizer) { speechRecognizer?.setRecognitionListener(listener); onDispose { speechRecognizer?.destroy() } }
        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) { confirmedTranscript = ""; charStatusMap = emptyMap(); partialDebounceJob?.cancel(); autoCheckJob?.cancel(); sedangMerekam = true; speechRecognizer?.startListening(recIntent) } }

        // Auto-restart microphone when switching to the next verse if it was already recording
        LaunchedEffect(indexAyatSekarang) {
            if (sedangMerekam && indexAyatSekarang > 0) {
                delay(600) // Wait for verse transition animation to finish
                speechRecognizer?.startListening(recIntent)
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // Header Row
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (showBackButton) {
                        IconButton(
                            onClick = { (context as? ComponentActivity)?.finish() },
                            modifier = Modifier
                                .size(40.dp)
                                .background(if (dark) Color.White.copy(0.1f) else Color.Black.copy(0.05f), CircleShape)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = if (dark) Color.White else Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Sesi Mengaji", color = Color(0xFF2E7D32), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (daftarAyatTarget.size > 1) "Ayat ${indexAyatSekarang + 1} dari ${daftarAyatTarget.size}" else "Bacalah ayat ini",
                            style = MaterialTheme.typography.labelSmall,
                            color = textSub
                        )
                    }
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                AnimatedContent(
                    targetState = indexAyatSekarang,
                    transitionSpec = { (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut()) },
                    label = "AyatTransition"
                ) { targetIndex ->
                    if (targetIndex < daftarAyatTarget.size) {
                        val currentVerse = daftarAyatTarget[targetIndex]
                        SideEffect { ayatAktif = currentVerse; charStatusMap = emptyMap(); confirmedTranscript = ""; percobaan = 0; partialDebounceJob?.cancel(); autoCheckJob?.cancel() }
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = cardBg), shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(color = pillBg, shape = CircleShape) { Text(text = "Surah $namaSurah", modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge, color = pillText, fontWeight = FontWeight.Bold) }
                                Spacer(modifier = Modifier.height(24.dp))
                                val annotatedVerse = buildAnnotatedString {
                                    dbHelper.cleanVerseForQuiz(currentVerse).split(Regex("\\s+")).forEachIndexed { wordIdx, word ->
                                        val wordNorm = dbHelper.normalizeArabic(word)
                                        val cmap     = charStatusMap[wordIdx] ?: emptyMap()
                                        val exactCount    = cmap.values.count { it == CharMatchStatus.EXACT }
                                        val phoneticCount = cmap.values.count { it == CharMatchStatus.PHONETIC }
                                        val skipCount     = cmap.values.count { it == CharMatchStatus.SKIP }
                                        val matchedCount  = exactCount + phoneticCount
                                        val wordRatio     = if (wordNorm.isNotEmpty()) matchedCount.toFloat() / wordNorm.length else 0f
                                        val isSkipped     = skipCount > 0 && wordRatio < 0.4f
                                        when {
                                            wordRatio >= 0.4f -> withStyle(SpanStyle(color = Color(0xFF2E7D32))) { append(word) }
                                            isSkipped -> withStyle(SpanStyle(color = Color(0xFFE53935))) { append(word) }
                                            else -> word.forEachIndexed { charIdx, c ->
                                                val status = cmap[charIdx] ?: CharMatchStatus.MISS
                                                val color = when (status) { CharMatchStatus.EXACT -> Color(0xFF2E7D32); CharMatchStatus.PHONETIC -> Color(0xFFF9A825); CharMatchStatus.SKIP -> Color(0xFFE53935); CharMatchStatus.MISS -> missColor }
                                                withStyle(SpanStyle(color = color)) { append(c) }
                                            }
                                        }
                                        append(" ")
                                    }
                                }
                                Text(text = annotatedVerse, style = MaterialTheme.typography.headlineMedium.copy(lineHeight = 48.sp, textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = transcriptBg), shape = RoundedCornerShape(16.dp)) { Text(text = teksHasilSuara, modifier = Modifier.padding(16.dp).fillMaxWidth().heightIn(min = 20.dp, max = 100.dp).verticalScroll(rememberScrollState()), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = transcriptText) }
                    if (sttUnavailable) {
                        Text("⚠️ Voice recognition tidak didukung", color = Color(0xFFE53935), style = MaterialTheme.typography.bodyMedium)
                        Text("Gunakan opsi game di bawah sebagai alternatif", color = textSecC(), style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text(text = "Tidak dapat berbicara sekarang?", modifier = Modifier.clickable { onSwitchToGame() }, style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF1CB0F6), fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline))
                    }
                    Button(onClick = { if (!sedangMerekam) { if (sttUnavailable) { return@Button }; if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { confirmedTranscript = ""; charStatusMap = emptyMap(); partialDebounceJob?.cancel(); autoCheckJob?.cancel(); sedangMerekam = true; speechRecognizer?.startListening(recIntent) } else { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) } } else { sedangMerekam = false; partialDebounceJob?.cancel(); autoCheckJob?.cancel(); speechRecognizer?.stopListening(); performCheck() } }, enabled = !sttUnavailable, colors = ButtonDefaults.buttonColors(containerColor = if (sedangMerekam) Color(0xFFFFA000) else Color(0xFF00C853), disabledContainerColor = Color.Gray), modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(16.dp)) { Text(if (sttUnavailable) "❌ Tidak Tersedia" else if (sedangMerekam) "✅ Selesai & Cek" else "▶️ Mulai Ngaji", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}