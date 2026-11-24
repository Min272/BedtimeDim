package com.example.bedtimedim

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowManager

class DimService : Service() {
    private var overlay: View? = null
    private var params: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(1, buildNotification())
        showOverlay()
        handler.postDelayed({ stopSelf() }, 3 * 60 * 60 * 1000L)
        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("dim", "Nhắc đi ngủ", NotificationManager.IMPORTANCE_LOW)
        )
        val stopPi = PendingIntent.getService(
            this, 0, Intent(this, DimService::class.java).setAction("STOP"),
            PendingIntent.FLAG_IMMUTABLE
        )
        val openPi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val action = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
            "Tắt làm tối", stopPi
        ).build()
        return Notification.Builder(this, "dim")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Đến giờ đi ngủ rồi 😴")
            .setContentText("Màn hình đang tối dần. Đặt điện thoại xuống thôi!")
            .setContentIntent(openPi)
            .addAction(action)
            .setOngoing(true)
            .build()
    }

    private fun showOverlay() {
        if (overlay != null || !Settings.canDrawOverlays(this)) return
        val wm = getSystemService(WindowManager::class.java)
        val v = View(this).apply { setBackgroundColor(Color.BLACK) }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.alpha = 0f
        wm.addView(v, lp)
        overlay = v
        params = lp

        val target = (Prefs.get(this).getInt("dim", 60).coerceIn(20, 80)) / 100f
        val ramp = object : Runnable {
            override fun run() {
                val l = params ?: return
                l.alpha = minOf(target, l.alpha + target / 60f)
                overlay?.let { wm.updateViewLayout(it, l) }
                if (l.alpha < target) handler.postDelayed(this, 10_000L)
            }
        }
        handler.post(ramp)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        overlay?.let { getSystemService(WindowManager::class.java).removeView(it) }
        overlay = null
        super.onDestroy()
    }
}
