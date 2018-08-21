package com.orionoscode.orionaudio.view

import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import com.orionoscode.orionaudio.R

class SplashScreen : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)
        supportActionBar?.hide()
        Handler().postDelayed({
            kotlin.run {
                startActivity(Intent(this@SplashScreen, HomeActivity::class.java))
                finish()
            }
        }, 3000)
    }
}
