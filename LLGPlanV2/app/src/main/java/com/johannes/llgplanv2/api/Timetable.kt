package com.johannes.llgplanv2.api

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.johannes.llgplanv2.MainActivity
import org.jsoup.Connection
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.File
import java.net.SocketTimeoutException

class Timetable(
    private val student: Student) {
    var lastUpdated = "" // format: "dd.MM.yyyy-HH:mm:ss-Z"
    var tables: Array<Array<Array<Lesson>>>? = null


    @Throws(HttpStatusException::class, SocketTimeoutException::class)
    fun sync(): SyncResponse {
        // download webpage
        val loginPageURL = "https://selbstlernportal.de/html/planinfo/planinfo_start.php"
        val homePageURL = "https://www.landrat-lucas.org/"

        val loginUser = MainActivity.sharedPref.getString("slp_login_user", null) ?: "invalid"
        val loginPassword = MainActivity.sharedPref.getString("slp_login_password", null) ?: "invalid"
        // params
        val loginParams: MutableMap<String, String> = mutableMapOf(
            "jsIsActive" to "0",
            "group" to "lev-llg",
            "login" to loginUser,
            "pw" to loginPassword,
            "checkLogin" to ""
        )
        val searchParams: MutableMap<String, String> = mutableMapOf(
            "quickSearch" to student.searchString,
            "chooseType" to "???",
            "chooseWeek" to "X",
            "chooseDesign" to "w"
        )

        println("searching for: ${student.searchString}")

        // get cookies and cFlag from login page
        val loginPageResponse =
            Jsoup.connect(loginPageURL)
                .referrer(homePageURL)
                .userAgent("Mozilla/5.0")
                .timeout(DataManager.timetableTimeoutDuration)
                .followRedirects(true)
                .execute()

        val loginPageCookies: MutableMap<String, String> = loginPageResponse.cookies()
        val loginPage: Document = loginPageResponse.parse()
        val cFlagElement: Element =
            loginPage.selectFirst("input[type=hidden][name=cFlag]")
                ?: return SyncResponse(SyncResponse.FAILED, "No cFlag found: + \n$loginPage") //no cFlag was found
        searchParams["cFlag"] = cFlagElement.attr("value").toString()

        // login to page
        Jsoup.connect(loginPageURL)
            .referrer(loginPageURL)
            .userAgent("Mozilla/5.0")
            .method(Connection.Method.POST)
            .timeout(10 * 1000)
            .data(loginParams) //post parameters
            .cookies(loginPageCookies) //cookies received from login page
            .followRedirects(true) //many websites redirect the user after login, so follow them
            .execute().parse()

        // get search results
        val planPageResponse =
            Jsoup.connect(loginPageURL)
                .referrer(loginPageURL)
                .userAgent("Mozilla/5.0")
                .method(Connection.Method.POST)
                .timeout(DataManager.timetableTimeoutDuration)
                .data(searchParams)
                .cookies(loginPageCookies)
                .followRedirects(true)
                .execute()


        val planPage = planPageResponse.parse()
        var finalPage: Document = planPage

        val selectionElement = planPage.selectFirst("select[id=chooseQuick]")
        if (selectionElement != null) {
            // student needs double search
            val optionElements = selectionElement.select("option")
            // select the option you want
            var studentID = ""
            for (el: Element in optionElements) {
                if (el.text().contains(student.fullName)) studentID = el.attr("value")
                println(el.text())
            }

            searchParams.remove("quickSearch")
            searchParams["chooseQuick"] = studentID

            // get search results
            val planPageResponse2 =
                Jsoup.connect(loginPageURL)
                    .referrer(loginPageURL)
                    .userAgent("Mozilla/5.0")
                    .method(Connection.Method.POST)
                    .timeout(DataManager.timetableTimeoutDuration)
                    .data(searchParams)
                    .cookies(loginPageCookies)
                    .followRedirects(true)
                    .execute()
            finalPage = planPageResponse2.parse()
        }


        // parse all data
        val timetablePage = finalPage
        var timetableA = Array(5) { Array(12) { Lesson() } }
        var timetableB = Array(5) { Array(12) { Lesson() } }

        for ((week, tableElement: Element) in timetablePage.select("table.tt").withIndex()) {
            val timetable = Array(5) { Array(12) { Lesson() } }
            val tableRowElements = tableElement.select("tr")
            for (y in 2 until tableRowElements.size) {
                val tableColumnElements = tableRowElements[y].select("td")
                for (x in tableColumnElements.indices) {
                    timetable[x][y - 2] = Lesson.fromString(tableColumnElements[x].ownText())
                }
            }
            if (week == 0) {
                timetableA = timetable
            } else {
                timetableB = timetable
            }
        }

        lastUpdated = CalendarUtils.dateToString(CalendarUtils.getInstance().time)
        tables = arrayOf(timetableA, timetableB)
        return SyncResponse(SyncResponse.SUCCESS)
    }

    fun getAllLessons() : MutableList<Lesson> {
        val lessons = mutableListOf<Lesson>()
        tables?.let {
            for (table: Array<Array<Lesson>> in it) {
                for (day in table) { for (lesson in day) {
                    if (lesson !in lessons && !lesson.isEmpty()) lessons.add(lesson)
                }}
            }
        }
        return lessons
    }

    fun save(context: Context) {
        val file = getSaveFile(context)
        if (!file.exists()) file.createNewFile()
        file.writeText(Gson().toJson(DataSaveContainer(lastUpdated, tables)))
    }

    fun load(context: Context): Boolean {
        val file = getSaveFile(context)
        if (!file.exists()) return false
        val text = file.readText()
        val type = object : TypeToken<DataSaveContainer<
                Array<Array<Array<Lesson>>>?>>(){}.type
        val result = Gson().fromJson<DataSaveContainer<
                Array<Array<Array<Lesson>>>?>>(text, type)
        lastUpdated = result.date
        tables = result.data
        return true
    }

    private fun getSaveFile(context: Context): File {
        return File(context.filesDir,DataManager.timetableFilePath.replace("{name}",
            student.fullName.lowercase().replace(" ", "_")))
    }

    data class SyncResponse(val state: Int, val message: String="") {
        companion object {
            const val SUCCESS = 1
            const val FAILED = -1
        }
    }
}