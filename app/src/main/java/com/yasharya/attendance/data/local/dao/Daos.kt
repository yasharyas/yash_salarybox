package com.yasharya.attendance.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.yasharya.attendance.data.local.entity.AttendanceEntity
import com.yasharya.attendance.data.local.entity.FaceTemplateEntity
import com.yasharya.attendance.data.local.entity.StaffEntity
import com.yasharya.attendance.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun findByUsername(username: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(users: List<UserEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(user: UserEntity)

    @Query("DELETE FROM users WHERE staffId = :staffId")
    suspend fun deleteForStaff(staffId: Long)

    @Query("SELECT COUNT(*) FROM users")
    suspend fun count(): Int
}

/** A staff member plus the counts the list and profile screens need. */
data class StaffWithEnrolment(
    @Embedded val staff: StaffEntity,
    val sampleCount: Int,
)

@Dao
interface StaffDao {
    @Query(
        """
        SELECT s.*, (
            SELECT COUNT(*) FROM face_templates t WHERE t.staffId = s.id
        ) AS sampleCount
        FROM staff s
        ORDER BY s.name COLLATE NOCASE ASC
        """,
    )
    fun observeAll(): Flow<List<StaffWithEnrolment>>

    @Query(
        """
        SELECT s.*, (
            SELECT COUNT(*) FROM face_templates t WHERE t.staffId = s.id
        ) AS sampleCount
        FROM staff s WHERE s.id = :id
        """,
    )
    fun observeById(id: Long): Flow<StaffWithEnrolment?>

    @Query("SELECT * FROM staff WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): StaffEntity?

    @Query("SELECT * FROM staff WHERE employeeId = :employeeId COLLATE NOCASE LIMIT 1")
    suspend fun findByEmployeeId(employeeId: String): StaffEntity?

    @Insert
    suspend fun insert(staff: StaffEntity): Long

    @Upsert
    suspend fun upsert(staff: StaffEntity)

    @Query("DELETE FROM staff WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM staff")
    suspend fun count(): Int
}

@Dao
interface FaceTemplateDao {
    @Query("SELECT * FROM face_templates WHERE staffId = :staffId ORDER BY capturedAt ASC")
    suspend fun forStaff(staffId: Long): List<FaceTemplateEntity>

    @Query("SELECT * FROM face_templates WHERE staffId = :staffId ORDER BY capturedAt ASC")
    fun observeForStaff(staffId: Long): Flow<List<FaceTemplateEntity>>

    @Insert
    suspend fun insertAll(templates: List<FaceTemplateEntity>)

    @Query("DELETE FROM face_templates WHERE staffId = :staffId")
    suspend fun deleteForStaff(staffId: Long)

    /** Replacing an enrolment is one unit of work: never leave a staff member half enrolled. */
    @Transaction
    suspend fun replaceForStaff(staffId: Long, templates: List<FaceTemplateEntity>) {
        deleteForStaff(staffId)
        insertAll(templates)
    }
}

data class AttendanceWithStaff(
    @Embedded val attendance: AttendanceEntity,
    val staffName: String,
    val employeeId: String,
)

@Dao
interface AttendanceDao {
    @Insert
    suspend fun insert(record: AttendanceEntity): Long

    @Query("SELECT * FROM attendance WHERE staffId = :staffId ORDER BY markedAt DESC")
    fun observeForStaff(staffId: Long): Flow<List<AttendanceEntity>>

    @Query(
        """
        SELECT a.*, s.name AS staffName, s.employeeId AS employeeId
        FROM attendance a JOIN staff s ON s.id = a.staffId
        ORDER BY a.markedAt DESC
        """,
    )
    fun observeAllWithStaff(): Flow<List<AttendanceWithStaff>>

    /**
     * The most recent record inside a local-day window. The window is computed by
     * the caller from the device time zone, because "today" is a calendar
     * question and the database only stores instants.
     */
    @Query(
        """
        SELECT * FROM attendance
        WHERE staffId = :staffId AND markedAt >= :startOfDay AND markedAt < :endOfDay
        ORDER BY markedAt DESC LIMIT 1
        """,
    )
    fun observeForDay(staffId: Long, startOfDay: Long, endOfDay: Long): Flow<AttendanceEntity?>

    @Query(
        """
        SELECT COUNT(*) FROM attendance
        WHERE staffId = :staffId AND markedAt >= :from AND markedAt < :to
        """,
    )
    fun observeCountBetween(staffId: Long, from: Long, to: Long): Flow<Int>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM attendance
            WHERE staffId = :staffId AND markedAt >= :startOfDay AND markedAt < :endOfDay
        )
        """,
    )
    suspend fun existsForDay(staffId: Long, startOfDay: Long, endOfDay: Long): Boolean
}
