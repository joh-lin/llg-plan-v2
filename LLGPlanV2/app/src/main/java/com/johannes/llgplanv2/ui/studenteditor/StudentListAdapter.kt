package com.johannes.llgplanv2.ui.studenteditor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.PopupWindow
import androidx.recyclerview.widget.RecyclerView
import com.johannes.llgplanv2.MainActivity
import com.johannes.llgplanv2.MainViewModel
import com.johannes.llgplanv2.api.DataManager
import com.johannes.llgplanv2.api.Student
import com.johannes.llgplanv2.databinding.StudentListItemBinding
import com.johannes.llgplanv2.settings.PrefKeys

class StudentListAdapter(private var dataSet: MutableList<Student>,
                         val popupWindow: PopupWindow) :
        RecyclerView.Adapter<StudentListAdapter.ViewHolder>() {

    var showingSearchResults = false

    class ViewHolder(val binding: StudentListItemBinding) : RecyclerView.ViewHolder(binding.root) {}

    fun setDataSet(newDataSet: MutableList<Student>) {
        this.dataSet = newDataSet
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = StudentListItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.binding.textView.text =
            if (MainActivity.sharedPref.getBoolean(PrefKeys.secretsEnabled, false)) {
                dataSet[position].fullName
            } else {
                dataSet[position].fullName
            }

        holder.binding.constraintLayout.setOnClickListener {
            if (showingSearchResults) {
                DataManager.studentProfiles.add(0, dataSet[position])
                MainActivity.instance.setActiveStudent(dataSet[position])

                // close window
                popupWindow.dismiss()
            } else {
                MainActivity.instance.setActiveStudent(dataSet[position])
                DataManager.studentProfiles.apply {
                    val temp = get(position)
                    removeAt(position)
                    add(0, temp)
                }
                popupWindow.dismiss()
            }
        }
        holder.binding.deleteButton.setOnClickListener{
            DataManager.studentProfiles.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, itemCount)
            if (position == 0) {
                MainActivity.instance.setActiveStudent(dataSet.firstOrNull())
            }
        }


        if (showingSearchResults) holder.binding.deleteButton.visibility = Button.GONE
    }

    override fun getItemCount() = dataSet.size
}