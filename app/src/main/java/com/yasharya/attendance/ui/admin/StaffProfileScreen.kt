package com.yasharya.attendance.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.yasharya.attendance.data.local.entity.AttendanceEntity
import com.yasharya.attendance.data.local.entity.FaceTemplateEntity
import com.yasharya.attendance.theme.AttendanceTheme
import com.yasharya.attendance.theme.Spacing
import com.yasharya.attendance.theme.TabularFigures
import com.yasharya.attendance.ui.components.SectionHeader
import com.yasharya.attendance.util.formatDay
import com.yasharya.attendance.util.formatDateTime
import com.yasharya.attendance.util.formatTime
import java.io.File
import kotlin.math.roundToInt
import com.yasharya.attendance.ui.components.Avatar
import com.yasharya.attendance.ui.components.PrimaryButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffProfileScreen(
    state: StaffProfileUiState,
    onBack: () -> Unit,
    onEnrol: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val staff = state.staff?.staff

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(staff?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove staff member")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = Spacing.huge),
        ) {
            item {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.gutter),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(name = staff?.name.orEmpty(), size = 40.dp)
                    Spacer(Modifier.width(Spacing.md))
                    Text(
                        text = staff?.employeeId.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge.merge(TabularFigures),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(Spacing.xl))
            }

            item {
                EnrolmentHero(
                    isEnrolled = state.isEnrolled,
                    name = staff?.name.orEmpty(),
                    templates = state.templates,
                    enrolledAt = staff?.enrolledAt,
                    onEnrol = onEnrol,
                    modifier = Modifier.padding(horizontal = Spacing.gutter),
                )
            }

            item { SectionHeader("Attendance history") }

            if (state.history.isEmpty()) {
                item {
                    Text(
                        text = if (state.isEnrolled) {
                            "Nothing yet. Records appear here once ${staff?.name?.firstName().orEmpty()} marks attendance."
                        } else {
                            "Nothing yet, and nothing can be recorded until a face is enrolled."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.lg),
                    )
                }
            } else {
                items(state.history, key = { it.id }) { record ->
                    AdminAttendanceRow(record)
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Remove ${staff?.name.orEmpty()}?") },
            // A destructive dialog names the object and names the consequence.
            text = {
                Text(
                    "Their attendance history will be kept, but they will no longer " +
                        "be able to sign in or mark attendance.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    // "Remove", not "OK". The button says what it does.
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }
}

/**
 * Enrolment status is the hero of this screen, and the two states are styled
 * asymmetrically: not-enrolled is a coloured call to action, enrolled is quiet.
 * The exception should be loud; the normal state should not shout.
 */
@Composable
private fun EnrolmentHero(
    isEnrolled: Boolean,
    name: String,
    templates: List<FaceTemplateEntity>,
    enrolledAt: Long?,
    onEnrol: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (isEnrolled) {
                MaterialTheme.colorScheme.surfaceContainer
            } else {
                MaterialTheme.colorScheme.tertiaryContainer
            },
        ),
    ) {
        Column(Modifier.padding(Spacing.xxl)) {
            if (!isEnrolled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.PersonOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(Modifier.width(Spacing.lg))
                    Column {
                        Text(
                            text = "Face not enrolled",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Text(
                            text = "${name.firstName()} cannot mark attendance until " +
                                "their face is enrolled.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.lg))
                PrimaryButton(text = "Enrol face now", onClick = onEnrol)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    templates.forEach { template ->
                        template.thumbnailPath?.let { path ->
                            AsyncImage(
                                model = File(path),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.lg))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = AttendanceTheme.status.present,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Face enrolled", style = MaterialTheme.typography.titleMedium)
                }
                if (enrolledAt != null) {
                    Text(
                        text = "Enrolled ${enrolledAt.formatDateTime()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(Spacing.sm))
                TextButton(onClick = onEnrol) { Text("Re-enrol") }
            }
        }
    }
}

@Composable
private fun AdminAttendanceRow(record: AttendanceEntity, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = File(record.selfiePath),
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp)),
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
        // The confidence number is shown to the admin and never to the staff
        // member: an admin can act on a consistently marginal score, whereas a
        // staff member can only worry about it.
        Text(
            text = "${(record.matchScore * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelMedium.merge(TabularFigures),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun String.firstName(): String = trim().substringBefore(' ').ifEmpty { this }
