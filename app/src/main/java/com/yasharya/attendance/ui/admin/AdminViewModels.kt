package com.yasharya.attendance.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasharya.attendance.data.local.dao.StaffWithEnrolment
import com.yasharya.attendance.data.local.entity.AttendanceEntity
import com.yasharya.attendance.data.local.entity.FaceTemplateEntity
import com.yasharya.attendance.data.repository.AddStaffResult
import com.yasharya.attendance.data.repository.AttendanceRepository
import com.yasharya.attendance.data.repository.AuthRepository
import com.yasharya.attendance.data.repository.StaffRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- staff list

data class StaffListUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val staff: List<StaffWithEnrolment> = emptyList(),
) {
    val isSearching: Boolean get() = query.isNotBlank()
    val isEmpty: Boolean get() = !isLoading && staff.isEmpty() && !isSearching
    val hasNoMatches: Boolean get() = !isLoading && staff.isEmpty() && isSearching
}

class StaffListViewModel(
    staffRepository: StaffRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val state: StateFlow<StaffListUiState> = combine(
        staffRepository.observeStaff(),
        query,
    ) { staff, search ->
        val filtered = if (search.isBlank()) {
            staff
        } else {
            staff.filter {
                it.staff.name.contains(search, ignoreCase = true) ||
                    it.staff.employeeId.contains(search, ignoreCase = true)
            }
        }
        StaffListUiState(isLoading = false, query = search, staff = filtered)
    }.stateIn(
        scope = viewModelScope,
        // Eagerly, not WhileSubscribed(5_000). The upstream is local Room
        // queries, so keeping it alive costs nothing, and this screen sits on
        // the back stack while the user changes its data on the screen above
        // (marking attendance, enrolling a face). With a 5 second timeout the
        // upstream stopped during that visit and the stale value was replayed
        // on return: "Face not enrolled" straight after enrolling someone. Caught
        // by reading a demo recording frame by frame.
        started = SharingStarted.Eagerly,
        initialValue = StaffListUiState(),
    )

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }
}

// ----------------------------------------------------------------- add staff

data class AddStaffUiState(
    val name: String = "",
    val employeeId: String = "",
    val nameError: String? = null,
    val employeeIdError: String? = null,
    val nameTouched: Boolean = false,
    val employeeIdTouched: Boolean = false,
    val isCheckingId: Boolean = false,
    val isSaving: Boolean = false,
    val savedStaffId: Long? = null,
    val savedStaffName: String? = null,
) {
    val isValid: Boolean
        get() = validateName(name) == null &&
            validateEmployeeId(employeeId) == null &&
            employeeIdError == null
}

/** Validation rules live next to each other so the copy stays consistent. */
fun validateName(value: String): String? {
    val trimmed = value.trim()
    return when {
        trimmed.isEmpty() -> "Enter the staff member's name"
        trimmed.length < 2 -> "Name must be at least 2 characters"
        trimmed.length > 60 -> "Name must be 60 characters or fewer"
        !trimmed.matches(NAME_PATTERN) -> "Use letters, spaces, hyphens and apostrophes only"
        else -> null
    }
}

fun validateEmployeeId(value: String): String? {
    val trimmed = value.trim()
    return when {
        trimmed.isEmpty() -> "Enter an employee ID"
        !trimmed.matches(EMPLOYEE_ID_PATTERN) ->
            "Use 3 to 16 letters, numbers, hyphens or underscores"
        else -> null
    }
}

/**
 * Letters, plus combining marks.
 *
 * \p{M} is not optional decoration: in Devanagari, Arabic, Thai and others the
 * vowel signs are separate combining characters, so a pattern of \p{L} alone
 * rejects perfectly ordinary names. A name field that only accepts A to Z is a
 * bug wearing the costume of a validation rule.
 */
private val NAME_PATTERN = Regex("^[\\p{L}][\\p{L}\\p{M} .'\\-]*$")
private val EMPLOYEE_ID_PATTERN = Regex("^[A-Za-z0-9][A-Za-z0-9_-]{2,15}$")

@OptIn(FlowPreview::class)
class AddStaffViewModel(private val staffRepository: StaffRepository) : ViewModel() {

    private val _state = MutableStateFlow(AddStaffUiState())
    val state: StateFlow<AddStaffUiState> = _state.asStateFlow()

    private val employeeIdInput = MutableStateFlow("")

    init {
        viewModelScope.launch {
            employeeIdInput
                .debounce(DUPLICATE_CHECK_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collect { value -> checkDuplicate(value) }
        }
    }

    /**
     * Validate late, revalidate early.
     *
     * A field is not judged until it loses focus or the form is submitted. Once
     * it HAS shown an error it revalidates on every keystroke, so the error
     * disappears the moment it is fixed. Showing an error while someone is still
     * typing their first attempt is the single most common form mistake.
     */
    fun onNameChange(value: String) = _state.update { current ->
        current.copy(
            name = value,
            nameError = if (current.nameError != null) validateName(value) else null,
        )
    }

    fun onNameBlur() = _state.update {
        it.copy(nameTouched = true, nameError = validateName(it.name))
    }

    fun onEmployeeIdChange(value: String) {
        _state.update { current ->
            current.copy(
                employeeId = value,
                employeeIdError = if (current.employeeIdTouched) validateEmployeeId(value) else null,
                isCheckingId = validateEmployeeId(value) == null && value.isNotBlank(),
            )
        }
        employeeIdInput.value = value
    }

    fun onEmployeeIdBlur() = _state.update {
        it.copy(employeeIdTouched = true, employeeIdError = validateEmployeeId(it.employeeId))
    }

    private suspend fun checkDuplicate(value: String) {
        if (validateEmployeeId(value) != null) {
            _state.update { it.copy(isCheckingId = false) }
            return
        }
        val existing = staffRepository.findByEmployeeId(value)
        _state.update { current ->
            if (current.employeeId != value) return@update current
            current.copy(
                isCheckingId = false,
                employeeIdError = existing?.let {
                    // Naming the holder matters: telling someone an ID is taken
                    // without saying who took it is half an error message.
                    "${it.employeeId} is already used by ${it.name}"
                },
            )
        }
    }

    fun save() {
        val current = _state.value
        val nameError = validateName(current.name)
        val idError = validateEmployeeId(current.employeeId)
        if (nameError != null || idError != null) {
            _state.update {
                it.copy(
                    nameError = nameError,
                    employeeIdError = idError,
                    nameTouched = true,
                    employeeIdTouched = true,
                )
            }
            return
        }

        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            when (val result = staffRepository.addStaff(current.name, current.employeeId)) {
                is AddStaffResult.Success -> _state.update {
                    it.copy(
                        isSaving = false,
                        savedStaffId = result.staffId,
                        savedStaffName = current.name.trim(),
                    )
                }
                is AddStaffResult.DuplicateEmployeeId -> _state.update {
                    it.copy(
                        isSaving = false,
                        employeeIdError = "${current.employeeId.uppercase()} is already used by " +
                            result.existingName,
                    )
                }
            }
        }
    }

    private companion object {
        const val DUPLICATE_CHECK_DEBOUNCE_MS = 350L
    }
}

// -------------------------------------------------------------- staff profile

data class StaffProfileUiState(
    val isLoading: Boolean = true,
    val staff: StaffWithEnrolment? = null,
    val templates: List<FaceTemplateEntity> = emptyList(),
    val history: List<AttendanceEntity> = emptyList(),
    val deleted: Boolean = false,
) {
    val isEnrolled: Boolean get() = (staff?.sampleCount ?: 0) > 0
}

class StaffProfileViewModel(
    private val staffId: Long,
    private val staffRepository: StaffRepository,
    attendanceRepository: AttendanceRepository,
) : ViewModel() {

    private val deleted = MutableStateFlow(false)

    val state: StateFlow<StaffProfileUiState> = combine(
        staffRepository.observeStaff(staffId),
        staffRepository.observeTemplates(staffId),
        attendanceRepository.observeForStaff(staffId),
        deleted,
    ) { staff, templates, history, isDeleted ->
        StaffProfileUiState(
            isLoading = false,
            staff = staff,
            templates = templates,
            history = history,
            deleted = isDeleted,
        )
    }.stateIn(
        scope = viewModelScope,
        // Eagerly, not WhileSubscribed(5_000). The upstream is local Room
        // queries, so keeping it alive costs nothing, and this screen sits on
        // the back stack while the user changes its data on the screen above
        // (marking attendance, enrolling a face). With a 5 second timeout the
        // upstream stopped during that visit and the stale value was replayed
        // on return: "Face not enrolled" straight after enrolling someone. Caught
        // by reading a demo recording frame by frame.
        started = SharingStarted.Eagerly,
        initialValue = StaffProfileUiState(),
    )

    fun delete() {
        viewModelScope.launch {
            staffRepository.deleteStaff(staffId)
            deleted.value = true
        }
    }
}
