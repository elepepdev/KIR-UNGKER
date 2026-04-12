package com.ungker.ungkeh.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PreferenceManager(context: Context) {
    private val sharedPref: SharedPreferences

    init {
        sharedPref = context.getSharedPreferences(Companion.UNGKER_PREF, Context.MODE_PRIVATE)
    }

    private val _remainingCredit = MutableStateFlow(sharedPref.getLong(REMAINING_CREDIT, 0L))
    val remainingCredit: StateFlow<Long> = _remainingCredit

    fun updateCredit(newCredit: Long) {
        sharedPref.edit {putLong(REMAINING_CREDIT, newCredit)}
        _remainingCredit.value = newCredit
    }

    fun getBlockedApps(): Set<String> {
        return sharedPref.getStringSet(Companion.BLOCKED_APPS, emptySet()) ?: emptySet()
    }

    fun saveBlockedApps(apps: Set<String>) {
        sharedPref.edit {putStringSet(BLOCKED_APPS, apps)}
    }

    // New methods for the new preferences
    fun getQuranPagesReadForUnlock(): Int = sharedPref.getInt(Companion.QURAN_PAGES_READ_FOR_UNLOCK, 0)
    fun setQuranPagesReadForUnlock(count: Int) = sharedPref.edit { putInt(
        QURAN_PAGES_READ_FOR_UNLOCK, count) }
    fun incrementQuranPagesReadForUnlock() = setQuranPagesReadForUnlock(getQuranPagesReadForUnlock() + 1)

    fun getTempUnlockExpiryMs(): Long = sharedPref.getLong(Companion.TEMP_UNLOCK_EXPIRY_MS, 0L)
    fun setTempUnlockExpiryMs(timestamp: Long) = sharedPref.edit { putLong(TEMP_UNLOCK_EXPIRY_MS, timestamp) }

    fun isHardLockedUntilTomorrow(): Boolean = sharedPref.getBoolean(Companion.IS_HARD_LOCKED_UNTIL_TOMORROW, false)
    fun setHardLockedUntilTomorrow(locked: Boolean) = sharedPref.edit { putBoolean(
        IS_HARD_LOCKED_UNTIL_TOMORROW, locked) }

    fun getHardLockDate(): String = sharedPref.getString(Companion.HARD_LOCK_DATE, "") ?: ""
    fun setHardLockDate(date: String) = sharedPref.edit { putString(HARD_LOCK_DATE, date) }

    fun hasTakenTempUnlockToday(): Boolean = sharedPref.getBoolean(Companion.HAS_TAKEN_TEMP_UNLOCK_TODAY, false)
    fun setHasTakenTempUnlockToday(taken: Boolean) = sharedPref.edit { putBoolean(
        HAS_TAKEN_TEMP_UNLOCK_TODAY, taken) }

    fun hasChosenIkhlasToday(): Boolean = sharedPref.getBoolean(Companion.HAS_CHOSEN_IKHLAS_TODAY, false)
    fun setHasChosenIkhlasToday(chosen: Boolean) = sharedPref.edit { putBoolean(
        HAS_CHOSEN_IKHLAS_TODAY, chosen) }

    fun getResetDetoxStreakToday(): Boolean = sharedPref.getBoolean(Companion.RESET_DETOX_STREAK_TODAY, false)
    fun setResetDetoxStreakToday(reset: Boolean) = sharedPref.edit { putBoolean(
        RESET_DETOX_STREAK_TODAY, reset) }

    fun isInUnlockChallenge(): Boolean = sharedPref.getBoolean(Companion.IS_IN_UNLOCK_CHALLENGE, false)
    fun setIsInUnlockChallenge(inChallenge: Boolean) = sharedPref.edit { putBoolean(
        IS_IN_UNLOCK_CHALLENGE, inChallenge) }

    fun getSocialMediaTimeUsedMs(): Long = sharedPref.getLong(Companion.SOCIAL_MEDIA_TIME_USED_MS, 0L)
    fun setSocialMediaTimeUsedMs(timeMs: Long) = sharedPref.edit { putLong(SOCIAL_MEDIA_TIME_USED_MS, timeMs) }

    fun getSocialMediaDate(): String = sharedPref.getString(Companion.SOCIAL_MEDIA_DATE, "") ?: ""
    fun setSocialMediaDate(date: String) = sharedPref.edit { putString(SOCIAL_MEDIA_DATE, date) }

    fun getSocialMediaLimitMinutes(): Long = sharedPref.getLong(Companion.SOCIAL_MEDIA_LIMIT_MINUTES, 60L) // Default 60 mins

    // Social Media Temp Unlock
    fun getSocialMediaTempUnlockExpiryMs(): Long = sharedPref.getLong(Companion.SOCIAL_MEDIA_TEMP_UNLOCK_EXPIRY_MS, 0L)
    fun setSocialMediaTempUnlockExpiryMs(timestamp: Long) = sharedPref.edit { putLong(SOCIAL_MEDIA_TEMP_UNLOCK_EXPIRY_MS, timestamp) }

    fun isSocialMediaTempUnlocked(): Boolean = System.currentTimeMillis() < getSocialMediaTempUnlockExpiryMs()

    companion object {
        const val UNGKER_PREF = "UNGKER_PREF"
        const val QURAN_PAGES_READ_FOR_UNLOCK = "quran_pages_read_for_unlock"
        const val TEMP_UNLOCK_EXPIRY_MS = "temp_unlock_expiry_ms"
        const val IS_HARD_LOCKED_UNTIL_TOMORROW = "is_hard_locked_until_tomorrow"
        const val HARD_LOCK_DATE = "hard_lock_date"
        const val HAS_TAKEN_TEMP_UNLOCK_TODAY = "has_taken_temp_unlock_today"
        const val HAS_CHOSEN_IKHLAS_TODAY = "has_chosen_ikhlas_today"
        const val RESET_DETOX_STREAK_TODAY = "reset_detox_streak_today"
        const val IS_IN_UNLOCK_CHALLENGE = "is_in_unlock_challenge"
        const val REMAINING_CREDIT = "remaining_credit"
        const val BLOCKED_APPS = "blocked_apps"
        const val SOCIAL_MEDIA_LIMIT_MINUTES = "social_media_limit_minutes"
        const val SOCIAL_MEDIA_TIME_USED_MS = "social_media_time_used_ms"
        const val SOCIAL_MEDIA_DATE = "social_media_date"
        const val SOCIAL_MEDIA_TEMP_UNLOCK_EXPIRY_MS = "social_media_temp_unlock_expiry_ms"
        const val USER_ROLE = "user_role"
        const val PARENT_PASSWORD = "parent_password"
    }

    fun getUserRole(): String = sharedPref.getString(Companion.USER_ROLE, "personal") ?: "personal"
    fun setUserRole(role: String) = sharedPref.edit { putString(USER_ROLE, role) }

    fun getParentPassword(): String = sharedPref.getString(Companion.PARENT_PASSWORD, "") ?: ""
    fun setParentPassword(password: String) = sharedPref.edit { putString(PARENT_PASSWORD, password) }
}
