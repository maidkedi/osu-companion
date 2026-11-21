package com.osuchat.app.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkConnectivityMonitor @Inject constructor(
    @ApplicationContext context: Context
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val lock = Any()
    private var currentNetworkHandle: Long? = null
    private var lastTransportMask: Int = 0

    private val _isOnline = MutableStateFlow(initConnectivityState())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _networkSwitchEvents = MutableSharedFlow<Long>(extraBufferCapacity = 16)
    val networkSwitchEvents: SharedFlow<Long> = _networkSwitchEvents.asSharedFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val handle = network.networkHandle
            val caps = runCatching { connectivityManager.getNetworkCapabilities(network) }.getOrNull()
            if (caps != null && !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                return
            }
            val mask = caps?.let(::computeTransportMask) ?: 0

            val switched = synchronized(lock) {
                val wasOnline = _isOnline.value
                val prevHandle = currentNetworkHandle
                currentNetworkHandle = handle
                if (mask != 0) {
                    lastTransportMask = mask
                }
                wasOnline && prevHandle != null && prevHandle != handle
            }
            _isOnline.value = true
            if (switched) {
                _networkSwitchEvents.tryEmit(handle)
            }
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val handle = network.networkHandle
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val mask = computeTransportMask(capabilities)

            var emitSwitch = false
            var nextOnline: Boolean? = null

            synchronized(lock) {
                if (currentNetworkHandle != null && currentNetworkHandle != handle) {
                    return
                }
                if (!hasInternet) {
                    if (currentNetworkHandle == handle) {
                        currentNetworkHandle = null
                        lastTransportMask = 0
                        nextOnline = false
                    }
                } else {
                    val wasOnline = _isOnline.value
                    val sameHandle = currentNetworkHandle == handle
                    emitSwitch = wasOnline && sameHandle && lastTransportMask != 0 && lastTransportMask != mask
                    currentNetworkHandle = handle
                    if (mask != 0) {
                        lastTransportMask = mask
                    }
                    nextOnline = true
                }
            }

            nextOnline?.let { _isOnline.value = it }
            if (emitSwitch) {
                _networkSwitchEvents.tryEmit(handle)
            }
        }

        override fun onLost(network: Network) {
            val handle = network.networkHandle
            val shouldMarkOffline = synchronized(lock) {
                if (currentNetworkHandle == null || currentNetworkHandle == handle) {
                    currentNetworkHandle = null
                    lastTransportMask = 0
                    true
                } else {
                    // Ignore late onLost for an older network after default already moved to a newer handle
                    false
                }
            }
            if (shouldMarkOffline) {
                _isOnline.value = false
            }
        }

        override fun onUnavailable() {
            synchronized(lock) {
                currentNetworkHandle = null
                lastTransportMask = 0
            }
            _isOnline.value = false
        }
    }

    init {
        runCatching {
            connectivityManager.registerDefaultNetworkCallback(callback)
        }
    }

    private fun initConnectivityState(): Boolean {
        val active = runCatching { connectivityManager.activeNetwork }.getOrNull() ?: return false
        val caps = runCatching { connectivityManager.getNetworkCapabilities(active) }.getOrNull() ?: return false
        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (hasInternet) {
            synchronized(lock) {
                currentNetworkHandle = active.networkHandle
                lastTransportMask = computeTransportMask(caps)
            }
        }
        return hasInternet
    }

    private fun computeTransportMask(caps: NetworkCapabilities): Int {
        var mask = 0
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) mask = mask or 1
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) mask = mask or 2
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) mask = mask or 4
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) mask = mask or 8
        return mask
    }
}
