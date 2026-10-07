/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 */

package com.jay.m3play.ui.screens.settings

import android.content.Intent
import android.text.format.DateFormat
import android.util.Log
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.navigation.NavController
import com.jay.m3play.LocalPlayerConnection
import com.jay.m3play.R
import com.jay.m3play.ui.component.IconButton
import com.jay.m3play.ui.utils.backToMain
import com.jay.m3play.utils.GlobalLog
import com.jay.m3play.utils.LogEntry
import kotlinx.coroutines.launch

@Composable
fun DebugSettings(navController: NavController) {
    val logs by GlobalLog.logs.collectAsState()
    val playerConnection = LocalPlayerConnection.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var selectedLevels by remember {
        mutableStateOf(setOf(Log.DEBUG, Log.INFO, Log.WARN, Log.ERROR))
    }
    var filterMenuExpanded by remember { mutableStateOf(false) }

    val filteredLogs = remember(logs, selectedLevels) {
        logs.filter { it.level in selectedLevels }
    }

    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Debug & Logs", fontWeight = FontWeight.Bold)
                        Text(
                            "${filteredLogs.size} visible / ${logs.size} total",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    Box {
                        FilledTonalButton(onClick = { filterMenuExpanded = true }) {
                            Text("Filter")
                        }
                        DropdownMenu(
                            expanded = filterMenuExpanded,
                            onDismissRequest = { filterMenuExpanded = false },
                        ) {
                            LogLevelMenuItem("Debug", Log.DEBUG, selectedLevels) {
                                selectedLevels = toggleLevel(selectedLevels, it)
                            }
                            LogLevelMenuItem("Info", Log.INFO, selectedLevels) {
                                selectedLevels = toggleLevel(selectedLevels, it)
                            }
                            LogLevelMenuItem("Warn", Log.WARN, selectedLevels) {
                                selectedLevels = toggleLevel(selectedLevels, it)
                            }
                            LogLevelMenuItem("Error", Log.ERROR, selectedLevels) {
                                selectedLevels = toggleLevel(selectedLevels, it)
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlaybackDiagnostics(playerConnection)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 280.dp, max = 520.dp)
                    .padding(horizontal = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                shape = RoundedCornerShape(24.dp),
            ) {
                if (filteredLogs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No logs yet. Reproduce the playback error and come back here.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(
                            items = filteredLogs,
                            key = { index, _ -> index },
                        ) { entry ->
                            LogEntryItem(entry, clipboard, scope)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(
                    onClick = { GlobalLog.clear() },
                    enabled = logs.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.clear_all),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Clear")
                }

                FilledTonalButton(
                    onClick = {
                        val text = GlobalLog.allAsText()
                        if (text.isNotBlank()) {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(send, "Share debug logs"))
                        }
                    },
                    enabled = logs.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.share),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Share")
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun toggleLevel(current: Set<Int>, level: Int): Set<Int> =
    if (level in current) current - level else current + level

@Composable
private fun LogLevelMenuItem(
    label: String,
    level: Int,
    selectedLevels: Set<Int>,
    onToggle: (Int) -> Unit,
) {
    DropdownMenuItem(
        onClick = { onToggle(level) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = level in selectedLevels,
                    onCheckedChange = { onToggle(level) },
                )
                Text(label)
            }
        },
    )
}

@Composable
private fun LogEntryItem(
    entry: LogEntry,
    clipboard: androidx.compose.ui.platform.ClipboardManager,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    var expanded by remember { mutableStateOf(false) }
    val levelColor = when (entry.level) {
        Log.ERROR -> MaterialTheme.colorScheme.error
        Log.WARN -> MaterialTheme.colorScheme.tertiary
        Log.INFO -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { expanded = !expanded },
                onLongClick = {
                    clipboard.setText(AnnotatedString(entry.message))
                    scope.launch {
                        GlobalLog.append(Log.INFO, "DebugUI", "Log copied to clipboard")
                    }
                },
            ),
        shape = RoundedCornerShape(14.dp),
        color = levelColor.copy(alpha = 0.08f),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    DateFormat.format("HH:mm:ss.SSS", entry.time).toString(),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    entry.tag ?: "M3Play",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                entry.message,
                color = levelColor,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PlaybackDiagnostics(
    playerConnection: com.jay.m3play.playback.PlayerConnection?,
) {
    if (playerConnection == null) return

    val metadata by playerConnection.mediaMetadata.collectAsState()
    val currentFormat by playerConnection.currentFormat.collectAsState(initial = null)
    val player = playerConnection.player

    var position by remember { mutableStateOf(player.currentPosition) }
    var buffered by remember { mutableStateOf(player.bufferedPosition) }
    var bufferPercent by remember { mutableStateOf(player.bufferedPercentage) }

    LaunchedEffect(Unit) {
        while (true) {
            position = player.currentPosition
            buffered = player.bufferedPosition
            bufferPercent = player.bufferedPercentage
            kotlinx.coroutines.delay(500)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Playback diagnostics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Text(
                "Track: ${metadata?.title ?: "none"}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("State: ${playbackStateName(player.playbackState)}")
            Text("Position: ${position / 1000}s")
            Text("Buffered: ${buffered / 1000}s (${bufferPercent}%)")
            Text(
                "Format: ${currentFormat?.mimeType ?: "resolving…"}  " +
                    "bitrate=${currentFormat?.bitrate ?: 0}  " +
                    "length=${currentFormat?.contentLength ?: 0}",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (player.playbackState == Player.STATE_BUFFERING) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private fun playbackStateName(state: Int): String =
    when (state) {
        Player.STATE_IDLE -> "IDLE"
        Player.STATE_BUFFERING -> "BUFFERING"
        Player.STATE_READY -> "READY"
        Player.STATE_ENDED -> "ENDED"
        else -> "UNKNOWN($state)"
    }
