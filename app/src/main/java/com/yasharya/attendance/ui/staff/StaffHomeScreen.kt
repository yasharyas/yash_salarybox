package com.yasharya.attendance.ui.staff

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yasharya.attendance.data.local.entity.AttendanceEntity
import com.yasharya.attendance.data.local.entity.LocationStatus
import com.yasharya.attendance.theme.AttendanceTheme
import com.yasharya.attendance.theme.Motion
import com.yasharya.attendance.theme.Spacing
import com.yasharya.attendance.theme.TabularFigures
import com.yasharya.attendance.ui.components.SectionHeader
import com.yasharya.attendance.util.formatDay
import com.yasharya.attendance.util.formatFullDay
import com.yasharya.attendance.util.formatTime
import com.yasharya.attendance.util.greetingFor
import kotlinx.coroutines.delay
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffHomeScreen(
    state: StaffHomeUiState,
    onMarkAttendance: () -> Unit,
    onViewAll: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Attendance") },
                actions = {
                    IconButton(onClick = onSignOut) {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Sign out",
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Spacing.huge),
        ) {
            item {
                Column(Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.lg)) {
                    Text(
                        text = "${greetingFor(java.time.LocalTime.now().hour)}, ${state.name.firstName()}",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = System.currentTimeMillis().formatFullDay(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                TodayStatusCard(
                    record = state.todayRecord,
                    modifier = Modifier.padding(horizontal = Spacing.gutter),
                )
            }

            item {
                Spacer(Modifier.height(Spacing.xxl))
                PrimaryActionBlock(
                    state = state,
                    onMarkAttendance = onMarkAttendance,
                    modifier = Modifier.padding(horizontal = Spacing.gutter),
                )
            }

            if (state.recent.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(Spacing.xxxl))
                    SectionHeader("Recent")
                }
                items(state.recent, key = { it.id }) { record ->
                    AttendanceRow(record)
                }
                item {
                    TextButton(
                        onClick = onViewAll,
                        modifier = Modifier.padding(horizontal = Spacing.sm),
                    ) {
                        Text("View all")
                    }
                }
            }
        }
    }
}

/**
 * The hero. This card is the answer to the only question most people open the
 * app to ask, and it has to be readable without reading, which is why the
 * container colour itself carries the state.
 */
@Composable
private fun TodayStatusCard(record: AttendanceEntity?, modifier: Modifier = Modifier) {
    val status = AttendanceTheme.status
    val container by animateColorAsState(
        targetValue = if (record != null) {
            status.presentContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = Motion.tweenEffectSlow(),
        label = "todayContainer",
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        AnimatedContent(
            targetState = record,
            transitionSpec = {
                (fadeIn(Motion.tweenEffect()) + scaleIn(Motion.springSpatial(), initialScale = 0.94f))
                    .togetherWith(fadeOut(Motion.tweenEffectFast()))
            },
            label = "today",
        ) { current ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp)
                    .padding(Spacing.xxl),
            ) {
                if (current == null) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(Modifier.height(Spacing.md))
                    LiveClock()
                    Spacer(Modifier.height(Spacing.xs))
                    Text("Not marked yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Mark your attendance to check in for today.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = status.present,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(Modifier.height(Spacing.md))
                    Text(
                        text = current.markedAt.formatTime(),
                        style = MaterialTheme.typography.displaySmall.merge(TabularFigures),
                        color = status.onPresentContainer,
                    )
                    Text(
                        text = "Marked present",
                        style = MaterialTheme.typography.titleMedium,
                        color = status.onPresentContainer,
                    )
                    if (current.locationStatus == LocationStatus.RESOLVED && current.address != null) {
                        Spacer(Modifier.height(Spacing.xs))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = status.onPresentContainer,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(Spacing.xs))
                            Text(
                                text = current.address,
                                style = MaterialTheme.typography.bodyMedium,
                                color = status.onPresentContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A clock that ticks. It costs eight lines and makes the card feel alive. */
@Composable
private fun LiveClock(modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    Text(
        text = now.formatTime(),
        // Tabular figures, otherwise the digits jitter the layout every second.
        style = MaterialTheme.typography.displaySmall.merge(TabularFigures),
        modifier = modifier,
    )
}

@Composable
private fun PrimaryActionBlock(
    state: StaffHomeUiState,
    onMarkAttendance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        when (state.action) {
            PrimaryAction.CanMark -> {
                Button(
                    onClick = onMarkAttendance,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = CircleShape,
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(Modifier.width(Spacing.md))
                    Text("Mark attendance", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(Spacing.md))
                LocationHint(enabled = state.locationEnabled)
            }

            PrimaryAction.AlreadyMarked -> {
                OutlinedButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = CircleShape,
                ) {
                    Text("Already marked today")
                }
                Spacer(Modifier.height(Spacing.sm))
                // A disabled control always states its reason within a line of
                // itself. A button that silently does nothing is a bug report.
                Text(
                    text = "You can mark attendance once per day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }

            PrimaryAction.NotEnrolled -> {
                // No button at all. There is no action this user can take, and
                // offering one that leads nowhere is worse than offering none.
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                ) {
                    Column(Modifier.padding(Spacing.xl)) {
                        Text(
                            text = "Your face is not enrolled yet",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            text = "Ask your admin to enrol your face so you can mark attendance.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Surfacing location state before the tap, so nobody gets halfway through a
 * selfie and then hits a wall.
 */
@Composable
private fun LocationHint(enabled: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (enabled) Icons.Default.LocationOn else Icons.Default.LocationOff,
            contentDescription = null,
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            text = if (enabled) "Location on" else "Location off, attendance will still be recorded",
            style = MaterialTheme.typography.bodySmall,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
        )
    }
}

@Composable
fun AttendanceRow(record: AttendanceEntity, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = AttendanceTheme.status.present,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(Spacing.lg))
        Column(Modifier.weight(1f)) {
            Text(record.markedAt.formatDay(), style = MaterialTheme.typography.titleMedium)
            Text(
                text = buildString {
                    append(record.markedAt.formatTime())
                    record.address?.let { append(", ").append(it) }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun String.firstName(): String = trim().substringBefore(' ').ifEmpty { "there" }
