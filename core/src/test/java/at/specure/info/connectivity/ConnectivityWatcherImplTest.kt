package at.specure.info.connectivity

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import at.specure.info.TransportType
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Regression test for the home screen being stuck in the offline (white) state after device
 * wake/unlock although wifi is connected.
 *
 * ConnectivityWatcherImpl caches connectivity purely from NetworkCallback events. While the
 * device sleeps, the app process is frozen and can miss the onAvailable/onCapabilitiesChanged
 * events of the reconnected wifi, leaving the cached state null. When the UI comes back and
 * adds its listener, addListener() must resync from ConnectivityManager instead of delivering
 * the stale null.
 */
class ConnectivityWatcherImplTest {

    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var watcher: ConnectivityWatcherImpl

    @Before
    fun setup() {
        connectivityManager = mockk(relaxed = true)
        watcher = ConnectivityWatcherImpl(connectivityManager)
    }

    /**
     * In production the first listener is the permanent one registered by ActiveNetworkWatcher
     * at app start, so the UI listener under test is always a later one. The first addListener
     * call also triggers registerCallbacks() whose NetworkRequest.Builder is an Android stub
     * that may throw in unit tests — the listener is added before that, so the throw is ignored.
     */
    private fun addPermanentListener() {
        try {
            watcher.addListener(object : ConnectivityWatcher.ConnectivityChangeListener {
                override fun onConnectivityChanged(connectivityInfo: ConnectivityInfo?, network: Network?) = Unit
            })
        } catch (e: RuntimeException) {
            // ignored — thrown by the NetworkRequest.Builder stub inside registerCallbacks()
        }
    }

    @Test
    fun addListener_activeNetworkExists_deliversCurrentConnectivityInfo() {
        val network = mockk<Network>()
        every { network.toString() } returns "105"
        val capabilities = mockk<NetworkCapabilities>(relaxed = true)
        every { capabilities.hasTransport(any()) } returns false
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities

        addPermanentListener()

        var delivered: ConnectivityInfo? = null
        watcher.addListener(object : ConnectivityWatcher.ConnectivityChangeListener {
            override fun onConnectivityChanged(connectivityInfo: ConnectivityInfo?, net: Network?) {
                delivered = connectivityInfo
            }
        })

        assertNotNull(
            "Cached state is stale null while an active network exists — listener must receive fresh info",
            delivered
        )
        assertEquals(TransportType.WIFI, delivered?.transportType)
        assertEquals(105, delivered?.netId)
    }

    @Test
    fun addListener_noActiveNetwork_deliversNull() {
        every { connectivityManager.activeNetwork } returns null

        addPermanentListener()

        var delivered: ConnectivityInfo? = ConnectivityInfo(
            netId = 1,
            transportType = TransportType.WIFI,
            capabilities = emptyList(),
            capabilitiesRaw = null,
            linkDownstreamBandwidthKbps = 0,
            linkUpstreamBandwidthKbps = 0
        )
        watcher.addListener(object : ConnectivityWatcher.ConnectivityChangeListener {
            override fun onConnectivityChanged(connectivityInfo: ConnectivityInfo?, net: Network?) {
                delivered = connectivityInfo
            }
        })

        assertNull("Without an active network the listener must receive null", delivered)
    }
}
