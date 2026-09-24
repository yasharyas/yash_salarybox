package com.yasharya.attendance.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.yasharya.attendance.theme.Spacing
import com.yasharya.attendance.ui.components.PrimaryButton

@Composable
fun LoginScreen(
    state: LoginUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val passwordFocus = remember { FocusRequester() }
    var passwordVisible by remember { mutableStateOf(false) }

    // The view model clears the password on a failed attempt, so put the cursor
    // back where the work is rather than making the user find it again.
    LaunchedEffect(state.error) {
        if (state.error != null) passwordFocus.requestFocus()
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .widthIn(max = 420.dp)
                    .padding(horizontal = Spacing.xxl),
                // Left aligned, so the masthead, the fields and the button all
                // share one vertical edge instead of floating on a centre line.
                horizontalAlignment = Alignment.Start,
            ) {
                Masthead()

                Spacer(Modifier.height(Spacing.xxxl))

                OutlinedTextField(
                    value = state.username,
                    onValueChange = onUsernameChange,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    label = { Text("Username or employee ID") },
                    singleLine = true,
                    // Never error-tinted. On a failed sign-in this field usually
                    // holds the correct value, and painting it red alongside
                    // everything else turns one message into a wall of alarm.
                    isError = false,
                    enabled = !state.isSubmitting,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) },
                    ),
                )

                Spacer(Modifier.height(Spacing.lg))

                OutlinedTextField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(passwordFocus),
                    shape = MaterialTheme.shapes.medium,
                    label = { Text("Password") },
                    singleLine = true,
                    isError = state.error != null,
                    // supportingText wires into M3's own error semantics, so a
                    // screen reader gets the reason and not just "invalid".
                    supportingText = { state.error?.let { Text(it) } },
                    enabled = !state.isSubmitting,
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                // The state belongs in the label, not just the
                                // icon: "Show password" says what will happen.
                                contentDescription = if (passwordVisible) {
                                    "Hide password"
                                } else {
                                    "Show password"
                                },
                                // Pinned: a visibility toggle has no error state.
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                )

                // A reserved slot. The supportingText above appears and
                // disappears inside the field's own footprint, so the button
                // below never slides out from under the finger that just
                // tapped it.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Spacing.sm)
                        .semantics { liveRegion = LiveRegionMode.Assertive },
                )

                Spacer(Modifier.height(Spacing.lg))

                PrimaryButton(
                    text = "Sign in",
                    onClick = onSubmit,
                    // Always live. A greyed-out primary action gives the user
                    // nothing to act on and nothing to read; submitting an empty
                    // form now produces a reason instead of silence. The view
                    // model still guards re-entry.
                    isBusy = state.isSubmitting,
                )

                Spacer(Modifier.height(Spacing.xxxl))
                DemoCredentialsCard()
                Spacer(Modifier.height(Spacing.xxl))
            }
        }
    }
}

/**
 * A rule and a word, rather than a stock pictogram in a circle.
 *
 * `Icons.Default.Face` is Material clip art, and the enrolment intro uses the
 * same glyph, so the app's only two branded moments were identical. One primary
 * rule on the same left edge as everything below it is a considered line
 * instead of a floating medallion, and it costs no asset.
 */
@Composable
private fun Masthead(modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(34.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.shapes.extraSmall,
                    ),
            )
            Spacer(Modifier.width(Spacing.md))
            Text("Attendance", style = MaterialTheme.typography.headlineLarge)
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = "Sign in to mark or manage attendance.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Demo credentials, on screen.
 *
 * This is a hiring assignment: whoever opens the APK should be able to sign in
 * within five seconds, without going back to the README. A real product would
 * never do this, which is why the card says so out loud. The footnote also
 * steers people to Admin first, because a reviewer who starts as Staff lands on
 * a screen with no button and may read that as a broken build.
 */
@Composable
private fun DemoCredentialsCard(modifier: Modifier = Modifier) {
    // Outlined, because surfaceContainerLow against this background is a 2%
    // step and the card had no visible boundary at all.
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.lg)) {
            Text("Demo credentials", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(Spacing.md))
            CredentialRow("Admin", "admin", "admin123")
            Spacer(Modifier.height(Spacing.sm))
            CredentialRow("Staff", "EMP-001", "staff123")
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = "Start with Admin: staff cannot mark attendance until an " +
                    "admin has enrolled their face. Credentials are shown only in " +
                    "this demo build.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CredentialRow(role: String, username: String, password: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = role,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "$username  /  $password",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
