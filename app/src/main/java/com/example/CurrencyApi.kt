package com.example

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

@JsonClass(generateAdapter = true)
data class CurrencyResponse(
    @Json(name = "result") val result: String,
    @Json(name = "base_code") val baseCode: String,
    @Json(name = "time_last_update_utc") val timeLastUpdateUtc: String?,
    @Json(name = "time_next_update_utc") val timeNextUpdateUtc: String?,
    @Json(name = "rates") val rates: Map<String, Double>
)

interface CurrencyApiService {
    @GET("v6/latest/{base}")
    suspend fun getLatestRates(@Path("base") base: String): CurrencyResponse
}

object CurrencyApi {
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://open.er-api.com/")
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    val service: CurrencyApiService = retrofit.create(CurrencyApiService::class.java)
}
