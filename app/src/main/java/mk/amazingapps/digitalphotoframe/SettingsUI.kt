package mk.amazingapps.digitalphotoframe

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsOverlay(
    showMenu: Boolean,
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
    AnimatedVisibility(
        visible = showMenu,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 16.dp),
        label = "SettingsMenuVisibility"
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            SettingsMenu(
                currentInterval = currentInterval,
                onIntervalSelected = onIntervalSelected,
                albums = albums,
                selectedAlbumId = selectedAlbumId,
                onAlbumSelected = onAlbumSelected,
                clockSize = clockSize,
                onClockSizeSelected = onClockSizeSelected,
                clockStyle = clockStyle,
                onClockStyleSelected = onClockStyleSelected,
                showClock = showClock,
                onShowClockChanged = onShowClockChanged,
                showWeather = showWeather,
                onShowWeatherChanged = onShowWeatherChanged,
                shuffleImages = shuffleImages,
                onShuffleChanged = onShuffleChanged,
                showLocation = showLocation,
                onShowLocationChanged = onShowLocationChanged,
                showDate = showDate,
                onShowDateChanged = onShowDateChanged,
                imageSource = imageSource,
                onImageSourceChanged = onImageSourceChanged,
                remoteListUrl = remoteListUrl,
                onRemoteListUrlChanged = onRemoteListUrlChanged,
                transitionType = transitionType,
                onTransitionTypeSelected = onTransitionTypeSelected
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
