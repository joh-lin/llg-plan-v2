package com.johannes.llgplanv2.api

import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*

class PlanInterpreter(val student: Student? = null,
                      val substitutionPlan: SubstitutionPlan? = null) {

    data class Lesson(
        val num: Int,
        var subject: String,
        var teacher: String,
        var room: String ){
        var message = ""
        var canceled = false
        var subjectChanged = false
        var teacherChanged = false
        var roomChanged = false

    }

    fun getSubstitutions(date: Calendar): MutableList<SubstitutionPlan.Substitution> {
        return substitutionPlan?.table?.let { substitutionTable ->
            val dateString = try{ SimpleDateFormat("d.M.yyyy", Locale.GERMANY)
                .format(date.time) } catch (e: Exception) {null}
            substitutionTable[dateString]
        } ?: mutableListOf()
    }

    fun getLesson(date: Calendar, num: Int): Lesson {
        val week = date.get(Calendar.WEEK_OF_YEAR).mod(2)
        val dayOfWeek = date.get(Calendar.DAY_OF_WEEK)

        return student?.timetable?.tables?.let { timetable ->
            val lesson = timetable[week][dayOfWeek-2][num-1]
            val newLesson = Lesson(num, lesson.subject, lesson.teacher, lesson.room)

            // merge with substitution
            substitutionPlan?.table?.let { substitutionTable ->
                // get day
                val dateString = try { SimpleDateFormat("d.M.yyyy", Locale.GERMANY)
                    .format(date.time) } catch (e: Exception) { return@let }
                val substitutions = substitutionTable[dateString] ?: return@let

                for (subst: SubstitutionPlan.Substitution in substitutions) {
                    // check if subst matches
                    if (subst.class_ != student.gradeLevel.toString()) continue
                    if (subst.oldSubject != lesson.subject) continue
                    if (subst.lesson != num) continue
                    // check for cancellation
                    newLesson.message = subst.comment
                    if (subst.type == "entfälllt") {
                        newLesson.canceled = true
                        continue
                    }
                    // check for other cases
                    if (subst.newTeacher != lesson.teacher) {
                        newLesson.teacher = subst.newTeacher
                        newLesson.teacherChanged = true
                    }
                    if(subst.newSubject != lesson.subject) {
                        newLesson.subject = subst.newSubject
                        newLesson.subjectChanged = true
                    }
                    if(subst.room != "" && subst.room != lesson.room) {
                        newLesson.room = subst.room
                        newLesson.roomChanged = true
                    }
                }
            }


            return@let newLesson
        } ?: Lesson(-1, "", "", "")
    }
}