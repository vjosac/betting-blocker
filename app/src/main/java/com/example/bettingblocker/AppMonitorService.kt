package com.example.bettingblocker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat

class AppMonitorService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val checkInterval = 1000L
    private val targetApps = listOf("com.android.settings", "com.yellpayment.yell")
    private var lastBlockedTime = 0L
    private val blockCooldown = 5000L
    private var lastForegroundApp: String? = null
    private val CHANNEL_ID = "AppMonitorChannel"
    private val NOTIFICATION_ID = 1

    private val checkRunnable = object : Runnable {
        override fun run() {
            checkCurrentApp()
            handler.postDelayed(this, checkInterval)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        startMonitoring()
        registerRestartReceiver()
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Monitor Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors and blocks specified applications"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("App Monitor Active")
        .setContentText("Monitoring for blocked applications")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    private fun registerRestartReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BOOT_COMPLETED)
            addAction(Intent.ACTION_MY_PACKAGE_REPLACED)
        }
        registerReceiver(restartReceiver, filter)
    }

    private val restartReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
                intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
                val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                if (prefs.getBoolean("isMonitoring", false)) {
                    val serviceIntent = Intent(context, AppMonitorService::class.java)
                    context?.startService(serviceIntent)
                }
            }
        }
    }

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
        unregisterReceiver(restartReceiver)
    }
} 