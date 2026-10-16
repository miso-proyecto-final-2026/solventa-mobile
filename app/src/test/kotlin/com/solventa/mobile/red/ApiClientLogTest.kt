package com.solventa.mobile.red

import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ApiClientLogTest {

    @Test
    fun `en debug no se registra el cuerpo de las peticiones`() {
        // BODY volcaría contraseñas, el token y las imágenes del documento al logcat.
        assertEquals(HttpLoggingInterceptor.Level.BASIC, ApiClient.nivelDeLog(debug = true))
        assertNotEquals(HttpLoggingInterceptor.Level.BODY, ApiClient.nivelDeLog(debug = true))
    }

    @Test
    fun `en release no se registra nada`() {
        assertEquals(HttpLoggingInterceptor.Level.NONE, ApiClient.nivelDeLog(debug = false))
    }
}
