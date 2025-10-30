package com.call.janmapping

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SearchViewModel(
    private val api: ApiService = ApiClient.service
) : ViewModel() {

    enum class Stage { CATEGORY, PRODUCT }

    data class UiState(
        val stage: Stage = Stage.CATEGORY,
        val query: String = "",
        val basePrompt: String = "",
        val suggestions: List<SuggestOption> = emptyList(),
        val loading: Boolean = false,
        val error: String? = null,
        val lastCandidates: List<ProductCandidate> = emptyList()
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    data class MapData(val jans: List<String>, val productNames: List<String>)
    private val _goMap = MutableSharedFlow<MapData>(extraBufferCapacity = 1)
    val goMap = _goMap.asSharedFlow()

    fun onQueryChanged(q: String) {
        _state.update { it.copy(query = q) }
    }

    fun startSearch() {
        val q = _state.value.query.trim()
        if (q.isBlank()) return
        viewModelScope.launch {
            try {
                _state.update { it.copy(loading = true, error = null, basePrompt = q) }
                Log.d("SearchVM", "suggest(category) q='$q'")
                val res = api.suggest(SuggestReq(query = q, stage = "category"))
                _state.update {
                    it.copy(
                        stage = Stage.CATEGORY,
                        suggestions = res.options,
                        loading = false
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = "候補の取得に失敗しました: ${e.message}") }
            }
        }
    }

    fun selectSuggestion(option: SuggestOption) {
        val s = _state.value
        viewModelScope.launch {
            when (s.stage) {
                Stage.CATEGORY -> {
                    val joined = "${s.basePrompt} ${option.label}".trim()
                    Log.d("SearchVM", "suggest(product) joined='$joined'")
                    try {
                        _state.update { it.copy(loading = true, error = null) }
                        val res = api.suggest(SuggestReq(query = joined, stage = "product"))
                        _state.update {
                            it.copy(
                                stage = Stage.PRODUCT,
                                suggestions = res.options,
                                loading = false
                            )
                        }
                    } catch (e: Exception) {
                        _state.update { it.copy(loading = false, error = "候補の取得に失敗しました: ${e.message}") }
                    }
                }
                Stage.PRODUCT -> {
                    try {
                        _state.update { it.copy(loading = true, error = null) }
                        val sr = api.search(SearchReq(query = option.label, limit = 50))
                        val jans = sr.candidates.map { it.jan }.distinct()
                        val productNames = sr.candidates.map { it.name }.distinct()
                        Log.d("SearchVM", "search done. candidates=${sr.candidates.size} jans=${jans.size} products=${productNames.size}")
                        _state.update { it.copy(loading = false, lastCandidates = sr.candidates) }
                        _goMap.tryEmit(MapData(jans, productNames))
                    } catch (e: Exception) {
                        _state.update { it.copy(loading = false, error = "検索に失敗しました: ${e.message}") }
                    }
                }
            }
        }
    }
}
