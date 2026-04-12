package com.ungker.ungkeh

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object UnlockManager {

    fun recordPageRead(prefs: com.ungker.ungkeh.data.PreferenceManager) {
        if (prefs.isInUnlockChallenge()) {
            prefs.incrementQuranPagesReadForUnlock()
        }
    }

    fun isUnlockChallengeActive(prefs: com.ungker.ungkeh.data.PreferenceManager): Boolean {
        return prefs.isInUnlockChallenge()
    }

    fun getQuranPagesReadForUnlock(prefs: com.ungker.ungkeh.data.PreferenceManager): Int {
        return prefs.getQuranPagesReadForUnlock()
    }

    fun checkUnlockCondition(prefs: com.ungker.ungkeh.data.PreferenceManager): Boolean {
        return getQuranPagesReadForUnlock(prefs) >= 5
    }

    fun activateTemporaryUnlock(prefs: com.ungker.ungkeh.data.PreferenceManager) {
        prefs.setTempUnlockExpiryMs(System.currentTimeMillis() + 30 * 60 * 1000L) // 30 minutes
        prefs.setHasTakenTempUnlockToday(true)
        prefs.setIsInUnlockChallenge(false)
        prefs.setResetDetoxStreakToday(true) // Signal to reset detox streak
        prefs.setQuranPagesReadForUnlock(0) // Reset pages read
    }

    fun activateHardLockUntilTomorrow(prefs: com.ungker.ungkeh.data.PreferenceManager) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        prefs.setHardLockedUntilTomorrow(true)
        prefs.setHardLockDate(todayStr)
        prefs.setHasChosenIkhlasToday(true)
        prefs.setIsInUnlockChallenge(false)
        prefs.setQuranPagesReadForUnlock(0) // Reset pages read
    }

    fun resetDailyUnlockStates(prefs: com.ungker.ungkeh.data.PreferenceManager) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        // Reset challenge state if hard lock date is not today, meaning it's a new day
        if (prefs.isHardLockedUntilTomorrow() && prefs.getHardLockDate() != todayStr) {
            prefs.setHardLockedUntilTomorrow(false)
            prefs.setHardLockDate(todayStr)
        }

        // Reset social media daily stats if date has changed
        if (prefs.getSocialMediaDate() != todayStr) {
            prefs.setSocialMediaTimeUsedMs(0L)
            prefs.setSocialMediaDate(todayStr)
            prefs.setHasTakenTempUnlockToday(false) // This allows one 30-min bonus per day
        }

        // Reset all daily flags
        prefs.setQuranPagesReadForUnlock(0)
        prefs.setHasChosenIkhlasToday(false)
        prefs.setResetDetoxStreakToday(false)
        prefs.setIsInUnlockChallenge(false)
        prefs.setTempUnlockExpiryMs(0L)
        prefs.setSocialMediaTempUnlockExpiryMs(0L)
    }
}
