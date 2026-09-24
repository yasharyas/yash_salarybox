package com.yasharya.attendance

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yasharya.attendance.data.PhotoStorage
import com.yasharya.attendance.data.local.AttendanceDatabase
import com.yasharya.attendance.data.local.entity.LocationStatus
import com.yasharya.attendance.data.repository.AddStaffResult
import com.yasharya.attendance.data.repository.AttendanceRepository
import com.yasharya.attendance.data.repository.EnrolmentSample
import com.yasharya.attendance.data.repository.MarkAttendanceResult
import com.yasharya.attendance.data.repository.StaffRepository
import com.yasharya.attendance.face.FaceRecognitionService
import com.yasharya.attendance.location.LocationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The whole feature, end to end, through the real repositories.
 *
 * Enrol one person from three photographs, then try to mark attendance as that
 * person using a FOURTH photograph they have never been enrolled with, and as
 * somebody else entirely. This is the test that actually answers "does face
 * matching work", because it exercises detection, alignment, embedding,
 * storage, retrieval and the accept/reject decision in one pass.
 */
@RunWith(AndroidJUnit4::class)
class EnrolAndMarkAttendanceTest {

    private lateinit var database: AttendanceDatabase
    private lateinit var staffRepository: StaffRepository
    private lateinit var attendanceRepository: AttendanceRepository
    private lateinit var faceRecognition: FaceRecognitionService

    @Before
    fun setUp() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(appContext, AttendanceDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val photoStorage = PhotoStorage(appContext)
        faceRecognition = FaceRecognitionService(appContext)

        staffRepository = StaffRepository(
            staffDao = database.staffDao(),
            userDao = database.userDao(),
            faceTemplateDao = database.faceTemplateDao(),
            photoStorage = photoStorage,
        )
        attendanceRepository = AttendanceRepository(
            attendanceDao = database.attendanceDao(),
            staffRepository = staffRepository,
            faceRecognition = faceRecognition,
            locationProvider = LocationProvider(appContext),
            photoStorage = photoStorage,
        )
    }

    @After
    fun tearDown() {
        database.close()
        faceRecognition.release()
    }

    @Test
    fun enrolledStaffCanMarkAttendanceWithAnUnseenPhoto() = runBlocking {
        val staffId = addStaff()
        enrol(staffId, "obama.jpg", "obama2.jpg", "obama-480p.jpg")

        // obama_small.jpg was NOT one of the enrolment samples.
        val result = attendanceRepository.markAttendance(
            staffId = staffId,
            employeeId = EMPLOYEE_ID,
            selfie = fixture("obama_small.jpg"),
        )

        Log.i(TAG, "unseen-photo result: $result")
        assertTrue(
            "An unseen photo of the enrolled person was rejected: $result",
            result is MarkAttendanceResult.Success,
        )

        val record = (result as MarkAttendanceResult.Success).record
        Log.i(TAG, "matched at %.4f against threshold %.2f".format(record.matchScore, record.matchThreshold))
        assertTrue(record.matchScore >= record.matchThreshold)

        // The record must carry its evidence, not just a boolean.
        assertTrue("Selfie was not persisted", java.io.File(record.selfiePath).exists())
        assertTrue("Timestamp was not recorded", record.markedAt > 0)
        // No location permission is granted to the test, so the row should say
        // exactly that rather than silently holding a null.
        assertEquals(LocationStatus.PERMISSION_DENIED, record.locationStatus)

        val history = attendanceRepository.observeForStaff(staffId).first()
        assertEquals(1, history.size)
    }

    @Test
    fun someoneElseIsRejected() = runBlocking {
        val staffId = addStaff()
        enrol(staffId, "obama.jpg", "obama2.jpg", "obama-480p.jpg")

        val result = attendanceRepository.markAttendance(
            staffId = staffId,
            employeeId = EMPLOYEE_ID,
            selfie = fixture("biden.jpg"),
        )

        Log.i(TAG, "impostor result: $result")
        assertTrue(
            "A different person was accepted as the enrolled staff member: $result",
            result is MarkAttendanceResult.FaceMismatch,
        )

        val mismatch = result as MarkAttendanceResult.FaceMismatch
        Log.i(TAG, "impostor scored %.4f against threshold %.2f".format(mismatch.score, mismatch.threshold))
        assertTrue(mismatch.score < mismatch.threshold)

        // A rejected attempt must leave nothing behind.
        assertTrue(attendanceRepository.observeForStaff(staffId).first().isEmpty())
    }

    @Test
    fun unenrolledStaffCannotMarkAttendance() = runBlocking {
        val staffId = addStaff()
        val result = attendanceRepository.markAttendance(
            staffId = staffId,
            employeeId = EMPLOYEE_ID,
            selfie = fixture("obama.jpg"),
        )
        assertEquals(MarkAttendanceResult.NotEnrolled, result)
    }

    @Test
    fun attendanceCanOnlyBeMarkedOncePerDay() = runBlocking {
        val staffId = addStaff()
        enrol(staffId, "obama.jpg", "obama2.jpg", "obama-480p.jpg")

        val first = attendanceRepository.markAttendance(staffId, EMPLOYEE_ID, fixture("obama.jpg"))
        assertTrue(first is MarkAttendanceResult.Success)

        val second = attendanceRepository.markAttendance(staffId, EMPLOYEE_ID, fixture("obama2.jpg"))
        assertEquals(MarkAttendanceResult.AlreadyMarkedToday, second)

        assertEquals(1, attendanceRepository.observeForStaff(staffId).first().size)
    }

    private suspend fun addStaff(): Long {
        val result = staffRepository.addStaff("Test Person", EMPLOYEE_ID)
        assertTrue(result is AddStaffResult.Success)
        return (result as AddStaffResult.Success).staffId
    }

    private suspend fun enrol(staffId: Long, vararg fixtures: String) {
        val samples = fixtures.map { name ->
            when (val outcome = faceRecognition.embed(fixture(name))) {
                is FaceRecognitionService.EmbedOutcome.Success ->
                    EnrolmentSample(outcome.embedding, outcome.alignedCrop)
                else -> throw AssertionError("Could not enrol $name: $outcome")
            }
        }
        staffRepository.saveEnrolment(staffId, samples)
    }

    private fun fixture(name: String): Bitmap {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        return testContext.assets.open("faces/$name").use { stream ->
            BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })
        } ?: throw AssertionError("Could not decode faces/$name")
    }

    private companion object {
        const val TAG = "EnrolAndMark"
        const val EMPLOYEE_ID = "EMP-TEST"
    }
}
