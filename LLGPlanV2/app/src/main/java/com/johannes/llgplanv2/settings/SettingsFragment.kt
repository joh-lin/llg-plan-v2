package com.johannes.llgplanv2.settings

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.johannes.llgplanv2.BuildConfig
import com.johannes.llgplanv2.R

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        val versionPreference: Preference? = findPreference("version")
        versionPreference?.summary = "${BuildConfig.VERSION_NAME} - (${BuildConfig.VERSION_CODE})"

        val dsb_user_preference: Preference? = findPreference("dsb_login_user")
        val dsb_password_preference: Preference? = findPreference("dsb_login_password")
        val slp_user_preference: Preference? = findPreference("slp_login_user")
        val slp_password_preference: Preference? = findPreference("slp_login_password")

        dsb_user_preference?.summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
        dsb_password_preference?.summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
        slp_user_preference?.summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
        slp_password_preference?.summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
    }
}