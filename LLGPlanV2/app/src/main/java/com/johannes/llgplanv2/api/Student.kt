package com.johannes.llgplanv2.api

import com.johannes.llgplanv2.MainActivity
import org.jsoup.Connection
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.SocketTimeoutException


data class Student(
    val fullName: String,
    val gradeLevel: GradeLevel,
    val searchString: String) {

    @Transient var timetable: Timetable? = null // transient = not serialized by gson

    fun createTimetable() = Timetable(this).also{ this.timetable = it }

    companion object {

        @Throws(HttpStatusException::class, SocketTimeoutException::class)
        fun syncStudents(searchString: String): SyncResponse {
            val result: MutableList<Student> = mutableListOf<Student>()
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
                "quickSearch" to searchString.lowercase(),
                "chooseType" to "???",
                "chooseWeek" to "X",
                "chooseDesign" to "w"
            )

            // get cookies and cFlag from login page
            val loginPageResponse =
                Jsoup.connect(loginPageURL)
                    .referrer(homePageURL)
                    .userAgent("Mozilla/5.0")
                    .timeout(DataManager.studentSyncTimeoutDuration)
                    .followRedirects(true)
                    .execute()


            val loginPageCookies: MutableMap<String, String> = loginPageResponse.cookies()
            val loginPage: Document = loginPageResponse.parse()
            val cFlagElement: Element =
                loginPage.selectFirst("input[type=hidden][name=cFlag]")
                    ?: return SyncResponse(SyncResponse.FAILED)
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

            // start actual check
            val planPageResponse =
                Jsoup.connect(loginPageURL)
                    .referrer(loginPageURL)
                    .userAgent("Mozilla/5.0")
                    .method(Connection.Method.POST)
                    .timeout(DataManager.studentSyncTimeoutDuration)
                    .data(searchParams)
                    .cookies(loginPageCookies)
                    .followRedirects(true)
                    .execute()
            val planPage = planPageResponse.parse()

            // check for different cases
            val planPageString = planPage.toString()

            // NO RESULT
            if (planPageString.contains("Die Suche erbrachte kein Ergebnis und wird protokolliert.")) {
                return SyncResponse(SyncResponse.NO_RESULTS) }

            // SEARCH TOO SHORT
            else if (planPageString.contains("Suchtext ist zu kurz")) {
                return SyncResponse(SyncResponse.SEARCH_TO_SHORT) }

            // TOO MANY RESULTS
            else if (planPageString.contains("Die Suche erbrachte zu viele")) {
                return SyncResponse(SyncResponse.TOO_MANY_RESULTS) }

            // SEARCH WAS VALID
            else if (planPageString.contains(Regex("[AB]-Woche-Stundenplan von Schüler:in"))) {
                val title = planPage.selectFirst("th.title[colspan=5]")?.ownText()
                    ?: return SyncResponse(SyncResponse.FAILED)
                val fullName = Regex(
                    "(?<=[AB]-Woche-Stundenplan von Schüler:in ).+(?= \\()"
                ).find(title)?.value ?: return SyncResponse(SyncResponse.FAILED)
                val classInfo = Regex("(?<=\\().+-.+(?=\\))").find(title)?.value
                    ?: return SyncResponse(SyncResponse.FAILED)
                val gradeLevel = with(classInfo) {
                    when {
                        startsWith("EF") -> GradeLevel.EF
                        startsWith("Q1") -> GradeLevel.Q1
                        startsWith("Q2") -> GradeLevel.Q2
                        else -> return SyncResponse(SyncResponse.FAILED)
                    }

                }

                result.add(Student(fullName, gradeLevel, searchString))
            }

            // RESULT IS TEACHER
            else if (planPageString.contains(Regex("[AB]-Woche-Stundenplan von Lehrer:in"))) {
                return SyncResponse(SyncResponse.RESULT_IS_TEACHER) } // TODO allow teacher

            // MULTIPLE RESULTS
            else if (planPageString.contains("Die Suche erbrachte mehrere Ergebnisse")) {

                // search has multiple options
                val selectionElement = planPage.selectFirst("select[id=chooseQuick]")
                    ?: return SyncResponse(SyncResponse.FAILED)
                val optionElements = selectionElement.select("option")
                for (i in 1 until optionElements.size) {
                    result.add( Student(
                        Regex("(?<=Schüler:in ).+(?= \\()")
                            .find(optionElements[i].ownText())?.value ?: continue,
                        with(Regex("(?<=\\().+")
                                .find(optionElements[i].ownText())?.value ?: continue
                        ) {
                            when {
                                startsWith("EF") -> GradeLevel.EF
                                startsWith("Q1") -> GradeLevel.Q1
                                startsWith("Q2") -> GradeLevel.Q2
                                else -> return SyncResponse(SyncResponse.FAILED)
                            }
                        },
                        searchString
                    ))
                }
            }

            // INVALID
            else { return SyncResponse(SyncResponse.FAILED) }
            return SyncResponse(SyncResponse.SUCCESS, result)
        }
    }

    data class SyncResponse(val state: Int, val data: MutableList<Student>? = null) {
        companion object {
            const val SUCCESS = 1
            const val FAILED = 0
            const val NO_RESULTS = -1
            const val TOO_MANY_RESULTS = -2
            const val SEARCH_TO_SHORT = -3
            const val RESULT_IS_TEACHER = -4
        }
    }
}