package com.ungker.ungkeh

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.core.content.edit
import android.util.Log // Add this import

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
        // PENTING: Aplikasi komunikasi dikecualikan dari Deep Friction
        if (isCommunicationApp(packageName)) {
            Log.d(TAG, "getCategory: Skipping $packageName because it's a communication app.")
            return null
        }
        
        if (isSocialMediaApp(packageName)) return FrictionCategory.SOCIAL_MEDIA
        if (isGameApp(packageName)) return FrictionCategory.GAME
        if (isVideoApp(packageName)) return FrictionCategory.VIDEO
        return null
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

