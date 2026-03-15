package com.dame.ungker.ungkeh

import android.app.*
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.util.Calendar

class UngkerService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channelId = "UNGKER_NOTIF"
        val channel = NotificationChannel(
            channelId, "Ungker Guardian", NotificationManager.IMPORTANCE_LOW
        )
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("UNGKER Active")
            .setContentText("Monitoring distractions...")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1, notification)

        serviceScope.launch {
            var lastCheckTime          = System.currentTimeMillis()
            var regularLockShownAt     = 0L   
            var sholatLockShownAt      = 0L   
            var cachedPrayerTimes: Map<String, Double>? = null
            var cacheHour = -1

            while (true) {
                val currentTime = System.currentTimeMillis()
                val deltaTime   = currentTime - lastCheckTime
                lastCheckTime   = currentTime

                // ── Baca semua state dari IO thread ──────────────────────
                val (foregroundApp, remainingCredit, blockedApps, lat, lng, tz) =
                    withContext(Dispatchers.IO) {
                        val sp      = getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE)
                        val credit  = sp.getLong("remaining_credit", 0L)
                        val blocked = sp.getStringSet("blocked_apps", emptySet()) ?: emptySet()
                        val fgApp   = getForegroundApp(this@UngkerService)
                        val l       = sp.getFloat("sholat_lat", -6.2088f).toDouble()
                        val g       = sp.getFloat("sholat_lng", 106.8456f).toDouble()
                        val z       = sp.getInt("sholat_tz", 7)
                        SixTuple(fgApp, credit, blocked, l, g, z)
                    }

                // ── Cache waktu sholat per jam agar hemat CPU ─────────────
                val cal = Calendar.getInstance()
                val nowHour = cal.get(Calendar.HOUR_OF_DAY)
                if (cachedPrayerTimes == null || cacheHour != nowHour) {
                    cachedPrayerTimes = withContext(Dispatchers.Default) {
                        try {
                            val pt = hitungWaktuSholat(
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH) + 1,
                                cal.get(Calendar.DAY_OF_MONTH),
                                lat, lng, tz
                            )
                            fun toSec(hhmm: String): Double {
                                val p = hhmm.split(":")
                                return p[0].toDouble() * 3600 + p[1].toDouble() * 60
                            }
                            mapOf(
                                "Subuh"   to toSec(pt.subuh),
                                "Dzuhur"  to toSec(pt.dzuhur),
                                "Ashar"   to toSec(pt.ashar),
                                "Maghrib" to toSec(pt.maghrib),
                                "Isya"    to toSec(pt.isya),
                            )
                        } catch (e: Exception) { null }
                    }
                    cacheHour = nowHour
                }

                val nowSec = cal.get(Calendar.HOUR_OF_DAY) * 3600.0 +
                        cal.get(Calendar.MINUTE) * 60.0 +
                        cal.get(Calendar.SECOND)
                val windowSec = SHOLAT_WINDOW_MS / 1000.0  

                val inPrayerWindow = cachedPrayerTimes?.entries?.any { (_, pSec) ->
                    nowSec >= pSec && nowSec <= pSec + windowSec
                } ?: false

                // ── Logika Penguncian ────────────────────────────────────
                if (foregroundApp != null && isDeviceUnlocked()) {
                    val isOwnApp = foregroundApp == packageName

                    if (!isOwnApp && inPrayerWindow && !hasPledgeCredit(this@UngkerService)) {
                        if (currentTime - sholatLockShownAt > 3000L) {
                            sholatLockShownAt = currentTime
                            val intentLock = Intent(this@UngkerService, SholatLockActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                putExtra("target_package", foregroundApp)
                            }
                            startActivity(intentLock)
                        }
                    } else if (!isOwnApp && blockedApps.contains(foregroundApp) && !inPrayerWindow) {
                        if (remainingCredit > 0) {
                            withContext(Dispatchers.IO) {
                                val sp = getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE)
                                val latest    = sp.getLong("remaining_credit", 0L)
                                val newCredit = maxOf(0L, latest - deltaTime)
                                sp.edit().putLong("remaining_credit", newCredit).apply()
                            }
                        } else {
                            if (currentTime - regularLockShownAt > 3000L) {
                                regularLockShownAt = currentTime
                                val intentLock = Intent(this@UngkerService, LockActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                    putExtra("target_package", foregroundApp)
                                }
                                startActivity(intentLock)
                            }
                        }
                    } else if (!isOwnApp && blockedApps.contains(foregroundApp) && inPrayerWindow) {
                        if (!hasPledgeCredit(this@UngkerService)) {
                            if (currentTime - sholatLockShownAt > 3000L) {
                                sholatLockShownAt = currentTime
                                val intentLock = Intent(this@UngkerService, SholatLockActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                    putExtra("target_package", foregroundApp)
                                }
                                startActivity(intentLock)
                            }
                        } else if (remainingCredit > 0) {
                            withContext(Dispatchers.IO) {
                                val sp = getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE)
                                val latest    = sp.getLong("remaining_credit", 0L)
                                val newCredit = maxOf(0L, latest - deltaTime)
                                sp.edit().putLong("remaining_credit", newCredit).apply()
                            }
                        }
                    }
                }
                delay(1000)
            }
        }
        return START_STICKY
    }

    private fun getForegroundApp(context: Context): String? {
        return try {
            val usm   = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val waktu = System.currentTimeMillis()
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, waktu - 3000, waktu)
            val topApp = stats?.maxByOrNull { it.lastTimeUsed }?.packageName
            if (topApp == context.packageName) null else topApp
        } catch (e: Exception) { null }
    }
    private fun isDeviceUnlocked(): Boolean {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        
        return try {
            val isInteractive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                pm.isInteractive
            } else {
                @Suppress("DEPRECATION")
                pm.isScreenOn
            }
            
            // Jika layar mati, anggap "locked" agar tidak memicu aktivitas
            if (!isInteractive) return false
            
            val isLocked = km.isKeyguardLocked
            !isLocked
        } catch (e: Exception) {
            true 
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

private data class SixTuple(
    val a: String?,
    val b: Long,
    val c: Set<String>,
    val d: Double,
    val e: Double,
    val f: Int
)
