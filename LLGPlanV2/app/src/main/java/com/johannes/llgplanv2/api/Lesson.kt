package com.johannes.llgplanv2.api

data class Lesson(
    val rail: String = "",
    val subject: String = "",
    val teacher: String = "",
    val room: String = "") {

    // factory functions
    companion object {
        fun fromString(string: String) : Lesson {
            // string format: "(L2) IF-LK1 WIB A306"
            if (string=="") return Lesson()
            val split = string.split(" ")
            return Lesson(split[0], split[1], split[2], split[3])
        }
    }

    fun isEmpty() : Boolean {
        return rail == "" && subject == "" &&teacher == "" && room == ""
    }

    override fun toString() : String {
        return "$rail $subject $teacher $room"
    }
}