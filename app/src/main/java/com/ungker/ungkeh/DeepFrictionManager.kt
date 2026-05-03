package com.ungker.ungkeh

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.core.content.edit
import android.util.Log // Add this import
import java.util.Calendar

enum class FrictionCategory {
    SOCIAL_MEDIA,
    GAME,
    VIDEO
}

class DeepFrictionManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "DeepFrictionManager" // Add TAG for logging
        private const val COOLDOWN_DURATION_MS = 3 * 60 * 1000L // 3 minutes
        private const val PREF_LAST_SHOWN_KEY_PREFIX = "deep_friction_last_"
        private const val PREF_ACTIVE_APP_KEY = "deep_friction_active_app"
        
        // Cache for top apps to avoid heavy calculation on every tick
        @Volatile private var cachedTopApps: Set<String>? = null
        @Volatile private var lastTopAppsUpdateTime = 0L
    }

    // Identifies frequently used apps (Top 10 by usage time today)
    private fun isFrequentlyUsed(packageName: String): Boolean {
        val now = System.currentTimeMillis()
        if (cachedTopApps == null || now - lastTopAppsUpdateTime > 5 * 60 * 1000L) {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val usageMap = calcUsageFromEvents(context, cal.timeInMillis, now)
            cachedTopApps = usageMap.entries
                .filter { it.value > 60_000 } // Minimal 1 menit
                .sortedByDescending { it.value }
                .take(10)
                .map { it.key }
                .toSet()
            lastTopAppsUpdateTime = now
            Log.d(TAG, "isFrequentlyUsed: Updated Top Apps Cache: $cachedTopApps")
        }
        return cachedTopApps?.contains(packageName) ?: false
    }

    // Checks if the cooldown period has passed since the last friction was shown for a category.
    fun isCooldownExpired(category: FrictionCategory): Boolean {
        val lastTime = prefs.getLong("$PREF_LAST_SHOWN_KEY_PREFIX${category.name}", 0L)
        val currentTime = System.currentTimeMillis()
        val isExpired = (currentTime - lastTime) > COOLDOWN_DURATION_MS
        Log.d(TAG, "isCooldownExpired: Category=$category, lastTime=$lastTime, currentTime=$currentTime, COOLDOWN_DURATION_MS=$COOLDOWN_DURATION_MS, isExpired=$isExpired")
        return isExpired
    }

    // Get category for a package
    fun getCategory(packageName: String): FrictionCategory? {
        val pkg = packageName.lowercase()

        // KECUALIKAN aplikasi komunikasi & utilitas lain (TERMASUK Telegram, Google, Opera, dll)
        if (isCommunicationApp(packageName) || isUtilityApp(packageName)) {
            Log.d(TAG, "getCategory: Skipping $packageName because it's a communication/utility app.")
            return null
        }

        // PENTING: Chrome hanya kena Deep Friction jika masuk top usage
        // (Chrome sengaja dipisah karena sering dipakai doomscroll tapi secara kategori sistem adalah browser)
        if (pkg.contains("chrome")) {
            return if (isFrequentlyUsed(packageName)) FrictionCategory.SOCIAL_MEDIA else null
        }
        
        // Medsos, Game, Video hanya muncul jika "sering dipakai"
        if (!isFrequentlyUsed(packageName)) {
            return null
        }

        if (isSocialMediaApp(packageName)) return FrictionCategory.SOCIAL_MEDIA
        if (isGameApp(packageName)) return FrictionCategory.GAME
        if (isVideoApp(packageName)) return FrictionCategory.VIDEO
        return null
    }

    /**
     * Identifikasi aplikasi utilitas.
     */
    fun isUtilityApp(packageName: String): Boolean {
        val pm = context.packageManager
        val pkg = packageName.lowercase()
        
        // Chrome dikecualikan dari filter utilitas karena ditangani khusus di getCategory
        if (pkg.contains("chrome")) return false

        // Tambahkan Opera, Google (search), dan keyword browser lain
        val utilityKeywords = listOf(
            "browser", "calculator", "clock", "calendar", "camera", "gallery", 
            "settings", "filemanager", "contact", "phone", "map", "weather",
            "provider.telephony", "android.gms", "android.vending", "camera", "photos",
            "opera", "google.android.googlequicksearchbox", "google.android.apps.searchlite",
            "bing", "duckduckgo", "microsoft.emmx", "firefox", "puffin", "dolphin"
        )
        if (utilityKeywords.any { pkg.contains(it) }) return true

        return try {
            val info = pm.getApplicationInfo(packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                info.category == ApplicationInfo.CATEGORY_MAPS ||
                info.category == ApplicationInfo.CATEGORY_PRODUCTIVITY ||
                info.category == ApplicationInfo.CATEGORY_IMAGE
            } else false
        } catch (_: Exception) { false }
    }

    // Get the app that was successfully opened after Deep Friction (still in use)
    fun getCurrentlyActiveApp(): String? {
        val activeApp = prefs.getString(PREF_ACTIVE_APP_KEY, null)
        Log.d(TAG, "getCurrentlyActiveApp: activeApp=$activeApp")
        return activeApp
    }

    // Set the app that is currently being used after successfully passing Deep Friction
    fun setCurrentlyActiveApp(packageName: String) {
        Log.d(TAG, "setCurrentlyActiveApp: packageName=$packageName")
        prefs.edit {putString(PREF_ACTIVE_APP_KEY, packageName)}
    }

    // Clear the currently active app (when user exits the app)
    fun clearCurrentlyActiveApp() {
        Log.d(TAG, "clearCurrentlyActiveApp called")
        prefs.edit {remove(PREF_ACTIVE_APP_KEY)}
    }

    // daftar lengkap medsos yang bisa cause doomscroll
    private val knownSocialMediaPackages = listOf(
        "instagram", "facebook", "twitter", "x.android", "threads",
        "snapchat", "pinterest", "linkedin", "tiktok", "tiktoklite",
        "whatsapp", "telegram", "signal", "discord"
    )

    // Identifies social media applications.
    fun isSocialMediaApp(packageName: String): Boolean {
        val pm = context.packageManager
        val pkg = packageName.lowercase()

        // WhatsApp dihapus dari sini karena masuk kategori komunikasi
        val socialMediaPackages = listOf(
            "instagram", "facebook", "twitter", "x.android", "threads",
            "snapchat", "pinterest", "linkedin", "tiktok"
        )
        if (socialMediaPackages.any { pkg.contains(it) }) return true

        return try {
            val info = pm.getApplicationInfo(packageName, 0)
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    info.category == ApplicationInfo.CATEGORY_SOCIAL)
        } catch (_: Exception) { false }
    }

    // Get list of installed but unblocked social media apps
    fun getUnblockedSocialMediaApps(): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        val blockedApps = prefs.getStringSet("blocked_apps", emptySet()) ?: emptySet()
        
        for (pkgName in knownSocialMediaPackages) {
            try {
                val packages = context.packageManager.getInstalledApplications(0)
                val match = packages.find { it.packageName.lowercase().contains(pkgName) }
                if (match != null && !blockedApps.contains(match.packageName)) {
                    val appName = context.packageManager.getApplicationLabel(match).toString()
                    result.add(Pair(match.packageName, appName))
                }
            } catch (_: Exception) { continue }
        }
        return result
    }

    // Check if app is blocked
    fun isAppBlocked(packageName: String): Boolean {
        val blockedApps = prefs.getStringSet("blocked_apps", emptySet()) ?: emptySet()
        return blockedApps.contains(packageName)
    }

    // Identifies game applications.
    private fun isGameApp(packageName: String): Boolean {
        val pm = context.packageManager
        val pkg = packageName.lowercase()

        val gamePackages = listOf("mobile.legends", "pubg", "freefire", "genshin", "coc")
        if (gamePackages.any { pkg.contains(it) }) return true

        return try {
            val info = pm.getApplicationInfo(packageName, 0)
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    info.category == ApplicationInfo.CATEGORY_GAME)
        } catch (_: Exception) { false }
    }

    /**
     * Identifikasi aplikasi komunikasi/messaging.
     * Aplikasi ini dikecualikan dari Deep Friction karena fungsinya yang penting untuk koordinasi.
     */
    fun isCommunicationApp(packageName: String): Boolean {
        val pkg = packageName.lowercase()
        val communicationPackages = listOf(
            "whatsapp", "telegram", "signal", "line", "wechat", "viber", "slack", "discord"
        )
        return communicationPackages.any { pkg.contains(it) }
    }

    // Identifies video applications.
    private fun isVideoApp(packageName: String): Boolean {
        val pm = context.packageManager
        val pkg = packageName.lowercase()

        val videoPackages = listOf("youtube", "netflix", "disney", "primevideo", "spotify", "tiktok")
        if (videoPackages.any { pkg.contains(it) }) return true

        return try {
            val info = pm.getApplicationInfo(packageName, 0)
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    info.category == ApplicationInfo.CATEGORY_VIDEO)
        } catch (_: Exception) { false }
    }

    // Determines if the friction screen should be shown.
    // Deep Friction hanya muncul untuk kategori hiburan (game, video, medsos)
    // dan hanya jika cooldown untuk kategori tersebut sudah expired.
    // Ditambahkan: Tidak muncul jika aplikasi yang diperiksa sedang aktif (sudah melewati Deep Friction)
    fun shouldShowFriction(packageName: String): Boolean {
        Log.d(TAG, "shouldShowFriction: Checking for packageName=$packageName")
        
        // Cek master switch dari pengaturan profil
        if (!prefs.getBoolean("feature_deep_friction_enabled", true)) {
            Log.d(TAG, "shouldShowFriction: Deep Friction is disabled in settings.")
            return false
        }

        // Jika aplikasi yang ingin dibuka sama dengan aplikasi yang saat ini aktif (sudah melewati Deep Friction),
        // maka jangan tampilkan friction lagi.
        if (packageName == getCurrentlyActiveApp()) {
            Log.d(TAG, "shouldShowFriction: Skipping for $packageName because it is currently active.")
            return false
        }
        val category = getCategory(packageName)
        if (category == null) {
            Log.d(TAG, "shouldShowFriction: Skipping for $packageName because it's not a recognized friction category.")
            return false
        }
        val showFriction = isCooldownExpired(category)
        Log.d(TAG, "shouldShowFriction: For $packageName (Category=$category), showFriction=$showFriction (Is cooldown expired?)")
        return showFriction
    }

    // Marks that friction was shown for a category, resetting the cooldown timer.
    fun markFrictionShown(category: FrictionCategory) {
        Log.d(TAG, "markFrictionShown: Marking friction shown for category=$category")
        prefs.edit {putLong("$PREF_LAST_SHOWN_KEY_PREFIX${category.name}", System.currentTimeMillis())}
    }

    // Marks friction as shown for the given package's category.
    fun markFrictionShown(packageName: String) {
        val category = getCategory(packageName)
        if (category == null) {
            Log.d(TAG, "markFrictionShown: Cannot mark friction for $packageName as category is null.")
            return
        }
        markFrictionShown(category)
    }
}

