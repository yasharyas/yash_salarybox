package com.yasharya.attendance.data.local

import androidx.room.TypeConverter
import com.yasharya.attendance.data.local.entity.LocationStatus
import com.yasharya.attendance.data.local.entity.UserRole
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Converters {
    @TypeConverter fun roleToString(value: UserRole): String = value.name

    @TypeConverter fun stringToRole(value: String): UserRole = UserRole.valueOf(value)

    @TypeConverter fun locationStatusToString(value: LocationStatus): String = value.name

    @TypeConverter
    fun stringToLocationStatus(value: String): LocationStatus =
        runCatching { LocationStatus.valueOf(value) }.getOrDefault(LocationStatus.UNAVAILABLE)
}

/**
 * Embeddings are stored as a raw little-endian float32 BLOB rather than as a
 * comma-joined string. It is half the bytes of text, it round-trips exactly
 * instead of through decimal formatting, and it decodes without allocating a
 * string per read.
 */
object EmbeddingCodec {
    fun encode(values: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(values.size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach(buffer::putFloat)
        return buffer.array()
    }

    fun decode(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / Float.SIZE_BYTES) { buffer.float }
    }
}
