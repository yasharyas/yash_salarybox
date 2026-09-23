package com.yasharya.attendance.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yasharya.attendance.data.local.dao.StaffWithEnrolment
import com.yasharya.attendance.theme.Spacing
import com.yasharya.attendance.theme.TabularFigures
import com.yasharya.attendance.ui.components.Avatar
import com.yasharya.attendance.ui.components.EmptyState
import com.yasharya.attendance.ui.components.EnrolmentBadge
import com.yasharya.attendance.ui.components.StaffRowSkeleton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffListScreen(
    state: StaffListUiState,
    onQueryChange: (String) -> Unit,
    onStaffClick: (Long) -> Unit,
    onAddStaff: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Staff") },
                actions = {
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddStaff,
                icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                text = { Text("Add staff") },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
                placeholder = { Text("Search name or ID") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
            )

            when {
                state.isLoading -> Column {
                    // Skeletons, not a spinner: the shape of this list is known
                    // before the data arrives, and showing it makes the wait feel
                    // shorter than a spinner that throws that information away.
                    repeat(6) { StaffRowSkeleton() }
                }

                state.isEmpty -> EmptyState(
                    icon = Icons.Default.Group,
                    title = "No staff yet",
                    description = "Add your first team member to start tracking attendance.",
                    actionLabel = "Add staff",
                    onAction = onAddStaff,
                )

                state.hasNoMatches -> Box(
                    Modifier.fillMaxSize().padding(Spacing.xxxl),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // No illustration for a zero-result search. A big graphic
                        // here is condescending when the fix is one tap away.
                        Text(
                            text = "No one matches \"${state.query}\"",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                        )
                        TextButton(onClick = { onQueryChange("") }) { Text("Clear search") }
                    }
                }

                else -> LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(state.staff, key = { it.staff.id }) { entry ->
                        StaffRow(entry = entry, onClick = { onStaffClick(entry.staff.id) })
                        HorizontalDivider(
                            // Inset to line up under the text, not under the
                            // avatar, so the eye follows one vertical edge.
                            modifier = Modifier.padding(start = 76.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StaffRow(entry: StaffWithEnrolment, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Avatar(name = entry.staff.name) },
        headlineContent = {
            Text(entry.staff.name, style = MaterialTheme.typography.titleMedium)
        },
        supportingContent = {
            Text(
                text = entry.staff.employeeId,
                style = MaterialTheme.typography.bodyMedium.merge(TabularFigures),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = { EnrolmentBadge(isEnrolled = entry.sampleCount > 0) },
    )
}
