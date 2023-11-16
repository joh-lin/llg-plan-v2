package com.johannes.llgplanv2.api

data class Teacher(
    val firstName: String,
    val lastName: String,
    val abbreviation: String,
    val websiteId: Int,
    var email: String
)