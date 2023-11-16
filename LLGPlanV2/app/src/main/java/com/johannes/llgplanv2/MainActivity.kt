package com.johannes.llgplanv2

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import com.johannes.llgplanv2.api.*
import com.johannes.llgplanv2.databinding.ActivityMainBinding
import com.johannes.llgplanv2.settings.PrefKeys
import com.johannes.llgplanv2.ui.login.LoginActivity
import kotlinx.coroutines.*
import org.jsoup.UncheckedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.ParseException
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    // how frequent the plans need to be updated (minutes)


    companion object {
        lateinit var viewModel: MainViewModel
        lateinit var instance: MainActivity
        lateinit var sharedPref: SharedPreferences

        const val timetableSyncCooldown = 12*60
        const val teacherListSyncCooldown = 24*60
        const val substitutionPlanSyncCooldown = 5
        const val eventListSyncCooldown = 24*60
    }

    init {
        instance = this
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()

        //TODO remove
        runBlocking {
            println(WidgetApiAdapter.getActiveStudentWithTimetable(applicationContext))
            println(WidgetApiAdapter.getEventList(applicationContext))
            println(WidgetApiAdapter.getSubstitutionPlan(applicationContext))
        }


        // TEMPORARY
        sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        //sharedPref = getSharedPreferences(BuildConfig.APPLICATION_ID, Context.MODE_PRIVATE)
        val secretsEnabled = sharedPref.getBoolean(PrefKeys.secretsEnabled, false)

        AppCompatDelegate.setDefaultNightMode(
            if (sharedPref.getBoolean("dark_mode_enabled", true)) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO)

        super.onCreate(savedInstanceState)

        viewModel =
            ViewModelProvider(this).get(MainViewModel::class.java)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setTheme(R.style.Theme_LLGPlanV2)
        setContentView(binding.root)

        // FIRST LAUNCH
        val firstLaunch = sharedPref.getBoolean(PrefKeys.firstLaunch, true)
        sharedPref.edit().putBoolean(PrefKeys.firstLaunch, false).apply()
        if (firstLaunch) {
            // TODO
        }
        val slpLoginDone = sharedPref.getBoolean(PrefKeys.slpLoginDone, false)
        val dsbLoginDone = sharedPref.getBoolean(PrefKeys.dsbLoginDone, false)
        if (!slpLoginDone || !dsbLoginDone) {
            val intent = Intent(applicationContext, LoginActivity::class.java)
            startActivity(intent)
        }


        val navView: BottomNavigationView = binding.navView

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_info
            )
        )
        navView.setupWithNavController(navController)

        // background gradient

        // API Data
        loadAPIData()
    }

    private fun loadAPIData() {
        var lastUpdated: Calendar? = null

        DataManager.loadStudentProfiles(applicationContext)
        setActiveStudent(DataManager.studentProfiles.firstOrNull())

        // Substitution Plan
        val substitutionPlan = SubstitutionPlan()
        if(substitutionPlan.load(applicationContext)) {
            viewModel.substitutionPlan.value = substitutionPlan
            lastUpdated = try {
                CalendarUtils.stringToCalendar(substitutionPlan.lastUpdated)
            } catch (e: ParseException) { null }
        } else { lastUpdated = null }
        if (lastUpdated == null || CalendarUtils.calendarOlderThanMinutes(lastUpdated,
                substitutionPlanSyncCooldown)) {
            syncSubstitutionPlan()
        } else {
            viewModel.substitutionPlan.value = substitutionPlan
        }

        // Teacher List
        val teacherList = TeacherList()
        if(teacherList.load(applicationContext)) {
            viewModel.teacherList.value = teacherList
            lastUpdated = try {
                CalendarUtils.stringToCalendar(teacherList.lastUpdated)
            } catch (e: ParseException) { null }
        } else { lastUpdated = null}
        if (lastUpdated == null || CalendarUtils.calendarOlderThanMinutes(lastUpdated,
                teacherListSyncCooldown)) {
            syncTeacherList()
        } else {
            viewModel.teacherList.value = teacherList
        }

        // Event List
        val eventList = EventList()
        if(eventList.load(applicationContext)) {
            viewModel.eventList.value = eventList
            lastUpdated = try {
                CalendarUtils.stringToCalendar(eventList.lastUpdated)
            } catch (E: ParseException) { null }
        } else { lastUpdated = null }
        if (lastUpdated == null || CalendarUtils.calendarOlderThanMinutes(lastUpdated,
            eventListSyncCooldown)) {
            syncEventList()
        } else {
            viewModel.eventList.value = eventList
        }

    }

    //
    fun syncSubstitutionPlan(): Job {
        return lifecycleScope.launch(Dispatchers.IO) {
            val substitutionPlan = viewModel.substitutionPlan.value ?: SubstitutionPlan()
            tryNetworkRequest {
                substitutionPlan.sync()
                substitutionPlan.save(applicationContext)
                withContext(Dispatchers.Main) {
                    viewModel.substitutionPlan.value = substitutionPlan
                }
            }
        }
    }

    fun syncTeacherList(): Job {
        return lifecycleScope.launch(Dispatchers.IO) {
            val teacherList = viewModel.teacherList.value ?: TeacherList()
            tryNetworkRequest {
                teacherList.sync()
                teacherList.save(applicationContext)
                withContext(Dispatchers.Main) {
                    viewModel.teacherList.value = teacherList
                }
            }
        }
    }

    fun syncEventList(): Job {
        return lifecycleScope.launch(Dispatchers.IO) {
            val eventList = viewModel.eventList.value ?: EventList()
            tryNetworkRequest {
                eventList.sync()
                eventList.save(applicationContext)
                withContext(Dispatchers.Main) {
                    viewModel.eventList.value = eventList
                }
            }
        }
    }

    fun syncActiveStudentTimetable(): Job {
        return lifecycleScope.launch(Dispatchers.IO) {
            val activeStudent = viewModel.activeStudent.value ?: return@launch
            val timetable = activeStudent.timetable ?: return@launch
            tryNetworkRequest {
                timetable.sync()
                timetable.save(applicationContext)
                withContext(Dispatchers.Main) {
                    viewModel.activeStudent.value = activeStudent
                }
            }
        }
    }

    fun syncTimetable(timetable: Timetable): Job {
        return lifecycleScope.launch(Dispatchers.IO) {
            tryNetworkRequest {
                timetable.sync()
                timetable.save(applicationContext)
            }
        }
    }

    fun setActiveStudent(newStudent: Student?) {
        lifecycleScope.launch(Dispatchers.IO) {
            if (newStudent != null) {
                var syncRequired = true
                val timetable = Timetable(newStudent)
                if (timetable.load(applicationContext)) {
                    val lastUpdated =
                        try { CalendarUtils.stringToCalendar(timetable.lastUpdated) }
                        catch(e: ParseException) { null }
                    println("Last Updated: ${lastUpdated?.time?.let { CalendarUtils.dateToString(it) }}")
                    syncRequired = (lastUpdated == null ||
                            CalendarUtils.calendarOlderThanMinutes(lastUpdated,
                                timetableSyncCooldown))
                }
                newStudent.timetable = timetable
                withContext(Dispatchers.Main) {
                    viewModel.activeStudent.value = newStudent
                }
                if (syncRequired) syncTimetable(timetable).join()
            }

            withContext(Dispatchers.Main) {
                viewModel.activeStudent.value = newStudent
            }
        }

    }

    suspend fun tryNetworkRequest(block: suspend () -> Unit) {
        tryNetworkRequest(block) {}
    }

    suspend fun tryNetworkRequest(block: suspend () -> Unit, finally: suspend () -> Unit = {}){
        if (hasInternetConnection()) {
            try {
                block()
            } catch (e: SocketTimeoutException) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    showErrorSnackbar(resources.getString(R.string.network_timeout))
                }
            } catch (e: UncheckedIOException) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    showErrorSnackbar(resources.getString(R.string.network_error))
                }
            } catch (e: UnknownHostException) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    showErrorSnackbar(resources.getString(R.string.network_error))
                }
            } catch (e: javax.net.ssl.SSLHandshakeException) {
                withContext(Dispatchers.Main) {
                    showErrorSnackbar(resources.getString(R.string.network_error))
                }
            } catch (e: java.net.ConnectException) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    showErrorSnackbar(resources.getString(R.string.network_no_connection))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    showErrorSnackbar(resources.getString(R.string.network_unknown_error))
                }
            } finally {
                finally()
            }
        } else {
            withContext(Dispatchers.Main) {
                showErrorSnackbar(resources.getString(R.string.network_no_connection))
            }
            finally()
        }
    }

    fun showErrorSnackbar(message: String) {
        val snackbar = Snackbar.make(
            binding.root,
            message, Snackbar.LENGTH_LONG
        )
        val layout = snackbar.view as Snackbar.SnackbarLayout

        // RED BACKGROUND
        val typedValue = TypedValue()
        theme.resolveAttribute(R.attr.colorPlanCancelled, typedValue, true)
        @ColorInt val c = typedValue.data
        snackbar.setBackgroundTint(c)

        // TEXT STYLING
        val textView = layout.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        textView.textAlignment = TextView.TEXT_ALIGNMENT_CENTER
        textView.textSize = resources.getDimension(R.dimen.textSizeSnackbar)

        // LAYOUT GRAVITY
        val params = snackbar.view.layoutParams as FrameLayout.LayoutParams
        params.gravity = Gravity.TOP
        snackbar.view.layoutParams = params

        snackbar.show()
    }

    override fun onPause() {
        super.onPause()
        DataManager.saveStudentProfiles(applicationContext)
    }

    fun hasInternetConnection(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val netCap = cm.activeNetwork ?: return false
        val actNw = cm.getNetworkCapabilities(netCap) ?: return false
        return when {
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }
}