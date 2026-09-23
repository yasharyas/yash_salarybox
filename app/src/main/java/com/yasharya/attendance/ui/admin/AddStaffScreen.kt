package com.yasharya.attendance.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yasharya.attendance.theme.AttendanceTheme
import com.yasharya.attendance.theme.Spacing

/**
 * A full screen, not a bottom sheet.
 *
 * A sheet with two fields, async validation and a keyboard leaves no room for
 * error text on a small phone, and predictive back on a sheet holding unsaved
 * input is a trap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStaffScreen(
    state: AddStaffUiState,
    onNameChange: (String) -> Unit,
    onNameBlur: () -> Unit,
    onEmployeeIdChange: (String) -> Unit,
    onEmployeeIdBlur: () -> Unit,
    onSaveOnly: () -> Unit,
    onSaveAndEnrol: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Add staff") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = Spacing.gutter),
        ) {
            Spacer(Modifier.height(Spacing.xxl))

            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (!it.isFocused && state.name.isNotEmpty()) onNameBlur() },
                label = { Text("Full name") },
                singleLine = true,
                isError = state.nameError != null,
                // supportingText is ALWAYS present, even when valid, so the
                // layout never jumps 20dp when an error appears.
                supportingText = { Text(state.nameError ?: "") },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                ),
            )

            OutlinedTextField(
                value = state.employeeId,
                onValueChange = onEmployeeIdChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged {
                        if (!it.isFocused && state.employeeId.isNotEmpty()) onEmployeeIdBlur()
                    },
                label = { Text("Employee ID") },
                singleLine = true,
                isError = state.employeeIdError != null,
                supportingText = { Text(state.employeeIdError ?: "") },
                trailingIcon = {
                    when {
                        state.isCheckingId -> CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        state.employeeId.isNotBlank() && state.employeeIdError == null ->
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = AttendanceTheme.status.present,
                            )
                        else -> Unit
                    }
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            )

            Spacer(Modifier.height(Spacing.xl))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(Modifier.padding(Spacing.lg)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Face,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                        Spacer(Modifier.width(Spacing.md))
                        Text("Face enrolment", style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = "You can enrol this person's face now, or later from their profile.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.lg))
                    // Two exits, both legitimate, neither buried in an overflow.
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        FilledTonalButton(
                            onClick = onSaveAndEnrol,
                            enabled = state.isValid && !state.isSaving,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Save and enrol")
                        }
                        OutlinedButton(
                            onClick = onSaveOnly,
                            enabled = state.isValid && !state.isSaving,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Save only")
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}
