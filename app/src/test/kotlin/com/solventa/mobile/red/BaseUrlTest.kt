package com.solventa.mobile.red

import com.solventa.mobile.BuildConfig
import org.junit.Assert.assertTrue
import org.junit.Test

class BaseUrlTest {

    @Test
    fun `la URL base es http o https y termina en barra`() {
        // Retrofit exige la barra final; build.gradle.kts la agrega cuando se sobrescribe con -PsolventaBaseUrl.
        assertTrue(BuildConfig.BASE_URL, BuildConfig.BASE_URL.startsWith("http://") || BuildConfig.BASE_URL.startsWith("https://"))
        assertTrue(BuildConfig.BASE_URL, BuildConfig.BASE_URL.endsWith("/"))
    }
}
