package com.yasharya.attendance.data.repository

import android.graphics.Bitmap
import com.yasharya.attendance.data.PhotoStorage
import com.yasharya.attendance.data.local.dao.AttendanceDao
import com.yasharya.attendance.data.local.entity.AttendanceEntity
import com.yasharya.attendance.face.FaceMatcher
import com.yasharya.attendance.face.FaceRecognitionService
import com.yasharya.attendance.location.LocationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Everything that can come back from trying to mark attendance. */
sealed interface MarkAttendanceResult {
    data class Success(val record: AttendanceEntity) : MarkAttendanceResult

    /** The face was read fine, it simply was not a match. Score is carried so the UI can explain. */
    data class FaceMismatch(val score: Float, val threshold: Float) : MarkAttendanceResult

    data object NotEnrolled : MarkAttendanceResult
    data object NoFaceDetected : MarkAttendanceResult
    data object MultipleFacesDetected : MarkAttendanceResult
    data object FaceUnreadable : MarkAttendanceResult
    data object AlreadyMarkedToday : MarkAttendanceResult
}

class AttendanceRepository(
    private val attendanceDao: AttendanceDao,
    private val staffRepository: StaffRepository,
    private val faceRecognition: FaceRecognitionService,
    private val locationProvider: LocationProvider,
    private val photoStorage: PhotoStorage,
) {
    fun observeForStaff(staffId: Long): Flow<List<AttendanceEntity>> =
        attendanceDao.observeForStaff(staffId)

    fun observeAllWithStaff() = attendanceDao.observeAllWithStaff()

    fun observeToday(staffId: Long, zone: ZoneId = ZoneId.systemDefault()): Flow<AttendanceEntity?> {
        val (start, end) = dayBounds(LocalDate.now(zone), zone)
        return attendanceDao.observeForDay(staffId, start, end)
    }

    fun observeCountThisMonth(staffId: Long, zone: ZoneId = ZoneId.systemDefault()): Flow<Int> {
        val today = LocalDate.now(zone)
        val start = today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusMonths(1).withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return attendanceDao.observeCountBetween(staffId, start, end)
    }

    /**
     * The whole check-in transaction: identity first, then evidence.
     *
     * Ordering matters. The face is verified before anything is written or
     * saved to disk, so a failed match leaves no trace: no orphan selfie, no
     * partial row. Only once identity is established do we spend time on
     * location and storage.
     */
    suspend fun markAttendance(
        staffId: Long,
        employeeId: String,
        selfie: Bitmap,
        threshold: Float = FaceMatcher.DEFAULT_THRESHOLD,
        zone: ZoneId = ZoneId.systemDefault(),
    ): MarkAttendanceResult = withContext(Dispatchers.Default) {
        val enrolled = staffRepository.enrolledEmbeddings(staffId)
        if (enrolled.isEmpty()) return@withContext MarkAttendanceResult.NotEnrolled

        val outcome = faceRecognition.embed(selfie)
        val embedding = when (outcome) {
            is FaceRecognitionService.EmbedOutcome.Success -> outcome.embedding
            FaceRecognitionService.EmbedOutcome.Failure.NoFace ->
                return@withContext MarkAttendanceResult.NoFaceDetected
            FaceRecognitionService.EmbedOutcome.Failure.MultipleFaces ->
                return@withContext MarkAttendanceResult.MultipleFacesDetected
            FaceRecognitionService.EmbedOutcome.Failure.AlignmentFailed,
            FaceRecognitionService.EmbedOutcome.Failure.ModelUnavailable,
            -> return@withContext MarkAttendanceResult.FaceUnreadable
        }

        val match = FaceMatcher.match(embedding, enrolled, threshold)
        if (!match.matched) {
            return@withContext MarkAttendanceResult.FaceMismatch(match.score, match.threshold)
        }

        // One record per day. Checked after the match so that a second attempt
        // still tells the user the truth about their face rather than hiding a
        // mismatch behind a duplicate-day message.
        val (start, end) = dayBounds(LocalDate.now(zone), zone)
        if (attendanceDao.existsForDay(staffId, start, end)) {
            return@withContext MarkAttendanceResult.AlreadyMarkedToday
        }

        val markedAt = System.currentTimeMillis()

        // The location fix and the JPEG encode are independent, and the fix can
        // take seconds. Running them together keeps the user's wait to the
        // longer of the two rather than their sum.
        val (fix, selfiePath) = coroutineScope {
            val locationJob = async { locationProvider.currentFix() }
            val photoJob = async { photoStorage.saveSelfie(selfie, employeeId, markedAt) }
            locationJob.await() to photoJob.await()
        }

        val record = AttendanceEntity(
            staffId = staffId,
            markedAt = markedAt,
            selfiePath = selfiePath,
            matchScore = match.score,
            // The threshold in force is written onto the row so that changing the
            // constant later cannot retroactively rewrite what past decisions meant.
            matchThreshold = match.threshold,
            latitude = fix.latitude,
            longitude = fix.longitude,
            accuracyMeters = fix.accuracyMeters,
            address = fix.address,
            locationStatus = fix.status,
        )
        val id = attendanceDao.insert(record)
        MarkAttendanceResult.Success(record.copy(id = id))
    }

    private fun dayBounds(date: LocalDate, zone: ZoneId): Pair<Long, Long> {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return start to end
    }
}

/** Convenience for formatting, kept out of the entity so the row stays a plain record. */
fun AttendanceEntity.instant(): Instant = Instant.ofEpochMilli(markedAt)
