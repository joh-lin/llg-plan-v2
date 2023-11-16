package com.johannes.llgplanv2.api

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.jsoup.Jsoup
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class EventList {
    var lastUpdated = ""
    var list: MutableList<Event>? = null


    fun sync(): Boolean {
        // https://selbstlernportal.de/html/termin/termin_klausur.php?anzkw=20&kw=44/2021&endkw=11/2022
        val calDate = CalendarUtils.getInstance()
        var url = "https://selbstlernportal.de/html/termin/termin_klausur.php?ug=lev-llg&" +
                "anzkw=20&" +
                "kw=${calDate.get(Calendar.WEEK_OF_YEAR)}/${calDate.get(Calendar.YEAR)}"

        calDate.add(Calendar.WEEK_OF_YEAR, 20)
        url += "&endkw=${calDate.get(Calendar.WEEK_OF_YEAR)}/${calDate.get(Calendar.YEAR)}"

        // get cookies
        val cookiePage = Jsoup.connect("https://termin.selbstlernportal.de/?ug=lev-llg")
            .userAgent("Mozilla/5.0")
            .timeout(DataManager.eventListTimeoutDuration)
            .followRedirects(true) //many websites redirects the user after login, so follow them
            .execute()
        // get data from website
        val response = Jsoup.connect(url)
            .userAgent("Mozilla/5.0")
            .timeout(DataManager.eventListTimeoutDuration)
            .cookies(cookiePage.cookies())
            .followRedirects(true) //many websites redirects the user after login, so follow them
            .execute()

        // parse data
        val events = mutableListOf<Event>()
        val responsePage = response.parse()

        val tableItems = responsePage.select("div.klausur")
        for (elem in tableItems) {
            // get date of current item
            val date = Calendar.getInstance()
            date.time = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY)
                .parse(elem.attr("id").split("_").last())
                ?: return false
            val dateString = SimpleDateFormat("dd.MM.yy").format(date.time)

            // iterate over degrees
            val textNodes = elem.textNodes()
            for (i in textNodes.indices) {
                val text = textNodes[i].toString()
                if (text != "&nbsp;") {
                    //println("${dateString} : ${GradeLevel.values()[i]} : ${text}")
                    events.add(Event(dateString, GradeLevel.values()[i], text))
                }
            }
        }

        lastUpdated = CalendarUtils.dateToString(CalendarUtils.getInstance().time)
        list = events
        return true
    }

    fun save(context: Context) {
        val file = File(context.filesDir, DataManager.eventListFilePath)
        if (!file.exists()) file.createNewFile()
        file.writeText(Gson().toJson(DataSaveContainer(lastUpdated, list)))
    }

    fun load(context: Context): Boolean {
        val file = File(context.filesDir, DataManager.eventListFilePath)
        if (!file.exists()) return false
        val text = file.readText()
        val type = object : TypeToken<DataSaveContainer<MutableList<Event>?>>(){}.type
        val result = Gson().fromJson<DataSaveContainer<MutableList<Event>?>>(text, type)
        lastUpdated = result.date
        list = result.data
        return true
    }
}