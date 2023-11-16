package com.johannes.llgplanv2.ui.teacherlist

import android.content.ClipData
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import com.johannes.llgplanv2.MainActivity
import com.johannes.llgplanv2.R
import com.johannes.llgplanv2.api.Teacher
import com.johannes.llgplanv2.api.TeacherList
import com.johannes.llgplanv2.databinding.TeacherListItemBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.UncheckedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import android.content.ClipboardManager
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager


class TeacherListAdapter(
    private val lifecycleScope: LifecycleCoroutineScope) :
    RecyclerView.Adapter<TeacherListAdapter.ViewHolder>() {

    private var dataSet = mutableListOf<Teacher>()
    private var searchString = ""
    private val filteredDataSet = mutableListOf<Teacher>()
    private var amountDataShown = 0
    private val scrollThreshhold = 5

    class ViewHolder(val binding: TeacherListItemBinding) : RecyclerView.ViewHolder(binding.root) {
        var emailShown = false
        var teacher: Teacher? = null
    }

    fun setDataSet(newDataSet: MutableList<Teacher>) {
        this.dataSet = newDataSet
        updateFilter()
    }

    fun setSearchFilter(newString: String) {
        searchString = newString
        updateFilter()
    }

    private fun updateFilter() {

        filteredDataSet.clear()
        for (item: Teacher in dataSet) {
            if ("${item.abbreviation} ${item.firstName} ${item.lastName}"
                    .lowercase().contains(searchString.lowercase())) {
                filteredDataSet.add(item)
            }
        }

        amountDataShown = filteredDataSet.size
        if (amountDataShown>20) amountDataShown = 20

        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = TeacherListItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val teacher = filteredDataSet[position]
        holder.teacher = teacher
        holder.emailShown = false
        holder.binding.abbreviationTextView.text = teacher.abbreviation
        holder.binding.fullNameTextView.text =
            "${teacher.firstName} ${teacher.lastName}"

        holder.binding.contentConstraintLayout.setOnClickListener{ // show email
            if (holder.emailShown) {
                holder.emailShown = false
                holder.binding.emailFrameLayout.visibility = View.GONE
            } else {
                holder.emailShown = true
                holder.binding.emailFrameLayout.visibility = View.VISIBLE
                holder.binding.loadingSpinner.visibility = View.VISIBLE
                holder.binding.emailConstrainLayout.visibility = View.GONE

                lifecycleScope.launch(Dispatchers.IO) {
                    var email = teacher.email
                    val resources = MainActivity.instance.resources

                    if (email == "") {
                        MainActivity.instance.tryNetworkRequest {
                            email = TeacherList.requestEmail(teacher)
                        }
                    }

                    if (email == "") {
                        withContext(Dispatchers.Main) {
                            holder.binding.emailFrameLayout.visibility = View.GONE
                            holder.binding.loadingSpinner.visibility = View.GONE
                            holder.binding.emailConstrainLayout.visibility = View.GONE
                            holder.emailShown = false
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            holder.binding.emailTextView.text = email
                            holder.binding.emailFrameLayout.visibility = View.VISIBLE
                            holder.binding.loadingSpinner.visibility = View.GONE
                            holder.binding.emailConstrainLayout.visibility = View.VISIBLE
                            holder.emailShown = true
                        }
                    }
                }
            }
        }

        // copy to clipboard
        holder.binding.emailConstrainLayout.setOnClickListener {

            if (teacher.email != "") {
                val clipboard = MainActivity.instance.getSystemService(Context.CLIPBOARD_SERVICE)
                    as ClipboardManager
                val clip = ClipData.newPlainText("email", teacher.email)
                clipboard.setPrimaryClip(clip)

                Toast.makeText(
                    MainActivity.instance.applicationContext,
                    MainActivity.instance.resources.getString(R.string.email_copied_to_clipboard),
                    Toast.LENGTH_LONG).show()

            }
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        val layoutManager = LinearLayoutManager(MainActivity.instance.applicationContext)
        recyclerView.layoutManager = layoutManager
        recyclerView.addOnScrollListener( object: RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                var desiredAmount = layoutManager.findLastVisibleItemPosition() + scrollThreshhold
                if (desiredAmount > filteredDataSet.size) desiredAmount = filteredDataSet.size
                val shortage = desiredAmount - amountDataShown
                if (shortage > 0) {
                    recyclerView.post {
                        val oldAmountDataShown = amountDataShown
                        amountDataShown += shortage
                        notifyItemRangeInserted(oldAmountDataShown, amountDataShown - 1)
                    }
                }
            }
        })
    }

    override fun getItemCount() = amountDataShown
}