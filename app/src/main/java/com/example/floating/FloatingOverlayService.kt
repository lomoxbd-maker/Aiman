package com.example.floating

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.MAXApplication
import com.example.MainActivity
import com.example.R

/**
 * FloatingOverlayService — Optional Android 8.0+ (API 26+) system overlay service displaying
 * the compact "MAX" bubble on screen ONLY when explicitly activated by the user with overlay permission.
 *
 * Strictly zero background recording or camera usage; tapping restores or triggers MAX voice safely.
 */
class FloatingOverlayService : Service() {

  private var windowManager: WindowManager? = null
  private var bubbleView: LinearLayout? = null

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    startForegroundSafely()
    attachOverlayBubbleSafely()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_CLOSE_OVERLAY) {
      stopSelf()
      return START_NOT_STICKY
    }
    return START_NOT_STICKY
  }

  override fun onDestroy() {
    detachOverlayBubbleSafely()
    super.onDestroy()
  }

  private fun startForegroundSafely() {
    runCatching {
      val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null) {
        val channel =
          NotificationChannel(
            CHANNEL_ID,
            "MAX Floating Assistant",
            NotificationManager.IMPORTANCE_LOW,
          ).apply {
            description = "Quick access bubble while MAX floating assistant is active"
            setShowBadge(false)
          }
        manager.createNotificationChannel(channel)
      }

      val openIntent =
        PendingIntent.getActivity(
          this,
          101,
          Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
          PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

      val notification: Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
          .setSmallIcon(R.mipmap.ic_launcher)
          .setContentTitle("MAX Floating Bubble")
          .setContentText("Tap to restore MAX • Tap Mic inside MAX when ready")
          .setOngoing(true)
          .setSilent(true)
          .setContentIntent(openIntent)
          .build()

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        startForeground(
          NOTIFICATION_ID,
          notification,
          ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
      } else {
        startForeground(NOTIFICATION_ID, notification)
      }
    }
  }

  private fun attachOverlayBubbleSafely() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
      stopSelf()
      return
    }
    runCatching {
      val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
      windowManager = wm

      val layoutFlag =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
          @Suppress("DEPRECATION")
          WindowManager.LayoutParams.TYPE_PHONE
        }

      val params =
        WindowManager.LayoutParams(
          WindowManager.LayoutParams.WRAP_CONTENT,
          WindowManager.LayoutParams.WRAP_CONTENT,
          layoutFlag,
          WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
          PixelFormat.TRANSLUCENT,
        ).apply {
          gravity = Gravity.TOP or Gravity.END
          x = 24
          y = 260
        }

      val container =
        LinearLayout(this).apply {
          orientation = LinearLayout.HORIZONTAL
          gravity = Gravity.CENTER_VERTICAL
          setPadding(24, 16, 20, 16)
          background =
            GradientDrawable(
              GradientDrawable.Orientation.LEFT_RIGHT,
              intArrayOf(0xFF2563EB.toInt(), 0xFF7C3AED.toInt()),
            ).apply {
              cornerRadius = 64f
            }
        }

      val label =
        TextView(this).apply {
          text = "MAX"
          setTextColor(0xFFFFFFFF.toInt())
          textSize = 14f
          setPadding(8, 0, 14, 0)
          setOnClickListener {
            (application as? MAXApplication)?.maxCore?.activateManualVoiceSession()
            startActivity(
              Intent(this@FloatingOverlayService, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
          }
        }

      val closeBtn =
        ImageButton(this).apply {
          setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
          setBackgroundColor(0x00000000)
          contentDescription = "Close floating MAX bubble"
          setOnClickListener {
            (application as? MAXApplication)?.maxCore?.floatingAssistantManager?.closeFloatingAssistant()
            stopSelf()
          }
        }

      container.addView(label)
      container.addView(closeBtn)
      bubbleView = container
      wm.addView(container, params)
    }
  }

  private fun detachOverlayBubbleSafely() {
    runCatching {
      bubbleView?.let { view -> windowManager?.removeView(view) }
    }
    bubbleView = null
    windowManager = null
  }

  companion object {
    private const val CHANNEL_ID = "max_floating_assistant_channel"
    private const val NOTIFICATION_ID = 4102
    const val ACTION_CLOSE_OVERLAY = "com.example.floating.CLOSE_OVERLAY"
  }
}
