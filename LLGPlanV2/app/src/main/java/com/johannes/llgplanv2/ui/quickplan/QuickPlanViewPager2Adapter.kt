package com.johannes.llgplanv2.ui.quickplan

import android.graphics.Color
import android.graphics.Shader
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.johannes.llgplanv2.MainActivity
import com.johannes.llgplanv2.R
import com.johannes.llgplanv2.api.CalendarUtils
import com.johannes.llgplanv2.api.PlanInterpreter
import com.johannes.llgplanv2.api.Student
import com.johannes.llgplanv2.databinding.CardQuickPlanBinding
import com.johannes.llgplanv2.databinding.PopupPlanDetailsBinding
import com.johannes.llgplanv2.ui.TileDrawable
import kotlin.math.abs
import java.text.SimpleDateFormat
import java.util.*

class QuickPlanViewPager2Adapter(
    val viewPager2: ViewPager2,
    val invisiblePageCount: Int
) : RecyclerView.Adapter<QuickPlanViewPager2Adapter.ViewHolder>() {

    var dayOffset = 0
    var student: Student? = null
    var planInterpreter = PlanInterpreter()

    class ViewHolder(val binding: CardQuickPlanBinding) : RecyclerView.ViewHolder(binding.root) {

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // create layout and view holder
        val view = CardQuickPlanBinding.inflate(
            LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val context = MainActivity.instance.applicationContext
        val totalOffset = (position - invisiblePageCount) + dayOffset
        val date = CalendarUtils.calendarOffsetToWorkday(
            CalendarUtils.getInstance(), totalOffset
        )
        val weekOfYear = date.get(Calendar.WEEK_OF_YEAR).mod(2)
        val dayOfWeek = date.get(Calendar.DAY_OF_WEEK)

        // day info
        holder.binding.dayTextView.text = when(dayOfWeek) {
            1 -> "SO"; 2 -> "MO"; 3 -> "DI"; 4 -> "MI"
            5 -> "DO"; 6 -> "FR"; 7 -> "SA"; else -> "ERROR"
        }
        holder.binding.dateTextView.text = SimpleDateFormat("dd.MM.yy").format(date.time)
        holder.binding.weekTextView.text = when(weekOfYear) {
            1 -> "B"; 0 -> "A"; else -> "ERROR"
        }

        // Info text view
        holder.binding.infoTextView.text =
            if (planInterpreter.getSubstitutions(date).isEmpty())
                context.resources.getString(R.string.no_substitution_found)
            else ""

        // fill table
        val drawCanceled = ContextCompat.getDrawable(MainActivity.instance,
            R.drawable.draw_plan_canceled) ?: throw Exception("Failed to load drawable")
        val drawChanged = ContextCompat.getDrawable(MainActivity.instance,
            R.drawable.draw_plan_change) ?: throw Exception("Failed to load drawable")

        // NAVIGATION BUTTONS
        holder.binding.buttonLeft.setOnClickListener {
            viewPager2.setCurrentItem(invisiblePageCount-1, true)
        }
        holder.binding.buttonRight.setOnClickListener {
            viewPager2.setCurrentItem(invisiblePageCount+1, true)
        }


        val tableView = holder.binding.tableLayout
        for (i: Int in 0 until tableView.childCount) {
            val tableRow = tableView.getChildAt(i) as? TableRow
            tableRow?.let { row ->
                val rowItems = row.getChildAt(1) as LinearLayout
                val num = (row.getChildAt(0) as TextView).text.toString().toInt()
                val lesson = planInterpreter.getLesson(date, num)
                // TEXT

                (rowItems.getChildAt(0) as TextView).text =
                    lesson.subject.split("-").first()
                (rowItems.getChildAt(1) as TextView).text = lesson.teacher
                (rowItems.getChildAt(2) as TextView).text = lesson.room
                (rowItems.getChildAt(3) as ImageView).visibility =
                    if (lesson.message == "") ImageView.INVISIBLE
                    else ImageView.VISIBLE

                rowItems.visibility = ImageView.VISIBLE
                if (lesson.message == "") {
                    rowItems.setOnClickListener(null)
                    row.setOnClickListener(null)
                } else {
                    rowItems.setOnClickListener {
                        showSubstitutionMessage(row, lesson.message)
                    }
                }

                // SUBSTITUTION
                if (lesson.canceled) {
                    rowItems.background = TileDrawable(drawCanceled)
                    (rowItems.getChildAt(0) as TextView).setBackgroundResource(0)
                    (rowItems.getChildAt(1) as TextView).setBackgroundResource(0)
                    (rowItems.getChildAt(2) as TextView).setBackgroundResource(0)
                } else {
                    rowItems.setBackgroundResource(0)
                    with(rowItems.getChildAt(0) as TextView) {
                        if (lesson.subjectChanged) {
                            this.background = TileDrawable(drawChanged)
                        } else { this.setBackgroundResource(0) } }

                    with(rowItems.getChildAt(1) as TextView) {
                        if (lesson.teacherChanged) {
                            this.background = TileDrawable(drawChanged)
                        } else { this.setBackgroundResource(0) } }

                    with(rowItems.getChildAt(2) as TextView) {
                        if (lesson.roomChanged) {
                            this.background = TileDrawable(drawChanged)
                        } else { this.setBackgroundResource(0) } }
                }
            }
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        viewPager2.setCurrentItem(invisiblePageCount, false)
        viewPager2.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                super.onPageScrollStateChanged(state)
                if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    val offsetChange = viewPager2.currentItem - invisiblePageCount
                    dayOffset += offsetChange
                    viewPager2.setCurrentItem(invisiblePageCount, false)
                    if (offsetChange > 0) {
                        repeat(abs(offsetChange)) { notifyItemMoved(0, itemCount - 1) }
                    } else if (offsetChange < 0) {
                        repeat(abs(offsetChange)) { notifyItemMoved(itemCount - 1, 0) }
                    }
                }
            }
        })
    }

    override fun getItemCount() = invisiblePageCount * 2 + 1

    fun changeStudent(newStudent: Student?) {
        student = newStudent
        invalidate()
    }

    fun invalidate() {
        planInterpreter = PlanInterpreter(student,
            MainActivity.viewModel.substitutionPlan.value)
        notifyItemRangeChanged(0, itemCount)
    }

    private fun showSubstitutionMessage(rootView: View, message: String) {
        val popupBinding = PopupPlanDetailsBinding.inflate(MainActivity.instance.layoutInflater)
        val popupWindow = PopupWindow(popupBinding.root, rootView.width,
            LinearLayout.LayoutParams.WRAP_CONTENT,true)

        // MODIFY LAYOUT
        popupBinding.textView.text = message

        // ONCLICK DISMISS
        popupBinding.root.setOnClickListener {
            popupWindow.dismiss()
        }

        // SHOW POPUP
        popupWindow.showAsDropDown(rootView, 0, -rootView.height, Gravity.CENTER_HORIZONTAL)
    }
}