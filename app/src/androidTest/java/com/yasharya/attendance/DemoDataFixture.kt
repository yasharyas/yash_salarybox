package com.yasharya.attendance

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yasharya.attendance.data.repository.EnrolmentSample
import com.yasharya.attendance.face.FaceRecognitionService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A development utility, not a test.
 *
 * Populates the INSTALLED app's real database with an enrolled staff member and
 * an attendance record, so the data-dependent screens (enrolled profile,
 * attendance history, the marked-today home card) can be opened and looked at on
 * an emulator whose camera cannot produce a real face.
 *
 * It goes through the real repositories, so what you see is what the app would
 * actually have produced.
 *
 * @Ignore keeps it out of CI and out of `connectedDebugAndroidTest`; logcat
 * confirms `ignored: populate` and the Gradle task still exits 0. Note that AGP
 * serialises an ignored instrumentation test as an empty `<failure/>` element in
 * the XML report, so a report viewer may show it as failed. It is not.
 *
 * Run it deliberately:
 *
 *   ./gradlew connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.yasharya.attendance.DemoDataFixture#populate \
 *     -Pandroid.testInstrumentationRunnerArguments.notAnnotation=org.junit.Ignore
 */
@RunWith(AndroidJUnit4::class)
class DemoDataFixture {

    @Test
    @Ignore("Development utility. Run explicitly to populate the app for screenshots.")
    fun populate() = runBlocking {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val container = AppContainer(appContext)
        val faceRecognition = FaceRecognitionService(appContext)

        // Belt and braces alongside @Ignore: some runner configurations execute
        // ignored methods anyway, and this one needs an app that has already
        // been launched once. Assume marks it skipped rather than failed.
        val staff = container.staffRepository.observeStaff().first().firstOrNull()
        assumeTrue("No seeded staff. Launch the app once, then run this.", staff != null)
        requireNotNull(staff)

        Log.i(TAG, "enrolling ${staff.staff.name} (${staff.staff.employeeId})")

        val samples = listOf("obama.jpg", "obama2.jpg", "obama-480p.jpg").map { name ->
            when (val outcome = faceRecognition.embed(fixture(name))) {
                is FaceRecognitionService.EmbedOutcome.Success ->
                    EnrolmentSample(outcome.embedding, outcome.alignedCrop)
                else -> error("Could not embed $name: $outcome")
            }
        }
        container.staffRepository.saveEnrolment(staff.staff.id, samples)

        val result = container.attendanceRepository.markAttendance(
            staffId = staff.staff.id,
            employeeId = staff.staff.employeeId,
            selfie = fixture("obama_small.jpg"),
        )
        Log.i(TAG, "attendance: $result")

        faceRecognition.release()
    }

    private fun fixture(name: String): Bitmap {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        return testContext.assets.open("faces/$name").use { stream ->
            BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })
        } ?: error("Could not decode faces/$name")
    }

    private companion object {
        const val TAG = "DemoDataFixture"
    }
}
