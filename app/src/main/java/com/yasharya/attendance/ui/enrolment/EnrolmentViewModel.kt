package com.yasharya.attendance.ui.enrolment

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasharya.attendance.data.repository.EnrolmentSample
import com.yasharya.attendance.data.repository.StaffRepository
import com.yasharya.attendance.face.FaceRecognitionService
import com.yasharya.attendance.face.PoseTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class EnrolmentStep { Intro, Capturing, Review, Saved }

data class EnrolmentUiState(
    val step: EnrolmentStep = EnrolmentStep.Intro,
    val samples: List<EnrolmentSample> = emptyList(),
    val isProcessing: Boolean = false,
    val message: String? = null,
) {
    val captured: Int get() = samples.size

    /**
     * Three samples at three poses.
     *
     * One frontal sample makes the matcher brittle against the one thing that
     * always varies at check-in, which is head angle. Three cover the realistic
     * yaw range for someone holding a phone, and they are stored separately so
     * matching can take the best of them rather than an average that represents
     * no pose at all.
     */
    val poseTarget: PoseTarget
        get() = when (samples.size) {
            0 -> PoseTarget.Frontal
            1 -> PoseTarget.SlightLeft
            else -> PoseTarget.SlightRight
        }

    val stepInstruction: String
        get() = when (samples.size) {
            0 -> "Look straight at the camera"
            1 -> "Turn your head slightly left"
            else -> "Turn your head slightly right"
        }
}

class EnrolmentViewModel(
    private val staffId: Long,
    private val staffRepository: StaffRepository,
    private val faceRecognition: FaceRecognitionService,
) : ViewModel() {

    private val _state = MutableStateFlow(EnrolmentUiState())
    val state: StateFlow<EnrolmentUiState> = _state.asStateFlow()

    init {
        // Pay the model load now, while the user is reading the intro, so the
        // first capture is not the one that waits for it.
        viewModelScope.launch { faceRecognition.warmUp() }
    }

    fun start() = _state.update { it.copy(step = EnrolmentStep.Capturing) }

    fun onCaptured(bitmap: Bitmap) {
        if (_state.value.isProcessing) return
        _state.update { it.copy(isProcessing = true, message = null) }

        viewModelScope.launch {
            when (val outcome = faceRecognition.embed(bitmap)) {
                is FaceRecognitionService.EmbedOutcome.Success -> {
                    val sample = EnrolmentSample(outcome.embedding, outcome.alignedCrop)
                    _state.update { current ->
                        val samples = current.samples + sample
                        current.copy(
                            samples = samples,
                            isProcessing = false,
                            step = if (samples.size >= REQUIRED_SAMPLES) {
                                EnrolmentStep.Review
                            } else {
                                EnrolmentStep.Capturing
                            },
                        )
                    }
                }
                FaceRecognitionService.EmbedOutcome.Failure.NoFace -> fail("No face in that photo. Try again.")
                FaceRecognitionService.EmbedOutcome.Failure.MultipleFaces ->
                    fail("More than one face in that photo.")
                FaceRecognitionService.EmbedOutcome.Failure.AlignmentFailed ->
                    fail("Could not read that angle. Face the camera and try again.")
                FaceRecognitionService.EmbedOutcome.Failure.ModelUnavailable ->
                    fail("Face recognition is unavailable on this device.")
            }
        }
    }

    private fun fail(message: String) =
        _state.update { it.copy(isProcessing = false, message = message) }

    fun retakeAll() = _state.update {
        EnrolmentUiState(step = EnrolmentStep.Capturing)
    }

    fun save() {
        val samples = _state.value.samples
        if (samples.size < REQUIRED_SAMPLES) return

        _state.update { it.copy(isProcessing = true) }
        viewModelScope.launch {
            staffRepository.saveEnrolment(staffId, samples)
            _state.update { it.copy(isProcessing = false, step = EnrolmentStep.Saved) }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    companion object {
        const val REQUIRED_SAMPLES = 3
    }
}
