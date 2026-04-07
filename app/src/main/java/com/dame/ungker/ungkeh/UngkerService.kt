package com.dame.ungker.ungkeh

import android.app.*
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import kotlinx.coroutines.*
import java.util.Calendar

class UngkerService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var prefs: SharedPreferences

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        key?.let { onPrefsChanged(it) }
    }

    private var cachedBlockedApps: Set<String> = emptySet()
    @Volatile private var cachedRemainingCredit = 0L
    private var cachedLocationLat = -6.2088
    private var cachedLocationLng = 106.8456
    private var cachedLocationTz = 7
    @Volatile private var cachedPledgeExpiry = 0L

    // ── Variabel untuk timestamp-based credit deduction ──────────────
    @Volatile private var lastKnownForeground  = ""
    @Volatile private var creditSessionStart   = 0L
    @Volatile private var creditAtSessionStart = 0L
    @Volatile private var lastCreditWriteMs    = 0L

    private var cachedPrayerTimes: Map<String, Double>? = null
    private var lastPrayerUpdateDay = -1

    private fun checkHasPledgeCredit(): Boolean {
        return System.currentTimeMillis() < cachedPledgeExpiry
    }

    private fun onPrefsChanged(key: String) {
        when (key) {
            "blocked_apps" -> cachedBlockedApps = prefs.getStringSet("blocked_apps", emptySet()) ?: emptySet()
            "remaining_credit" -> {
                val spValue = prefs.getLong("remaining_credit", 0L)
                // Jika nilai SP lebih besar dari cache (artinya ada credit yang ditambah dari luar),
                // update cache dan RESET session snapshot agar tidak ter-overwrite oleh snapshot lama.
                if (spValue > cachedRemainingCredit) {
                    cachedRemainingCredit = spValue
                    creditAtSessionStart  = spValue
                    creditSessionStart    = System.currentTimeMillis()
                }
            }
            "sholat_pledge_credit_expiry" -> cachedPledgeExpiry = prefs.getLong("sholat_pledge_credit_expiry", 0L)
            "sholat_lat" -> {
                cachedLocationLat = prefs.getFloat("sholat_lat", -6.2088f).toDouble()
                updatePrayerTimesCache()
            }
            "sholat_lng" -> {
                cachedLocationLng = prefs.getFloat("sholat_lng", 106.8456f).toDouble()
                updatePrayerTimesCache()
            }
            "sholat_tz" -> {
                cachedLocationTz = prefs.getInt("sholat_tz", 7)
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
            for (service in manager.getRunningServices(Int.MAX_VALUE)) {
                if (UngkerService::class.java.name == service.service.className) return true
            }
            return false
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        return try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 1000, now)
            stats != null && stats.isNotEmpty()
        } catch (e: Exception) { false }
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
        prefs = getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        cachedBlockedApps = prefs.getStringSet("blocked_apps", emptySet()) ?: emptySet()
        cachedRemainingCredit = prefs.getLong("remaining_credit", 0L)
        cachedPledgeExpiry = prefs.getLong("sholat_pledge_credit_expiry", 0L)
        cachedLocationLat = prefs.getFloat("sholat_lat", -6.2088f).toDouble()
        cachedLocationLng = prefs.getFloat("sholat_lng", 106.8456f).toDouble()
        cachedLocationTz = prefs.getInt("sholat_tz", 7)
        updatePrayerTimesCache()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setupNotificationChannels()
        }
        scheduleKeepAlive(this)
        if (!checkAndRequestUsageStatsPermission()) return START_STICKY
        val notification = NotificationCompat.Builder(this, CHANNEL_GUARDIAN)
            .setContentTitle("UNGKER Aktif")
            .setContentText("Melindungi fokusmu...")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID_GUARDIAN, notification)

        serviceScope.launch {
            var regularLockShownAt  = 0L
            var sholatLockShownAt   = 0L
            var iterations          = 0
            val POST_LOCK_GRACE_MS  = 5_000L
            val MONITOR_INTERVAL    = 100L
            val LOCK_SHOW_COOLDOWN  = 3_000L
            val launcherPkg         = getLauncherPackageName()

            while (isActive) {
                val currentTime = System.currentTimeMillis()
                if (iterations % 150 == 0) { checkAndUpdateBadges(this@UngkerService) }
                iterations++

                val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
                if (today != lastPrayerUpdateDay) { updatePrayerTimesCache(); lastPrayerUpdateDay = today }

                val lockDismissedAt = prefs.getLong("lock_dismissed_at", 0L)
                val inGracePeriod   = (currentTime - lockDismissedAt) < POST_LOCK_GRACE_MS

                // Jika baru keluar dari grace period atau LockActivity sedang tampil,
                // reset sesi agar tidak terhitung waktu saat lock screen terbuka.
                if (inGracePeriod || isLockActivityVisible) {
                    lastKnownForeground = ""
                }

                if (isDeviceUnlocked()) {
                    val foregroundApp = getForegroundApp(this@UngkerService)
                    
                    val cal   = Calendar.getInstance()
                    val nowSec = cal.get(Calendar.HOUR_OF_DAY) * 3600.0 +
                            cal.get(Calendar.MINUTE) * 60.0 +
                            cal.get(Calendar.SECOND) +
                            cal.get(Calendar.MILLISECOND) / 1000.0

                    val inPrayerWindow = cachedPrayerTimes?.entries?.any { (_, pSec) ->
                        nowSec >= pSec && nowSec <= pSec + SHOLAT_WINDOW_SEC
                    } ?: false

                    if (inPrayerWindow && !checkHasPledgeCredit() && !inGracePeriod && !isLockActivityVisible) {
                        if (currentTime - sholatLockShownAt > LOCK_SHOW_COOLDOWN) {
                            val target = foregroundApp ?: packageName
                            showLockScreen(SholatLockActivity::class.java, target, "Waktu Sholat", "Saatnya menunaikan sholat 🕌", NOTIF_ID_LOCK)
                            sholatLockShownAt = currentTime
                            lastKnownForeground = "" // reset sesi setelah lock
                        }
                    } else if (foregroundApp != null && foregroundApp != packageName) {

                        // Deteksi pergantian app → reset sesi deduction
                        // Kita hanya reset sesi jika app yang terdeteksi BUKAN app yang sedang di-track.
                        if (foregroundApp != lastKnownForeground) {
                            lastKnownForeground  = foregroundApp
                            creditSessionStart   = currentTime
                            creditAtSessionStart = cachedRemainingCredit
                        }

                        val locked = checkAndApplyLock(
                            foregroundApp    = foregroundApp,
                            currentTime      = currentTime,
                            regularLockShownAt = regularLockShownAt,
                            inGracePeriod    = inGracePeriod
                        )

                        if (locked) {
                            regularLockShownAt  = currentTime
                            lastKnownForeground = "" // reset sesi setelah lock
                        }

                    } else if (foregroundApp == launcherPkg || foregroundApp == null) {
                        // User benar-benar di home atau detection gagal (null).
                        // Untuk null, kita biarkan track sesi yang lama berjalan (sticky) 
                        // agar tidak ada waktu yang terbuang jika user hanya scrolling tanpa pindah app.
                        // HANYA reset jika kita YAKIN user ada di Launcher atau app non-blocked.
                        if (foregroundApp == launcherPkg) {
                            lastKnownForeground = ""
                        }
                    } else {
                        // Foreground app terdeteksi sebagai app unblocked lain (bukan launcher)
                        lastKnownForeground = ""
                    }
                } else {
                    // Layar mati / terkunci → reset sesi
                    lastKnownForeground = ""
                }

                delay(MONITOR_INTERVAL)
            }
        }
        return START_STICKY
    }

    private fun updatePrayerTimesCache() {
        val cal = Calendar.getInstance()
        try {
            val pt = hitungWaktuSholat(
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
                cachedLocationLat, cachedLocationLng, cachedLocationTz
            )
            fun toSec(hhmm: String): Double {
                val p = hhmm.split(":"); return p[0].toDouble() * 3600 + p[1].toDouble() * 60
            }
            cachedPrayerTimes = mapOf(
                "Subuh"   to toSec(pt.subuh),
                "Dzuhur"  to toSec(pt.dzuhur),
                "Ashar"   to toSec(pt.ashar),
                "Maghrib" to toSec(pt.maghrib),
                "Isya"    to toSec(pt.isya)
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
                }
                false
            }
            currentTime - regularLockShownAt > 3_000L -> {
                showLockScreen(
                    LockActivity::class.java, foregroundApp,
                    "Waktu Istirahat 📵", "Baca Al-Qur'an dulu untuk melanjutkan",
                    NOTIF_ID_LOCK
                )
                true
            }
            else -> false
        }
    }

    private fun showLockScreen(targetClass: Class<*>, targetPackage: String, notifTitle: String, notifText: String, notifId: Int) {
        val lockIntent = Intent(this, targetClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            putExtra("target_package", targetPackage)
        }
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else
            PendingIntent.FLAG_UPDATE_CURRENT
        val fullScreenPendingIntent = PendingIntent.getActivity(this, notifId, lockIntent, pendingFlags)
        val lockNotification = NotificationCompat.Builder(this, CHANNEL_LOCK)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle(notifTitle)
            .setContentText(notifText)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setAutoCancel(false)
            .setOngoing(true)
            .build()
        try { (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(notifId, lockNotification) } catch (_: Exception) { }
        try { startActivity(lockIntent) } catch (_: Exception) { }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupNotificationChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_GUARDIAN) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_GUARDIAN, "Ungker Guardian", NotificationManager.IMPORTANCE_LOW))
        }
        if (nm.getNotificationChannel(CHANNEL_LOCK) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_LOCK, "Ungker Lock", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notifikasi pengunci aplikasi"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            })
        }
        if (nm.getNotificationChannel(CHANNEL_PERMISSION) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_PERMISSION, "Izin Diperlukan", NotificationManager.IMPORTANCE_HIGH))
        }
    }

    private fun getForegroundApp(context: Context): String? {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            
            // 1. Cek via queryUsageStats (lebih "sticky" untuk deteksi app yang sedang dibuka)
            // Kita ambil data 1 menit terakhir untuk mencari app dengan lastTimeUsed paling baru.
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 1000 * 60, now)
            var lastApp: String? = null
            var lastTime = 0L
            
            if (stats != null && stats.isNotEmpty()) {
                for (s in stats) {
                    if (s.lastTimeUsed > lastTime) {
                        lastTime = s.lastTimeUsed
                        lastApp = s.packageName
                    }
                }
            }

            // 2. Verifikasi tambahan via queryEvents (lebih cepat deteksi transisi ACTIVITY_RESUMED)
            val events = usm.queryEvents(now - 5000, now)
            val event  = android.app.usage.UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                    lastApp = event.packageName
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