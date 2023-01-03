package com.zionhuang.music.compose.component

import android.app.SearchManager
import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zionhuang.music.R
import com.zionhuang.music.compose.LocalPlayerConnection
import com.zionhuang.music.compose.component.shimmer.ShimmerHost
import com.zionhuang.music.compose.component.shimmer.TextPlaceholder
import com.zionhuang.music.compose.screens.settings.LyricsPosition
import com.zionhuang.music.compose.utils.fadingEdge
import com.zionhuang.music.constants.LYRICS_TEXT_POSITION
import com.zionhuang.music.db.entities.LyricsEntity
import com.zionhuang.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.zionhuang.music.extensions.mutablePreferenceState
import com.zionhuang.music.models.MediaMetadata
import com.zionhuang.music.repos.SongRepository
import com.zionhuang.music.utils.lyrics.LyricsEntry
import com.zionhuang.music.utils.lyrics.LyricsHelper
import com.zionhuang.music.utils.lyrics.LyricsUtils.parseLyrics
import com.zionhuang.music.viewmodels.LyricsMenuViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

@Composable
fun Lyrics(
    sliderPositionProvider: () -> Long?,
    mediaMetadataProvider: () -> MediaMetadata,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val lyricsTextPosition by mutablePreferenceState(LYRICS_TEXT_POSITION, LyricsPosition.CENTER)

    val lyricsEntity by playerConnection.currentLyrics.collectAsState(initial = null)
    val lyrics = remember(lyricsEntity) {
        lyricsEntity?.lyrics
    }

    val lines = remember(lyrics) {
        if (lyrics == null || lyrics == LYRICS_NOT_FOUND) emptyList()
        else if (lyrics.startsWith("[")) listOf(LyricsEntry(0L, "")) + parseLyrics(lyrics)
        else lyrics.lines().mapIndexed { index, line -> LyricsEntry(index * 100L, line) }
    }
    val isSynced = remember(lyrics) {
        !lyrics.isNullOrEmpty() && lyrics.startsWith("[")
    }

    var currentLineIndex by remember {
        mutableStateOf(-1)
    }
    // Because LaunchedEffect has delay, which leads to inconsistent with current line color and scroll animation,
    // we use deferredCurrentLineIndex when user is scrolling
    var deferredCurrentLineIndex by rememberSaveable {
        mutableStateOf(0)
    }

    var lastPreviewTime by rememberSaveable {
        mutableStateOf(0L)
    }
    var isSeeking by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(lyrics) {
        if (lyrics.isNullOrEmpty() || !lyrics.startsWith("[")) {
            currentLineIndex = -1
            return@LaunchedEffect
        }
        while (isActive) {
            delay(50)
            val sliderPosition = sliderPositionProvider()
            isSeeking = sliderPosition != null
            currentLineIndex = findCurrentLineIndex(lines, sliderPosition ?: playerConnection.player.currentPosition)
        }
    }

    LaunchedEffect(isSeeking, lastPreviewTime) {
        if (isSeeking) {
            lastPreviewTime = 0L
        } else if (lastPreviewTime != 0L) {
            delay(LyricsPreviewTime)
            lastPreviewTime = 0L
        }
    }

    val lazyListState = rememberLazyListState()

    LaunchedEffect(currentLineIndex, lastPreviewTime) {
        if (!isSynced) return@LaunchedEffect
        if (currentLineIndex != -1) {
            deferredCurrentLineIndex = currentLineIndex
            if (lastPreviewTime == 0L) {
                if (isSeeking) {
                    lazyListState.scrollToItem(currentLineIndex, with(density) { 36.dp.toPx().toInt() })
                } else {
                    lazyListState.animateScrollToItem(currentLineIndex, with(density) { 36.dp.toPx().toInt() })
                }
            }
        }
    }

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 12.dp)
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = WindowInsets.systemBars
                .only(WindowInsetsSides.Top)
                .add(WindowInsets(top = maxHeight / 2, bottom = maxHeight / 2))
                .asPaddingValues(),
            modifier = Modifier
                .fadingEdge(vertical = 64.dp)
                .nestedScroll(remember {
                    object : NestedScrollConnection {
                        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                            lastPreviewTime = System.currentTimeMillis()
                            return super.onPostScroll(consumed, available, source)
                        }

                        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                            lastPreviewTime = System.currentTimeMillis()
                            return super.onPostFling(consumed, available)
                        }
                    }
                })
        ) {
            val displayedCurrentLineIndex = if (isSeeking) deferredCurrentLineIndex else currentLineIndex
            itemsIndexed(
                items = lines
            ) { index, item ->
                Text(
                    text = item.text,
                    fontSize = 20.sp,
                    color = if (index == displayedCurrentLineIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    textAlign = when (lyricsTextPosition) {
                        LyricsPosition.LEFT -> TextAlign.Left
                        LyricsPosition.CENTER -> TextAlign.Center
                        LyricsPosition.RIGHT -> TextAlign.Right
                    },
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = isSynced) {
                            playerConnection.player.seekTo(item.time)
                            lastPreviewTime = 0L
                        }
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .alpha(if (!isSynced || index == displayedCurrentLineIndex) 1f else 0.5f)
                )
            }

            if (lyrics == null) {
                item {
                    ShimmerHost {
                        repeat(10) {
                            Box(
                                contentAlignment = when (lyricsTextPosition) {
                                    LyricsPosition.LEFT -> Alignment.CenterStart
                                    LyricsPosition.CENTER -> Alignment.Center
                                    LyricsPosition.RIGHT -> Alignment.CenterEnd
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 4.dp)
                            ) {
                                TextPlaceholder()
                            }
                        }
                    }
                }
            }
        }

        if (lyrics == LYRICS_NOT_FOUND) {
            Text(
                text = stringResource(R.string.lyrics_not_found),
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = when (lyricsTextPosition) {
                    LyricsPosition.LEFT -> TextAlign.Left
                    LyricsPosition.CENTER -> TextAlign.Center
                    LyricsPosition.RIGHT -> TextAlign.Right
                },
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .alpha(0.5f)
            )
        }

        IconButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            onClick = {
                menuState.show {
                    LyricsMenu(
                        lyricsProvider = { lyricsEntity?.lyrics },
                        mediaMetadataProvider = mediaMetadataProvider,
                        coroutineScope = coroutineScope,
                        onDismiss = menuState::dismiss
                    )
                }
            }
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_more_horiz),
                contentDescription = null
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsMenu(
    lyricsProvider: () -> String?,
    mediaMetadataProvider: () -> MediaMetadata,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
    viewModel: LyricsMenuViewModel = viewModel(),
) {
    val context = LocalContext.current

    var showEditDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showEditDialog) {
        TextFieldDialog(
            onDismiss = { showEditDialog = false },
            icon = { Icon(painter = painterResource(R.drawable.ic_edit), contentDescription = null) },
            title = { Text(text = mediaMetadataProvider().title) },
            initialTextFieldValue = TextFieldValue(lyricsProvider().orEmpty()),
            singleLine = false,
            onDone = {
                coroutineScope.launch {
                    SongRepository(context).upsert(LyricsEntity(
                        id = mediaMetadataProvider().id,
                        lyrics = it
                    ))
                }
            }
        )
    }

    var showSearchDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var showSearchResultDialog by rememberSaveable {
        mutableStateOf(false)
    }

    val searchMediaMetadata = remember(showSearchDialog) {
        mediaMetadataProvider()
    }
    val (titleField, onTitleFieldChange) = rememberSaveable(showSearchDialog, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(
            TextFieldValue(
                text = mediaMetadataProvider().title
            )
        )
    }
    val (artistField, onArtistFieldChange) = rememberSaveable(showSearchDialog, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(
            TextFieldValue(
                text = mediaMetadataProvider().artists.joinToString { it.name }
            )
        )
    }

    if (showSearchDialog) {
        DefaultDialog(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            onDismiss = { showSearchDialog = false },
            icon = { Icon(painter = painterResource(R.drawable.ic_search), contentDescription = null) },
            title = { Text(stringResource(R.string.dialog_title_search_lyrics)) },
            buttons = {
                TextButton(
                    onClick = { showSearchDialog = false }
                ) {
                    Text(stringResource(android.R.string.cancel))
                }

                Spacer(Modifier.width(8.dp))

                TextButton(
                    onClick = {
                        showSearchDialog = false
                        onDismiss()
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_WEB_SEARCH).apply {
                                    putExtra(SearchManager.QUERY, "${artistField.text} ${titleField.text} lyrics")
                                }
                            )
                        } catch (_: Exception) {
                        }
                    }
                ) {
                    Text(stringResource(R.string.menu_search_online))
                }

                Spacer(Modifier.width(8.dp))

                TextButton(
                    onClick = {
                        viewModel.search(searchMediaMetadata.id, titleField.text, artistField.text, searchMediaMetadata.duration)
                        showSearchResultDialog = true
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        ) {
            OutlinedTextField(
                value = titleField,
                onValueChange = onTitleFieldChange,
                singleLine = true,
                label = { Text(stringResource(R.string.song_title)) }
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = artistField,
                onValueChange = onArtistFieldChange,
                singleLine = true,
                label = { Text(stringResource(R.string.song_artists)) }
            )
        }
    }

    if (showSearchResultDialog) {
        val results by viewModel.results.collectAsState()
        val isLoading by viewModel.isLoading.collectAsState()

        var expandedItemIndex by rememberSaveable {
            mutableStateOf(-1)
        }

        ListDialog(
            onDismiss = { showSearchResultDialog = false }
        ) {
            itemsIndexed(results) { index, result ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            viewModel.cancelSearch()
                            coroutineScope.launch {
                                SongRepository(context).upsert(LyricsEntity(
                                    id = searchMediaMetadata.id,
                                    lyrics = result.lyrics
                                ))
                            }
                        }
                        .padding(12.dp)
                        .animateContentSize()
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = result.lyrics,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = if (index == expandedItemIndex) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = result.providerName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1
                            )
                            if (result.lyrics.startsWith("[")) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_sync),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .size(18.dp)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = {
                            expandedItemIndex = if (expandedItemIndex == index) -1 else index
                        }
                    ) {
                        Icon(
                            painter = painterResource(if (index == expandedItemIndex) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                            contentDescription = null
                        )
                    }
                }
            }

            if (isLoading) {
                item {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }

    GridMenu(
        contentPadding = PaddingValues(
            start = 8.dp,
            top = 8.dp,
            end = 8.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
        )
    ) {
        GridMenuItem(
            icon = R.drawable.ic_edit,
            title = R.string.menu_edit
        ) {
            showEditDialog = true
        }
        GridMenuItem(
            icon = R.drawable.ic_cached,
            title = R.string.menu_refetch
        ) {
            val mediaMetadata = mediaMetadataProvider()
            coroutineScope.launch {
                SongRepository(context).deleteLyrics(mediaMetadata.id)
                LyricsHelper.loadLyrics(context, mediaMetadata)
            }
            onDismiss()
        }
        GridMenuItem(
            icon = R.drawable.ic_search,
            title = R.string.menu_search,
        ) {
            showSearchDialog = true
        }
    }
}

private val LINE_REGEX = "((\\[\\d\\d:\\d\\d\\.\\d{2,3}\\])+)(.+)".toRegex()
private val TIME_REGEX = "\\[(\\d\\d):(\\d\\d)\\.(\\d{2,3})\\]".toRegex()

fun parseLyrics(lyrics: String): List<LyricsEntry> =
    lyrics.lines()
        .flatMap { line ->
            parseLine(line).orEmpty()
        }.sorted()

private fun parseLine(line: String): List<LyricsEntry>? {
    if (line.isEmpty()) {
        return null
    }
    val matchResult = LINE_REGEX.matchEntire(line.trim()) ?: return null
    val times = matchResult.groupValues[1]
    val text = matchResult.groupValues[3]
    val timeMatchResults = TIME_REGEX.findAll(times)

    return timeMatchResults.map { timeMatchResult ->
        val min = timeMatchResult.groupValues[1].toLong()
        val sec = timeMatchResult.groupValues[2].toLong()
        val milString = timeMatchResult.groupValues[3]
        var mil = milString.toLong()
        if (milString.length == 2) {
            mil *= 10
        }
        val time = min * DateUtils.MINUTE_IN_MILLIS + sec * DateUtils.SECOND_IN_MILLIS + mil
        LyricsEntry(time, text)
    }.toList()
}

data class LyricsEntry(
    val time: Long,
    val text: String,
) : Comparable<LyricsEntry> {
    override fun compareTo(other: LyricsEntry): Int = (time - other.time).toInt()
}

fun findCurrentLineIndex(lines: List<LyricsEntry>, position: Long): Int {
    for (index in lines.indices) {
        if (lines[index].time >= position + animateScrollDuration) {
            return index - 1
        }
    }
    return lines.lastIndex
}

const val animateScrollDuration = 300L
val LyricsPreviewTime = 4.seconds