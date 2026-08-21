package mk.amazingapps.digitalphotoframe

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ClockAndWeatherOverlay(
    modifier: Modifier = Modifier,
    fontSize: Float,
    fontStyle: String,
    weatherData: WeatherData?,
    showClock: Boolean,
    isLoading: Boolean,
    metadata: ImageMetadata?,
    showLocation: Boolean,
    showDate: Boolean
) {
    var currentTime by remember { mutableStateOf(Calendar.getInstance().time) }
    
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Calendar.getInstance().time
            delay(1000)
        }
    }

    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val dateFormat = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())

    val textShadow = Shadow(
        color = Color.Black,
        offset = Offset(2f, 2f),
        blurRadius = 4f
    )

    val fontFamily = when(fontStyle) {
        "Serif" -> FontFamily.Serif
        "Mono" -> FontFamily.Monospace
        else -> FontFamily.SansSerif
    }

    Column(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(bottom = 32.dp, end = 32.dp)
            .padding(8.dp),
        horizontalAlignment = Alignment.End
    ) {
        metadata?.let { info ->
            if ((showDate && info.dateTaken != null) || (showLocation && info.location != null)) {
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    if (showLocation) {
                        info.location?.let { loc ->
                            Text(
                                text = loc,
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = (fontSize * 0.35).sp,
                                fontFamily = fontFamily,
                                style = MaterialTheme.typography.bodySmall.copy(shadow = textShadow)
                            )
                        }
                    }
                    if (showDate) {
                        info.dateTaken?.let { date ->
                            Text(
                                text = date,
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = (fontSize * 0.3).sp,
                                fontFamily = fontFamily,
                                style = MaterialTheme.typography.bodySmall.copy(shadow = textShadow)
                            )
                        }
                    }
                }
            }
        }

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size((fontSize * 0.5).dp)
                    .padding(bottom = 8.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
        } else if (weatherData != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = weatherData.iconUrl,
                    contentDescription = null,
                    modifier = Modifier.size((fontSize * 0.8).dp)
                )
                Text(
                    text = "${weatherData.temperature}°C",
                    color = Color.White,
                    fontSize = (fontSize * 0.6).sp,
                    fontFamily = fontFamily,
                    style = MaterialTheme.typography.headlineSmall.copy(shadow = textShadow)
                )
            }
        }
        if (showClock) {
            Text(
                text = timeFormat.format(currentTime),
                color = Color.White,
                fontSize = fontSize.sp,
                fontFamily = fontFamily,
                style = MaterialTheme.typography.headlineLarge.copy(shadow = textShadow)
            )
            Text(
                text = dateFormat.format(currentTime),
                color = Color.White,
                fontSize = (fontSize * 0.4).sp,
                fontFamily = fontFamily,
                style = MaterialTheme.typography.bodyMedium.copy(shadow = textShadow)
            )
        }
    }
}
