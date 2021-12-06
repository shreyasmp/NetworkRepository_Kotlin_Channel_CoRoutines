import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 *  Network Repository Interface
 */
interface INetworkStatusRepository {
    // Conflated BroadCast channel - only the most recent/latest value is received and previously
    // sent value is overwritten/lost
    // For network status changes you can have only one network in usage on Phone switching between
    // WiFi and Mobile Data
    val networkStatus: ConflatedBroadcastChannel<NetworkStatus>

    // Sealed class with different network status objects
    sealed class NetworkStatus {
        // When no available internet connection
        object Unavailable : NetworkStatus()

        // Internet connection but no data received
        object Unknown : NetworkStatus()

        // WiFi connection available
        data class Wifi(val ssid: String) : NetworkStatus()

        // Mobile Data connection available
        data class MobileData(val carrier: String) : NetworkStatus()
    }
}

/**
 *  This class gives us active network interface change status
 *  Singleton class implementing Network Status Repository interface
 *  This class needs a context as network API's require cntext
 *  Reason this is a singleton class is because we need only one context of network statuses
 */
@Singleton
open class NetworkStatusRepository @Inject constructor(context: Context) :
        INetworkStatusRepository {

    // Connectivity Manager to listen to network changes of type Mobile/WiFi
    private val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val mobileManager =
            context.applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    override val networkStatus = ConflatedBroadcastChannel<INetworkStatusRepository.NetworkStatus>()

    init {
        // Register the network callback
        // Register only once to avoid duplicate callbacks, hence keeping in init.
        connectivityManager.registerDefaultNetworkCallback(object :
                ConnectivityManager.NetworkCallback() {

            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                connectivityManager.activeNetwork?.let {
                    connectivityManager.getNetworkCapabilities(it)
                }?.also {
                    when {
                        it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                            MainScope().launch {
                                networkStatus.send(
                                        INetworkStatusRepository.NetworkStatus.Wifi(
                                                wifiManager.connectionInfo.ssid.toString().replace("\"", "")
                                        )
                                )
                            }
                        }
                        it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                            MainScope().launch {
                                networkStatus.send(
                                        INetworkStatusRepository.NetworkStatus.MobileData(
                                                mobileManager.networkOperatorName.toString()
                                                        .replace("\"", "")
                                        )
                                )
                            }
                        }
                    }
                } ?: MainScope().launch {
                    networkStatus.send(INetworkStatusRepository.NetworkStatus.Unknown)
                }
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                MainScope().launch {
                    networkStatus.send(INetworkStatusRepository.NetworkStatus.Unavailable)
                }
            }
        })
    }
}