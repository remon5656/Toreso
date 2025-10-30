package com.call.janmapping

import retrofit2.http.Body
import retrofit2.http.POST

data class SuggestReq(
    val query: String,
    val stage: String,   // "category" | "product"
    val locale: String = "ja-JP"
)

data class SuggestOption(
    val id: String,
    val label: String
)

data class SuggestRes(
    val stage: String,
    val options: List<SuggestOption>,
    val source: String? = null
)

interface ApiService {
    @POST("/suggest")
    suspend fun suggest(@Body req: SuggestReq): SuggestRes

    @POST("/search")
    suspend fun search(@Body req: SearchReq): SearchRes

    @POST("/stores")
    suspend fun stores(@Body req: StoresReq): StoresRes
}

data class SearchReq(val query: String, val limit: Int = 50)

data class ProductCandidate(
    val jan: String,
    val name: String,
    val category: String? = null,
    val tags: List<String>? = emptyList(),
    val score_query_match: Double = 0.0
)

data class SearchRes(val candidates: List<ProductCandidate>)

data class StoresReq(
    val jan_list: List<String>,
    val lat: Double,
    val lng: Double,
    val radius_km: Double = 3.0
)

data class StoreItem(
    val store_id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val last_sold_at: String?,
    val weekly_count: Int,
    val score_availability: Double,
    val grade: String
)

data class StoresRes(val stores: List<StoreItem>)
