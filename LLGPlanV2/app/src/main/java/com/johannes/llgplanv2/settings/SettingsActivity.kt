package com.johannes.llgplanv2.settings

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.johannes.llgplanv2.R

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.SettingsTheme)
        setContentView(R.layout.activity_settings)

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.content_preference, SettingsFragment())
            .commit()
    }
}