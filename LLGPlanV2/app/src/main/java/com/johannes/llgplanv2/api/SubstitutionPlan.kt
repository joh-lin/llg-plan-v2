package com.johannes.llgplanv2.api

import android.content.Context
import com.google.gson.Gson
import com.google.gson.internal.LinkedTreeMap
import com.google.gson.reflect.TypeToken
import com.johannes.llgplanv2.MainActivity
import org.jsoup.Connection
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream


class SubstitutionPlan() {
    var lastUpdated = "" // format: "dd.MM.yyyy-HH:mm:ss-Z"
    // MutableMap<date, substitutions>
    var table: MutableMap<String, MutableList<Substitution>>? = null

    data class Substitution(
        val class_: String,
        val lesson: Int,
        val newTeacher: String,
        val newSubject: String,
        val oldSubject: String,
        var comment: String,
        val type: String,
        val room: String
    )

    @Throws(HttpStatusException::class, SocketTimeoutException::class)
    fun sync(): SyncResponse {
        // based on api from nerrixDE:
        // https://github.com/nerrixDE/DSBApi/blob/master/dsbapi/__init__.py
        val gson = Gson()

        // get current time in iso format
        val currentTime = "${SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SS", Locale.GERMANY)
            .format(Calendar.getInstance(Locale.GERMANY).time)}Z"


        val loginUser = MainActivity.sharedPref.getString("dsb_login_user", null) ?: "invalid"
        val loginPassword = MainActivity.sharedPref.getString("dsb_login_password", null) ?: "invalid"

        // parameters for server
        val params = HashMap<String, String>()
        params["UserId"] = loginUser
        params["UserPW"] = loginPassword
        params["AppVersion"] = "2.5.9"
        params["Language"] = "de"
        params["OsVersion"] = "27.8.1.0"
        params["AppId"] = UUID.randomUUID().toString()
        params["Device"] = "Nexus 4"
        params["BundleId"] = "de.heinekingmedia.dsbmobile"
        params["Data"] = currentTime
        params["LastUpdate"] = currentTime

        // convert params to json
        val paramsJson = gson.toJson(params)
        val byteArrayOutputStream = ByteArrayOutputStream()
        GZIPOutputStream(byteArrayOutputStream)
            .bufferedWriter().use { it.write(paramsJson)}
        val paramsGzip = byteArrayOutputStream.toByteArray()
        val paramsBase64 = Base64.getEncoder().encode(paramsGzip)
        val paramsCompressed = paramsBase64.decodeToString()

        val finalData = gson.toJson (hashMapOf(
            "req" to hashMapOf(
                "Data" to paramsCompressed,
                "DataType" to 1
            )
        ))

        // request server
        // https://dirask.com/posts/Java-how-to-make-Jsoup-http-POST-request-with-json-body-payload-and-get-json-as-response-QD93Vj
        val response = Jsoup.connect("https://app.dsbcontrol.de/JsonHandler.ashx/GetData")
            .header("Content-Type", "application/json;charset=utf-8")
            .header("Accept-Encoding", "gzip, deflate")
            .method(Connection.Method.POST)
            .ignoreContentType(true)
            .userAgent("Dalvik/2.1.0 (Linux; U; Android 8.1.0; Nexus 4 Build/OPM7.181205.001)")
            .requestBody(finalData)
            .maxBodySize(1_000_000 * 30) // 30mb ~
            .timeout(DataManager.substitutionPlanTimeoutDuration)
            .execute()

        // decompress response
        val type = object : TypeToken<HashMap<String, String>>(){}.type
        val responseJson: HashMap<String, String> = gson.fromJson(response.body(), type)
        val responseCompressed = responseJson["d"]
        val responseGzip = Base64.getDecoder().decode(responseCompressed)
        val bais = ByteArrayInputStream(responseGzip)
        var responseDecompressed: String
        GZIPInputStream(bais).bufferedReader().use { responseDecompressed = it.readText() }
        val responseData = gson.fromJson(responseDecompressed, HashMap::class.java)

        // validate response
        if (responseData["Resultcode"] != 0.0) {
            return SyncResponse.FAILED
        }

        // extract timetable page url
        // inspired by yandere dev :)
        var url = ""
        for (menuItem in (responseData["ResultMenuItems"] as List<*>)) {
            val childs = (menuItem as LinkedTreeMap<*, *>)["Childs"] as List<*>
            for (page in childs) {
                val root = (page as LinkedTreeMap<*, *>)["Root"]
                if (root != null) {
                    for (child1 in ((root as LinkedTreeMap<*, *>)["Childs"] as ArrayList<*>)) {
                        for (child2 in (child1 as LinkedTreeMap<*, *>)["Childs"] as ArrayList<*>) {
                            val detail: String = (child2 as LinkedTreeMap<*, *>)["Detail"].toString()
                            if (detail.endsWith(".htm") && !detail.endsWith("news.htm")) {
                                url = detail
                            }
                        }
                    }
                }
            }
        }


        // now you have to url of the substitution plan page,
        // so just download
        val substPage = Jsoup.connect(url).execute()
        substPage.charset("ISO-8859-1")


        // -------------------- PARSE --------------------
        val substitutionPage = substPage.parse()
        val tableTitles = substitutionPage.select("div.mon_title")
        val tableInfos = substitutionPage.select("table.info")
        val tables = substitutionPage.select("table.mon_list")
        // iterate over all tables
        val substTable = mutableMapOf<String, MutableList<Substitution>>()
        for (i in tableTitles.indices) {
            val substitutions = mutableListOf<Substitution>()
            val title = tableTitles[i].ownText().split(" ").first()
            if (title == "") return SyncResponse.FAILED

            // go over table rows
            val lastEntries = mutableListOf<Substitution>() // used for adding comment rows
            for (tr in tables[i].select("tr")) {
                val items = tr.select("td.list")
                if (items.size > 1) {
                    // if first item of row is empty add comment to previous
                    if (items[0].text() == "") {
                        if (substitutions.isNotEmpty()) {
                            for (last in lastEntries) {
                                last.comment += "\n${items[5].text()}"
                            }
                            continue
                        }
                        else return SyncResponse.FAILED
                    }

                    // create + fill substitution
                    lastEntries.clear()
                    for (l in 0..( if ("-" in items[1].text()) 1 else 0 )) {
                        substitutions.add( Substitution(
                            items[0].text().replace("---", ""),
                            items[1].text() .replace(" ", "")
                                .split("-")[l].toInt(),
                            items[2].text().replace("---", ""),
                            items[3].text().replace("---", ""),
                            items[4].text().replace("---", ""),
                            items[5].text().replace("---", ""),
                            items[6].text().replace("---", ""),
                            items[7].text().replace("---", ""),
                        ))
                        lastEntries.add(substitutions.last())
                    }
                }
            }
            substTable[title] = substitutions
        }
        table = substTable
        lastUpdated = CalendarUtils.dateToString(Calendar.getInstance(Locale.GERMANY).time)
        return SyncResponse.SUCCESS
    }

    fun save(context: Context) {
        val file = File(context.filesDir, DataManager.substitutionPlanFilePath)
        if (!file.exists()) file.createNewFile()
        file.writeText(Gson().toJson(DataSaveContainer(lastUpdated, table)))
    }

    fun load(context: Context): Boolean {
        val file = File(context.filesDir, DataManager.substitutionPlanFilePath)
        if (!file.exists()) return false
        val text = file.readText()
        val type = object : TypeToken<DataSaveContainer<
                MutableMap<String, MutableList<Substitution>>?>>(){}.type
        val result = Gson().fromJson<DataSaveContainer<
                MutableMap<String, MutableList<Substitution>>?>>(text, type)
        lastUpdated = result.date
        table = result.data
        return true
    }

    enum class SyncResponse {
        SUCCESS, FAILED
    }
}