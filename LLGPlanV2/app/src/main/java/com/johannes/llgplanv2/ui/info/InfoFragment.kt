package com.johannes.llgplanv2.ui.info

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.widget.addTextChangedListener
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.johannes.llgplanv2.MainActivity
import com.johannes.llgplanv2.R
import com.johannes.llgplanv2.api.CalendarUtils
import com.johannes.llgplanv2.api.Teacher
import com.johannes.llgplanv2.databinding.FragmentInfoBinding
import com.johannes.llgplanv2.ui.teacherlist.TeacherListAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.lang.Exception
import java.lang.NullPointerException
import java.text.ParseException
import java.text.SimpleDateFormat
import kotlin.system.measureTimeMillis

class InfoFragment : Fragment() {

    private var _binding: FragmentInfoBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val dashboardViewModel =
            ViewModelProvider(this).get(InfoViewModel::class.java)

        _binding = FragmentInfoBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Adapter
        val teacherListAdapter = TeacherListAdapter(lifecycleScope)
        binding.teachListRecyclerView.adapter = teacherListAdapter


        // swipe to refresh
        binding.swipeRefreshLayout.setOnRefreshListener {
            lifecycleScope.launch {
                val jobs = mutableListOf<Job>()
                with(MainActivity.instance) {
                    jobs.add(syncTeacherList())
                }
                for (j in jobs) j.join()
                try {
                    binding.swipeRefreshLayout.isRefreshing = false
                } catch (e: NullPointerException) {}

            }

        }

        // search text
        binding.btnClearSearch.setOnClickListener {
            binding.editTextSearch.text.clear()
        }
        binding.editTextSearch.doOnTextChanged { text, start, before, count ->
            teacherListAdapter.setSearchFilter(text.toString())
        }


        // observer teacher list
        MainActivity.viewModel.teacherList.observe(viewLifecycleOwner) { newTeacherList ->
            if (newTeacherList == null) {
                binding.noTeachListFound.visibility = TextView.VISIBLE
                binding.swipeRefreshLayout.visibility = TextView.GONE
            } else {
                binding.noTeachListFound.visibility = TextView.GONE
                binding.swipeRefreshLayout.visibility = TextView.VISIBLE
                teacherListAdapter.setDataSet(newTeacherList.list ?: mutableListOf())
            }
        }

        return root
    }

    override fun onPause() {
        super.onPause()
        binding.swipeRefreshLayout.isRefreshing = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}