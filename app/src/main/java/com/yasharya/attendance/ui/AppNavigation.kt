package com.yasharya.attendance.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.yasharya.attendance.AppContainer
import com.yasharya.attendance.appContainer
import com.yasharya.attendance.data.local.entity.UserRole
import com.yasharya.attendance.theme.Motion
import com.yasharya.attendance.ui.admin.AddStaffScreen
import com.yasharya.attendance.ui.admin.AddStaffViewModel
import com.yasharya.attendance.ui.admin.StaffListScreen
import com.yasharya.attendance.ui.admin.StaffListViewModel
import com.yasharya.attendance.ui.admin.StaffProfileScreen
import com.yasharya.attendance.ui.admin.StaffProfileViewModel
import com.yasharya.attendance.ui.attendance.MarkAttendanceScreen
import com.yasharya.attendance.ui.attendance.MarkAttendanceViewModel
import com.yasharya.attendance.ui.enrolment.EnrolmentScreen
import com.yasharya.attendance.ui.enrolment.EnrolmentStep
import com.yasharya.attendance.ui.enrolment.EnrolmentViewModel
import com.yasharya.attendance.ui.login.LoginScreen
import com.yasharya.attendance.ui.login.LoginViewModel
import com.yasharya.attendance.ui.staff.StaffHomeScreen
import com.yasharya.attendance.ui.staff.StaffHomeViewModel
import androidx.compose.runtime.remember
import com.yasharya.attendance.data.Session
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable

@Composable
fun AttendanceNavHost(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val container = context.appContainer
    val navController = rememberNavController()

    // Three states, not two. Reading DataStore takes a moment, and collecting
    // with a null initial value made "still loading" indistinguishable from
    // "signed out": a returning user saw the login form flash past and slide
    // away before landing on their own screen.
    val sessionState by remember(container) {
        container.authRepository.session
            .map<Session?, SessionState> { SessionState.Resolved(it) }
    }.collectAsStateWithLifecycle(initialValue = SessionState.Loading)

    // Session is the single source of truth for which half of the app is
    // reachable. Driving navigation from it rather than from the sign-in
    // callback means signing out from anywhere lands correctly, including
    // after process death.
    //
    // Only on a genuine ROLE CHANGE, though. Navigating on every emission meant
    // any configuration change (a rotation, a theme switch) re-ran this and
    // popped the whole back stack to the root, throwing the admin out of a staff
    // profile or out of a half-finished enrolment.
    var handledRole by rememberSaveable { mutableStateOf<String?>(UNHANDLED) }

    LaunchedEffect(sessionState) {
        val resolved = sessionState as? SessionState.Resolved ?: return@LaunchedEffect
        val role = resolved.session?.role?.name
        if (handledRole == role) return@LaunchedEffect
        handledRole = role

        val target = when (resolved.session?.role) {
            UserRole.ADMIN -> Route.AdminStaffList
            UserRole.STAFF -> Route.StaffHome
            null -> Route.Login
        }
        navController.navigate(target) {
            popUpTo(navController.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }

    val session = (sessionState as? SessionState.Resolved)?.session

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        NavHost(
            navController = navController,
            startDestination = Route.Login,
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    tween(Motion.EnterDurationMs, easing = Motion.EmphasizedDecelerate),
                ) + fadeIn(tween(Motion.EnterDurationMs))
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    tween(Motion.ExitDurationMs, easing = Motion.EmphasizedAccelerate),
                ) + fadeOut(tween(Motion.ExitDurationMs))
            },
            popEnterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    tween(Motion.EnterDurationMs, easing = Motion.EmphasizedDecelerate),
                ) + fadeIn(tween(Motion.EnterDurationMs))
            },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    tween(Motion.ExitDurationMs, easing = Motion.EmphasizedAccelerate),
                ) + fadeOut(tween(Motion.ExitDurationMs))
            },
        ) {
            composable<Route.Login> {
                val viewModel: LoginViewModel = viewModel {
                    LoginViewModel(container.authRepository)
                }
                val state by viewModel.state.collectAsStateWithLifecycle()
                LoginScreen(
                    state = state,
                    onUsernameChange = viewModel::onUsernameChange,
                    onPasswordChange = viewModel::onPasswordChange,
                    onSubmit = viewModel::signIn,
                )
            }

            composable<Route.AdminStaffList> {
                val viewModel: StaffListViewModel = viewModel {
                    StaffListViewModel(container.staffRepository, container.authRepository)
                }
                val state by viewModel.state.collectAsStateWithLifecycle()
                StaffListScreen(
                    state = state,
                    onQueryChange = viewModel::onQueryChange,
                    onStaffClick = { navController.navigate(Route.AdminStaffProfile(it)) },
                    onAddStaff = { navController.navigate(Route.AdminAddStaff) },
                    onSignOut = viewModel::signOut,
                )
            }

            composable<Route.AdminAddStaff> {
                val viewModel: AddStaffViewModel = viewModel {
                    AddStaffViewModel(container.staffRepository)
                }
                val state by viewModel.state.collectAsStateWithLifecycle()

                // Two exits from the same save, decided by which button was
                // pressed; the flag is captured when the save completes.
                var enrolAfterSave = rememberEnrolIntent()

                LaunchedEffect(state.savedStaffId) {
                    val id = state.savedStaffId ?: return@LaunchedEffect
                    if (enrolAfterSave.value) {
                        navController.navigate(
                            Route.Enrolment(id, state.savedStaffName.orEmpty()),
                        ) {
                            popUpTo(Route.AdminAddStaff) { inclusive = true }
                        }
                    } else {
                        navController.popBackStack()
                    }
                }

                AddStaffScreen(
                    state = state,
                    onNameChange = viewModel::onNameChange,
                    onNameBlur = viewModel::onNameBlur,
                    onEmployeeIdChange = viewModel::onEmployeeIdChange,
                    onEmployeeIdBlur = viewModel::onEmployeeIdBlur,
                    onSaveOnly = {
                        enrolAfterSave.value = false
                        viewModel.save()
                    },
                    onSaveAndEnrol = {
                        enrolAfterSave.value = true
                        viewModel.save()
                    },
                    onClose = { navController.popBackStack() },
                )
            }

            composable<Route.AdminStaffProfile> { entry ->
                val route = entry.toRoute<Route.AdminStaffProfile>()
                val viewModel: StaffProfileViewModel = viewModel {
                    StaffProfileViewModel(
                        route.staffId,
                        container.staffRepository,
                        container.attendanceRepository,
                    )
                }
                val state by viewModel.state.collectAsStateWithLifecycle()

                LaunchedEffect(state.deleted) {
                    if (state.deleted) navController.popBackStack()
                }

                StaffProfileScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onEnrol = {
                        state.staff?.staff?.let { staff ->
                            navController.navigate(Route.Enrolment(staff.id, staff.name))
                        }
                    },
                    onDelete = viewModel::delete,
                )
            }

            composable<Route.Enrolment> { entry ->
                val route = entry.toRoute<Route.Enrolment>()
                val viewModel: EnrolmentViewModel = viewModel {
                    EnrolmentViewModel(
                        route.staffId,
                        container.staffRepository,
                        container.faceRecognition,
                    )
                }
                val state by viewModel.state.collectAsStateWithLifecycle()

                LaunchedEffect(state.step) {
                    if (state.step == EnrolmentStep.Saved) navController.popBackStack()
                }

                EnrolmentScreen(
                    staffName = route.staffName,
                    state = state,
                    onStart = viewModel::start,
                    onCaptured = viewModel::onCaptured,
                    onSave = viewModel::save,
                    onRetakeAll = viewModel::retakeAll,
                    onClose = { navController.popBackStack() },
                )
            }

            composable<Route.StaffHome> {
                val staffId = session?.staffId
                if (staffId == null) {
                    // Session is still loading, or a staff session somehow has no
                    // staff row. Either way there is nothing to render.
                    return@composable
                }
                val viewModel: StaffHomeViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { container.staffHomeViewModel(staffId) }
                    },
                )
                val state by viewModel.state.collectAsStateWithLifecycle()
                StaffHomeScreen(
                    state = state,
                    onMarkAttendance = {
                        navController.navigate(
                            Route.MarkAttendance(staffId, state.employeeId),
                        )
                    },
                    onViewAll = { navController.navigate(Route.StaffHistory) },
                    onSignOut = viewModel::signOut,
                )
            }

            composable<Route.MarkAttendance> { entry ->
                val route = entry.toRoute<Route.MarkAttendance>()
                val viewModel: MarkAttendanceViewModel = viewModel {
                    MarkAttendanceViewModel(
                        route.staffId,
                        route.employeeId,
                        container.attendanceRepository,
                        container.faceRecognition,
                    )
                }
                val state by viewModel.state.collectAsStateWithLifecycle()
                MarkAttendanceScreen(
                    state = state,
                    onCaptured = viewModel::onCaptured,
                    onRetry = viewModel::retry,
                    onClose = { navController.popBackStack() },
                    onDone = { navController.popBackStack() },
                )
            }

            composable<Route.StaffHistory> {
                val staffId = session?.staffId ?: return@composable
                val viewModel: StaffHomeViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { container.staffHomeViewModel(staffId) }
                    },
                )
                val state by viewModel.state.collectAsStateWithLifecycle()
                com.yasharya.attendance.ui.staff.StaffHistoryScreen(
                    records = state.allRecords,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

private fun AppContainer.staffHomeViewModel(staffId: Long) = StaffHomeViewModel(
    staffId = staffId,
    staffRepository = staffRepository,
    attendanceRepository = attendanceRepository,
    authRepository = authRepository,
    locationProvider = location,
)

@Composable
private fun rememberEnrolIntent() =
    androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

/**
 * Distinguishes "we have not read the session yet" from "there is no session".
 * Without it, every cold launch renders the login screen for a frame.
 */
private sealed interface SessionState {
    data object Loading : SessionState
    data class Resolved(val session: Session?) : SessionState
}

/** Distinct from null, which is the real "signed out" role. */
private const val UNHANDLED = "role-not-yet-read"
