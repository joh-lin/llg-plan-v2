package com.johannes.llgplanv2.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.johannes.llgplanv2.BuildConfig
import com.johannes.llgplanv2.ConstValues
import com.johannes.llgplanv2.MainActivity
import com.johannes.llgplanv2.R
import com.johannes.llgplanv2.api.*
import com.johannes.llgplanv2.databinding.FragmentHomeBinding
import com.johannes.llgplanv2.databinding.PopupStudentEditorBinding
import com.johannes.llgplanv2.settings.PrefKeys
import com.johannes.llgplanv2.settings.SettingsActivity
import com.johannes.llgplanv2.ui.InformationCardView
import com.johannes.llgplanv2.ui.eventlist.EventListAdapter
import com.johannes.llgplanv2.ui.quickplan.QuickPlanViewPager2Adapter
import com.johannes.llgplanv2.ui.studenteditor.StudentListAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    lateinit var quickPlanAdapter: QuickPlanViewPager2Adapter
    lateinit var eventListAdapter: EventListAdapter

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // BETA WARNING
        //binding.infoCardViews.setEnabled(InformationCardView.Type.BETA_WARNING, true)

        // OUTDATED WARNING
        lifecycleScope.launch(Dispatchers.IO) {
            MainActivity.instance.tryNetworkRequest {
                val response = Jsoup.connect(ConstValues.VERSION_CODE_URL)
                    .ignoreContentType(true).execute()
                val versionCode = response.body().toInt()
                val outdated = versionCode > BuildConfig.VERSION_CODE
                withContext(Dispatchers.Main) {
                    binding.infoCardViews.setEnabled(
                        InformationCardView.Type.APP_VERSION_OUTDATED, outdated)
                }
            }
        }
        binding.infoCardViews.addClickEvent(InformationCardView.Type.APP_VERSION_OUTDATED) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ConstValues.APP_RELEASE_URL)))
        }

        // Settings Button
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(context, SettingsActivity::class.java))
        }

        // QuickPlan
        quickPlanAdapter = QuickPlanViewPager2Adapter(binding.quickPlanViewPager2, 5)
        binding.quickPlanViewPager2.adapter = quickPlanAdapter

        /*
        val drawCanceled = ContextCompat.getDrawable(MainActivity.instance,
            R.drawable.draw_plan_canceled) ?: throw Exception("Failed to load drawable")
        val drawChanged = ContextCompat.getDrawable(MainActivity.instance,
            R.drawable.draw_plan_change) ?: throw Exception("Failed to load drawable")
        binding.textViewChanged.background = TileDrawable(drawChanged)
        binding.textViewCanceled.background = TileDrawable(drawCanceled)*/

        // Event List
        eventListAdapter = EventListAdapter(mutableListOf(), mutableListOf())
        binding.eventListRecyclerView.adapter = eventListAdapter

        // Timetable View
        binding.timetableView.week = CalendarUtils.getInstance()
            .get(Calendar.WEEK_OF_YEAR).mod(2)
        binding.timetableView.setOnClickListener {
            binding.timetableView.toggleWeek()
            binding.timetableView.updateView()
        }

        // POPUP
        binding.btnStudentSelect.setOnClickListener {
            showStudentEditorPopup()
        }

        // On Refresh
        binding.swipeRefreshLayout.setOnRefreshListener {
            onRefresh()
        }

        // Student Change
        MainActivity.viewModel.activeStudent.observe(viewLifecycleOwner) { newStudent ->
            activeStudentChanged(newStudent)
            eventListChanged(MainActivity.viewModel.eventList.value)
        }

        // Substitution Plan
        MainActivity.viewModel.substitutionPlan.observe(viewLifecycleOwner) { substitutionPlan ->
            substitutionPlanChanged(substitutionPlan)
        }

        // Event List
        MainActivity.viewModel.eventList.observe(viewLifecycleOwner) { eventList ->
            eventListChanged(eventList)
        }

        return root
    }


    private fun eventListChanged(eventList: EventList?) {
        // EVENT PLAN
        val activeStudent = MainActivity.viewModel.activeStudent.value
        binding.textViewEventList.text = resources.getString(R.string.events) + " - " + activeStudent?.gradeLevel
        val filteredList = mutableListOf<Event>()
        eventList?.list?.let{
            for (event in it) {
                if (filteredList.size < resources.getInteger(R.integer.event_list_max_item_amount)
                    && event.gradeLevel == activeStudent?.gradeLevel) {
                        filteredList.add(event)
                }
            }
        }
        eventListAdapter.updateDataSet(filteredList, activeStudent?.timetable?.getAllLessons()
            ?: mutableListOf())

        // INFORMATION CARDS
        val lastUpdated = try {
            eventList?.let { CalendarUtils.stringToCalendar(it.lastUpdated) }
        } catch (e: ParseException) { null }
        binding.infoCardViews.setEnabled(InformationCardView.Type.EVENT_LIST_OUTDATED,
            (lastUpdated == null || CalendarUtils.calendarOlderThanMinutes(lastUpdated,
                MainActivity.eventListSyncCooldown)))

        // text at bottom for lastupdated
        try {
            eventList?.let {
                binding.eventListLastUpdatedTextView.text =
                    resources.getString(R.string.events) + ": " +
                            try {SimpleDateFormat("dd.MM.yyyy HH:mm")
                                .format(CalendarUtils.stringToDate(eventList.lastUpdated))}
                            catch (e: Exception) {"ERROR"}
            }
        } catch (e: ParseException) {
            binding.eventListLastUpdatedTextView.text = ""
        }
    }

    private fun substitutionPlanChanged(substitutionPlan: SubstitutionPlan?) {
        quickPlanAdapter.invalidate()

        // INFORMATION CARDS
        val lastUpdated = try {
            substitutionPlan?.let { CalendarUtils.stringToCalendar(it.lastUpdated) }
        } catch (e: ParseException) { null }
        binding.infoCardViews.setEnabled(InformationCardView.Type.SUBSTITUTION_OUTDATED,
            (lastUpdated == null || CalendarUtils.calendarOlderThanMinutes(lastUpdated,
                MainActivity.substitutionPlanSyncCooldown)))


        // text at bottom for lastupdated
        try {
            substitutionPlan?.let {
                binding.substitutionLastUpdatedTextView.text =
                    resources.getString(R.string.substitution_plan) + ": " +
                            try {SimpleDateFormat("dd.MM.yyyy HH:mm")
                                .format(CalendarUtils.stringToDate(substitutionPlan.lastUpdated))}
                            catch (e: Exception) {"ERROR"}
            }
        } catch (e: ParseException) {
            binding.substitutionLastUpdatedTextView.text = ""
        }
    }

    private fun activeStudentChanged(newStudent: Student?) {
        // QuickPlan View
        quickPlanAdapter.dayOffset = 0
        binding.btnStudentSelect.text = newStudent?.fullName
            ?: resources.getString(R.string.select_student)
        quickPlanAdapter.changeStudent(newStudent)

        // INFORMATION CARDS
        val lastUpdated = try {
            newStudent?.timetable?.lastUpdated?.let { CalendarUtils.stringToCalendar(it) }
        } catch (e: ParseException) { null }
        binding.infoCardViews.setEnabled(InformationCardView.Type.TIMETABLE_OUTDATED,
            (lastUpdated == null || CalendarUtils.calendarOlderThanMinutes(lastUpdated,
                MainActivity.timetableSyncCooldown)))

        // Timetable View
        binding.timetableView.timetable = newStudent?.timetable
        binding.timetableView.updateView()

        // Main Page Content show / hide
        if (newStudent == null) {
            binding.noStudentSelectedTextView.visibility = TextView.VISIBLE
            binding.swipeRefreshLayout.visibility = TextView.GONE
        } else {
            binding.noStudentSelectedTextView.visibility = TextView.GONE
            binding.swipeRefreshLayout.visibility = TextView.VISIBLE
        }

        // text at bottom for lastupdated
        newStudent?.let{
            try {
                binding.timetableLastUpdatedTextView.text =
                    it.timetable?.let { timetable ->
                        resources.getString(R.string.timetable) + ": " +
                                try {SimpleDateFormat("dd.MM.yyyy HH:mm")
                                    .format(CalendarUtils.stringToDate(timetable.lastUpdated))}
                                catch (e: Exception) {"ERROR"}
                    } ?: resources.getString(R.string.no_timetable_found)
            } catch (e: ParseException) {
                binding.timetableLastUpdatedTextView.text = ""
            }
        }
    }

    private fun onRefresh() {
        lifecycleScope.launch {
            val jobs = mutableListOf<Job>()
            with(MainActivity.instance) {
                jobs.add(syncActiveStudentTimetable())
                jobs.add(syncSubstitutionPlan())
                jobs.add(syncEventList())
            }
            for (j in jobs) j.join()
            try {
                binding.swipeRefreshLayout.isRefreshing = false
            } catch (e: NullPointerException) {
                e.printStackTrace()
            }
        }
    }

    private fun showStudentEditorPopup() {

        // Student Editor Popup Window
        val popupBinding = PopupStudentEditorBinding.inflate(layoutInflater)
        val popupWindow = PopupWindow(popupBinding.root,
            binding.constraintLayoutTopButton.width,
            LinearLayout.LayoutParams.WRAP_CONTENT,true)

        fun showErrorDialog(errorMessage: String) {
            popupBinding.errorTextView.text = errorMessage
            popupBinding.errorTextView.visibility = View.VISIBLE
        }
        fun hideErrorDialog() {
            popupBinding.errorTextView.visibility = View.GONE
        }

        // Modify Layout
        popupBinding.studentListRecyclerView.layoutManager = LinearLayoutManager(context)
        val studentListAdapter = StudentListAdapter(DataManager.studentProfiles, popupWindow)
        popupBinding.studentListRecyclerView.adapter = studentListAdapter
        popupBinding.editTextLastname.setOnEditorActionListener { v, actionId, event ->
            return@setOnEditorActionListener when (actionId) {
                EditorInfo.IME_ACTION_SEARCH -> {
                    popupBinding.buttonCheckStudent.callOnClick()
                    false
                }
                else -> false
            }
        }

        popupBinding.buttonCheckStudent.setOnClickListener {
            // hide the touch keyboard if opened
            val imm = MainActivity.instance.getSystemService(Context.INPUT_METHOD_SERVICE)
                    as? InputMethodManager
            imm?.hideSoftInputFromWindow(popupBinding.root.rootView.windowToken, 0)

            // hide error message
            hideErrorDialog()

            val searchText = popupBinding.editTextLastname.text.toString().trim().lowercase()

            // SECRETS
            if (PrefKeys.secretActivationStrings.contains(searchText)) {
                with(MainActivity.sharedPref.edit()) {
                    putBoolean(PrefKeys.secretsEnabled, true)
                    apply()
                }
                Toast.makeText(context,
                    resources.getString(R.string.secrets_activated),
                    Toast.LENGTH_LONG).show()
                popupWindow.dismiss()
            } else if (PrefKeys.secretDeactivationStrings.contains(searchText)) {
                with(MainActivity.sharedPref.edit()) {
                    putBoolean(PrefKeys.secretsEnabled, false)
                    apply()
                }
                Toast.makeText(context,
                    resources.getString(R.string.secrets_deactivated),
                    Toast.LENGTH_LONG).show()
                popupWindow.dismiss()
            }

            // ACTUAL SEARCH
            else if (searchText.length < 2) {
                showErrorDialog(resources.getString(R.string.input_to_short))
            } else if (searchText.contains(Regex("[^abcdefghijklmnopqrstuvwxyzöäüß ]"))) {
                showErrorDialog(resources.getString(R.string.input_only_letters_allowed))
            } else {
                // search for students
                popupBinding.loadingSpinner.visibility = ProgressBar.VISIBLE
                popupBinding.studentListRecyclerView.visibility = RecyclerView.GONE

                lifecycleScope.launch(Dispatchers.IO) {
                    MainActivity.instance.tryNetworkRequest(
                        { // try
                            val syncResponse = Student.syncStudents(searchText)
                            withContext(Dispatchers.Main) {
                                when (syncResponse.state) {
                                    Student.SyncResponse.SUCCESS -> {
                                        syncResponse.data?.let {
                                            studentListAdapter.showingSearchResults = true
                                            studentListAdapter.setDataSet(it)
                                        }
                                    }

                                    Student.SyncResponse.NO_RESULTS ->
                                        showErrorDialog(resources.getString(R.string.student_sync_no_results))

                                    Student.SyncResponse.FAILED ->
                                        showErrorDialog(resources.getString(R.string.student_sync_failed))

                                    Student.SyncResponse.TOO_MANY_RESULTS ->
                                        showErrorDialog(resources.getString(R.string.student_sync_too_many_results))

                                    Student.SyncResponse.SEARCH_TO_SHORT ->
                                        showErrorDialog(resources.getString(R.string.input_to_short))

                                    Student.SyncResponse.NO_RESULTS ->
                                        showErrorDialog(resources.getString(R.string.student_sync_failed))

                                    else -> showErrorDialog(resources.getString(R.string.student_sync_failed))
                                }
                            }
                        },
                        { // finally
                            withContext(Dispatchers.Main) {
                                popupBinding.loadingSpinner.visibility = ProgressBar.GONE
                                popupBinding.studentListRecyclerView.visibility =
                                    RecyclerView.VISIBLE
                            }
                        })}
            }
        }

        // Show Window
        popupWindow.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN

        //popupWindow.animationStyle = R.style.StudentEditorPopupAnimation
        popupWindow.showAsDropDown(binding.constraintLayoutTopButton, 0, 40, Gravity.CENTER_HORIZONTAL)

        // dim background
        val container = popupWindow.contentView.rootView
        val context = popupWindow.contentView.context
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val p = container.layoutParams as WindowManager.LayoutParams
        p.flags = p.flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
        p.dimAmount = 0.7f
        wm.updateViewLayout(container, p)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}