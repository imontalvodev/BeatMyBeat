package com.imontalvodev.beatmybeat.ui.feature.player

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.app.Activity
import android.app.RecoverableSecurityException
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.outlined.Loop
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import android.provider.MediaStore
import androidx.media3.common.Player
import java.io.File
import java.net.URLDecoder
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import com.imontalvodev.beatmybeat.R
import com.imontalvodev.beatmybeat.ui.data.DeviceTrack
import com.imontalvodev.beatmybeat.ui.network.LyricsCache
import com.imontalvodev.beatmybeat.ui.network.LyricsFetcher
import com.imontalvodev.beatmybeat.ui.network.LrcLine
import com.imontalvodev.beatmybeat.ui.network.LrcParser
import com.imontalvodev.beatmybeat.ui.network.ArtworkCache
import com.imontalvodev.beatmybeat.ui.network.BitmapDecoding
import com.imontalvodev.beatmybeat.ui.theme.Motion
import com.imontalvodev.beatmybeat.ui.theme.AppLogo
import com.imontalvodev.beatmybeat.ui.theme.TrackListSkeleton
import com.imontalvodev.beatmybeat.ui.theme.currentBeatMyBeatThemeProfile
import com.imontalvodev.beatmybeat.ui.theme.AppMiniBrand
import com.imontalvodev.beatmybeat.playback.LocalPlaybackService
import com.imontalvodev.beatmybeat.service.PlaybackArtworkHelper
import com.imontalvodev.beatmybeat.service.PlaybackService
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

@Composable
internal fun ArtworkThumbnail(
    track: DeviceTrack,
    sizeDp: Int,
    isPlaceholderOnly: Boolean = false,
) {
    val context = LocalContext.current
    var imageData by remember(track.uri) { mutableStateOf<Any?>(ArtworkCache.getUri(track.uri)) }

    LaunchedEffect(track.uri) {
        if (imageData != null) return@LaunchedEffect
        val bytes = withContext(Dispatchers.IO) {
            PlaybackArtworkHelper.resolveArtworkBytes(context, track.uri)
        }
        if (bytes == null || bytes.isEmpty()) {
            imageData = R.drawable.logo
            return@LaunchedEffect
        }
        val decoded = withContext(Dispatchers.IO) {
            BitmapDecoding.decodeSampled(bytes, LIST_ARTWORK_MAX_PX)
        }
        if (decoded != null) {
            ArtworkCache.putUri(track.uri, decoded)
            imageData = decoded
        } else {
            imageData = R.drawable.logo
        }
    }

    if (isPlaceholderOnly) {
        Box(modifier = Modifier.size(sizeDp.dp))
    } else {
        val fallback = R.drawable.logo
        Crossfade(
            targetState = imageData,
            animationSpec = tween(Motion.STANDARD),
            label = "row_artwork_cf",
        ) { data ->
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(data ?: fallback)
                    .crossfade(180)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .size(sizeDp.dp)
                    .border(0.dp, Color.Transparent),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(fallback),
                error = painterResource(fallback),
            )
        }
    }
}

internal fun androidArgbIntToComposeColor(argb: Int): Color =
    Color(
        red = AndroidColor.red(argb) / 255f,
        green = AndroidColor.green(argb) / 255f,
        blue = AndroidColor.blue(argb) / 255f,
        alpha = AndroidColor.alpha(argb) / 255f,
    )

/**
 * El título y el artista se dibujan sobre el gradiente (sin tarjeta). Si el color extraído de la
 * carátula es muy claro, el [MaterialTheme.colorScheme.onSurface] claro del tema queda ilegible.
 */
internal fun expandedPlayerOverlayGradientTop(artworkColor: Color, themeTop: Color): Color {
    val opaque = artworkColor.copy(alpha = 1f)
    val lum = 0.299f * opaque.red + 0.587f * opaque.green + 0.114f * opaque.blue
    val towardTheme = if (lum > 0.38f) 0.72f else 0.22f
    return lerp(opaque, themeTop, towardTheme).copy(alpha = 0.82f)
}

