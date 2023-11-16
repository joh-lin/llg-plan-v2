package com.johannes.llgplanv2.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.TableRow
import android.widget.TextView
import com.johannes.llgplanv2.R
import com.johannes.llgplanv2.api.Timetable
import com.johannes.llgplanv2.databinding.TimetableViewLayoutBinding

class TimetableView(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var timetable: Timetable? = null
    var week: Int = 0
    lateinit var binding: TimetableViewLayoutBinding

    init {
        val view = inflate(context, R.layout.timetable_view_layout, this)
        binding = TimetableViewLayoutBinding.bind(view)
    }

    fun toggleWeek() {
        week = if (week == 0) 1 else 0
    }

    fun updateView() {
        binding.weekTextView.text = if (week == 0) resources.getString(R.string.week_a)
            else resources.getString(R.string.week_b)

        timetable?.tables?.get(week)?.let { table ->
            val tableLayout = binding.tableLayout
            for (rowIndex in 1 until tableLayout.childCount) {
                val tableRow = tableLayout.getChildAt(rowIndex) as TableRow
                for (textIndex in 1 until tableRow.childCount) {
                    val textView = tableRow.getChildAt(textIndex) as TextView
                    textView.text = table[textIndex-1][rowIndex-1]
                        .subject.split("-").first()
                }
            }
        }
    }
}