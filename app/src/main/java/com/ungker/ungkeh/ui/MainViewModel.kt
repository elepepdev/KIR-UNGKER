package com.ungker.ungkeh.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val sp: SharedPreferences =
        application.getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE)

    private val _remainingCredit = MutableStateFlow(sp.getLong("remaining_credit", 0L))
    val remainingCredit: StateFlow<Long> = _remainingCredit

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == "remaining_credit") {
            _remainingCredit.value = prefs.getLong("remaining_credit", 0L)
        }
    }

    init {
        sp.registerOnSharedPreferenceChangeListener(listener)
        // Polling sebagai fallback agar UI selalu sinkron meski listener terlewat
        viewModelScope.launch {
            while (true) {
                delay(1_000L)
                _remainingCredit.value = sp.getLong("remaining_credit", 0L)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        sp.unregisterOnSharedPreferenceChangeListener(listener)
    }
}