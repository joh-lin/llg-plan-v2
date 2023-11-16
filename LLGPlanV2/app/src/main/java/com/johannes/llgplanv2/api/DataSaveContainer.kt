package com.johannes.llgplanv2.api

data class DataSaveContainer<T>(
    val date: String,
    val data: T,
)