package com.yasharya.attendance.data.repository

import android.graphics.Bitmap
import com.yasharya.attendance.data.PhotoStorage
import com.yasharya.attendance.data.local.DemoSeed
import com.yasharya.attendance.data.local.EmbeddingCodec
import com.yasharya.attendance.data.local.dao.FaceTemplateDao
import com.yasharya.attendance.data.local.dao.StaffDao
import com.yasharya.attendance.data.local.dao.StaffWithEnrolment
import com.yasharya.attendance.data.local.dao.UserDao
import com.yasharya.attendance.data.local.entity.FaceTemplateEntity
import com.yasharya.attendance.data.local.entity.StaffEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

sealed interface AddStaffResult {
    data class Success(val staffId: Long) : AddStaffResult
    data class DuplicateEmployeeId(val existingName: String, val staffId: Long) : AddStaffResult
}

/** One captured enrolment sample, ready to persist. */
data class EnrolmentSample(
    val embedding: FloatArray,
    val crop: Bitmap,
) {
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
}

class StaffRepository(
    private val staffDao: StaffDao,
    private val userDao: UserDao,
    private val faceTemplateDao: FaceTemplateDao,
    private val photoStorage: PhotoStorage,
) {
    fun observeStaff(): Flow<List<StaffWithEnrolment>> = staffDao.observeAll()

    fun observeStaff(id: Long): Flow<StaffWithEnrolment?> = staffDao.observeById(id)

    fun observeTemplates(staffId: Long): Flow<List<FaceTemplateEntity>> =
        faceTemplateDao.observeForStaff(staffId)

    suspend fun findByEmployeeId(employeeId: String): StaffEntity? =
        staffDao.findByEmployeeId(employeeId.trim())

    suspend fun addStaff(name: String, employeeId: String): AddStaffResult =
        withContext(Dispatchers.Default) {
            val normalisedId = employeeId.trim().uppercase()
            staffDao.findByEmployeeId(normalisedId)?.let { existing ->
                return@withContext AddStaffResult.DuplicateEmployeeId(existing.name, existing.id)
            }

            val staffId = staffDao.insert(
                StaffEntity(
                    employeeId = normalisedId,
                    name = name.trim(),
                    createdAt = System.currentTimeMillis(),
                ),
            )
            // A staff member who cannot sign in cannot mark attendance, so the
            // credential is created in the same step rather than as a separate
            // admin chore that is easy to forget.
            userDao.upsert(DemoSeed.newStaffUser(normalisedId, staffId))
            AddStaffResult.Success(staffId)
        }

    /**
     * Replaces any previous enrolment atomically. Re-enrolling is all or
     * nothing: a staff member must never be left with a mix of old and new
     * samples, because the matcher scores against whatever it finds.
     */
    suspend fun saveEnrolment(staffId: Long, samples: List<EnrolmentSample>) =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            photoStorage.deleteEnrolmentCrops(staffId)

            val templates = samples.mapIndexed { index, sample ->
                FaceTemplateEntity(
                    staffId = staffId,
                    embedding = EmbeddingCodec.encode(sample.embedding),
                    capturedAt = now + index,
                    thumbnailPath = photoStorage.saveEnrolmentCrop(sample.crop, staffId, index, now),
                )
            }
            faceTemplateDao.replaceForStaff(staffId, templates)

            staffDao.findById(staffId)?.let { staff ->
                staffDao.upsert(staff.copy(enrolledAt = now))
            }
        }

    suspend fun enrolledEmbeddings(staffId: Long): List<FloatArray> =
        faceTemplateDao.forStaff(staffId).map { EmbeddingCodec.decode(it.embedding) }

    suspend fun deleteStaff(staffId: Long) = withContext(Dispatchers.IO) {
        val staff = staffDao.findById(staffId)
        // Photos are not covered by the foreign key cascade, so they are removed
        // explicitly. Leaving orphaned face crops on disk after a deletion would
        // be a quiet privacy failure.
        if (staff != null) photoStorage.deleteForStaff(staffId, staff.employeeId)
        userDao.deleteForStaff(staffId)
        staffDao.deleteById(staffId)
    }
}
