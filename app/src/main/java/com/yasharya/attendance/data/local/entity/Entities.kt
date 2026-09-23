package com.yasharya.attendance.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Who can sign in. Admins have no staff record; staff rows point at one. */
@Entity(
    tableName = "users",
    indices = [Index(value = ["staffId"])],
)
data class UserEntity(
    @PrimaryKey val username: String,
    val passwordHash: String,
    val salt: String,
    val role: UserRole,
    val staffId: Long?,
)

enum class UserRole { ADMIN, STAFF }

@Entity(
    tableName = "staff",
    indices = [Index(value = ["employeeId"], unique = true)],
)
data class StaffEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeId: String,
    val name: String,
    val createdAt: Long,
    /** Null until an admin has enrolled at least one face sample. */
    val enrolledAt: Long? = null,
)

/**
 * One row per enrolment sample. We deliberately keep the samples separate rather
 * than averaging them into a single centroid: the samples are captured at
 * different head poses, and the mean of several poses sits in a region that
 * represents none of them. Matching takes the best score across samples.
 */
@Entity(
    tableName = "face_templates",
    foreignKeys = [
        ForeignKey(
            entity = StaffEntity::class,
            parentColumns = ["id"],
            childColumns = ["staffId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["staffId"])],
)
data class FaceTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val staffId: Long,
    /** 192 little-endian float32 values. See [com.yasharya.attendance.data.local.Converters]. */
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val embedding: ByteArray,
    val capturedAt: Long,
    /** Internal-storage path of the aligned 112x112 crop, shown on the staff profile. */
    val thumbnailPath: String?,
) {
    // ByteArray needs structural equality written out by hand; the generated
    // implementation would compare references and break diffing in tests.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FaceTemplateEntity) return false
        return id == other.id &&
            staffId == other.staffId &&
            capturedAt == other.capturedAt &&
            thumbnailPath == other.thumbnailPath &&
            embedding.contentEquals(other.embedding)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + staffId.hashCode()
        result = 31 * result + capturedAt.hashCode()
        result = 31 * result + (thumbnailPath?.hashCode() ?: 0)
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}

@Entity(
    tableName = "attendance",
    foreignKeys = [
        ForeignKey(
            entity = StaffEntity::class,
            parentColumns = ["id"],
            childColumns = ["staffId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["staffId"]), Index(value = ["markedAt"])],
)
data class AttendanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val staffId: Long,
    /** Epoch millis, UTC. Formatting to a local time is a presentation concern. */
    val markedAt: Long,
    val selfiePath: String,
    /** Cosine similarity actually achieved, and the bar it had to clear. */
    val matchScore: Float,
    val matchThreshold: Float,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Float?,
    val address: String?,
    val locationStatus: LocationStatus,
)

/**
 * Why a record has or has not got coordinates. Recording the reason rather than
 * a bare null is what lets an admin tell "the user denied location" apart from
 * "GPS could not get a fix indoors".
 */
enum class LocationStatus {
    RESOLVED,
    PERMISSION_DENIED,
    SERVICES_DISABLED,
    TIMED_OUT,
    UNAVAILABLE,
}
