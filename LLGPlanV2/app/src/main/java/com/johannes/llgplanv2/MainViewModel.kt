package com.johannes.llgplanv2

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.johannes.llgplanv2.api.EventList
import com.johannes.llgplanv2.api.Student
import com.johannes.llgplanv2.api.SubstitutionPlan
import com.johannes.llgplanv2.api.TeacherList

class MainViewModel : ViewModel() {
    val activeStudent = MutableLiveData<Student?>()
    val substitutionPlan = MutableLiveData<SubstitutionPlan?>()
    val teacherList = MutableLiveData<TeacherList?>()
    val eventList = MutableLiveData<EventList?>()

    fun loadPreferences() {
        //val prefs = MainActivity.instance.getPreferences()
    }
}