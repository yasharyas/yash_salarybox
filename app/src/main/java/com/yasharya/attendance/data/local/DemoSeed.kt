package com.yasharya.attendance.data.local

import com.yasharya.attendance.data.local.entity.StaffEntity
import com.yasharya.attendance.data.local.entity.UserEntity
import com.yasharya.attendance.data.local.entity.UserRole

/**
 * First-run demo data.
 *
 * Runs from [RoomDatabase.Callback.onCreate], so it fires exactly once per
 * install rather than on every launch, and it is guarded by a count check so a
 * re-entrant call cannot duplicate rows.
 */
object DemoSeed {
    const val ADMIN_USERNAME = "admin"
    const val ADMIN_PASSWORD = "admin123"

    /**
     * Every staff member signs in with their employee ID and this password.
     * A real deployment would issue a one-time credential at enrolment instead.
     */
    const val DEFAULT_STAFF_PASSWORD = "staff123"

    private val demoStaff = listOf(
        "EMP-001" to "Priya Sharma",
        "EMP-002" to "Rahul Mehta",
        "EMP-003" to "Aisha Khan",
    )

    suspend fun apply(db: AttendanceDatabase) {
        if (db.userDao().count() > 0) return

        val now = System.currentTimeMillis()
        val adminSalt = PasswordHasher.newSalt()
        db.userDao().upsert(
            UserEntity(
                username = ADMIN_USERNAME,
                passwordHash = PasswordHasher.hash(ADMIN_PASSWORD, adminSalt),
                salt = adminSalt,
                role = UserRole.ADMIN,
                staffId = null,
            ),
        )

        demoStaff.forEachIndexed { index, (employeeId, name) ->
            val staffId = db.staffDao().insert(
                StaffEntity(
                    employeeId = employeeId,
                    name = name,
                    createdAt = now - (demoStaff.size - index) * 86_400_000L,
                ),
            )
            db.userDao().upsert(newStaffUser(employeeId, staffId))
        }
    }

    /** Also used when an admin adds a staff member at runtime. */
    fun newStaffUser(employeeId: String, staffId: Long): UserEntity {
        val salt = PasswordHasher.newSalt()
        return UserEntity(
            username = employeeId.uppercase(),
            passwordHash = PasswordHasher.hash(DEFAULT_STAFF_PASSWORD, salt),
            salt = salt,
            role = UserRole.STAFF,
            staffId = staffId,
        )
    }
}
