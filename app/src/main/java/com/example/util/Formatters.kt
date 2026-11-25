package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object Formatters {
    private val inrLocale = Locale.Builder().setLanguage("en").setRegion("IN").build()
    private val currencyFmt: NumberFormat = NumberFormat.getCurrencyInstance(inrLocale).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 0
    }
    private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val shortDateFmt = SimpleDateFormat("dd MMM", Locale.getDefault())
    private val monthYearFmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    private val yearFmt = SimpleDateFormat("yyyy", Locale.getDefault())

    fun inr(amount: Double): String {
        val rounded = kotlin.math.round(amount * 100.0) / 100.0
        return currencyFmt.format(rounded)
    }

    fun qty(value: Double): String {
        val rounded = kotlin.math.round(value * 100.0) / 100.0
        return if (rounded % 1.0 == 0.0) {
            rounded.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", rounded).trimEnd('0').trimEnd('.')
        }
    }

    fun date(millis: Long): String = dateFmt.format(Date(millis))
    fun shortDate(millis: Long): String = shortDateFmt.format(Date(millis))
    fun monthYear(millis: Long): String = monthYearFmt.format(Date(millis))
    fun year(millis: Long): String = yearFmt.format(Date(millis))

    fun utcPickerMillisToLocalNoon(utcMillis: Long): Long {
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = utcMillis
        }
        return Calendar.getInstance().apply {
            set(
                utcCal.get(Calendar.YEAR),
                utcCal.get(Calendar.MONTH),
                utcCal.get(Calendar.DAY_OF_MONTH),
                12,
                0,
                0
            )
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}

fun observeNetworkConnectivity(context: Context): Flow<Boolean> = callbackFlow {
    val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun isCurrentlyOnline(): Boolean {
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    trySend(isCurrentlyOnline())

    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            trySend(isCurrentlyOnline())
        }

        override fun onLost(network: Network) {
            trySend(isCurrentlyOnline())
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            trySend(networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
        }
    }

    val request = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()

    try {
        cm.registerNetworkCallback(request, callback)
    } catch (_: Exception) {
        trySend(isCurrentlyOnline())
    }

    awaitClose {
        try {
            cm.unregisterNetworkCallback(callback)
        } catch (_: Exception) {
        }
    }
}.distinctUntilChanged()
