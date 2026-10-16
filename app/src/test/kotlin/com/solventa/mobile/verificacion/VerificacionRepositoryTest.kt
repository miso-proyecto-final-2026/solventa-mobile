package com.solventa.mobile.verificacion

import com.solventa.mobile.red.VerificacionApi
import com.solventa.mobile.red.VerificacionResponse
import com.solventa.mobile.sesion.SesionActual
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.Headers.Companion.headersOf
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Response

class VerificacionRepositoryTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    private val api = mockk<VerificacionApi>()
    private val repository = VerificacionRepository(api, SesionActual)

    private lateinit var anverso: String
    private lateinit var reverso: String
    private lateinit var selfie: String

    private val verificado = VerificacionResponse("v1", "VERIFICADO", null, 3, "2026-10-16T12:00:00Z")

    @Before
    fun preparar() {
        SesionActual.iniciar("c1", "token-123")
        fun imagen(nombre: String) = carpeta.newFile(nombre).apply { writeBytes(byteArrayOf(1, 2, 3)) }.absolutePath
        anverso = imagen("a.jpg")
        reverso = imagen("r.jpg")
        selfie = imagen("s.jpg")
    }

    @After
    fun limpiar() = SesionActual.cerrar()

    private fun fallo(codigo: Int, cabeceras: okhttp3.Headers? = null): Response<VerificacionResponse> {
        val bruta = okhttp3.Response.Builder()
            .code(codigo)
            .message("error")
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .request(okhttp3.Request.Builder().url("http://localhost/").build())
            .apply { cabeceras?.let { headers(it) } }
            .build()
        return Response.error("{}".toResponseBody(), bruta)
    }

    private suspend fun enviar() = repository.enviar("clave-0001", anverso, reverso, selfie)

    @Test
    fun `sin sesion no llama al servidor`() = runTest {
        SesionActual.cerrar()

        val resultado = enviar()

        assertEquals(VerificacionRepository.Resultado.Fallo(ErrorEnvio.SIN_SESION), resultado)
        coVerify(exactly = 0) { api.enviar(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `envia el token, la clave de idempotencia y las tres partes del formulario`() = runTest {
        val autorizacion = slot<String>()
        val clave = slot<String>()
        val reto = slot<RequestBody>()
        val partes = mutableListOf<MultipartBody.Part>()
        coEvery {
            api.enviar("c1", capture(autorizacion), capture(clave), capture(reto), capture(partes), any(), any())
        } returns Response.success(201, verificado)
        coEvery { api.enviar(any(), any(), any(), any(), any(), any(), any()) } returns Response.success(201, verificado)

        val resultado = enviar()

        assertTrue(resultado is VerificacionRepository.Resultado.Exito)
        coVerify {
            api.enviar(
                "c1",
                "Bearer token-123",
                "clave-0001",
                match { it.contentType().toString().startsWith("text/plain") },
                match { nombreDeParte(it) == "documento_anverso" },
                match { nombreDeParte(it) == "documento_reverso" },
                match { nombreDeParte(it) == "selfie" },
            )
        }
    }

    private fun nombreDeParte(parte: MultipartBody.Part): String? =
        parte.headers?.get("Content-Disposition")?.substringAfter("name=\"")?.substringBefore("\"")

    @Test
    fun `un 200 por repeticion idempotente tambien es exito`() = runTest {
        coEvery { api.enviar(any(), any(), any(), any(), any(), any(), any()) } returns Response.success(200, verificado)

        assertTrue(enviar() is VerificacionRepository.Resultado.Exito)
    }

    @Test
    fun `reintenta una vez con la misma clave ante un corte de conexion`() = runTest {
        val claves = mutableListOf<String>()
        coEvery { api.enviar(any(), any(), capture(claves), any(), any(), any(), any()) } throws
            IOException("reset") andThen Response.success(201, verificado)

        val resultado = enviar()

        assertTrue(resultado is VerificacionRepository.Resultado.Exito)
        assertEquals(listOf("clave-0001", "clave-0001"), claves)
    }

    @Test
    fun `si el reintento tambien falla informa que no hay conexion`() = runTest {
        coEvery { api.enviar(any(), any(), any(), any(), any(), any(), any()) } throws IOException("sin red")

        val resultado = enviar()

        assertEquals(VerificacionRepository.Resultado.Fallo(ErrorEnvio.SIN_CONEXION), resultado)
        coVerify(exactly = 2) { api.enviar(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `traduce los codigos de error del servidor`() = runTest {
        val esperados = mapOf(
            401 to ErrorEnvio.SIN_SESION,
            403 to ErrorEnvio.SIN_SESION,
            409 to ErrorEnvio.YA_PROCESADA,
            413 to ErrorEnvio.IMAGEN_INVALIDA,
            415 to ErrorEnvio.IMAGEN_INVALIDA,
            422 to ErrorEnvio.IMAGEN_INVALIDA,
            500 to ErrorEnvio.INESPERADO,
        )
        esperados.forEach { (codigo, error) ->
            coEvery { api.enviar(any(), any(), any(), any(), any(), any(), any()) } returns fallo(codigo)

            assertEquals("código $codigo", VerificacionRepository.Resultado.Fallo(error), enviar())
        }
    }

    @Test
    fun `un 429 informa cuanto esperar segun Retry-After`() = runTest {
        coEvery { api.enviar(any(), any(), any(), any(), any(), any(), any()) } returns
            fallo(429, headersOf("Retry-After", "1800"))

        assertEquals(
            VerificacionRepository.Resultado.Fallo(ErrorEnvio.BLOQUEADO, 1800L),
            enviar(),
        )
    }

    @Test
    fun `el reto de vida es girar a la izquierda`() {
        assertEquals("GIRAR_IZQUIERDA", RETO_VIDA)
    }

    @Test
    fun `no se tocan los archivos al enviar`() = runTest {
        coEvery { api.enviar(any(), any(), any(), any(), any(), any(), any()) } returns Response.success(201, verificado)

        enviar()

        assertTrue(File(anverso).exists() && File(reverso).exists() && File(selfie).exists())
    }
}
