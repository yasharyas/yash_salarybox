package com.yasharya.attendance.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.yasharya.attendance.data.local.dao.AttendanceDao
import com.yasharya.attendance.data.local.dao.FaceTemplateDao
import com.yasharya.attendance.data.local.dao.StaffDao
import com.yasharya.attendance.data.local.dao.UserDao
import com.yasharya.attendance.data.local.entity.AttendanceEntity
import com.yasharya.attendance.data.local.entity.FaceTemplateEntity
import com.yasharya.attendance.data.local.entity.StaffEntity
import com.yasharya.attendance.data.local.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        StaffEntity::class,
        FaceTemplateEntity::class,
        AttendanceEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AttendanceDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun staffDao(): StaffDao
    abstract fun faceTemplateDao(): FaceTemplateDao
    abstract fun attendanceDao(): AttendanceDao

    companion object {
        private const val NAME = "attendance.db"

        @Volatile private var instance: AttendanceDatabase? = null

        fun get(context: Context): AttendanceDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        /**
         * Seeding deliberately does NOT happen in a RoomDatabase.Callback.
         *
         * onCreate fires part-way through the first database access, so seeding
         * from there either races the query that triggered it (the caller reads
         * an empty table and moves on) or re-enters the database mid-transaction.
         * The first symptom is the nastier one: sign-in fails with correct
         * credentials, intermittently, and only on a fresh install.
         *
         * Instead AppContainer starts the seed once and anything that depends on
         * it awaits that job. See AppContainer.awaitSeed.
         */
        private fun build(context: Context): AttendanceDatabase =
            Room.databaseBuilder(context, AttendanceDatabase::class.java, NAME).build()
    }
}
