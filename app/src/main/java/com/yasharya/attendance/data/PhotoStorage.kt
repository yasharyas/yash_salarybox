package com.yasharya.attendance.data

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Where selfies and enrolment crops live.
 *
 * Internal app storage, not MediaStore. A selfie taken to prove attendance is a
 * private record of an employee, and MediaStore would put it in the device
 * gallery next to their holiday photos, visible to every app with media
 * permission and to anyone who picks up the phone. Internal storage is
 * sandboxed to this app and is removed when the app is uninstalled, which is the
 * correct lifetime for this data.
 *
 * Room stores the path, not the bytes. Keeping multi-hundred-kilobyte images out
 * of SQLite keeps queries fast and the database file small.
 */
class PhotoStorage(private val context: Context) {

    private val selfieDir: File get() = File(context.filesDir, SELFIE_DIR).apply { mkdirs() }
    private val enrolmentDir: File get() = File(context.filesDir, ENROLMENT_DIR).apply { mkdirs() }

    suspend fun saveSelfie(bitmap: Bitmap, employeeId: String, timestamp: Long): String =
        write(File(selfieDir, "${employeeId}_$timestamp.jpg"), bitmap, SELFIE_QUALITY)

    suspend fun saveEnrolmentCrop(bitmap: Bitmap, staffId: Long, index: Int, timestamp: Long): String =
        write(File(enrolmentDir, "${staffId}_${index}_$timestamp.jpg"), bitmap, ENROLMENT_QUALITY)

    private suspend fun write(target: File, bitmap: Bitmap, quality: Int): String =
        withContext(Dispatchers.IO) {
            FileOutputStream(target).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }
            target.absolutePath
        }

    suspend fun deleteForStaff(staffId: Long, employeeId: String) = withContext(Dispatchers.IO) {
        enrolmentDir.listFiles { file -> file.name.startsWith("${staffId}_") }?.forEach { it.delete() }
        selfieDir.listFiles { file -> file.name.startsWith("${employeeId}_") }?.forEach { it.delete() }
        Unit
    }

    suspend fun deleteEnrolmentCrops(staffId: Long) = withContext(Dispatchers.IO) {
        enrolmentDir.listFiles { file -> file.name.startsWith("${staffId}_") }?.forEach { it.delete() }
        Unit
    }

    private companion object {
        const val SELFIE_DIR = "selfies"
        const val ENROLMENT_DIR = "enrolment"

        /** Attendance evidence: worth the bytes. */
        const val SELFIE_QUALITY = 90

        /** A 112x112 thumbnail, only ever shown at 48dp. */
        const val ENROLMENT_QUALITY = 85
    }
}
