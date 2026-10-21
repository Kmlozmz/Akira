package com.akira.app.core.lyrics

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

/** Validated internet, including live Wi-Fi/mobile handovers and captive portals. */
@Composable
fun rememberInternetAvailable(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(ConnectivityManager::class.java) }
    fun available() = manager?.getNetworkCapabilities(manager.activeNetwork)?.let {
        it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } == true
    var online by remember(manager) { mutableStateOf(available()) }
    DisposableEffect(manager) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { online = available() }
            override fun onLost(network: Network) { online = available() }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { online = available() }
        }
        manager?.registerDefaultNetworkCallback(callback)
        onDispose { manager?.unregisterNetworkCallback(callback) }
    }
    return online
}
