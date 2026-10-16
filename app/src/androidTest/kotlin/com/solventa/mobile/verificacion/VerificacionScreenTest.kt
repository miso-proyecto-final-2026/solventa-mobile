package com.solventa.mobile.verificacion

import android.graphics.Bitmap
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.solventa.mobile.R
import com.solventa.mobile.red.VerificacionApi
import com.solventa.mobile.red.VerificacionResponse
import java.io.File
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import retrofit2.Response

/**
 * Recorrido de HU02 con cámara y servidor simulados. Requiere dispositivo/emulador, por lo que corre
 * solo con ENABLE_ESPRESSO (ver .github/workflows/ci.yml). Los textos salen de los recursos, así que
 * las pruebas valen para es-CO y en-US.
 */
class VerificacionScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var foto: String

    private fun texto(id: Int, vararg argumentos: Any) = contexto.getString(id, *argumentos)

    @Before
    fun crearFoto() {
        val archivo = File.createTempFile("prueba_", ".jpg", contexto.cacheDir)
        Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 80, archivo.outputStream())
        foto = archivo.absolutePath
    }

    private class ApiSimulada(private val estado: String) : VerificacionApi {
        override suspend fun enviar(
            clienteId: String,
            autorizacion: String,
            idempotencyKey: String,
            reto: RequestBody,
            anverso: MultipartBody.Part,
            reverso: MultipartBody.Part,
            selfie: MultipartBody.Part,
        ): Response<VerificacionResponse> =
            Response.success(201, VerificacionResponse("v1", estado, null, 2, "2026-10-16T12:00:00Z"))
    }

    private fun viewModel(estado: String = "VERIFICADO", calidad: ProblemaImagen? = null): VerificacionViewModel {
        com.solventa.mobile.sesion.SesionActual.iniciar("c1", "token")
        return VerificacionViewModel(
            repository = VerificacionRepository(ApiSimulada(estado)),
            analizador = { calidad },
            borrarArchivo = {},
        )
    }

    private fun mostrar(
        vm: VerificacionViewModel,
        permiso: Boolean = true,
        onSolicitarPermiso: () -> Unit = {},
    ) = composeTestRule.setContent {
        VerificacionScreen(
            viewModel = vm,
            permisoCamaraConcedido = permiso,
            onSolicitarPermiso = onSolicitarPermiso,
            onAbrirAjustes = {},
            onFinalizar = {},
            // Cámara simulada: un botón que "toma" siempre la misma foto.
            camara = { _, _, onCaptura ->
                Button(onClick = { onCaptura(foto) }) { Text("Capturar simulada") }
            },
        )
    }

    private fun capturarYUsar() {
        composeTestRule.onNodeWithText("Capturar simulada").performClick()
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithTextCompat(texto(R.string.verificacion_usar_foto))
        }
        composeTestRule.onNodeWithText(texto(R.string.verificacion_usar_foto)).performClick()
    }

    @Test
    fun conPermisoDenegadoNoSeAvanzaYSeExplicaComoHabilitarlo() {
        var solicitudes = 0
        val vm = viewModel()
        mostrar(vm, permiso = false, onSolicitarPermiso = { solicitudes++ })

        composeTestRule.onNodeWithText(texto(R.string.verificacion_permiso_explicacion)).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.verificacion_permitir_camara)).performClick()
        vm.onPermiso(false) // el usuario lo niega en el diálogo del sistema

        composeTestRule.onNodeWithText(texto(R.string.verificacion_permiso_denegado)).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.verificacion_abrir_ajustes)).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.verificacion_anverso_titulo)).assertDoesNotExist()
        check(solicitudes == 1)
    }

    @Test
    fun recorridoCompletoHastaIdentidadVerificada() {
        mostrar(viewModel("VERIFICADO"))

        composeTestRule.onNodeWithText(texto(R.string.verificacion_anverso_titulo)).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.verificacion_progreso, 1, 4)).assertIsDisplayed()
        capturarYUsar()
        composeTestRule.onNodeWithText(texto(R.string.verificacion_reverso_titulo)).assertIsDisplayed()
        capturarYUsar()
        composeTestRule.onNodeWithText(texto(R.string.verificacion_revision_titulo)).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.verificacion_continuar)).performClick()
        composeTestRule.onNodeWithText(texto(R.string.verificacion_reto_vida)).assertIsDisplayed()
        capturarYUsar()

        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithTextCompat(texto(R.string.verificacion_ok_titulo))
        }
        composeTestRule.onNodeWithText(texto(R.string.verificacion_ok_titulo)).assertIsDisplayed()
    }

    @Test
    fun unaFotoBorrosaSeRechazaConSuIndicacion() {
        mostrar(viewModel(calidad = ProblemaImagen.BORROSA))

        composeTestRule.onNodeWithText("Capturar simulada").performClick()

        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithTextCompat(texto(R.string.verificacion_problema_borrosa))
        }
        composeTestRule.onNodeWithText(texto(R.string.verificacion_anverso_titulo)).assertIsDisplayed()
    }

    @Test
    fun unRechazoPermiteReintentarDesdeElPrincipio() {
        mostrar(viewModel("RECHAZADO"))
        capturarYUsar()
        capturarYUsar()
        composeTestRule.onNodeWithText(texto(R.string.verificacion_continuar)).performClick()
        capturarYUsar()

        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithTextCompat(texto(R.string.verificacion_rechazo_titulo))
        }
        composeTestRule.onNodeWithText(texto(R.string.verificacion_reintentar)).performClick()

        composeTestRule.onNodeWithText(texto(R.string.verificacion_anverso_titulo)).assertIsDisplayed()
    }

    @Test
    fun unCasoEnRevisionInformaQueSeLeAvisara() {
        mostrar(viewModel("EN_REVISION"))
        capturarYUsar()
        capturarYUsar()
        composeTestRule.onNodeWithText(texto(R.string.verificacion_continuar)).performClick()
        capturarYUsar()

        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithTextCompat(texto(R.string.verificacion_revision_caso_titulo))
        }
        composeTestRule.onNodeWithText(texto(R.string.verificacion_revision_caso_mensaje)).assertIsDisplayed()
    }
}
