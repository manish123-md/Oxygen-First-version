package com.oxygen.assistant

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.animation.doOnEnd

/**
 * iPhone Dynamic Island jaisa ek chhota floating overlay jo screen ke top-center
 * mein dikhta hai jab bhi Oxygen active hota hai. Har waqt on-screen nahi rehta -
 * sirf listening/thinking ke time dikhta hai, phir shrink hoke gayab ho jaata hai.
 *
 * Use karne ke liye MainActivity mein overlay permission (Settings.canDrawOverlays)
 * grant hona zaroori hai.
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var islandView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.getStringExtra(EXTRA_ACTION) ?: ACTION_HIDE
        when (action) {
            ACTION_SHOW_LISTENING -> showIsland(listening = true)
            ACTION_SHOW_THINKING -> showIsland(listening = false)
            ACTION_HIDE -> hideIsland()
        }
        return START_NOT_STICKY
    }

    @SuppressLint("InflateParams")
    private fun showIsland(listening: Boolean) {
        if (!Settings.canDrawOverlays(this)) return

        if (islandView == null) {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            islandView = LayoutInflater.from(this).inflate(R.layout.overlay_island, null)

            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = 24 // status bar ke thoda neeche
            }
            windowManager.addView(islandView, params)
            animateIn(islandView!!)
        }

        val label = islandView?.findViewById<TextView>(R.id.tvIslandLabel)
        val logo = islandView?.findViewById<ImageView>(R.id.ivLogo)
        label?.text = if (listening) "Oxygen sun rahi hai..." else "Soch rahi hoon..."
        pulseLogo(logo)
    }

    private fun hideIsland() {
        val view = islandView ?: return
        val shrink = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0f)
        val shrinkY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0f)
        shrink.duration = 220
        shrinkY.duration = 220
        shrink.start()
        shrinkY.start()
        shrink.doOnEnd {
            windowManager.removeView(view)
            islandView = null
        }
    }

    private fun animateIn(view: View) {
        view.scaleX = 0f
        view.scaleY = 0f
        view.animate().scaleX(1f).scaleY(1f).setDuration(220).start()
    }

    /** Logo ko halka sa dhadakta hua (pulse) animation - "AI soch rahi hai" feel dene ke liye. */
    private fun pulseLogo(logo: ImageView?) {
        logo ?: return
        val animator = ValueAnimator.ofFloat(1f, 1.25f, 1f)
        animator.duration = 900
        animator.repeatCount = ValueAnimator.INFINITE
        animator.addUpdateListener {
            val scale = it.animatedValue as Float
            logo.scaleX = scale
            logo.scaleY = scale
        }
        animator.start()
    }

    override fun onDestroy() {
        islandView?.let { runCatching { windowManager.removeView(it) } }
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_ACTION = "action"
        private const val ACTION_SHOW_LISTENING = "show_listening"
        private const val ACTION_SHOW_THINKING = "show_thinking"
        private const val ACTION_HIDE = "hide"

        fun showListening(context: Context) {
            context.startService(Intent(context, OverlayService::class.java)
                .putExtra(EXTRA_ACTION, ACTION_SHOW_LISTENING))
        }

        fun showThinking(context: Context) {
            context.startService(Intent(context, OverlayService::class.java)
                .putExtra(EXTRA_ACTION, ACTION_SHOW_THINKING))
        }

        fun hide(context: Context) {
            context.startService(Intent(context, OverlayService::class.java)
                .putExtra(EXTRA_ACTION, ACTION_HIDE))
        }
    }
}
