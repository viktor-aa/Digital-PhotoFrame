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
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.exifinterface.media.ExifInterface
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
data class ImageMetadata(val dateTaken: String?, val location: String?)

enum class ImageSource {
    LOCAL, REMOTE
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
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
        PermissionScreen(
            stringResource(R.string.permission_photos_required),
            stringResource(R.string.grant_permission)
        ) {
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
    var imageSource by rememberSaveable { mutableStateOf(ImageSource.LOCAL) }
    var remoteListUrl by rememberSaveable { mutableStateOf("") }
    var showMenu by rememberSaveable { mutableStateOf(false) }
    var menuInteractionTrigger by remember { mutableLongStateOf(0L) }
    
    var clockSize by rememberSaveable { mutableFloatStateOf(48f) }
    var clockFontStyle by rememberSaveable { mutableStateOf("Sans") }
    var showClock by rememberSaveable { mutableStateOf(true) }
    var showWeather by rememberSaveable { mutableStateOf(false) }
    var showPhotoLocation by rememberSaveable { mutableStateOf(true) }
    var showPhotoDate by rememberSaveable { mutableStateOf(true) }
    var isWeatherLoading by remember { mutableStateOf(false) }
    var shuffleImages by rememberSaveable { mutableStateOf(false) }
    var transitionType by rememberSaveable { mutableStateOf("Fade") }
    var weatherData by remember { mutableStateOf<WeatherData?>(null) }
    var currentImageMetadata by remember { mutableStateOf<ImageMetadata?>(null) }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showWeather = true
        }
    }

    LaunchedEffect(Unit) {
        if (imageSource == ImageSource.LOCAL) {
            albums = loadAlbums(context)
        }
    }

    LaunchedEffect(selectedAlbumId, imageSource, remoteListUrl) {
        if (imageSource == ImageSource.LOCAL) {
            images.clear()
            val loadedImages = loadImages(context, selectedAlbumId)
            images.addAll(if (shuffleImages) loadedImages.shuffled() else loadedImages)
        } else if (remoteListUrl.isNotBlank()) {
            images.clear()
            val remoteImages = fetchRemoteImages(remoteListUrl)
            
            val prefs = context.getSharedPreferences("photo_frame_prefs", Context.MODE_PRIVATE)
            if (remoteImages.isNotEmpty()) {
                prefs.edit().putString("cached_remote_list", remoteImages.joinToString(",")).apply()
                images.addAll(if (shuffleImages) remoteImages.shuffled() else remoteImages)
            } else {
                val cachedData = prefs.getString("cached_remote_list", null)
                if (!cachedData.isNullOrBlank()) {
                    val cachedUris = cachedData.split(",").map { it.toUri() }
                    images.addAll(if (shuffleImages) cachedUris.shuffled() else cachedUris)
                }
            }
            currentImageIndex = 0
        }
    }

    LaunchedEffect(currentImageIndex, images.size) {
        if (images.isNotEmpty() && currentImageIndex < images.size) {
            currentImageMetadata = getImageMetadata(context, images[currentImageIndex])
        }
    }

    LaunchedEffect(shuffleImages) {
        if (images.isNotEmpty()) {
            val currentList = images.toList()
            images.clear()
            images.addAll(if (shuffleImages) currentList.shuffled() else currentList.sortedByDescending { it.toString() })
        }
    }

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

    LaunchedEffect(showWeather) {
        if (showWeather) {
            isWeatherLoading = weatherData == null
            while (showWeather) {
                val data = fetchWeather(context)
                isWeatherLoading = false
                if (data != null) weatherData = data
                delay(1800000) 
            }
        } else {
            weatherData = null
            isWeatherLoading = false
        }
    }

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
                        else -> {
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
                Text(stringResource(R.string.no_images_found), color = Color.Gray)
            }
        }

        if (showClock || weatherData != null || isWeatherLoading || ((showPhotoLocation || showPhotoDate) && currentImageMetadata != null)) {
            ClockAndWeatherOverlay(
                modifier = Modifier.align(Alignment.BottomEnd),
                fontSize = clockSize,
                fontStyle = clockFontStyle,
                weatherData = weatherData,
                showClock = showClock,
                isLoading = isWeatherLoading,
                metadata = currentImageMetadata,
                showLocation = showPhotoLocation,
                showDate = showPhotoDate
            )
        }

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
                showLocation = showPhotoLocation,
                onShowLocationChanged = {
                    showPhotoLocation = it
                    menuInteractionTrigger++
                },
                showDate = showPhotoDate,
                onShowDateChanged = {
                    showPhotoDate = it
                    menuInteractionTrigger++
                },
                imageSource = imageSource,
                onImageSourceChanged = {
                    imageSource = it
                    currentImageIndex = 0
                    menuInteractionTrigger++
                },
                remoteListUrl = remoteListUrl,
                onRemoteListUrlChanged = {
                    remoteListUrl = it
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
    showLocation: Boolean,
    onShowLocationChanged: (Boolean) -> Unit,
    showDate: Boolean,
    onShowDateChanged: (Boolean) -> Unit,
    imageSource: ImageSource,
    onImageSourceChanged: (ImageSource) -> Unit,
    remoteListUrl: String,
    onRemoteListUrlChanged: (String) -> Unit,
    transitionType: String,
    onTransitionTypeSelected: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(0.95f)
            .fillMaxHeight(0.85f),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 12.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            SettingsSectionTitle(stringResource(R.string.image_source_section))
            Row(modifier = Modifier.padding(vertical = 12.dp)) {
                ImageSource.entries.forEach { source ->
                    FilterChip(
                        selected = imageSource == source,
                        onClick = { onImageSourceChanged(source) },
                        label = { 
                            Text(if (source == ImageSource.LOCAL) stringResource(R.string.local_gallery) else stringResource(R.string.remote_list_url)) 
                        },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            if (imageSource == ImageSource.REMOTE) {
                OutlinedTextField(
                    value = remoteListUrl,
                    onValueChange = onRemoteListUrlChanged,
                    label = { Text(stringResource(R.string.remote_list_label)) },
                    placeholder = { Text(stringResource(R.string.remote_list_placeholder)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Text(
                    text = stringResource(R.string.remote_list_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 16.dp)
                )
            } else {
                SettingsSectionTitle(stringResource(R.string.albums_section))
                LazyRow(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedAlbumId == null,
                            onClick = { onAlbumSelected(null) },
                            label = { Text(stringResource(R.string.all_images)) },
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
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), thickness = 0.5.dp)

            SettingsSectionTitle(stringResource(R.string.slideshow_settings_section))
            
            Text(stringResource(R.string.switching_interval), style = MaterialTheme.typography.bodyMedium)
            Row(modifier = Modifier.padding(vertical = 12.dp)) {
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

            SettingToggleItem(stringResource(R.string.shuffle_images), shuffleImages, onShuffleChanged)
            
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.transition_effect), style = MaterialTheme.typography.bodyMedium)
            Row(modifier = Modifier.padding(vertical = 12.dp)) {
                listOf("Fade", "Slide", "Zoom", "None").forEach { type ->
                    val label = when(type) {
                        "Fade" -> stringResource(R.string.transition_fade)
                        "Slide" -> stringResource(R.string.transition_slide)
                        "Zoom" -> stringResource(R.string.transition_zoom)
                        else -> stringResource(R.string.transition_none)
                    }
                    FilterChip(
                        selected = transitionType == type,
                        onClick = { onTransitionTypeSelected(type) },
                        label = { Text(label) },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), thickness = 0.5.dp)

            SettingsSectionTitle(stringResource(R.string.clock_weather_section))
            
            SettingToggleItem(stringResource(R.string.show_clock), showClock, onShowClockChanged)
            SettingToggleItem(stringResource(R.string.show_weather), showWeather, onShowWeatherChanged)

            if (showClock) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.clock_size), style = MaterialTheme.typography.bodyMedium)
                Row(modifier = Modifier.padding(vertical = 12.dp)) {
                    listOf(32f to R.string.size_small, 48f to R.string.size_medium, 72f to R.string.size_large).forEach { (size, labelRes) ->
                        FilterChip(
                            selected = clockSize == size,
                            onClick = { onClockSizeSelected(size) },
                            label = { Text(stringResource(labelRes)) },
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }

                Text(stringResource(R.string.font_style), style = MaterialTheme.typography.bodyMedium)
                Row(modifier = Modifier.padding(vertical = 12.dp)) {
                    listOf("Sans" to R.string.style_sans, "Serif" to R.string.style_serif, "Mono" to R.string.style_mono).forEach { (style, labelRes) ->
                        FilterChip(
                            selected = clockStyle == style,
                            onClick = { onClockStyleSelected(style) },
                            label = { Text(stringResource(labelRes)) },
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), thickness = 0.5.dp)

            SettingsSectionTitle(stringResource(R.string.photo_info_section))
            SettingToggleItem(stringResource(R.string.show_location), showLocation, onShowLocationChanged)
            SettingToggleItem(stringResource(R.string.show_date), showDate, onShowDateChanged)
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
fun SettingToggleItem(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

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

@SuppressLint("MissingPermission")
suspend fun fetchWeather(context: Context): WeatherData? {
    return withContext(Dispatchers.IO) {
        try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            var location = fusedLocationClient.lastLocation.await()
            
            if (location == null) {
                location = withTimeoutOrNull(10000) {
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
                }
            }
            
            if (location != null) {
                val apiKey = "06ccb8eddef7cbb14f57377c31607ffe"
                val urlString = "https://api.openweathermap.org/data/2.5/weather?lat=${location.latitude}&lon=${location.longitude}&units=metric&appid=$apiKey"
                
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

suspend fun fetchRemoteImages(url: String): List<Uri> {
    return withContext(Dispatchers.IO) {
        try {
            val cleanUrl = url.trim()
            val finalUrl = if (cleanUrl.contains("drive.google.com")) {
                val fileId = extractGoogleDriveId(cleanUrl)
                if (fileId != null) "https://drive.google.com/uc?export=download&id=$fileId" else cleanUrl
            } else {
                cleanUrl
            }

            val content = URL(finalUrl).readText()
            urisFromContent(content)
        } catch (e: Exception) {
            emptyList()
        }
    }
}

private fun urisFromContent(content: String): List<Uri> {
    return content.split(Regex("[,\\n\\r]+"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { link ->
            if (link.contains("drive.google.com")) {
                val fileId = extractGoogleDriveId(link)
                if (fileId != null) {
                    "https://drive.google.com/uc?export=download&id=$fileId".toUri()
                } else {
                    link.toUri()
                }
            } else {
                link.toUri()
            }
        }
}

fun extractGoogleDriveId(url: String): String? {
    val regex1 = "/file/d/([^/]+)".toRegex()
    val regex2 = "id=([^&]+)".toRegex()
    
    return regex1.find(url)?.groupValues?.get(1) 
        ?: regex2.find(url)?.groupValues?.get(1)
}

suspend fun getImageMetadata(context: Context, uri: Uri): ImageMetadata? {
    return withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                val dateTaken = exif.getAttribute(ExifInterface.TAG_DATETIME)
                
                val latLong = FloatArray(2)
                val hasLocation = exif.getLatLong(latLong)
                
                var locationName: String? = null
                if (hasLocation) {
                    try {
                        val geocoder = android.location.Geocoder(context, Locale.getDefault())
                        val addresses = geocoder.getFromLocation(latLong[0].toDouble(), latLong[1].toDouble(), 1)
                        if (!addresses.isNullOrEmpty()) {
                            val addr = addresses[0]
                            locationName = addr.locality ?: addr.adminArea ?: addr.countryName
                        }
                    } catch (e: Exception) {
                        locationName = String.format(Locale.getDefault(), "%.3f, %.3f", latLong[0], latLong[1])
                    }
                }

                val formattedDate = dateTaken?.let {
                    try {
                        val parser = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault())
                        val formatter = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
                        parser.parse(it)?.let { date -> formatter.format(date) }
                    } catch (e: Exception) {
                        it
                    }
                }

                ImageMetadata(formattedDate, locationName)
            }
        } catch (e: Exception) {
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
        // Silently fail
    }
    return imageUris
}
