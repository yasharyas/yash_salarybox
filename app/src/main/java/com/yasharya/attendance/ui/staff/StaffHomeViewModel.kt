package com.yasharya.attendance.ui.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasharya.attendance.data.local.entity.AttendanceEntity
import com.yasharya.attendance.data.repository.AttendanceRepository
import com.yasharya.attendance.data.repository.AuthRepository
import com.yasharya.attendance.data.repository.StaffRepository
import com.yasharya.attendance.location.LocationProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StaffHomeUiState(
    val isLoading: Boolean = true,
    val name: String = "",
    val employeeId: String = "",
    val isEnrolled: Boolean = false,
    val todayRecord: AttendanceEntity? = null,
    val recent: List<AttendanceEntity> = emptyList(),
    val allRecords: List<AttendanceEntity> = emptyList(),
    val presentThisMonth: Int = 0,
    val locationEnabled: Boolean = true,
) {
    val isMarkedToday: Boolean get() = todayRecord != null

    /**
     * Three mutually exclusive states for the primary action. Modelling this as
     * an enum rather than a pile of booleans is what stops the screen ever
     * showing a button that cannot do anything.
     */
    val action: PrimaryAction
        get() = when {
            !isEnrolled -> PrimaryAction.NotEnrolled
            isMarkedToday -> PrimaryAction.AlreadyMarked
            else -> PrimaryAction.CanMark
        }
}

enum class PrimaryAction { CanMark, AlreadyMarked, NotEnrolled }

class StaffHomeViewModel(
    private val staffId: Long,
    staffRepository: StaffRepository,
    attendanceRepository: AttendanceRepository,
    private val authRepository: AuthRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    val state: StateFlow<StaffHomeUiState> = combine(
        staffRepository.observeStaff(staffId),
        attendanceRepository.observeToday(staffId),
        attendanceRepository.observeForStaff(staffId),
        attendanceRepository.observeCountThisMonth(staffId),
    ) { staff, today, all, monthCount ->
        StaffHomeUiState(
            isLoading = false,
            name = staff?.staff?.name.orEmpty(),
            employeeId = staff?.staff?.employeeId.orEmpty(),
            isEnrolled = (staff?.sampleCount ?: 0) > 0,
            todayRecord = today,
            recent = all.take(RECENT_LIMIT),
            allRecords = all,
            presentThisMonth = monthCount,
            locationEnabled = locationProvider.isLocationEnabled(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StaffHomeUiState(),
    )

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    private companion object {
        const val RECENT_LIMIT = 5
    }
}
