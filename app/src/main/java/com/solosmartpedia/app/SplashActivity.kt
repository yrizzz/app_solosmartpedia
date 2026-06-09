package com.solosmartpedia.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.view.animation.AnimationSet
import androidx.appcompat.app.AppCompatActivity
import com.solosmartpedia.app.databinding.ActivitySplashBinding

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        startAnimations()

        Handler(Looper.getMainLooper()).postDelayed({
            navigateToNext()
        }, SPLASH_DURATION)
    }

    private fun startAnimations() {
        val fadeIn = AlphaAnimation(0f, 1f).apply {
            duration = 800
            fillAfter = true
        }
        val scaleUp = ScaleAnimation(
            0.5f, 1f, 0.5f, 1f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 800
            fillAfter = true
        }
        val logoAnim = AnimationSet(true).apply {
            addAnimation(fadeIn)
            addAnimation(scaleUp)
        }
        binding.ivLogo.startAnimation(logoAnim)

        val textFade = AlphaAnimation(0f, 1f).apply {
            duration = 600
            startOffset = 600
            fillAfter = true
        }
        binding.tvAppName.startAnimation(textFade)
        binding.tvTagline.startAnimation(textFade)
    }

    private fun navigateToNext() {
        val prefs = getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
        val isLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)

        val intent = if (isLoggedIn) {
            Intent(this, MainActivity::class.java)
        } else {
            Intent(this, WelcomeActivity::class.java)
        }
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    companion object {
        private const val SPLASH_DURATION = 2000L
        const val SESSION_PREFS = "solosmartpedia_session"
        const val KEY_IS_LOGGED_IN = "is_logged_in"
    }
}
