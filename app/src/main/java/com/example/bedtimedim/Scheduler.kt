package com.example.bedtimedim

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import java.util.Calendar

object Prefs {
    fun get(c: Context): SharedPreferences =
        c.getSharedPreferences("bedtime", Context.MODE_PRIVATE)
}

object Scheduler {
    private fun alarmPi(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 1, Intent(ctx, AlarmReceiver::class.java).setAction("com.example.bedtimedim.FIRE"),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun schedule(ctx: Context): Long? {
        val p = Prefs.get(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java)
        if (!p.getBoolean("enabled", false)) {
            am.cancel(alarmPi(ctx))
            return null
        }
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, p.getInt("h", 23))
            set(Calendar.MINUTE, p.getInt("m", 0))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        val showPi = PendingIntent.getActivity(
            ctx, 2, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(c.timeInMillis, showPi), alarmPi(ctx))
        return c.timeInMillis
    }
}

class AlarmReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            ctx.startForegroundService(Intent(ctx, DimService::class.java))
        }
        Scheduler.schedule(ctx)
    }
}
