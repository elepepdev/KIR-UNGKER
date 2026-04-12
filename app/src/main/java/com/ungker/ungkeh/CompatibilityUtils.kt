package com.ungker.ungkeh

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.net.toUri
import androidx.core.content.edit

object CompatibilityUtils {

    /**
     * Cek apakah perangkat adalah Xiaomi/Redmi.
     */
    fun isXiaomi(): Boolean = Build.MANUFACTURER.lowercase().contains("xiaomi")

    fun isOppoRealme(): Boolean {
        val m = Build.MANUFACTURER.lowercase()
        return m.contains("oppo") || m.contains("realme")
    }

    fun isVivo(): Boolean = Build.MANUFACTURER.lowercase().contains("vivo")
    
    fun isHuawei(): Boolean = Build.MANUFACTURER.lowercase().contains("huawei") || Build.MANUFACTURER.lowercase().contains("honor")

    /**
     * Cek apakah optimasi baterai (Doze Mode) diabaikan.
     */
    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Request izin untuk mengabaikan optimasi baterai.
     */
    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimization(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = "package:${context.packageName}".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try { context.startActivity(intent) } catch (_: Exception) {}
    }

    /**
     * Buka pengaturan Auto-start spesifik OEM (Xiaomi, Oppo, Vivo, dsb).
     */
    fun openAutoStartSettings(context: Context) {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val intent = Intent()
        when {
            manufacturer.contains("xiaomi") -> {
                intent.component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
            }
            manufacturer.contains("oppo") -> {
                intent.component = ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
            }
            manufacturer.contains("vivo") -> {
                intent.component = ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
            }
            manufacturer.contains("letv") -> {
                intent.component = ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity")
            }
            manufacturer.contains("honor") -> {
                intent.component = ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
            }
            else -> {
                intent.action = Settings.ACTION_SETTINGS
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { context.startActivity(intent) } catch (e: Exception) {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(fallback)
        }
    }

    /**
     * Buka pengaturan "Tampilkan jendela pop-up saat berjalan di latar belakang" (Khusus Xiaomi/MIUI).
     */
    fun openMiuiPopupPermission(context: Context) {
        val intent = Intent("miui.intent.action.APP_PERM_EDITOR")
        intent.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
        intent.putExtra("extra_pkgname", context.packageName)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = "package:${context.packageName}".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }

    /**
     * Buka pengaturan "Tampilkan di Kunci Layar" untuk Xiaomi, Oppo, dan Realme.
     */
    fun openShowOnLockScreenSettings(context: Context) {
        val manufacturer = Build.MANUFACTURER.lowercase()
        when {
            manufacturer.contains("xiaomi") -> {
                openMiuiPopupPermission(context)
            }
            manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("vivo") -> {
                val intents = mutableListOf<Intent>()
                
                // ColorOS / Realme UI (Oppo/Realme)
                intents.add(Intent().apply { component = ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity") })
                intents.add(Intent().apply { component = ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.floatwindow.FloatWindowListActivity") })
                intents.add(Intent().apply { component = ComponentName("com.coloros.securitypermission", "com.coloros.securitypermission.permission.PermissionAppListActivity") })
                intents.add(Intent().apply { component = ComponentName("com.oppo.safe", "com.oppo.safe.permission.PermissionAppListActivity") })
                
                // Vivo Funtouch OS
                intents.add(Intent().apply { component = ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity") })
                intents.add(Intent().apply { component = ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager") })
                
                // Common Overlay Permission
                intents.add(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri()))

                var success = false
                for (intent in intents) {
                    try {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        success = true
                        break
                    } catch (_: Exception) { continue }
                }

                if (!success) {
                    val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = "package:${context.packageName}".toUri()
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallback)
                }
            }
            else -> {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri()).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try { context.startActivity(intent) } catch (_: Exception) {
                    val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = "package:${context.packageName}".toUri()
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallback)
                }
            }
        }
    }

    /**
     * Cek apakah izin "Tampilkan jendela pop-up saat berjalan di latar belakang" aktif (MIUI).
     */
    fun isMiuiPopupPermissionGranted(context: Context): Boolean {
        if (!isXiaomi()) return true
        return checkOp(context, 10021)
    }

    /**
     * Cek apakah izin "Tampilkan di Kunci Layar" aktif (MIUI).
     */
    fun isMiuiShowOnLockScreenGranted(context: Context): Boolean {
        if (!isXiaomi()) return true
        return checkOp(context, 10020)
    }

    private fun checkOp(context: Context, op: Int): Boolean {
        return try {
            val ops = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            val method = ops.javaClass.getMethod("checkOpNoThrow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java)
            val result = method.invoke(ops, op, android.os.Process.myUid(), context.packageName) as Int
            result == android.app.AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            true
        }
    }

    /**
     * Cek apakah semua izin khusus OEM sudah diberikan.
     */
    fun isAllOemPermissionsGranted(context: Context): Boolean {
        if (isXiaomi()) {
            return isMiuiPopupPermissionGranted(context) && isMiuiShowOnLockScreenGranted(context)
        }
        if (isOppoRealme()) {
            return checkOp(context, 10021) 
        }
        return canUseFullScreenIntent(context)
    }

    fun setOemPermissionManualDone(context: Context) {
        val sp = context.getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE)
        sp.edit {putBoolean("oem_perm_manual_done", true)}
    }

    fun isOemPermissionManualDone(context: Context): Boolean {
        val sp = context.getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE)
        return sp.getBoolean("oem_perm_manual_done", false)
    }

    fun canUseFullScreenIntent(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            return nm.canUseFullScreenIntent()
        }
        return true
    }

    fun openFullScreenIntentSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                data = "package:${context.packageName}".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try { context.startActivity(intent) } catch (_: Exception) {}
        }
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            return am.canScheduleExactAlarms()
        }
        return true
    }

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = "package:${context.packageName}".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try { context.startActivity(intent) } catch (_: Exception) {}
        }
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val componentName = context.packageName + "/" + UngkerAccessibilityService::class.java.name
        return enabledServices.contains(componentName)
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try { context.startActivity(intent) } catch (_: Exception) {}
    }

    @SuppressLint("WrongConstant")
    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
