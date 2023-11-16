package com.johannes.llgplanv2.api

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object DataManager {
    // General
    val generalFilePath = ""
    val defaultTimeoutDuration = 30*1000 // 30 seconds

    // Timetables
    var timetableTimeoutDuration = defaultTimeoutDuration
    val timetableFilePath = generalFilePath + "timetable_data_{name}.json"  // {name} is replaced

    // SubstitutionPlan
    var substitutionPlanTimeoutDuration = defaultTimeoutDuration
    val substitutionPlanFilePath = generalFilePath + "substitutionPlan_data.json"

    // TeacherList
    var teacherListTimeoutDuration = defaultTimeoutDuration
    val teacherListFilePath = generalFilePath + "teacherList_data.json"

    // Student Sync
    var studentSyncTimeoutDuration = defaultTimeoutDuration

    // Student Profile Data
    val studentProfileFilePath = generalFilePath + "studentProfile_data.json"
    var studentProfiles = mutableListOf<Student>()

    // Event List
    var eventListTimeoutDuration = defaultTimeoutDuration
    val eventListFilePath = generalFilePath + "eventList_data.json"

    fun saveStudentProfiles(context: Context) {
        val file = File(context.filesDir, studentProfileFilePath)
        if (!file.exists()) file.createNewFile()
        file.writeText(Gson().toJson(studentProfiles))
    }

    fun loadStudentProfiles(context: Context): Boolean {
        val file = File(context.filesDir, studentProfileFilePath)
        if (!file.exists()) return false
        val text = file.readText()
        val type = object : TypeToken<MutableList<Student>>(){}.type
        studentProfiles = Gson().fromJson(text, type)
        return true
    }
}