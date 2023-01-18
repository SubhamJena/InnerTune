package com.zionhuang.music.ui.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.android.exoplayer2.Player.STATE_BUFFERING
import com.zionhuang.music.LocalPlayerConnection
import com.zionhuang.music.R
import com.zionhuang.music.ui.component.LinearProgressIndicator
import com.zionhuang.music.constants.MiniPlayerHeight
import com.zionhuang.music.constants.ThumbnailCornerRadius
import com.zionhuang.music.extensions.togglePlayPause
import com.zionhuang.music.models.MediaMetadata

@Composable
fun MiniPlayer(
    mediaMetadata: MediaMetadata?,
    playbackState: Int,
    playWhenReady: Boolean,
    position: Long,
    duration: Long,
    modifier: Modifier = Modifier,
) {
    mediaMetadata ?: return
    val playerConnection = LocalPlayerConnection.current ?: return
    val canSkipNext by playerConnection.canSkipNext.collectAsState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(MiniPlayerHeight)
            .padding(WindowInsets.systemBars
                .only(WindowInsetsSides.Horizontal)
                .asPaddingValues())
    ) {
        LinearProgressIndicator(
            indeterminate = playbackState == STATE_BUFFERING,
            progress = (position.toFloat() / duration).coerceIn(0f, 1f),
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .align(Alignment.BottomCenter)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
        ) {
            Box(modifier = Modifier.padding(6.dp)) {
                AsyncImage(
                    model = mediaMetadata.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(ThumbnailCornerRadius))
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp)
            ) {
                Text(
                    text = mediaMetadata.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = mediaMetadata.artists.joinToString { it.name },
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            IconButton(
                onClick = playerConnection.player::togglePlayPause
            ) {
                Icon(
                    painter = painterResource(if (playWhenReady) R.drawable.ic_pause else R.drawable.ic_play),
                    contentDescription = null
                )
            }
            IconButton(
                enabled = canSkipNext,
                onClick = playerConnection.player::seekToNext
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_skip_next),
                    contentDescription = null
                )
            }
        }
    }
}