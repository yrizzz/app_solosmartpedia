package com.solosmartpedia.app

import android.content.Intent
import android.os.Bundle
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.TranslateAnimation
import androidx.appcompat.app.AppCompatActivity
import com.solosmartpedia.app.databinding.ActivityWelcomeBinding

class WelcomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWelcomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        startEntranceAnimations()
        setupClickListeners()
    }

    private fun startEntranceAnimations() {
        val logoFade = AlphaAnimation(0f, 1f).apply {
            duration = 700
            fillAfter = true
        }
        binding.ivLogo.startAnimation(logoFade)

        val titleAnim = AnimationSet(true).apply {
            addAnimation(AlphaAnimation(0f, 1f).apply { duration = 700 })
            addAnimation(TranslateAnimation(0f, 0f, 60f, 0f).apply { duration = 700 })
            startOffset = 200
            fillAfter = true
        }
        binding.tvWelcomeTitle.startAnimation(titleAnim)
        binding.tvWelcomeSubtitle.startAnimation(titleAnim)

        val buttonAnim = AnimationSet(true).apply {
            addAnimation(AlphaAnimation(0f, 1f).apply { duration = 600 })
            addAnimation(TranslateAnimation(0f, 0f, 80f, 0f).apply { duration = 600 })
            startOffset = 500
            fillAfter = true
        }
        binding.btnMasuk.startAnimation(buttonAnim)
    }

    private fun setupClickListeners() {
        binding.btnMasuk.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        }
    }
}
