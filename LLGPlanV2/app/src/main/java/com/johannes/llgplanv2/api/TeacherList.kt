package com.johannes.llgplanv2.api

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import java.io.File
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.*


class TeacherList() {
    var lastUpdated = "" // format: "dd.MM.yyyy-HH:mm:ss-Z"
    var list: MutableList<Teacher>? = null


    @Throws(HttpStatusException::class, SocketTimeoutException::class)
    fun sync(): SyncResponse {
        // get page
        val url = "https://www.landrat-lucas.org/kollegium.html"
        val response = Jsoup.connect(url)
            .userAgent("Mozilla/5.0")
            .timeout(DataManager.teacherListTimeoutDuration)
            .followRedirects(true) //many websites redirects the user after login, so follow them
            .execute()

        // parse data
        val responsePage = response.parse()
        val teachers = mutableListOf<Teacher>()
        val tableRows = responsePage.select("table.all_records")
            .select("tbody").select("tr")
        // iterate over table rows
        for (row in tableRows) {
            teachers.add(Teacher(
                row.select("td.col_0").text(),
                row.select("td.col_1").text(),
                row.select("td.col_2").text(),
                Regex("(?<=show=)\\d+").find(
                    row.select("td.col_3").select("a").attr("href")
                )?.value?.toInt() ?: return SyncResponse.FAILED,
                ""
            ))
        }

        list = teachers
        lastUpdated = CalendarUtils.dateToString(Calendar.getInstance(Locale.GERMANY).time)
        return SyncResponse.SUCCESS
    }

    companion object {
        fun requestEmail(teacher: Teacher): String {
            // get page
            val url = "https://www.landrat-lucas.org/kollegium.html?show=${teacher.websiteId.toString()}"
            val response = Jsoup.connect(url)
                .userAgent("Mozilla/5.0")
                .timeout(DataManager.teacherListTimeoutDuration)
                .followRedirects(true) //many websites redirects the user after login, so follow them
                .execute()

            // parse data
            val responsePage = response.parse()
            val result = responsePage.select("tr.row_2").select("a").text()

            // set email of searched teacher
            teacher.email = result

            return result
        }
    }

    fun save(context: Context) {
        val file = File(context.filesDir, DataManager.teacherListFilePath)
        if (!file.exists()) file.createNewFile()
        file.writeText(Gson().toJson(DataSaveContainer(lastUpdated, list)))
    }

    fun load(context: Context): Boolean {
        val file = File(context.filesDir, DataManager.teacherListFilePath)
        if (!file.exists()) return false
        val text = file.readText()
        val type = object : TypeToken<DataSaveContainer<MutableList<Teacher>?>>(){}.type
        val result = Gson().fromJson<DataSaveContainer<MutableList<Teacher>?>>(text, type)
        lastUpdated = result.date
        list = result.data
        return true
    }

    enum class SyncResponse {
        SUCCESS, FAILED
    }
}