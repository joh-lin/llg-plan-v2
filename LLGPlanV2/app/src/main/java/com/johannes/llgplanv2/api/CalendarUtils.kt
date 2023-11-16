package com.johannes.llgplanv2.api

import java.text.SimpleDateFormat
import java.util.*

class CalendarUtils {
    companion object {
        fun dateToString(date: Date): String {
            return SimpleDateFormat("dd.MM.yyyy-HH:mm:ss-Z", Locale.GERMANY)
                .format(date)
        }
        fun stringToDate(string: String): Date {
            return SimpleDateFormat("dd.MM.yyyy-HH:mm:ss-Z", Locale.GERMANY)
                .parse(string)
        }
        fun stringToCalendar(string: String): Calendar {
            val calendar = Calendar.getInstance(Locale.GERMANY)
            calendar.time = stringToDate(string)
            return calendar
        }

        fun calendarOlderThanMinutes(calendar: Calendar, minutes: Int): Boolean {
            val compCalendar = Calendar.getInstance() // Calendar for comparing
            compCalendar.add(Calendar.MINUTE, -minutes)
            return compCalendar.after(calendar)
        }
        fun calendarOffsetToWorkday(calendar: Calendar, offsetDays: Int): Calendar {
            // SO MO DI MI DO FR SA
            // 1  2  3  4  5  6  7
            var dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

            var calcOffset = 0
            var offsetCounter = offsetDays

            // this is necessary if there is no offset given
            if (offsetDays < 0) {
                if (dayOfWeek == 1) { dayOfWeek = 6; calcOffset -= 2 }
                else if (dayOfWeek == 7) { dayOfWeek = 6; calcOffset -= 1}
            } else { // offsetDays >= 0
                if (dayOfWeek == 1) { dayOfWeek = 2; calcOffset += 1 }
                else if (dayOfWeek == 7) { dayOfWeek = 2; calcOffset += 2}
            }

            // loop for real offset
            while (offsetCounter != 0) {
                if (offsetCounter < 0) {
                    offsetCounter++; calcOffset--; dayOfWeek--
                    if (dayOfWeek == 1) {
                        dayOfWeek = 6
                        calcOffset -= 2
                    }
                } else if (offsetCounter > 0) {
                    offsetCounter--; calcOffset++; dayOfWeek++
                    if (dayOfWeek == 7) {
                        dayOfWeek = 2
                        calcOffset += 2
                    }
                }
            }

            calendar.add(Calendar.DAY_OF_YEAR, calcOffset)
            return calendar
        }
        fun getInstance() = Calendar.getInstance(Locale.GERMANY)
    }
}