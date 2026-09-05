package com.airsoft.tracker.data.network

import com.airsoft.tracker.BuildConfig
import com.airsoft.tracker.data.model.JoinResponseDto
import com.airsoft.tracker.data.model.SquadStateDto
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

/** API REST del backend Airsoft Tracker */
interface ApiService {

    @POST("api/squad/create")
    suspend fun createSquad(@Body body: CreateSquadRequest): JoinResponseDto

    @POST("api/squad/join")
    suspend fun joinSquad(@Body body: JoinSquadRequest): JoinResponseDto

    @GET("api/squad/{code}/state")
    suspend fun getSquadState(@Path("code") code: String): SquadStateDto
}

@kotlinx.serialization.Serializable
data class CreateSquadRequest(val nick: String, val name: String = "")

@kotlinx.serialization.Serializable
data class JoinSquadRequest(val nick: String, val squadCode: String)

object ApiClient {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    val api: ApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
    }

    val jsonParser: Json get() = json
}