package com.yasharya.attendance.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.yasharya.attendance.data.local.dao.AttendanceDao
import com.yasharya.attendance.data.local.dao.FaceTemplateDao
import com.yasharya.attendance.data.local.dao.StaffDao
import com.yasharya.attendance.data.local.dao.UserDao
import com.yasharya.attendance.data.local.entity.AttendanceEntity
import com.yasharya.attendance.data.local.entity.FaceTemplateEntity
import com.yasharya.attendance.data.local.entity.StaffEntity
import com.yasharya.attendance.data.local.entity.UserEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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

        private fun build(context: Context): AttendanceDatabase {
            // Held outside the builder so the callback can seed off the main thread
            // without blocking whoever triggered the first database open.
            val seedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            return Room.databaseBuilder(context, AttendanceDatabase::class.java, NAME)
                .addCallback(
                    object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            seedScope.launch { DemoSeed.apply(get(context)) }
                        }
                    },
                )
                .build()
        }
    }
}
