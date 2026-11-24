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
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.WindowManager

class DimService : Service() {

    private var overlay: View? = null
    private var params: WindowManager.LayoutParams? = null

    private val handler = Handler(Looper.getMainLooper())

    private val stopRunnable = Runnable {
        stopServiceNow()
    }

    private var rampRunnable: Runnable? = null
    private var rampStartTime = 0L

    companion object {
        private const val CHANNEL_ID = "dim"
        private const val NOTIFICATION_ID = 1

        private const val DIM_DURATION = 180_000L // 3 phút
        private const val AUTO_STOP_DURATION = 3 * 60 * 60 * 1000L // 3 giờ
        private const val RAMP_INTERVAL = 3_000L
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (intent?.action == "STOP") {
            stopServiceNow()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())

        // Hủy bộ đếm tự tắt cũ trước khi tạo bộ đếm mới
        handler.removeCallbacks(stopRunnable)
        handler.postDelayed(stopRunnable, AUTO_STOP_DURATION)

        val preview = intent?.getBooleanExtra("preview", false) == true

        showOverlay(preview)

        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {

        val nm = getSystemService(NotificationManager::class.java)

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Nhắc đi ngủ",
                NotificationManager.IMPORTANCE_LOW
            )
        )

        val stopPi = PendingIntent.getService(
            this,
            0,
            Intent(this, DimService::class.java).setAction("STOP"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openPi = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val action = Notification.Action.Builder(
            Icon.createWithResource(
                this,
                android.R.drawable.ic_menu_close_clear_cancel
            ),
            "Tắt làm tối",
            stopPi
        ).build()

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Đến giờ đi ngủ rồi 😴")
            .setContentText("Màn hình đang tối dần. Đặt điện thoại xuống thôi!")
            .setContentIntent(openPi)
            .addAction(action)
            .setOngoing(true)
            .build()
    }

    private fun showOverlay(instant: Boolean) {

        if (!Settings.canDrawOverlays(this)) {
            stopServiceNow()
            return
        }

        if (instant) {
            removeOverlay()
        }

        if (overlay != null) return

        val wm = getSystemService(WindowManager::class.java)

        val view = View(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        val target = (
            Prefs.get(this)
                .getInt("dim", 70)
                .coerceIn(20, 90)
            ) / 100f

        layoutParams.alpha = if (instant) target else 0f

        try {
            wm.addView(view, layoutParams)

            overlay = view
            params = layoutParams

        } catch (e: Exception) {
            stopServiceNow()
            return
        }

        if (instant) return

        // Bắt đầu tăng độ tối trong đúng 3 phút
        rampStartTime = SystemClock.uptimeMillis()

        val ramp = object : Runnable {

            override fun run() {

                val lp = params ?: return
                val currentView = overlay ?: return

                val elapsed = SystemClock.uptimeMillis() - rampStartTime

                val progress = (
                    elapsed.toFloat() / DIM_DURATION
                ).coerceIn(0f, 1f)

                lp.alpha = target * progress

                try {
                    wm.updateViewLayout(currentView, lp)
                } catch (e: Exception) {
                    removeOverlay()
                    return
                }

                if (progress < 1f) {
                    handler.postDelayed(this, RAMP_INTERVAL)
                }
            }
        }

        rampRunnable = ramp

        // Chờ 3 giây rồi mới tăng độ tối lần đầu
        handler.postDelayed(ramp, RAMP_INTERVAL)
    }

    private fun removeOverlay() {

        rampRunnable?.let {
            handler.removeCallbacks(it)
        }

        rampRunnable = null

        val currentView = overlay

        if (currentView != null) {
            try {
                getSystemService(WindowManager::class.java)
                    .removeView(currentView)
            } catch (e: Exception) {
                // View đã bị gỡ hoặc không còn tồn tại
            }
        }

        overlay = null
        params = null
    }

    private fun stopServiceNow() {

        handler.removeCallbacks(stopRunnable)

        removeOverlay()

        stopForeground(STOP_FOREGROUND_REMOVE)

        stopSelf()
    }

    override fun onDestroy() {

        handler.removeCallbacksAndMessages(null)

        removeOverlay()

        stopForeground(STOP_FOREGROUND_REMOVE)

        super.onDestroy()
    }
}
