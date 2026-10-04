package com.sbnc.bot

import android.animation.ObjectAnimator
import android.animation.AnimatorSet
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logo = findViewById<ImageView>(R.id.logo)

        val rotate = ObjectAnimator.ofFloat(logo, "rotation", 0f, 360f).apply {
            duration = 2500
            interpolator = AccelerateDecelerateInterpolator()
        }
        val scaleX = ObjectAnimator.ofFloat(logo, "scaleX", 0.5f, 1.2f, 1f).apply { duration = 2000 }
        val scaleY = ObjectAnimator.ofFloat(logo, "scaleY", 0.5f, 1.2f, 1f).apply { duration = 2000 }
        val alpha = ObjectAnimator.ofFloat(logo, "alpha", 0f, 1f).apply { duration = 1500 }

        AnimatorSet().apply {
            playTogether(rotate, scaleX, scaleY, alpha)
            start()
        }

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 3000)
    }
}
