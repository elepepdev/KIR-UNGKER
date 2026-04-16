package com.ungker.ungkeh

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DeepFrictionActivity : ComponentActivity() {

    private var targetPackageState = mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        targetPackageState.value = intent.getStringExtra("target_package")
    }

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
        
        val initialPackage = intent.getStringExtra("target_package")
        if (initialPackage == null) {
            finish()
            return
        }
        targetPackageState.value = initialPackage

        setupLockScreenFlags()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                exitToHome()
            }
        })

        UngkerService.isLockActivityVisible = true

        setContent {
            val sp = remember { getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE) }
            val isDarkMode = remember { sp.getBoolean("dark_mode", false) }
            val colorScheme = if (isDarkMode) {
                darkColorScheme(
                    background = Color(0xFF0F172A),
                    surface = Color(0xFF1E293B),
                    primary = Color(0xFF4ADE80)
                )
            } else {
                lightColorScheme(
                    primary = Color(0xFF2E7D32)
                )
            }
            
            MaterialTheme(colorScheme = colorScheme) {
                val currentPackage = targetPackageState.value
                if (currentPackage != null) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        DeepFrictionScreen(
                            targetPackage = currentPackage,
                            onCancel = { 
                                // User cancel - tidak jadi buka app
                                exitToHome() 
                            },
                            onSuccess = { 
                                // User berhasil - mulai cooldown DAN catat app yang aktif
                                val dfManager = DeepFrictionManager(this)
                                dfManager.markFrictionShown(currentPackage)
                                dfManager.setCurrentlyActiveApp(currentPackage)
                                finish() 
                            }
                        )
                    }
                } else {
                    finish()
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

    private fun exitToHome() {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }
}

@Composable
fun DeepFrictionScreen(
    targetPackage: String,
    onCancel: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var appName by remember { mutableStateOf("") }
    var appIcon by remember { mutableStateOf<Drawable?>(null) }
    
    var reasonText by remember { mutableStateOf("") }
    var aiResponse by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(-1) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Load App Info
    LaunchedEffect(targetPackage) {
        try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(targetPackage, 0)
            appName = pm.getApplicationLabel(info).toString()
            appIcon = pm.getApplicationIcon(info)
        } catch (e: Exception) {
            appName = targetPackage
        }
    }

    val isValid = remember(reasonText) {
        val trimmed = reasonText.trim()
        if (trimmed.length < 10) return@remember false
        
        // Cek jika hanya angka
        if (trimmed.all { it.isDigit() }) return@remember false
        
        // Cek lazy patterns
        val lazyPatterns = listOf("gak tau", "gatau", "bosen", "gabut", "iseng", "hehe", "aaa", "zzz", "...', '---")
        if (lazyPatterns.any { trimmed.lowercase().contains(it) }) return@remember false
        
        // Cek karakter berulang
        if (trimmed.all { it == trimmed[0] }) return@remember false
        
        true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (aiResponse == null) {
            // Step 1: Input Reason
            appIcon?.let {
                Image(
                    bitmap = it.toBitmap(120, 120).asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(80.dp).clip(RoundedCornerShape(16.dp))
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Kenapa kamu mau buka $appName sekarang?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = reasonText,
                onValueChange = { 
                    reasonText = it
                    errorMessage = null
                },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                placeholder = { Text("Tulis alasanmu di sini (min. 10 karakter)...") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                )
            )
            
            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = Color.Red,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = {
                    isLoading = true
                    scope.launch {
                        val response = ClaudeApiClient.getWarmResponse(reasonText)
                        aiResponse = response
                        isLoading = false
                        
                        delay(3000)
                        countdown = 3
                        while (countdown > 0) {
                            delay(1000)
                            countdown--
                        }
                        onSuccess()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !isLoading && isValid,
                shape = RoundedCornerShape(16.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Icon(Icons.Default.Send, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Kirim alasan", fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            TextButton(onClick = onCancel) {
                Icon(Icons.Default.Cancel, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Gak jadi deh, tutup aja")
            }
        } else {
            // Step 2: AI Response & Countdown
            Text(
                text = "💬 Pesan dari orang tak dikenal:",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = aiResponse!!,
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    lineHeight = 28.sp
                )
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            if (countdown > 0) {
                Text(
                    text = "Membuka $appName dalam...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
                Text(
                    text = "$countdown",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (countdown == 0) {
                Text(
                    text = "Selamat menggunakan!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Batalkan & Tutup")
            }
        }
    }
}
