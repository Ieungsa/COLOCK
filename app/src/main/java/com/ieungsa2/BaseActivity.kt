package com.ieungsa2

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity

open class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val sharedPref = newBase.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val progress = sharedPref.getInt("font_scale_progress", 0)
        
        val fontScale = when (progress) {
            1 -> 1.2f  // 크게
            2 -> 1.5f  // 아주 크게
            else -> 1.0f // 보통
        }

        val configuration = Configuration(newBase.resources.configuration)
        configuration.fontScale = fontScale
        
        val context = newBase.createConfigurationContext(configuration)
        super.attachBaseContext(context)
    }
}
