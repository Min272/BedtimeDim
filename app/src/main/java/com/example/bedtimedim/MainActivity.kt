package com.example.bedtimedim

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val p = Prefs.get(this)
        val pad = (20 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        fun label(t: String) = TextView(this).apply {
            text = t
            textSize = 16f
            setPadding(0, pad / 2, 0, 0)
        }

        root.addView(label("Giờ đi ngủ"))

        val tp = TimePicker(this).apply {
            setIs24HourView(true)
            hour = p.getInt("h", 23)
            minute = p.getInt("m", 0)
        }
        root.addView(tp)

        val dimLabel = label("")
        val sb = SeekBar(this).apply {
            max = 70
            progress = (p.getInt("dim", 70) - 20).coerceIn(0, 70)
        }

        fun refresh() {
            val dim = sb.progress + 20
            dimLabel.text = "Độ tối tối đa: ${dim}%" +
                if (dim > 80) " (trên 80% có thể không chạm được màn hình)" else ""
        }

        refresh()

        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, v: Int, fromUser: Boolean) {
                refresh()
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        root.addView(dimLabel)
        root.addView(sb)

        val sw = Switch(this).apply {
            text = "Bật nhắc ngủ mỗi ngày"
            isChecked = p.getBoolean("enabled", false)
        }
        root.addView(sw)

        root.addView(Button(this).apply {
            text = "1) Cấp quyền hiển thị trên ứng dụng khác"
            setOnClickListener {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        })

        root.addView(Button(this).apply {
            text = "2) Lưu & đặt giờ"
            setOnClickListener {
                p.edit()
                    .putInt("h", tp.hour)
                    .putInt("m", tp.minute)
                    .putInt("dim", sb.progress + 20)
                    .putBoolean("enabled", sw.isChecked)
                    .apply()

                if (sw.isChecked && !Settings.canDrawOverlays(this@MainActivity)) {
                    Toast.makeText(
                        this@MainActivity,
                        "Cần cấp quyền ở bước 1 trước",
                        Toast.LENGTH_LONG
                    ).show()
                }

                val t = Scheduler.schedule(this@MainActivity)
                val msg = if (t == null) {
                    "Đã tắt nhắc ngủ"
                } else {
                    "Sẽ làm tối lúc " +
                        SimpleDateFormat("HH:mm dd/MM", Locale.getDefault()).format(Date(t))
                }

                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
            }
        })

        root.addView(Button(this).apply {
            text = "Thử ngay (xem trước)"
            setOnClickListener {
                p.edit().putInt("dim", sb.progress + 20).apply()

                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    Toast.makeText(
                        this@MainActivity,
                        "Chưa có quyền hiển thị trên ứng dụng khác. Hãy bật quyền rồi quay lại.",
                        Toast.LENGTH_LONG
                    ).show()

                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                    )
                    return@setOnClickListener
                }

                val serviceIntent = Intent(
                    this@MainActivity,
                    DimService::class.java
                ).putExtra("preview", true)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            }
        })

        root.addView(Button(this).apply {
            text = "Tắt làm tối"
            setOnClickListener {
                startService(
                    Intent(this@MainActivity, DimService::class.java).setAction("STOP")
                )
            }
        })

        setContentView(ScrollView(this).apply {
            addView(root)
        })
    }
}
