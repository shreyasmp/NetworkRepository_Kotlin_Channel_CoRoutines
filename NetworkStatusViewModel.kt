import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.lang3.StringUtils
import javax.inject.Inject

/**
 *  Network status view that gets the status of every kind of active network
 *  on the device and then UI class can use the active network statuses for rendering
 *  or any operation that it intends to perform.
 */
class NetworkStatusViewModel @Inject constructor(
        private val networkStatusRepository: NetworkStatusRepository
) : ViewModel() {

    // Also observe carefully, we are using MutableLiveData to set value and
    // LiveData to read data in View as LiveData is ReadOnly for UI operations
    // if network is connected
    private var _isNetworkConnected: MutableLiveData<Boolean> = MutableLiveData()
    val isNetworkConnected: LiveData<Boolean> = _isNetworkConnected

    // if only mobile data
    private var _isOnlyMobileConnected: MutableLiveData<Boolean> = MutableLiveData()
    val isOnlyMobileConnected: LiveData<Boolean> = _isOnlyMobileConnected

    // current network (WiFi or Mobile)
    private var _currentConnectedNetwork: MutableLiveData<String> = MutableLiveData()
    val currentConnectedNetwork: LiveData<String> = _currentConnectedNetwork

    init {
        // CoRoutines for consuming the channel value sent
        this.viewModelScope.launch {
            // consumeEach is a extension operator similar to "for" loop on consumers
            networkStatusRepository.networkStatus.consumeEach { status ->
                _isNetworkConnected.value =
                        status !is INetworkStatusRepository.NetworkStatus.Unavailable
            }
        }
        this.viewModelScope.launch {
            networkStatusRepository.networkStatus.consumeEach { status ->
                _isOnlyMobileConnected.value =
                        status !is INetworkStatusRepository.NetworkStatus.Wifi && status is INetworkStatusRepository.NetworkStatus.Cellular
            }
        }
        this.viewModelScope.launch {
            networkStatusRepository.networkStatus.consumeEach { status ->
                _currentConnectedNetwork.value = when (status) {
                    is INetworkStatusRepository.NetworkStatus.Wifi -> status.ssid
                    is INetworkStatusRepository.NetworkStatus.Cellular -> status.carrier
                    else -> StringUtils.EMPTY
                }
            }
        }
    }
}