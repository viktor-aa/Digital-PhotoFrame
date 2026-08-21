package mk.amazingapps.digitalphotoframe

import android.annotation.SuppressLint
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

data class WeatherData(val temperature: String, val iconUrl: String)

object WeatherManager {
    private const val API_KEY = "06ccb8eddef7cbb14f57377c31607ffe"

    @SuppressLint("MissingPermission")
    suspend fun fetchWeather(context: Context): WeatherData? {
        return withContext(Dispatchers.IO) {
            try {
                val location = LocationUtil.getCurrentLocation(context)
                
                if (location != null) {
                    val urlString = "https://api.openweathermap.org/data/2.5/weather?lat=${location.latitude}&lon=${location.longitude}&units=metric&appid=$API_KEY"
                    
                    val response = URL(urlString).readText()
                    val json = JSONObject(response)
                    val main = json.getJSONObject("main")
                    val temp = main.getInt("temp").toString()
                    
                    val weatherArray = json.getJSONArray("weather")
                    val iconCode = weatherArray.getJSONObject(0).getString("icon")
                    val iconUrl = "https://openweathermap.org/img/wn/$iconCode@2x.png"
                    
                    WeatherData(temp, iconUrl)
                } else {
                    null
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                null
            }
        }
    }
}
