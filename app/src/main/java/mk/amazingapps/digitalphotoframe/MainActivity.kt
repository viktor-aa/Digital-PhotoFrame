package mk.amazingapps.digitalphotoframe

import android.Manifest
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.exifinterface.media.ExifInterface
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import mk.amazingapps.digitalphotoframe.ui.theme.DigitalPhotoFrameTheme
import java.text.SimpleDateFormat
import java.util.*

data class Album(val id: String?, val name: String)
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
            val remoteImages = RemoteGalleryManager.fetchRemoteImages(remoteListUrl)
            
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
                val data = WeatherManager.fetchWeather(context)
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
