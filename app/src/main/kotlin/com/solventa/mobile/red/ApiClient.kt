package com.solventa.mobile.red

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.solventa.mobile.BuildConfig
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

object ApiClient {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Solo método, URL y código de estado. Nunca el cuerpo: contendría contraseñas, el token y,
     * en la verificación de identidad, las imágenes del documento (HU02).
     */
    fun nivelDeLog(debug: Boolean): HttpLoggingInterceptor.Level =
        if (debug) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = nivelDeLog(BuildConfig.DEBUG)
                redactHeader("Authorization")
            },
        )
        .build()

    // Subir tres fotos por una red móvil tarda más que un JSON: tiempos propios para esta llamada.
    private val okHttpClientVerificacion = okHttpClient.newBuilder()
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    private val retrofitVerificacion: Retrofit by lazy {
        retrofit.newBuilder().client(okHttpClientVerificacion).build()
    }

    val verificacionApi: VerificacionApi by lazy {
        retrofitVerificacion.create(VerificacionApi::class.java)
    }

    val clienteApi: ClienteApi by lazy { retrofit.create(ClienteApi::class.java) }

    val consentimientoApi: ConsentimientoApi by lazy { retrofit.create(ConsentimientoApi::class.java) }
}
