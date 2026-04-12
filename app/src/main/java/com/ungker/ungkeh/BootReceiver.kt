package com.ungker.ungkeh

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || 
            action == Intent.ACTION_USER_PRESENT || 
            action == "UNGKER_KEEP_ALIVE") {
            
            val isRunning = UngkerService.isServiceRunning(context)
            if (!isRunning) {
                val serviceIntent = Intent(context, UngkerService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
            
            // Re-schedule alarm jika berasal dari keep-alive
            if (action == "UNGKER_KEEP_ALIVE") {
                UngkerService.scheduleKeepAlive(context)
            }
        }
    }
}
