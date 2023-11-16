package com.johannes.llgplanv2

import android.content.Context
import com.johannes.llgplanv2.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.ParseException
import java.util.*

class WidgetApiAdapter {
    companion object {
        /** Returns the student that was last selected in the app
         *  or null if there are no student profiles
         */
        suspend fun getActiveStudentWithTimetable(appContext: Context): Student? {
            DataManager.loadStudentProfiles(appContext)
            val activeStudent =  DataManager.studentProfiles.firstOrNull() ?: return null

            // timetable
            var syncRequired = true
            val timetable = Timetable(activeStudent)
            if (timetable.load(appContext)) {
                val lastUpdated =
                    try { CalendarUtils.stringToCalendar(timetable.lastUpdated) }
                    catch(e: ParseException) { null }
                syncRequired = (lastUpdated == null ||
                        CalendarUtils.calendarOlderThanMinutes(lastUpdated,
                            MainActivity.timetableSyncCooldown))
            }
            activeStudent.timetable = timetable
            try {
                if (syncRequired) timetable.sync()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return activeStudent
        }

        /** Returns substitution plan or null if could not load
         * and updating failed
         */
        suspend fun getSubstitutionPlan(appContext: Context): SubstitutionPlan? {
            try {
                val substitutionPlan = SubstitutionPlan()
                val lastUpdated: Calendar?
                if (substitutionPlan.load(appContext)) {
                    lastUpdated = try {
                        CalendarUtils.stringToCalendar(substitutionPlan.lastUpdated)
                    } catch (e: ParseException) {
                        null
                    }
                } else {
                    lastUpdated = null
                }
                if (lastUpdated == null || CalendarUtils.calendarOlderThanMinutes(
                        lastUpdated,
                        MainActivity.substitutionPlanSyncCooldown
                    )
                ) {
                    substitutionPlan.sync()
                }
                return substitutionPlan
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }

        suspend fun getEventList(appContext: Context): EventList? {
            try {
                val eventList = EventList()
                val lastUpdated: Calendar?
                if (eventList.load(appContext)) {
                    lastUpdated = try {
                        CalendarUtils.stringToCalendar(eventList.lastUpdated)
                    } catch (E: ParseException) {
                        null
                    }
                } else {
                    lastUpdated = null
                }
                if (lastUpdated == null || CalendarUtils.calendarOlderThanMinutes(
                        lastUpdated,
                        MainActivity.eventListSyncCooldown
                    )
                ) {
                    eventList.sync()
                }
                return eventList
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
                return null
            }
        }

        fun getPlanInterpreter(student: Student, substitutionPlan: SubstitutionPlan) =
            PlanInterpreter(student, substitutionPlan)
    }
}