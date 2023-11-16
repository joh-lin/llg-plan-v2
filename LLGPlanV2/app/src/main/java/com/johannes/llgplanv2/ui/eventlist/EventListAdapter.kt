package com.johannes.llgplanv2.ui.eventlist

import android.graphics.Typeface
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.johannes.llgplanv2.MainActivity
import com.johannes.llgplanv2.R
import com.johannes.llgplanv2.api.CalendarUtils
import com.johannes.llgplanv2.api.Event
import com.johannes.llgplanv2.api.Lesson
import com.johannes.llgplanv2.databinding.ItemEventListBinding
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class EventListAdapter(var dataSet: MutableList<Event>, var lessons: MutableList<Lesson>) : RecyclerView.Adapter<EventListAdapter.ViewHolder>() {

    @ColorInt private var colorOnPrimaryDark = 0


    class ViewHolder(val binding: ItemEventListBinding) : RecyclerView.ViewHolder(binding.root) {}


    fun updateDataSet(newDataSet: MutableList<Event>, newLessons: MutableList<Lesson>) {
        dataSet = newDataSet
        lessons = newLessons
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = ItemEventListBinding.inflate(
            LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val event = dataSet[position]
        val date = SimpleDateFormat("dd.MM.yy", Locale.GERMANY).parse(event.date) ?: return
        val today = CalendarUtils.getInstance()
        val cal = CalendarUtils.getInstance()
        cal.time = date

        holder.binding.dateTextView.text =
            SimpleDateFormat("dd.MM.yyyy:", Locale.GERMANY).format(date)
        holder.binding.dayTextView.text = when (cal.get(Calendar.DAY_OF_WEEK)) {
            1 -> "SO"; 2 -> "MO"; 3 -> "DI"; 4 -> "MI"
            5 -> "DO"; 6 -> "FR"; 7 -> "SA"; else -> "ERROR"
        }
        holder.binding.messageTextView.text = event.text
        holder.binding.seperatorLine.visibility =
            if (position==itemCount-1) { View.INVISIBLE } else { View.VISIBLE }

        // GRAY OUT IF PASSED
        if (
            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) &&
            cal.get(Calendar.YEAR) == today.get(Calendar.YEAR)) {
                holder.binding.dayTextView.setTypeface(null, Typeface.BOLD)
        } else if (cal.before(today)) {
            holder.binding.dateTextView.setTextColor(colorOnPrimaryDark)
            holder.binding.dayTextView.setTextColor(colorOnPrimaryDark)
        }

        // INFO TEXT
        for (lesson in lessons) {
            if (Regex("\\(.\\d+\\)").matches(lesson.rail.lowercase()) &&
                event.text.lowercase().contains(
                    lesson.rail.lowercase()
                        .trimStart('(')
                        .trimEnd(')'))) {
                holder.binding.infoTextView.text = lesson.subject
                break
            }
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(MainActivity.instance.applicationContext)
        val typedValue = TypedValue()
        MainActivity.instance.theme
            .resolveAttribute(R.attr.colorOnPrimaryDark, typedValue, true)
        colorOnPrimaryDark = typedValue.data
    }

    override fun getItemCount() = dataSet.size
}