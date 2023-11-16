package com.johannes.llgplanv2.ui.login

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.johannes.llgplanv2.R
import com.johannes.llgplanv2.settings.PrefKeys

class LoginActivity : AppCompatActivity() {

    private var slpLoginDone = false
    private var dsbLoginDone = false
    lateinit var sharedPref: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_LLGPlanV2)
        setContentView(R.layout.login_activity)

        sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        slpLoginDone = sharedPref.getBoolean(PrefKeys.slpLoginDone, false)
        dsbLoginDone = sharedPref.getBoolean(PrefKeys.dsbLoginDone, false)

        if (savedInstanceState == null) {
            val firstFragment = if (slpLoginDone) {
                DsbLoginFragment.newInstance(this)
            } else {
                SlpLoginFragment.newInstance(this)
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, firstFragment)
                .commitNow()
        }
    }

    fun nextFragment() {
        slpLoginDone = sharedPref.getBoolean(PrefKeys.slpLoginDone, false)
        dsbLoginDone = sharedPref.getBoolean(PrefKeys.dsbLoginDone, false)

        if (!slpLoginDone) {
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(
                    R.anim.login_fragment_slide_in,
                    R.anim.login_fragment_slide_out
                )
                .replace(R.id.container, SlpLoginFragment.newInstance(this))
                .commit()
        } else if (!dsbLoginDone) {
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(
                    R.anim.login_fragment_slide_in,
                    R.anim.login_fragment_slide_out
                )
                .replace(R.id.container, DsbLoginFragment.newInstance(this))
                .commit()
        } else {
            finish()
        }
    }

    override fun onBackPressed() {
        // This is to disable ability
        // to just go back and use the app
    }
}