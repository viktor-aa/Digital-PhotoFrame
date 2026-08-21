package mk.amazingapps.digitalphotoframe

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

object LocationUtil {

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): Location? {
        return try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            var location = fusedLocationClient.lastLocation.await()

            if (location == null) {
                location = withTimeoutOrNull(10000) {
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
                }
            }
            location
        } catch (e: Exception) {
            null
        }
    }

    fun getLocationName(context: Context, latitude: Double, longitude: Double): String? {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                addr.locality ?: addr.adminArea ?: addr.countryName
            } else {
                null
            }
        } catch (e: Exception) {
            String.format(Locale.getDefault(), "%.3f, %.3f", latitude, longitude)
        }
    }
}
