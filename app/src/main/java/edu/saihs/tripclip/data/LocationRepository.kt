package edu.saihs.tripclip.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** A human-readable place: [name] is the spot (e.g. 松山工農), [area] the district (e.g. 台北市信義區). */
data class Place(val name: String?, val area: String?) {
    /** "name・area", or whichever part exists. */
    fun label(separator: String = "・"): String? =
        listOfNotNull(name, area).distinct().joinToString(separator).ifEmpty { null }
}

class LocationRepository(private val context: Context) {

    private val locationManager = context.getSystemService(LocationManager::class.java)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** A fresh fix if one arrives within [timeoutMs], else the last known location. */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(timeoutMs: Long = 8_000): Location? {
        if (!hasPermission()) return null
        val providers = locationManager.getProviders(true)
        val provider = when {
            LocationManager.FUSED_PROVIDER in providers -> LocationManager.FUSED_PROVIDER
            LocationManager.GPS_PROVIDER in providers -> LocationManager.GPS_PROVIDER
            LocationManager.NETWORK_PROVIDER in providers -> LocationManager.NETWORK_PROVIDER
            else -> return lastKnown(providers)
        }
        val fresh = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val signal = android.os.CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                locationManager.getCurrentLocation(provider, signal, context.mainExecutor) { cont.resume(it) }
            }
        }
        return fresh ?: lastKnown(providers)
    }

    /**
     * Live location while collected: the last known fix first, then updates every ~[intervalMs]
     * or after moving [minDistanceMeters]. Completes immediately without location permission.
     */
    @SuppressLint("MissingPermission")
    fun locationUpdates(intervalMs: Long = 2_000, minDistanceMeters: Float = 5f): Flow<Location> = callbackFlow {
        if (!hasPermission()) {
            close()
            return@callbackFlow
        }
        val providers = locationManager.getProviders(true)
        lastKnown(providers)?.let { trySend(it) }

        val listener = LocationListener { trySend(it) }
        val request = LocationRequest.Builder(intervalMs)
            .setMinUpdateDistanceMeters(minDistanceMeters)
            .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
            .build()
        // Fused already blends GPS and network; otherwise listen to both.
        val sources = if (LocationManager.FUSED_PROVIDER in providers) listOf(LocationManager.FUSED_PROVIDER)
        else listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { it in providers }
        sources.forEach { locationManager.requestLocationUpdates(it, request, context.mainExecutor, listener) }

        awaitClose { locationManager.removeUpdates(listener) }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(providers: List<String>): Location? =
        providers.mapNotNull { locationManager.getLastKnownLocation(it) }.maxByOrNull { it.time }

    /** Reverse-geocodes to a [Place]; null when no geocoder backend is available. */
    suspend fun placeAt(latitude: Double, longitude: Double, locale: Locale = Locale.getDefault()): Place? {
        if (!Geocoder.isPresent()) return null
        val address = withTimeoutOrNull(8_000) {
            suspendCancellableCoroutine<Address?> { cont ->
                Geocoder(context, locale).getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) { cont.resume(addresses.firstOrNull()) }
                    override fun onError(errorMessage: String?) { cont.resume(null) }
                })
            }
        } ?: return null
        return address.toPlace()
    }

    private fun Address.toPlace(): Place {
        val feature = featureName?.takeUnless { it.isBlank() || it.all { c -> c.isDigit() || c == '-' } || it == thoroughfare }
        val name = feature ?: thoroughfare ?: subLocality ?: locality
        val area = listOfNotNull(locality ?: adminArea, subLocality).distinct().joinToString("").ifEmpty { null }
        return Place(name, area)
    }
}
