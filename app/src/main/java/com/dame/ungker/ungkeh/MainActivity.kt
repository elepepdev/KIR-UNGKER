package com.dame.ungker.ungkeh

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

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

        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000 * 10, time)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        if (stats.isEmpty()) {
            startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    LayarUngker()
                }
            }
        }
    }
}

fun sedangBukaTikTok(context: Context): Boolean {
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val waktu = System.currentTimeMillis()
    val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, waktu - 60 * 1000, waktu)

    if (stats != null) {
        val statsSorted = stats.sortedByDescending { it.lastTimeUsed }
        if (statsSorted.isNotEmpty()) {
            val paketApp = statsSorted[0].packageName
            return paketApp == "com.zhiliaoapp.musically" || paketApp == "com.ss.android.ugc.trill"
        }
    }
    return false
}

@Composable
fun LayarUngker() {
    var isInterruptionMode by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var teksHasilSuara by remember { mutableStateOf("Siap mendengarkan...") }
    var sedangMerekam by remember { mutableStateOf(false) }
    var tiktokTerdeteksi by remember { mutableStateOf(false) }
    var waktuTerakhirSukses by remember { mutableLongStateOf(0L) }

    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(tiktokTerdeteksi) {
        if (tiktokTerdeteksi) {
            teksHasilSuara = "Siap mendengarkan..."
        }
    }

    LaunchedEffect(Unit) {
        while(true) {
            val sekarang = System.currentTimeMillis()
            val durasiIzin = 60 * 1000
            val sudahBolehNonton = (sekarang - waktuTerakhirSukses) < durasiIzin

            if (sedangBukaTikTok(context)) {
                if (!sudahBolehNonton) {
                    val intentBuka = Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    context.startActivity(intentBuka)
                    tiktokTerdeteksi = true
                    isInterruptionMode = true 
                } else {
                    tiktokTerdeteksi = false
                    isInterruptionMode = false
                }
            }
            delay(1500)
        }
    }

    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    val intent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
        }
    }

    val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { teksHasilSuara = "Mulai bicara..." }
        override fun onEndOfSpeech() { sedangMerekam = false }
        override fun onError(error: Int) {
            teksHasilSuara = "Error: Coba lagi"
            sedangMerekam = false
        }
        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val hasil = matches[0].lowercase()
                teksHasilSuara = hasil

                if (hasil.contains("بسم") || hasil.contains("الله") || hasil.contains("الرحيم")) {
                    teksHasilSuara = "✅ Success! Redirecting..."
                    waktuTerakhirSukses = System.currentTimeMillis()

                    val sharedPref = context.getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE)
                    sharedPref.edit().putLong("last_success", System.currentTimeMillis()).apply()

                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        val pm = context.packageManager
                        val intentTikTok = pm.getLaunchIntentForPackage("com.zhiliaoapp.musically")
                            ?: pm.getLaunchIntentForPackage("com.ss.android.ugc.trill")

                        intentTikTok?.let {
                            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(it)
                        }

                        (context as? android.app.Activity)?.moveTaskToBack(true)

                        tiktokTerdeteksi = false
                        isInterruptionMode = false
                    }, 1500)
                }
            }
        }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    DisposableEffect(Unit) {
        speechRecognizer.setRecognitionListener(listener)
        onDispose { speechRecognizer.destroy() }
    }

    if (isInterruptionMode) {
        // --- LAYAR CEGATAN (SATPAM UNGKER) ---
        Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Hold On Kid...", color = Color.Red, style = MaterialTheme.typography.headlineSmall)
                Text("Read this first", textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(20.dp))
                Text("بِسْمِ اللَّهِ الرَّحْمَنِ الرَّحِيمِ", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(30.dp))
                Card(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.LightGray.copy(alpha = 0.2f))
                ) {
                    Text(text = teksHasilSuara, modifier = Modifier.padding(20.dp).fillMaxWidth(), textAlign = TextAlign.Center)
                }
                Spacer(modifier = Modifier.height(40.dp))
                Button(
                    onClick = {
                        if (!sedangMerekam) {
                            speechRecognizer.startListening(intent)
                            sedangMerekam = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (sedangMerekam) Color.Red else Color(0xFF2E7D32)),
                    modifier = Modifier.fillMaxWidth().height(60.dp)
                ) {
                    Text(if (sedangMerekam) "Merekam..." else "Mulai Ngaji")
                }
            }
        }
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Text("🏠") },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Color(0xFFE8F5E9))
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Text("⊞") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Text("🛡️") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Text("📊") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 4,
                        onClick = { selectedTab = 4 },
                        icon = { Text("👤") }
                    )
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize().background(Color(0xFFF8F9FA))) {
                when (selectedTab) {
                    0 -> BerandaScreen()
                    1 -> TeksPlaceholder("Daftar Aplikasi (Coming Soon)")
                    2 -> TeksPlaceholder("Target/Pencapaian (Coming Soon)")
                    3 -> TeksPlaceholder("Statistik Penggunaan (Coming Soon)")
                    4 -> TeksPlaceholder("Profil Pengguna (Coming Soon)")
                }
            }
        }
    }
}

@Composable
fun BerandaScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("UNGKER", style = MaterialTheme.typography.headlineMedium, color = Color(0xFF1A1A1A))
                Text("YoUNG KEeper for Recitation", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { }) {
                    Text("🔔")
                }
                IconButton(onClick = { }) {
                    Text("⚙️")
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        Card(
            modifier = Modifier.fillMaxWidth().height(300.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("UI Timer Menyusul di Sini", color = Color.Gray)
            }
        }
    }
}

@Composable
fun TeksPlaceholder(teks: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(teks, color = Color.Gray, style = MaterialTheme.typography.headlineSmall)
    }
}
