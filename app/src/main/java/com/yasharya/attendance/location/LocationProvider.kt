package com.yasharya.attendance.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.yasharya.attendance.data.local.entity.LocationStatus
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume

/** Where the device was when attendance was marked, or why we do not know. */
data class LocationFix(
    val status: LocationStatus,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val address: String? = null,
) {
    val hasCoordinates: Boolean get() = latitude != null && longitude != null
}

/**
 * One-shot location for an attendance record.
 *
 * Product decision worth stating: a failed location fix does NOT block
 * attendance. Someone standing in a basement stockroom with no GPS is still at
 * work, and refusing to record their attendance punishes them for their
 * building. Instead the record is written with the reason the fix failed, which
 * is strictly more information than a silent null and lets an admin tell
 * "denied the permission" apart from "could not see the sky".
 */
class LocationProvider(private val context: Context) {

    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** True when the user granted only the approximate location. */
    fun hasOnlyCoarsePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED && hasPermission()

    fun isLocationEnabled(): Boolean {
        val manager = ContextCompat.getSystemService(context, LocationManager::class.java)
        return manager != null && LocationManagerCompat.isLocationEnabled(manager)
    }

    @SuppressLint("MissingPermission") // Guarded by hasPermission() immediately below.
    suspend fun currentFix(resolveAddress: Boolean = true): LocationFix {
        if (!hasPermission()) return LocationFix(LocationStatus.PERMISSION_DENIED)
        if (!isLocationEnabled()) return LocationFix(LocationStatus.SERVICES_DISABLED)

        val location = try {
            withTimeout(FIX_TIMEOUT_MS) {
                // A fresh fix is what we want, but a recent cached one beats
                // failing: for an attendance stamp, thirty seconds of staleness
                // is irrelevant and a timeout is not.
                requestCurrentLocation() ?: lastKnownLocation()
            }
        } catch (_: TimeoutCancellationException) {
            runCatching { lastKnownLocation() }.getOrNull()
        } catch (_: SecurityException) {
            return LocationFix(LocationStatus.PERMISSION_DENIED)
        } ?: return LocationFix(LocationStatus.TIMED_OUT)

        val address = if (resolveAddress) reverseGeocode(location.latitude, location.longitude) else null

        return LocationFix(
            status = LocationStatus.RESOLVED,
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = location.accuracy,
            address = address,
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestCurrentLocation(): Location? = suspendCancellableCoroutine { cont ->
        val request = CurrentLocationRequest.Builder()
            .setPriority(
                if (hasOnlyCoarsePermission()) {
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY
                } else {
                    Priority.PRIORITY_HIGH_ACCURACY
                },
            )
            .setMaxUpdateAgeMillis(MAX_CACHED_AGE_MS)
            .setDurationMillis(FIX_TIMEOUT_MS)
            .build()

        val token = com.google.android.gms.tasks.CancellationTokenSource()
        client.getCurrentLocation(request, token.token)
            .addOnSuccessListener { cont.resumeIfActive(it) }
            .addOnFailureListener { cont.resumeIfActive(null) }
            .addOnCanceledListener { cont.resumeIfActive(null) }

        cont.invokeOnCancellation { token.cancel() }
    }

    @SuppressLint("MissingPermission")
    private suspend fun lastKnownLocation(): Location? = suspendCancellableCoroutine { cont ->
        client.lastLocation
            .addOnSuccessListener { cont.resumeIfActive(it) }
            .addOnFailureListener { cont.resumeIfActive(null) }
            .addOnCanceledListener { cont.resumeIfActive(null) }
    }

    /**
     * Best-effort street address. Never fatal: a record with coordinates and no
     * label is still a complete record, and geocoding needs network that the
     * device may not have.
     */
    private suspend fun reverseGeocode(latitude: Double, longitude: Double): String? =
        withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) return@withContext null
            val geocoder = Geocoder(context, Locale.getDefault())

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // The blocking overload is deprecated from API 33 because it does
                // network I/O; the listener form is the supported path.
                runCatching {
                    withTimeout(GEOCODE_TIMEOUT_MS) {
                        suspendCancellableCoroutine { cont ->
                            geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                                cont.resumeIfActive(addresses.firstOrNull()?.toShortLabel())
                            }
                        }
                    }
                }.getOrNull()
            } else {
                runCatching {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()?.toShortLabel()
                }.getOrElse { error -> if (error is IOException) null else null }
            }
        }

    companion object {
        private const val FIX_TIMEOUT_MS = 8_000L
        private const val GEOCODE_TIMEOUT_MS = 5_000L
        private const val MAX_CACHED_AGE_MS = 30_000L
    }
}

private fun <T> CancellableContinuation<T>.resumeIfActive(value: T) {
    if (isActive) resume(value)
}

/**
 * A short, human label rather than a full postal address. "Andheri East,
 * Mumbai" is what someone scanning an attendance list needs; the full address
 * is noise at list density.
 */
private fun android.location.Address.toShortLabel(): String? {
    val parts = listOfNotNull(
        subLocality ?: locality ?: featureName,
        locality?.takeIf { it != subLocality },
    ).distinct()
    return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
}
