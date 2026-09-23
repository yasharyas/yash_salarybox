package com.yasharya.attendance.ui.staff

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yasharya.attendance.data.local.entity.AttendanceEntity
import com.yasharya.attendance.theme.Spacing
import com.yasharya.attendance.ui.components.EmptyState
import com.yasharya.attendance.util.formatMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffHistoryScreen(
    records: List<AttendanceEntity>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("My attendance") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (records.isEmpty()) {
            EmptyState(
                icon = Icons.Default.EventNote,
                title = "No attendance yet",
                // No action here on purpose: this empty state is temporary by
                // nature and there is nothing to do from this screen.
                description = "Your marked days will appear here.",
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        // Grouped by month so a long list stays scannable.
        val grouped = records.groupBy { it.markedAt.formatMonth() }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = Spacing.huge),
        ) {
            grouped.forEach { (month, monthRecords) ->
                item(key = "header-$month") {
                    Text(
                        text = month,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
                    )
                }
                items(monthRecords, key = { it.id }) { record ->
                    AttendanceRow(record)
                }
            }
        }
    }
}
