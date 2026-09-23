package com.yasharya.attendance.ui

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes.
 *
 * Arguments are real Kotlin types rather than strings stuffed into a URL, so a
 * wrong argument is a compile error instead of a crash on a screen someone
 * reaches once a month.
 */
sealed interface Route {

    @Serializable data object Login : Route

    @Serializable data object AdminStaffList : Route

    @Serializable data object AdminAddStaff : Route

    @Serializable data class AdminStaffProfile(val staffId: Long) : Route

    /** Admin captures a staff member's face. [staffName] is passed to keep the copy personal. */
    @Serializable data class Enrolment(val staffId: Long, val staffName: String) : Route

    @Serializable data object StaffHome : Route

    @Serializable data object StaffHistory : Route

    @Serializable data class MarkAttendance(val staffId: Long, val employeeId: String) : Route
}
