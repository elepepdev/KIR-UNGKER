package com.ungker.ungkeh

import android.app.*
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

import com.ungker.ungkeh.data.PreferenceManager
import android.util.Log

class UngkerService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var monitorJob: Job? = null
    private lateinit var prefs: SharedPreferences

    private val TAG = "UngkerService"

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        key?.let { onPrefsChanged(it) }
    }

    private var cachedBlockedApps: Set<String> = emptySet()
    @Volatile private var cachedRemainingCredit = 0L
    @Volatile private var cachedPledgeExpiry = 0L

    // ── Variabel untuk timestamp-based credit deduction ──────────────
    @Volatile private var lastKnownForeground  = ""
    @Volatile private var creditSessionStart   = 0L
    @Volatile private var creditAtSessionStart = 0L
    @Volatile private var lastCreditWriteMs    = 0L

    private var cachedPrayerTimes: Map<String, Double>? = null
    private var lastPrayerUpdateDay = -1

    // Social Media Time Tracking
    private var cachedSocialMediaLimitMinutes: Long = 60L
    private var socialMediaTimeUsedMs: Long = 0L
    private var lastSocialMediaWriteMs: Long = 0L
    private var socialMediaLockShownAt: Long = 0L
    private var tempSocialMediaUnlockExpiryMs: Long = 0L // Timestamp when temp unlock expires
    private var cachedIsHardLocked: Boolean = false
    private var cachedHardLockDate: String = ""

    private fun checkHasPledgeCredit(): Boolean {
        return System.currentTimeMillis() < cachedPledgeExpiry
    }

    private fun onPrefsChanged(key: String) {
        when (key) {
            "blocked_apps" -> cachedBlockedApps = prefs.getStringSet("blocked_apps", emptySet()) ?: emptySet()
            "remaining_credit" -> {
                val spValue = prefs.getLong("remaining_credit", 0L)
                if (spValue > cachedRemainingCredit) {
                    cachedRemainingCredit = spValue
                    creditAtSessionStart  = spValue
                    creditSessionStart    = System.currentTimeMillis()
                }
            }
            "social_media_limit_minutes" -> {
                cachedSocialMediaLimitMinutes = prefs.getLong("social_media_limit_minutes", 60L)
            }
            "social_media_temp_unlock_expiry_ms" -> {
                tempSocialMediaUnlockExpiryMs = prefs.getLong("social_media_temp_unlock_expiry_ms", 0L)
                // SYNC: Ambil nilai terbaru yang mungkin sudah diubah oleh SocialMediaLockActivity
                socialMediaTimeUsedMs = prefs.getLong("social_media_time_used_ms", 0L)
            }
            "sholat_pledge_credit_expiry" -> cachedPledgeExpiry = prefs.getLong("sholat_pledge_credit_expiry", 0L)
            "is_hard_locked_until_tomorrow" -> cachedIsHardLocked = prefs.getBoolean("is_hard_locked_until_tomorrow", false)
            "hard_lock_date" -> cachedHardLockDate = prefs.getString("hard_lock_date", "") ?: ""
            "sholat_lat", "sholat_lng", "sholat_tz" -> {
                updatePrayerTimesCache()
            }
        }
    }

    companion object {
        private const val CHANNEL_GUARDIAN   = "UNGKER_NOTIF"
        private const val CHANNEL_LOCK       = "UNGKER_LOCK"
        private const val CHANNEL_PERMISSION = "UNGKER_PERMISSION"
        private const val NOTIF_ID_GUARDIAN  = 1
        private const val NOTIF_ID_LOCK      = 2
        private const val NOTIF_ID_PERMISSION = 3
        const val SHOLAT_WINDOW_SEC = 600.0
        const val LOCK_SHOW_COOLDOWN = 3_000L

        @Volatile var isLockActivityVisible = false

        fun scheduleKeepAlive(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val intent = Intent(context, BootReceiver::class.java).apply { action = "UNGKER_KEEP_ALIVE" }
            val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                android.app.PendingIntent.getBroadcast(context, 99, intent, android.app.PendingIntent.FLAG_IMMUTABLE)
            } else {
                android.app.PendingIntent.getBroadcast(context, 99, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT)
            }

            // Trigger tiap 15 menit menggunakan set (Inexact) agar tidak crash di Android 12+
            // setExactAndAllowWhileIdle dilarang kecuali ada izin khusus yang sulit didapat di Play Store.
            val triggerAt = System.currentTimeMillis() + 15 * 60 * 1000L
            alarmManager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }

        fun isServiceRunning(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            @Suppress("DEPRECATION")
            val runningServices = manager.getRunningServices(Int.MAX_VALUE)
            for (service in runningServices) {
                if (UngkerService::class.java.name == service.service.className) return true
            }
            return false
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun checkAndRequestUsageStatsPermission(): Boolean {
        if (!hasUsageStatsPermission()) {
            showPermissionRequiredNotification()
            return false
        }
        return true
    }

    private fun showPermissionRequiredNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val settingsIntent = Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else
            PendingIntent.FLAG_UPDATE_CURRENT
        val pendingIntent = PendingIntent.getActivity(this, 0, settingsIntent, pendingFlags)
        val notification = NotificationCompat.Builder(this, CHANNEL_PERMISSION)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("Izin Diperlukan")
            .setContentText("Klik untuk memberikan izin Akses Penggunaan")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIF_ID_PERMISSION, notification)
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setupNotificationChannels()
        }
        prefs = getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE)
        val prefManager = PreferenceManager(this)

        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        cachedBlockedApps = prefs.getStringSet("blocked_apps", emptySet()) ?: emptySet()
        cachedRemainingCredit = prefs.getLong("remaining_credit", 0L)
        cachedPledgeExpiry = prefs.getLong("sholat_pledge_credit_expiry", 0L)
        cachedIsHardLocked = prefs.getBoolean("is_hard_locked_until_tomorrow", false)
        cachedHardLockDate = prefs.getString("hard_lock_date", "") ?: ""
        cachedSocialMediaLimitMinutes = prefs.getLong("social_media_limit_minutes", 60L)
        tempSocialMediaUnlockExpiryMs = prefs.getLong("social_media_temp_unlock_expiry_ms", 0L)

        // Initialize social media tracking for today
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastSocialMediaDate = prefManager.getSocialMediaDate()

        // Reset harian terpusat via UnlockManager
        if (lastSocialMediaDate != todayStr || cachedHardLockDate != todayStr) {
            UnlockManager.resetDailyUnlockStates(prefManager)
            // Refresh cached values after reset
            cachedIsHardLocked = prefManager.isHardLockedUntilTomorrow()
            cachedHardLockDate = prefManager.getHardLockDate()
        }

        // Load the current usage time from preferences
        socialMediaTimeUsedMs = prefManager.getSocialMediaTimeUsedMs()

        // Handle cases where the limit is already exceeded when the service starts
        // Set socialMediaLockShownAt = current time MINUS cooldown agar langsung bisa trigger lock
        val initLimitMs = cachedSocialMediaLimitMinutes * 60 * 1000L
        val initTempUnlockActive = System.currentTimeMillis() < tempSocialMediaUnlockExpiryMs
        if ((socialMediaTimeUsedMs >= initLimitMs || cachedIsHardLocked) && !initTempUnlockActive) {
            socialMediaLockShownAt = System.currentTimeMillis() - LOCK_SHOW_COOLDOWN - 1_000L
        }

        updatePrayerTimesCache()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_GUARDIAN)
            .setContentTitle("UNGKER Aktif")
            .setContentText("Melindungi fokusmu...")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID_GUARDIAN, notification)

        scheduleKeepAlive(this)
        if (!checkAndRequestUsageStatsPermission()) return START_STICKY

        startMonitorLoop()
        return START_STICKY
    }

    private fun startMonitorLoop() {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            val prefManager = PreferenceManager(this@UngkerService)
            var regularLockShownAt  = 0L
            var sozalatLockShownAt  = 0L
            var deepFrictionShownAt = 0L
            var iterations          = 0
            var isFirstIteration    = true
            var distractionExitStartTime = 0L // Timer untuk mendeteksi kapan user benar-benar keluar
            val POST_LOCK_GRACE_MS  = 5_000L
            val MONITOR_INTERVAL    = 100L
            val launcherPkg         = getLauncherPackageName()

            while (isActive) {
                val currentTime = System.currentTimeMillis()
                if (iterations % 150 == 0) { checkAndUpdateBadges(this@UngkerService) }
                iterations++

                if (isFirstIteration) {
                    cachedBlockedApps = prefs.getStringSet("blocked_apps", emptySet()) ?: emptySet()
                    isFirstIteration = false
                }

                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                if ((cachedHardLockDate != "" && cachedHardLockDate != todayStr) ||
                    (prefManager.getSocialMediaDate() != "" && prefManager.getSocialMediaDate() != todayStr)) {
                    UnlockManager.resetDailyUnlockStates(prefManager)
                    cachedIsHardLocked = prefManager.isHardLockedUntilTomorrow()
                    cachedHardLockDate = prefManager.getHardLockDate()
                }

                val today = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
                if (today != lastPrayerUpdateDay) { updatePrayerTimesCache(); lastPrayerUpdateDay = today }

                val lockDismissedAt = prefs.getLong("lock_dismissed_at", 0L)
                val inGracePeriod   = (currentTime - lockDismissedAt) < POST_LOCK_GRACE_MS

                if (isScreenOn()) {
                    val foregroundApp = getForegroundApp(this@UngkerService)
                    val dfManager = DeepFrictionManager(this@UngkerService)

                    // Identifikasi apakah ini aplikasi distraksi
                    val category = if (foregroundApp != null) dfManager.getCategory(foregroundApp) else null
                    val isDistractionApp = category != null

                    val tzPref = prefs.getInt("sholat_tz", 7)
                    val nowSec = getNowSecInTz(tzPref)
                    val activePrayer = cachedPrayerTimes?.let { checkPrayerWindow(it, nowSec, SHOLAT_WINDOW_SEC) }
                    val inPrayerWindow = activePrayer != null && prefs.contains("sholat_city_name")

                    if (inPrayerWindow && !checkHasPledgeCredit() && !inGracePeriod && !isLockActivityVisible) {
                        if (currentTime - sozalatLockShownAt > LOCK_SHOW_COOLDOWN) {
                            val target = if (foregroundApp != null && foregroundApp != packageName && foregroundApp != launcherPkg) foregroundApp else packageName
                            showLockScreen(SholatLockActivity::class.java, target, "Waktu Sholat ${activePrayer}", "Saatnya menunaikan sholat ${activePrayer} 🕌", NOTIF_ID_LOCK, activePrayer)
                            sozalatLockShownAt = currentTime
                            lastKnownForeground = ""
                        }
                    } else if (foregroundApp != null && foregroundApp != packageName && foregroundApp != launcherPkg) {
                        distractionExitStartTime = 0L

                        // 1. DETEKSI APP HOPPING & CLEAR SESI LAMA
                        val currentActive = dfManager.getCurrentlyActiveApp()
                        if (isDistractionApp && currentActive != null && currentActive != foregroundApp) {
                            Log.d("UngkerService", "App Hopping Detected: $currentActive -> $foregroundApp. Clearing session.")
                            dfManager.clearCurrentlyActiveApp()
                        }

                        // 2. ENFORCE DEEP FRICTION (Setiap loop jika belum di-unlock)
                        if (isDistractionApp && dfManager.shouldShowFriction(foregroundApp)) {
                            if (currentTime - deepFrictionShownAt > 3000L && !isLockActivityVisible) {
                                Log.d("UngkerService", "Deep Friction ENFORCED for $foregroundApp")
                                showLockScreen(
                                    DeepFrictionActivity::class.java,
                                    foregroundApp,
                                    "Deep Friction 🧠",
                                    "Wajib isi alasan sebelum membuka $foregroundApp",
                                    NOTIF_ID_LOCK
                                )
                                deepFrictionShownAt = currentTime
                            }
                            // Blokir eksekusi selanjutnya dan jangan tandai sebagai "terakhir dilihat" agar saat masuk nanti tetap trigger
                            lastKnownForeground = "" 
                            delay(MONITOR_INTERVAL)
                            continue
                        }

                        // 3. LOGIKA NORMAL (Kredit & Limit)
                        if (foregroundApp != lastKnownForeground) {
                            lastKnownForeground = foregroundApp
                            creditSessionStart = currentTime
                            creditAtSessionStart = cachedRemainingCredit
                        }

                        val locked = checkAndApplyLock(foregroundApp, currentTime, regularLockShownAt, inGracePeriod)
                        if (locked) {
                            regularLockShownAt = currentTime
                            // Jangan reset lastKnownForeground agar tidak loop friction
                        }

                        if (!isLockActivityVisible) {
                            val limitMs = cachedSocialMediaLimitMinutes * 60 * 1000L
                            val hardLockThresholdMs = limitMs + (30 * 60 * 1000L)
                            
                            var totalUsageAllDistractions = 0L
                            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                            val startOfDay = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                            val usageMap = calcUsageFromEvents(this@UngkerService, startOfDay, currentTime)
                            for ((pkgName, usageMs) in usageMap) { if (isDistractingApp(this@UngkerService, pkgName)) totalUsageAllDistractions += usageMs }

                            socialMediaTimeUsedMs = totalUsageAllDistractions
                            val isTempUnlockActive = currentTime < tempSocialMediaUnlockExpiryMs
                            if (totalUsageAllDistractions >= hardLockThresholdMs && !cachedIsHardLocked && !isTempUnlockActive) {
                                UnlockManager.activateHardLockUntilTomorrow(prefManager, "limit_exceeded")
                                cachedIsHardLocked = true
                                cachedHardLockDate = prefManager.getHardLockDate()
                            }

                            val isExceeded = (totalUsageAllDistractions >= limitMs && !isTempUnlockActive) || cachedIsHardLocked
                            if (!inGracePeriod && isExceeded && cachedBlockedApps.contains(foregroundApp) && currentTime - socialMediaLockShownAt > LOCK_SHOW_COOLDOWN) {
                                prefManager.setSocialMediaTimeUsedMs(totalUsageAllDistractions)
                                showLockScreen(SocialMediaLockActivity::class.java, foregroundApp, if (cachedIsHardLocked) "Batas Harian Terlampaui 🚫" else "Batas Waktu Medsos 📱", if (cachedIsHardLocked) "Sudah melewati batas bonus 30 menit. Sampai jumpa besok!" else "Waktunya Istirahat & Mengaji 5 Halaman", NOTIF_ID_LOCK, null)
                                socialMediaLockShownAt = currentTime
                            }

                            if (currentTime - lastSocialMediaWriteMs >= 5_000L) {
                                prefManager.setSocialMediaTimeUsedMs(totalUsageAllDistractions)
                                lastSocialMediaWriteMs = currentTime
                            }
                        }
                    } else {
                        // User di Launcher atau flicker
                        if (distractionExitStartTime == 0L) distractionExitStartTime = currentTime
                        
                        val activeApp = dfManager.getCurrentlyActiveApp()
                        
                        // ANTI-BYPASS: Jika user keluar dari aplikasi distraksi yang BELUM di-unlock
                        if (lastKnownForeground != "" && lastKnownForeground != activeApp) {
                            if (dfManager.getCategory(lastKnownForeground) != null && !isLockActivityVisible) {
                                // Jika sudah lebih dari 1s (bukan flicker), reset status agar masuk lagi wajib friction
                                if (currentTime - distractionExitStartTime > 1000L) {
                                    Log.d("UngkerService", "Bypass attempt or incomplete friction for $lastKnownForeground. Resetting.")
                                    lastKnownForeground = ""
                                }
                            }
                        }

                        if (currentTime - distractionExitStartTime > 60000L) {
                            if (activeApp != null) {
                                Log.d("UngkerService", "Inactivity timeout (60s). Clearing session.")
                                dfManager.clearCurrentlyActiveApp()
                                lastKnownForeground = ""
                            }
                            distractionExitStartTime = 0L
                        }
                        creditSessionStart = 0L
                    }
                } else {
                    // Screen Off
                    val dfManager = DeepFrictionManager(this@UngkerService)
                    if (dfManager.getCurrentlyActiveApp() != null) {
                        dfManager.clearCurrentlyActiveApp()
                    }
                    lastKnownForeground = ""
                    distractionExitStartTime = 0L
                    creditSessionStart = 0L
                }
                delay(MONITOR_INTERVAL)
            }
        }
    }

    private fun updatePrayerTimesCache() {
        val tz  = prefs.getInt("sholat_tz", 7)
        val tzId = if (tz >= 0) "GMT+$tz" else "GMT$tz"
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone(tzId))
        
        try {
            val lat = prefs.getFloat("sholat_lat", -6.2088f).toDouble()
            val lng = prefs.getFloat("sholat_lng", 106.8456f).toDouble()
            
            val pt = hitungWaktuSholat(
                cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH),
                lat, lng, tz
            )
            
            cachedPrayerTimes = mapOf(
                "Subuh"   to pt.subuhSec,
                "Dzuhur"  to pt.dzuhurSec,
                "Ashar"   to pt.asharSec,
                "Maghrib" to pt.maghribSec,
                "Isya"    to pt.isyaSec
            )
        } catch (e: Exception) { cachedPrayerTimes = null }
    }

    /**
     * Cek dan terapkan pengurangan kredit menggunakan timestamp-based deduction.
     *
     * Perubahan dari versi lama:
     * - DITAMBAH: Reset otomatis session saat ada penambahan kredit dari luar (via onPrefsChanged).
     * - DITAMBAH: Cegah deduction jika isLockActivityVisible = true (mengatasi lag UsageStats).
     * - DITAMBAH: Hitung elapsed dari `currentTime - creditSessionStart` → akurat 100%.
     */
    private fun checkAndApplyLock(
        foregroundApp: String,
        currentTime: Long,
        regularLockShownAt: Long,
        inGracePeriod: Boolean
    ): Boolean {
        if (inGracePeriod || checkHasPledgeCredit() || isLockActivityVisible) return false
        if (!cachedBlockedApps.contains(foregroundApp)) return false

        // Reset session jika creditSessionStart tidak valid (0L)
        if (creditSessionStart == 0L) {
            creditSessionStart   = currentTime
            creditAtSessionStart = cachedRemainingCredit
        }

        return when {
            cachedRemainingCredit > 0 -> {
                // Hitung kredit tersisa berdasarkan waktu nyata yang telah berlalu sejak sesi dimulai.
                val elapsed   = currentTime - creditSessionStart
                val newCredit = maxOf(0L, creditAtSessionStart - elapsed)
                cachedRemainingCredit = newCredit

                // Tulis ke SharedPreferences maksimal sekali per detik.
                if (currentTime - lastCreditWriteMs >= 1_000L) {
                    prefs.edit { putLong("remaining_credit", newCredit) }
                    // Reset snapshot: sesi baru dimulai dari nilai kredit saat ini.
                    creditAtSessionStart = newCredit
                    creditSessionStart   = currentTime
                    lastCreditWriteMs    = currentTime
                    
                    // Jika kredit habis, reset session agar tidak salah hitung saat app berubah
                    if (newCredit == 0L) {
                        creditSessionStart = 0L
                        lastKnownForeground = ""
                    }
                }
                false
            }
            currentTime - regularLockShownAt > 3_000L || regularLockShownAt == 0L -> {
                showLockScreen(
                    LockActivity::class.java, foregroundApp,
                    "Waktu Istirahat 📵", "Baca Al-Qur'an dulu untuk melanjutkan",
                    NOTIF_ID_LOCK, null
                )
                true
            }
            else -> false
        }
    }

    private fun showLockScreen(targetClass: Class<*>, targetPackage: String, notifTitle: String, notifText: String, notifId: Int, activePrayer: String? = null) {
        // ── STEP 1: Nyalakan layar secara agresif (wajib sebelum launch Activity) ──
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val wakeLock = pm.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                "Ungker:LockWakeup"
            )
            wakeLock.acquire(5_000L)
        } catch (_: Exception) {}

        // ── STEP 2: Intent dengan semua flag yang dibutuhkan ──────────────────────
        val lockIntent = Intent(this, targetClass).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
            putExtra("target_package", targetPackage)
            activePrayer?.let { putExtra("active_prayer", it) }
        }

        // ── STEP 3: Priority 1 — Coba AccessibilityService (best for Oppo/Xiaomi/Vivo) ──
        UngkerAccessibilityService.instance?.let { service ->
            try {
                service.startActivity(lockIntent)
                // Accessibility Service started successfully, no need for notification fallback
                return
            } catch (e: Exception) {
                // Fall through to next fallback
            }
        }

        // ── STEP 4: Fallback — Direct startActivity dari Service ─────────────────────
        // Ini bekerja jika SYSTEM_ALERT_WINDOW (overlay) sudah diberikan
        try {
            lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(lockIntent)
            return
        } catch (e: Exception) {
            Log.w("UngkerService", "Direct startActivity gagal: ${e.message}")
            // Lanjut ke notification fallback
        }

        // ── STEP 5: Fallback — FullScreenIntent notification ───────────────────
        // Ini hanya needed jika Accessibility belum di-enable
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else
            PendingIntent.FLAG_UPDATE_CURRENT

        val fullScreenPendingIntent = PendingIntent.getActivity(this, notifId, lockIntent, pendingFlags)

        val notification = NotificationCompat.Builder(this, CHANNEL_LOCK)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle(notifTitle)
            .setContentText(notifText)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVibrate(longArrayOf(0, 400, 200, 400))
            .build()

        @Suppress("DEPRECATION")
        notification.flags = notification.flags or Notification.FLAG_SHOW_LIGHTS

        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(notifId, notification)
        } catch (_: Exception) {}
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupNotificationChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Channel Guardian: low importance, cukup untuk foreground service
        if (nm.getNotificationChannel(CHANNEL_GUARDIAN) == null) {
            NotificationChannel(CHANNEL_GUARDIAN, "Ungker Guardian", NotificationManager.IMPORTANCE_LOW).also {
                nm.createNotificationChannel(it)
            }
        }

        // Channel Lock: IMPORTANCE_HIGH wajib agar fullScreenIntent bisa ditrigger
        // ⚠️ Jika channel sudah ada dengan importance berbeda, user perlu clear app data
        if (nm.getNotificationChannel(CHANNEL_LOCK) == null) {
            NotificationChannel(CHANNEL_LOCK, "Ungker Lock Screen", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Layar pengunci aplikasi — Jangan dinonaktifkan"
                setShowBadge(true)
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)   // Bypass Do Not Disturb agar lock selalu tampil
            }.also { nm.createNotificationChannel(it) }
        }

        // Channel Permission
        if (nm.getNotificationChannel(CHANNEL_PERMISSION) == null) {
            NotificationChannel(CHANNEL_PERMISSION, "Izin Diperlukan", NotificationManager.IMPORTANCE_HIGH).also {
                nm.createNotificationChannel(it)
            }
        }
    }

    private fun getDailyUsageForPackage(context: Context, targetPkg: String): Long {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()

            // Tentukan awal hari (tengah malam)
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfDay = calendar.timeInMillis

            // 1. Ambil data agregat harian dari sistem (Sangat Stabil)
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, now)
            val packageStats = stats.find { it.packageName == targetPkg }

            // Ambil waktu penggunaan yang dicatat sistem hari ini
            var totalTime = packageStats?.totalTimeInForeground ?: 0L

            // 2. Koreksi presisi (Sistem UsageStats kadang telat update jika aplikasi masih terbuka)
            // Kita cek jika aplikasi sedang terbuka SEKARANG, kita tambahkan selisihnya secara manual.
            if (packageStats != null) {
                val lastTimeUsed = packageStats.lastTimeUsed
                // Jika terakhir digunakan adalah dalam 1 menit terakhir dan aplikasi di foreground
                if (now - lastTimeUsed < 60_000 && getForegroundApp(context) == targetPkg) {
                    // Berikan estimasi tambahan berdasarkan selisih waktu terakhir dicatat sistem
                    // (Sistem biasanya update UsageStats setiap 1-5 detik atau saat app pindah)
                }
            }

            totalTime
        } catch (e: Exception) {
            0L
        }
    }

    private fun getForegroundApp(context: Context): String? {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()

            // Cek transisi ACTIVITY_RESUMED dalam 2 detik terakhir (Sangat Cepat)
            val events = usm.queryEvents(now - 2000, now)
            val event  = android.app.usage.UsageEvents.Event()
            var lastApp: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                    lastApp = event.packageName
                }
            }

            if (lastApp != null) {
                return if (lastApp == context.packageName) null else lastApp
            }

            // Fallback: Cek via queryUsageStats jika queryEvents gagal (lebih "sticky")
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 5000, now)
            var lastTime = 0L

            if (stats != null && stats.isNotEmpty()) {
                for (s in stats) {
                    if (s.lastTimeUsed > lastTime) {
                        lastTime = s.lastTimeUsed
                        lastApp = s.packageName
                    }
                }
            }

            if (lastApp == context.packageName) null else lastApp
        } catch (e: Exception) { null }
    }

    private fun getLauncherPackageName(): String {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
            val resolveInfo = packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            resolveInfo?.activityInfo?.packageName ?: ""
        } catch (e: Exception) { "" }
    }

    private fun isScreenOn(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                pm.isInteractive
            } else {
                @Suppress("DEPRECATION")
                pm.isScreenOn
            }
        } catch (e: Exception) { true }
    }

    private fun getRecentlyUsedBlockedApp(context: Context, timeWindowMs: Long): String? {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val since = now - timeWindowMs

            val events = usm.queryEvents(since, now)
            val event = android.app.usage.UsageEvents.Event()
            var lastBlockedApp: String? = null

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                    val pkg = event.packageName ?: continue
                    if (pkg != context.packageName && cachedBlockedApps.contains(pkg)) {
                        lastBlockedApp = pkg
                    }
                }
            }
            lastBlockedApp
        } catch (e: Exception) { null }
    }

    private fun isDeviceUnlocked(): Boolean {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return try { pm.isInteractive && !km.isKeyguardLocked } catch (e: Exception) { true }
    }

    override fun onDestroy() {
        super.onDestroy()
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}