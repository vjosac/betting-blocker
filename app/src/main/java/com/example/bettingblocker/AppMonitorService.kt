package com.example.bettingblocker

import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log

class AppMonitorService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val checkInterval = 1000L
    private val targetApps = listOf("com.android.settings", "com.yellpayment.yell")
    private var lastBlockedTime = 0L
    private val blockCooldown = 5000L
    private var lastForegroundApp: String? = null

    private val checkRunnable = object : Runnable {
        override fun run() {
            checkCurrentApp()
            handler.postDelayed(this, checkInterval)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("isMonitoring", false)) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startMonitoring() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("isMonitoring", false)) {
            handler.post(checkRunnable)
        }
    }

    private fun getCurrentForegroundPackage(): String? {
        val usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val beginTime = endTime - 1000 * 60 // Look back 1 minute

        val usageEvents = usageStatsManager.queryEvents(beginTime, endTime)
        val event = UsageEvents.Event()
        var lastPackageName: String? = null

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastPackageName = event.packageName
            }
        }

        return lastPackageName
    }

    private fun checkCurrentApp() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBlockedTime < blockCooldown) {
            return
        }

        val currentForegroundPackage = getCurrentForegroundPackage()
        
        if (currentForegroundPackage != null &&
            currentForegroundPackage != lastForegroundApp && 
            targetApps.contains(currentForegroundPackage)) {
            
            Log.d("AppMonitor", "Blocking app: $currentForegroundPackage")
            val blockingIntent = Intent(this, BlockingActivity::class.java)
            blockingIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(blockingIntent)
            lastBlockedTime = currentTime
        }
        
        lastForegroundApp = currentForegroundPackage
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(checkRunnable)
    }
} 