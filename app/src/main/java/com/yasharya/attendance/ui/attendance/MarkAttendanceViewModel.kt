package com.yasharya.attendance.ui.attendance

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasharya.attendance.data.local.entity.AttendanceEntity
import com.yasharya.attendance.data.repository.AttendanceRepository
import com.yasharya.attendance.data.repository.MarkAttendanceResult
import com.yasharya.attendance.face.FaceRecognitionService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AttendanceOutcome {
    data class Marked(val record: AttendanceEntity) : AttendanceOutcome

    /** Recoverable: the user can try again. */
    data class Retryable(val title: String, val detail: String, val attempt: Int) : AttendanceOutcome

    /** Terminal: retrying will not help, so the screen stops offering it. */
    data class Blocked(val title: String, val detail: String) : AttendanceOutcome
}

data class MarkAttendanceUiState(
    val isVerifying: Boolean = false,
    val outcome: AttendanceOutcome? = null,
    val attempts: Int = 0,
)

class MarkAttendanceViewModel(
    private val staffId: Long,
    private val employeeId: String,
    private val attendanceRepository: AttendanceRepository,
    faceRecognition: FaceRecognitionService,
) : ViewModel() {

    private val _state = MutableStateFlow(MarkAttendanceUiState())
    val state: StateFlow<MarkAttendanceUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { faceRecognition.warmUp() }
    }

    fun onCaptured(selfie: Bitmap) {
        if (_state.value.isVerifying) return
        _state.update { it.copy(isVerifying = true) }

        viewModelScope.launch {
            val attempt = _state.value.attempts + 1
            val result = attendanceRepository.markAttendance(staffId, employeeId, selfie)
            _state.update { current ->
                current.copy(
                    isVerifying = false,
                    attempts = attempt,
                    outcome = result.toOutcome(attempt),
                )
            }
        }
    }

    fun retry() = _state.update { it.copy(outcome = null) }

    private fun MarkAttendanceResult.toOutcome(attempt: Int): AttendanceOutcome = when (this) {
        is MarkAttendanceResult.Success -> AttendanceOutcome.Marked(record)

        is MarkAttendanceResult.FaceMismatch ->
            // Three tries, then a clean stop with a named next step. Letting
            // someone burn attempts forever is worse UX than saying so.
            if (attempt >= MAX_ATTEMPTS) {
                AttendanceOutcome.Blocked(
                    "Attendance not marked",
                    "We could not match your face after $MAX_ATTEMPTS tries. " +
                        "Ask your admin to re-enrol your photo.",
                )
            } else {
                AttendanceOutcome.Retryable(
                    "We could not confirm it is you",
                    "This usually means the lighting changed. " +
                        "Move somewhere brighter and try again.",
                    attempt,
                )
            }

        MarkAttendanceResult.NoFaceDetected -> AttendanceOutcome.Retryable(
            "No face in that photo",
            "Hold the phone so your face fills the oval, then try again.",
            attempt,
        )

        MarkAttendanceResult.MultipleFacesDetected -> AttendanceOutcome.Retryable(
            "More than one face",
            "Move so you are the only person the camera can see.",
            attempt,
        )

        MarkAttendanceResult.FaceUnreadable -> AttendanceOutcome.Retryable(
            "Could not read that photo",
            "Face the camera straight on in even light, then try again.",
            attempt,
        )

        MarkAttendanceResult.NotEnrolled -> AttendanceOutcome.Blocked(
            "Your face is not enrolled",
            "Ask your admin to enrol your face before marking attendance.",
        )

        MarkAttendanceResult.AlreadyMarkedToday -> AttendanceOutcome.Blocked(
            "Already marked today",
            "You can mark attendance once per day.",
        )
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}
