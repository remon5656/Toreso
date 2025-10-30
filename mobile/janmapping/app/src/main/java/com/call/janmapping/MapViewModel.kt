package com.call.janmapping

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MapViewModel : ViewModel() {
    data class UiState(
        val center: LatLng = LatLng(35.658, 139.701),
        val radiusKm: Float = 3f,
        val janList: List<String> = emptyList(),
        val productNames: List<String> = emptyList(),
        val stores: List<StoreItem> = emptyList(),
        val loading: Boolean = false,
        val error: String? = null,
        val selectedStoreId: String? = null,
        val monitoringEnabled: Boolean = false
    )
    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    fun init(jans: List<String>, initialCenter: LatLng? = null, productNames: List<String> = emptyList()) {
        Log.d("MapVM", "init() jans=${jans.joinToString()} (size=${jans.size}), productNames=${productNames.joinToString()}, initialCenter=$initialCenter")
        _state.update { it.copy(janList = jans, productNames = productNames, center = initialCenter ?: it.center) }
    }
    fun setCenter(latLng: LatLng) {
        Log.d("MapVM", "setCenter $latLng")
        _state.update { it.copy(center = latLng) }
    }
    fun setRadius(km: Float) {
        Log.d("MapVM", "setRadius $km")
        _state.update { it.copy(radiusKm = km) }
    }
    fun selectStore(id: String?) { _state.update { it.copy(selectedStoreId = id) } }

    fun fetchStores() {
        val s = _state.value
        Log.d("MapVM", "fetchStores() enter janList.size=${s.janList.size} center=${s.center} radius=${s.radiusKm}")
        if (s.janList.isEmpty()) {
            Log.w("MapVM", "fetchStores() skipped because janList is empty")
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val res = ApiClient.service.stores(
                    StoresReq(
                        jan_list = s.janList,
                        lat = s.center.latitude,
                        lng = s.center.longitude,
                        radius_km = s.radiusKm.toDouble()
                    )
                )
                Log.d("MapVM", "fetchStores() success stores=${res.stores.size}")
                _state.update { it.copy(stores = res.stores, loading = false) }
            } catch (e: Exception) {
                Log.e("MapVM", "fetchStores() failed: ${e.message}", e)
                _state.update { it.copy(error = "店舗の取得に失敗しました: ${e.message}", loading = false) }
            }
        }
    }

    fun startLocationMonitoring(context: Context) {
        val s = _state.value
        if (s.stores.isNotEmpty() && !s.monitoringEnabled) {
            LocationMonitorService.start(context, s.stores, s.janList, s.productNames)
            _state.update { it.copy(monitoringEnabled = true) }
            Log.d("MapVM", "Location monitoring started with ${s.stores.size} stores and ${s.productNames.size} products")
        }
    }

    fun stopLocationMonitoring(context: Context) {
        if (_state.value.monitoringEnabled) {
            LocationMonitorService.stop(context)
            _state.update { it.copy(monitoringEnabled = false) }
            Log.d("MapVM", "Location monitoring stopped")
        }
    }
}
