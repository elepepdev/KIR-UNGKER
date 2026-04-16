package com.ungker.ungkeh

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.edit
import java.util.Calendar

class UngkerAccessibilityService : AccessibilityService() {

    private lateinit var prefs: SharedPreferences
    private var cachedBlockedApps: Set<String> = emptySet()
    private var lastLockTime = 0L
    private var cachedPrayerTimes: Map<String, Double>? = null

    companion object {
        @Volatile var instance: UngkerAccessibilityService? = null
            private set

        @Volatile var isLockVisible = false

        private const val POST_LOCK_GRACE_MS = 5_000L
        private const val LOCK_SHOW_COOLDOWN = 3_000L
        private const val SHOLAT_WINDOW_SEC = 600.0

        fun isServiceEnabled(context: Context): Boolean {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val componentName = context.packageName + "/" + UngkerAccessibilityService::class.java.name
            return enabledServices.contains(componentName)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE)
        cachedBlockedApps = prefs.getStringSet("blocked_apps", emptySet()) ?: emptySet()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 50
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Self-healing: ensure instance is set in case it was cleared
        instance = this
        
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName) return

        val currentTime = System.currentTimeMillis()

        cachedBlockedApps = prefs.getStringSet("blocked_apps", emptySet()) ?: emptySet()

        val lockDismissedAt = prefs.getLong("lock_dismissed_at", 0L)
        val inGracePeriod = (currentTime - lockDismissedAt) < POST_LOCK_GRACE_MS

        if (inGracePeriod || isLockVisible || !cachedBlockedApps.contains(packageName)) {
            return
        }

        if (currentTime - lastLockTime < LOCK_SHOW_COOLDOWN) {
            return
        }

        val remainingCredit = prefs.getLong("remaining_credit", 0L)
        val pledgeExpiry = prefs.getLong("sholat_pledge_credit_expiry", 0L)
        val hasPledgeCredit = currentTime < pledgeExpiry

        if (remainingCredit <= 0 && !hasPledgeCredit) {
            updatePrayerTimesCache()
            val inPrayerWindow = isInPrayerWindow()

            if (inPrayerWindow) {
                showLockScreen(SholatLockActivity::class.java, packageName)
            } else {
                showLockScreen(LockActivity::class.java, packageName)
            }
            lastLockTime = currentTime
        }
    }

    private fun isInPrayerWindow(): Boolean {
        val cached = cachedPrayerTimes ?: return false
        val cal = Calendar.getInstance()
        val nowSec = cal.get(Calendar.HOUR_OF_DAY) * 3600.0 +
                cal.get(Calendar.MINUTE) * 60.0 +
                cal.get(Calendar.SECOND) +
                cal.get(Calendar.MILLISECOND) / 1000.0

        return cached.entries.any { (_, pSec) ->
            nowSec >= pSec && nowSec <= pSec + SHOLAT_WINDOW_SEC
        }
    }

    private fun updatePrayerTimesCache() {
        val cal = Calendar.getInstance()
        val lat = prefs.getFloat("sholat_lat", -6.2088f).toDouble()
        val lng = prefs.getFloat("sholat_lng", 106.8456f).toDouble()
        val tz = prefs.getInt("sholat_tz", 7)

        try {
            val pt = hitungWaktuSholat(
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
                lat, lng, tz
            )
            fun toSec(hhmm: String): Double {
                val p = hhmm.split(":")
                return p[0].toDouble() * 3600 + p[1].toDouble() * 60
            }
            cachedPrayerTimes = mapOf(
                "Subuh" to toSec(pt.subuh),
                "Dzuhur" to toSec(pt.dzuhur),
                "Ashar" to toSec(pt.ashar),
                "Maghrib" to toSec(pt.maghrib),
                "Isya" to toSec(pt.isya)
            )
        } catch (e: Exception) {
            cachedPrayerTimes = null
        }
    }

    private fun showLockScreen(targetClass: Class<*>, targetPackage: String) {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val wakeLock = pm.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                "Ungker:AccessibilityLockWakeup"
            )
            wakeLock.acquire(5_000L)
        } catch (_: Exception) {}

        val lockIntent = Intent(this, targetClass).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
            putExtra("target_package", targetPackage)
        }

        try {
            startActivity(lockIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}