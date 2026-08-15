package mk.amazingapps.digitalphotoframe

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.util.Log
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import mk.amazingapps.digitalphotoframe.ui.theme.DigitalPhotoFrameTheme
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

data class Album(val id: String?, val name: String)
data class WeatherData(val temperature: String, val iconUrl: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Hide system bars (status bar, navigation bar)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        setContent {
            DigitalPhotoFrameTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    var hasPhotoPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPhotoPermission = isGranted
    }

    if (hasPhotoPermission) {
        PhotoFrameContent()
    } else {
        PermissionScreen("Permission required to access photos", "Grant Permission") {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            photoLauncher.launch(permission)
        }
    }
}

@Composable
fun PermissionScreen(message: String, buttonText: String, onRequestPermission: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message)
            Button(onClick = onRequestPermission, modifier = Modifier.padding(top = 16.dp)) {
                Text(buttonText)
            }
        }
    }
}

@Composable
fun PhotoFrameContent() {
    val context = LocalContext.current
    val images = remember { mutableStateListOf<Uri>() }
    var albums by remember { mutableStateOf(listOf<Album>()) }
    var currentImageIndex by rememberSaveable { mutableIntStateOf(0) }
    var slideShowIntervalSeconds by rememberSaveable { mutableLongStateOf(60L) }
    var selectedAlbumId by rememberSaveable { mutableStateOf<String?>(null) }
    var showMenu by rememberSaveable { mutableStateOf(false) }
    var menuInteractionTrigger by remember { mutableLongStateOf(0L) }
    
    // Clock & Weather Settings
    var clockSize by rememberSaveable { mutableFloatStateOf(48f) }
    var clockFontStyle by rememberSaveable { mutableStateOf("Sans") }
    var showClock by rememberSaveable { mutableStateOf(true) }
    var showWeather by rememberSaveable { mutableStateOf(false) }
    var isWeatherLoading by remember { mutableStateOf(false) }
    var shuffleImages by rememberSaveable { mutableStateOf(false) }
    var transitionType by rememberSaveable { mutableStateOf("Fade") }
    var weatherData by remember { mutableStateOf<WeatherData?>(null) }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showWeather = true
        }
    }

    // Load albums
    LaunchedEffect(Unit) {
        albums = loadAlbums(context)
    }

    // Load images when album changes
    LaunchedEffect(selectedAlbumId) {
        images.clear()
        val loadedImages = loadImages(context, selectedAlbumId)
        images.addAll(if (shuffleImages) loadedImages.shuffled() else loadedImages)
    }

    // Re-shuffle if shuffle setting changes
    LaunchedEffect(shuffleImages) {
        if (images.isNotEmpty()) {
            val currentList = images.toList()
            images.clear()
            images.addAll(if (shuffleImages) currentList.shuffled() else currentList.sortedByDescending { it.toString() })
        }
    }

    // Slideshow Timer
    LaunchedEffect(images.size, slideShowIntervalSeconds) {
        if (images.isNotEmpty()) {
            while (true) {
                delay(slideShowIntervalSeconds * 1000)
                if (images.isNotEmpty()) {
                    currentImageIndex = (currentImageIndex + 1) % images.size
                }
            }
        }
    }

    // Weather Update Timer
    LaunchedEffect(showWeather) {
        if (showWeather) {
            isWeatherLoading = weatherData == null
            while (showWeather) {
                val data = fetchWeather(context)
                isWeatherLoading = false
                if (data != null) weatherData = data
                delay(1800000) // 30 minutes
            }
        } else {
            weatherData = null
            isWeatherLoading = false
        }
    }

    // Hide menu after 7 seconds
    LaunchedEffect(showMenu, menuInteractionTrigger) {
        if (showMenu) {
            delay(7000)
            showMenu = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                showMenu = !showMenu
            }
    ) {
        if (images.isNotEmpty()) {
            AnimatedContent(
                targetState = currentImageIndex,
                transitionSpec = {
                    when (transitionType) {
                        "Slide" -> {
                            slideInHorizontally(animationSpec = tween(1000)) { it } + fadeIn(animationSpec = tween(1000)) togetherWith
                                    slideOutHorizontally(animationSpec = tween(1000)) { -it } + fadeOut(animationSpec = tween(1000))
                        }
                        "Zoom" -> {
                            scaleIn(initialScale = 0.8f, animationSpec = tween(1000)) + fadeIn(animationSpec = tween(1000)) togetherWith
                                    scaleOut(targetScale = 1.2f, animationSpec = tween(1000)) + fadeOut(animationSpec = tween(1000))
                        }
                        "None" -> {
                            EnterTransition.None togetherWith ExitTransition.None
                        }
                        else -> { // Fade
                            fadeIn(animationSpec = tween(1500)) togetherWith fadeOut(animationSpec = tween(1500))
                        }
                    }
                },
                label = "ImageTransition"
            ) { index ->
                if (index < images.size) {
                    AsyncImage(
                        model = images[index],
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No images found")
            }
        }

        // Overlay: Clock & Weather
        if (showClock || weatherData != null || isWeatherLoading) {
            ClockAndWeatherOverlay(
                modifier = Modifier.align(Alignment.BottomEnd),
                fontSize = clockSize,
                fontStyle = clockFontStyle,
                weatherData = weatherData,
                showClock = showClock,
                isLoading = isWeatherLoading
            )
        }

        // Settings Menu Overlay
        AnimatedVisibility(
            visible = showMenu,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            SettingsMenu(
                currentInterval = slideShowIntervalSeconds,
                onIntervalSelected = { 
                    slideShowIntervalSeconds = it 
                    menuInteractionTrigger++
                },
                albums = albums,
                selectedAlbumId = selectedAlbumId,
                onAlbumSelected = { 
                    selectedAlbumId = it
                    currentImageIndex = 0
                    menuInteractionTrigger++
                },
                clockSize = clockSize,
                onClockSizeSelected = { 
                    clockSize = it 
                    menuInteractionTrigger++
                },
                clockStyle = clockFontStyle,
                onClockStyleSelected = { 
                    clockFontStyle = it 
                    menuInteractionTrigger++
                },
                showClock = showClock,
                onShowClockChanged = { 
                    showClock = it 
                    menuInteractionTrigger++
                },
                showWeather = showWeather,
                onShowWeatherChanged = { enabled ->
                    if (enabled) {
                        val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (hasLocation) {
                            showWeather = true
                        } else {
                            locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                    } else {
                        showWeather = false
                    }
                    menuInteractionTrigger++
                },
                shuffleImages = shuffleImages,
                onShuffleChanged = {
                    shuffleImages = it
                    currentImageIndex = 0
                    menuInteractionTrigger++
                },
                transitionType = transitionType,
                onTransitionTypeSelected = {
                    transitionType = it
                    menuInteractionTrigger++
                }
            )
        }
    }
}

@Composable
fun SettingsMenu(
    currentInterval: Long,
    onIntervalSelected: (Long) -> Unit,
    albums: List<Album>,
    selectedAlbumId: String?,
    onAlbumSelected: (String?) -> Unit,
    clockSize: Float,
    onClockSizeSelected: (Float) -> Unit,
    clockStyle: String,
    onClockStyleSelected: (String) -> Unit,
    showClock: Boolean,
    onShowClockChanged: (Boolean) -> Unit,
    showWeather: Boolean,
    onShowWeatherChanged: (Boolean) -> Unit,
    shuffleImages: Boolean,
    onShuffleChanged: (Boolean) -> Unit,
    transitionType: String,
    onTransitionTypeSelected: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(0.9f)
            .fillMaxHeight(0.7f),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Switching interval:", style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.padding(top = 8.dp)) {
                listOf(10L, 30L, 60L, 300L).forEach { seconds ->
                    val label = when (seconds) {
                        10L -> "10s"
                        30L -> "30s"
                        60L -> "1m"
                        300L -> "5m"
                        else -> "${seconds}s"
                    }
                    FilterChip(
                        selected = currentInterval == seconds,
                        onClick = { onIntervalSelected(seconds) },
                        label = { Text(label) },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Select album (scroll left/right):", style = MaterialTheme.typography.titleMedium)
            LazyRow(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedAlbumId == null,
                        onClick = { onAlbumSelected(null) },
                        label = { Text("All images") },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                items(albums) { album ->
                    FilterChip(
                        selected = selectedAlbumId == album.id,
                        onClick = { onAlbumSelected(album.id) },
                        label = { Text(album.name) },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Clock size:", style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.padding(top = 8.dp)) {
                listOf(32f to "Small", 48f to "Medium", 72f to "Large").forEach { (size, label) ->
                    FilterChip(
                        selected = clockSize == size,
                        onClick = { onClockSizeSelected(size) },
                        label = { Text(label) },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Font style:", style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.padding(top = 8.dp)) {
                listOf("Sans", "Serif", "Mono").forEach { style ->
                    FilterChip(
                        selected = clockStyle == style,
                        onClick = { onClockStyleSelected(style) },
                        label = { Text(style) },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text("Show clock:", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.width(16.dp))
                Switch(checked = showClock, onCheckedChange = onShowClockChanged)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text("Show weather:", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.width(16.dp))
                Switch(checked = showWeather, onCheckedChange = onShowWeatherChanged)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text("Shuffle images:", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.width(16.dp))
                Switch(checked = shuffleImages, onCheckedChange = onShuffleChanged)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Transition effect:", style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.padding(top = 8.dp)) {
                listOf("Fade", "Slide", "Zoom", "None").forEach { type ->
                    FilterChip(
                        selected = transitionType == type,
                        onClick = { onTransitionTypeSelected(type) },
                        label = { Text(type) },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ClockAndWeatherOverlay(
    modifier: Modifier = Modifier,
    fontSize: Float,
    fontStyle: String,
    weatherData: WeatherData?,
    showClock: Boolean,
    isLoading: Boolean
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

@SuppressLint("MissingPermission")
suspend fun fetchWeather(context: Context): WeatherData? {
    return withContext(Dispatchers.IO) {
        try {
            Log.d("Weather", "Starting weather fetch...")
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            
            // Try last known location first (much faster)
            var location = fusedLocationClient.lastLocation.await()
            
            // If no last location, try to get a fresh one
            if (location == null) {
                Log.d("Weather", "No last location, requesting fresh location...")
                location = withTimeoutOrNull(10000) {
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
                }
            }
            
            if (location != null) {
                Log.d("Weather", "Location found: ${location.latitude}, ${location.longitude}")
                val apiKey = "06ccb8eddef7cbb14f57377c31607ffe"
                val urlString = "https://api.openweathermap.org/data/2.5/weather?lat=${location.latitude}&lon=${location.longitude}&units=metric&appid=$apiKey"
                
                val response = URL(urlString).readText()
                val json = JSONObject(response)
                val main = json.getJSONObject("main")
                val temp = main.getInt("temp").toString()
                
                val weatherArray = json.getJSONArray("weather")
                val iconCode = weatherArray.getJSONObject(0).getString("icon")
                val iconUrl = "https://openweathermap.org/img/wn/$iconCode@2x.png"
                
                Log.d("Weather", "Temperature fetched: $temp, Icon: $iconCode")
                WeatherData(temp, iconUrl)
            } else {
                Log.w("Weather", "Could not determine location (Timeout or GPS disabled)")
                null
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e("Weather", "Error fetching weather: ${e.message}")
            null
        }
    }
}

fun loadAlbums(context: Context): List<Album> {
    val albums = mutableListOf<Album>()
    val projection = arrayOf(
        MediaStore.Images.Media.BUCKET_ID,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME
    )
    
    context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
        null,
        null,
        "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} ASC"
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
        
        val seenIds = mutableSetOf<String>()
        while (cursor.moveToNext()) {
            val id = cursor.getString(idColumn)
            val name = cursor.getString(nameColumn)
            if (id != null && seenIds.add(id)) {
                albums.add(Album(id, name))
            }
        }
    }
    return albums
}

fun loadImages(context: Context, bucketId: String? = null): List<Uri> {
    val imageUris = mutableListOf<Uri>()
    val projection = arrayOf(MediaStore.Images.Media._ID)
    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
    
    val selection = bucketId?.let { "${MediaStore.Images.Media.BUCKET_ID} = ?" }
    val selectionArgs = bucketId?.let { arrayOf(it) }

    try {
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                imageUris.add(contentUri)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return imageUris
}
