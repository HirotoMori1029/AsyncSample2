package com.hirogram.asyncactivity

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.*
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.core.os.HandlerCompat
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.lang.StringBuilder
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    companion object {
        private const val DEBUG_TAG = "AsyncSample"
        private const val WEATHERINFO_URL = "https://api.openweathermap.org/data/2.5/weather?lang=ja"
        private const val APP_ID = "---"
    }

    private var _list: MutableList<MutableMap<String, String>> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        _list = createList()

        val lvCityList = findViewById<ListView>(R.id.lvCityList)
        val from = arrayOf("name")
        val to = intArrayOf(android.R.id.text1)
        val adapter = SimpleAdapter(this@MainActivity, _list, android.R.layout.
        simple_list_item_1, from, to)
        lvCityList.adapter = adapter
        lvCityList.onItemClickListener = ListItemClickListener()

    }

    private fun createList(): MutableList<MutableMap<String, String>> {
        val list: MutableList<MutableMap<String, String>> = mutableListOf()
        var city = mutableMapOf("name" to "大阪", "q" to "Osaka")
        list.add(city)
        city = mutableMapOf("name" to "神戸", "q" to "Kobe")
        list.add(city)
        Log.d(DEBUG_TAG, "createList() is working")

        return list
    }

    @UiThread
    private fun receiveWeatherInfo(urlFull: String) {
        val handler = HandlerCompat.createAsync(mainLooper)
        val backgroundReceiver = WeatherInfoBackgroundReceiver(handler, urlFull)
        val executeService = Executors.newSingleThreadExecutor()
        executeService.submit(backgroundReceiver)
        Log.d(DEBUG_TAG, "receiveWeatherInfo(urlFull) is working")
    }

    private inner class WeatherInfoBackgroundReceiver(handler: Handler, url: String): Runnable {
        val _handler = handler
        val _url = url
        @WorkerThread
        override fun run() {
            var result = ""
            val url = URL(_url)
            val con = url.openConnection() as? HttpURLConnection
            con?.let {
                try {
                    it.connectTimeout = 1000
                    it.readTimeout = 1000
                    it.requestMethod = "GET"
                    it.connect()
                    val stream = it.inputStream
                    result = is2String(stream)
                    stream.close()
                }
                catch (ex: SocketTimeoutException) {
                    Log.w(DEBUG_TAG, "通信タイムアウト",ex)
                }
                it.disconnect()
            }
            val postExecutor = WeatherInfoPostExecutor(result)
            _handler.post(postExecutor)
            Log.d(DEBUG_TAG, "WeatherInfoBackgroundReceiver.run() is working")
        }

        private fun is2String(stream: InputStream): String {
            val sb = StringBuilder()
            val reader = BufferedReader(InputStreamReader(stream, "UTF8"))
            var line = reader.readLine()
            while (line != null) {
                sb.append(line)
                line = reader.readLine()
            }
            reader.close()
            return sb.toString()
        }
    }

    private inner class WeatherInfoPostExecutor(result: String) :Runnable {

        private val _result = result
        @UiThread
        override fun run() {
            val rootJSON = JSONObject(_result)
            val cityName = rootJSON.getString("name")
            val coordJSON = rootJSON.getJSONObject("coord")
            val latitude = coordJSON.getString("lat")
            val longitude = coordJSON.getString("lon")
            val weatherJSONArray = rootJSON.getJSONArray("weather")
            val weatherJSON = weatherJSONArray.getJSONObject(0)
            val weather = weatherJSON.getString("description")
            val telop = "${cityName}の天気"
            val desc = "現在の天気は${weather}です。緯度: ${latitude}, 経度: $longitude"
            val tvWeatherTelop = findViewById<TextView>(R.id.tvWeatherTelop)
            val tvWeatherDesc  =findViewById<TextView>(R.id.tvWeatherDesc)
            tvWeatherTelop.text = telop
            tvWeatherDesc.text = desc
            Log.d(DEBUG_TAG, "WeatherInfoPostExecutor is working")
        }
    }

    private inner class ListItemClickListener: AdapterView.OnItemClickListener {
        override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            val item = _list.get(position)
            val q = item.get("q")
            q?.let {
                val urlFull = "$WEATHERINFO_URL&q=${q}&appid=$APP_ID"
                receiveWeatherInfo(urlFull)
            }
            Log.d(DEBUG_TAG, "onItemClick is working")
        }
    }
}