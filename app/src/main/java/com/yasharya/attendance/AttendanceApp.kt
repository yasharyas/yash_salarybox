package com.yasharya.attendance

import android.app.Application
import android.content.Context
import com.yasharya.attendance.data.PhotoStorage
import com.yasharya.attendance.data.SessionStore
import com.yasharya.attendance.data.local.AttendanceDatabase
import com.yasharya.attendance.data.local.DemoSeed
import com.yasharya.attendance.data.repository.AttendanceRepository
import com.yasharya.attendance.data.repository.AuthRepository
import com.yasharya.attendance.data.repository.StaffRepository
import com.yasharya.attendance.face.FaceRecognitionService
import com.yasharya.attendance.location.LocationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

/**
 * Manual dependency wiring.
 *
 * Hilt would be the reflex here, but for a single-module app with roughly eight
 * collaborators it buys an annotation processor, a second round of codegen and a
 * layer of indirection in exchange for saving this one file. Constructor
 * injection is already happening; this container is just the composition root
 * that does it. The trade would flip the moment this app gained modules or
 * needed scoped, swappable bindings, and the README says so.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = AttendanceDatabase.get(appContext)

    private val photoStorage = PhotoStorage(appContext)
    private val sessionStore = SessionStore(appContext)
    private val locationProvider = LocationProvider(appContext)

    val faceRecognition = FaceRecognitionService(appContext)

    private val containerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Demo seeding, started once at startup and awaited by anything that reads
     * the tables it fills.
     *
     * This is a Deferred rather than a fire-and-forget launch for one reason:
     * sign-in is usually the very first database read, and if it does not wait
     * it queries an empty table and rejects valid credentials. DemoSeed itself
     * is idempotent, so a second call is harmless.
     */
    private val seedJob: Deferred<Unit> = containerScope.async {
        runCatching { DemoSeed.apply(database) }.getOrElse { }
    }

    val authRepository = AuthRepository(
        userDao = database.userDao(),
        sessionStore = sessionStore,
        awaitReady = seedJob::await,
    )

    val staffRepository = StaffRepository(
        staffDao = database.staffDao(),
        userDao = database.userDao(),
        faceTemplateDao = database.faceTemplateDao(),
        photoStorage = photoStorage,
    )

    val attendanceRepository = AttendanceRepository(
        attendanceDao = database.attendanceDao(),
        staffRepository = staffRepository,
        faceRecognition = faceRecognition,
        locationProvider = locationProvider,
        photoStorage = photoStorage,
    )

    val location: LocationProvider get() = locationProvider
}

class AttendanceApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Reaches the container from a composable or a ViewModel factory. */
val Context.appContainer: AppContainer
    get() = (applicationContext as AttendanceApp).container
