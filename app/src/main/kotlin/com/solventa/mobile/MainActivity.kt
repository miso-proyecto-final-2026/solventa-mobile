package com.solventa.mobile

import android.os.Build
import android.os.Bundle
import android.Manifest
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.solventa.mobile.notificaciones.AvisosAndroid
import com.solventa.mobile.notificaciones.IdentificadorDispositivoPreferencias
import com.solventa.mobile.notificaciones.IdiomaDelTelefono
import com.solventa.mobile.notificaciones.NotificacionesRepository
import com.solventa.mobile.notificaciones.SondeadorNotificaciones
import kotlinx.coroutines.delay
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.solventa.mobile.compra.CompraRoute
import com.solventa.mobile.compra.CompraViewModel
import com.solventa.mobile.consentimiento.ConsentimientoScreen
import com.solventa.mobile.consentimiento.ConsentimientoViewModel
import com.solventa.mobile.cotizacion.CotizacionRoute
import com.solventa.mobile.cotizacion.CotizacionViewModel
import com.solventa.mobile.firma.FirmaRoute
import com.solventa.mobile.firma.FirmaViewModel
import com.solventa.mobile.red.CotizacionResponse
import com.solventa.mobile.privacidad.PrivacidadRoute
import com.solventa.mobile.privacidad.PrivacidadViewModel
import com.solventa.mobile.navegacion.BarraPrincipal
import com.solventa.mobile.navegacion.Paso
import com.solventa.mobile.navegacion.Seccion
import com.solventa.mobile.navegacion.atras
import com.solventa.mobile.navegacion.conBarra
import com.solventa.mobile.navegacion.reiniciaSeguros
import com.solventa.mobile.navegacion.seccion
import com.solventa.mobile.registro.RegistroScreen
import com.solventa.mobile.registro.RegistroViewModel
import com.solventa.mobile.resultado.Destino
import com.solventa.mobile.resultado.ResultadoRoute
import com.solventa.mobile.resultado.ResultadoViewModel
import com.solventa.mobile.sesion.AlmacenSesion
import com.solventa.mobile.sesion.SesionActual
import com.solventa.mobile.ui.tema.SolventaTheme
import com.solventa.mobile.verificacion.VerificacionRoute
import com.solventa.mobile.verificacion.VerificacionViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    // Póliza cuyo certificado debe abrirse porque el cliente tocó la notificación (HU22). Lo recoge la composición.
    private var suscripcionDeNotificacion by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        suscripcionDeNotificacion = intent.getStringExtra(EXTRA_SUSCRIPCION)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val almacen = AlmacenSesion.crear(this)
        suscripcionDeNotificacion = intent?.getStringExtra(EXTRA_SUSCRIPCION)
        val avisos = AvisosAndroid(this)
        val sondeador = SondeadorNotificaciones(NotificacionesRepository(), avisos, IdentificadorDispositivoPreferencias(this))
        setContent {
            SolventaTheme {
                Surface {
                    // Registro (HU01) → consentimiento (HU06) → verificación (HU02) → resultado (HU03) → secciones: seguros (HU37) y
                    // perfil con los consentimientos (HU07), unidas por la barra inferior.
                    var paso by rememberSaveable { mutableStateOf(Paso.ARRANQUE) }
                    var pasoTrasConsentimiento by rememberSaveable { mutableStateOf(Paso.VERIFICACION) }
                    // Cada visita a verificación o resultado empieza con un ViewModel nuevo.
                    var visita by rememberSaveable { mutableIntStateOf(0) }
                    // La cotización en curso sobrevive a cambiar de pestaña: solo se reinicia al llegar desde fuera de las secciones.
                    var visitaSeguros by rememberSaveable { mutableIntStateOf(0) }
                    // Oferta que el cliente va a comprar; no sobrevive a que el sistema mate el proceso (se vuelve a cotizar).
                    var ofertaAComprar by remember { mutableStateOf<CotizacionResponse?>(null) }
                    // Saga cuya póliza se va a firmar. Sobrevive a que el sistema mate el proceso: la firma queda pendiente 24 h.
                    var suscripcionAFirmar by rememberSaveable { mutableStateOf<String?>(null) }
                    var sesionRestaurada by remember { mutableStateOf(SesionActual.activa) }
                    val alcance = rememberCoroutineScope()

                    fun navegar(destino: Paso) {
                        visita++
                        if (reiniciaSeguros(desde = paso, hacia = destino)) visitaSeguros++
                        paso = destino
                    }

                    // Al reabrir la app (o tras morir el proceso) la sesión vuelve del almacén cifrado.
                    LaunchedEffect(Unit) {
                        if (!SesionActual.activa) {
                            almacen.leer()?.let { SesionActual.iniciar(it.clienteId, it.accessToken, it.nombre) }
                        }
                        sesionRestaurada = true
                        if (paso == Paso.ARRANQUE) navegar(if (SesionActual.activa) Paso.RESULTADO else Paso.REGISTRO)
                    }

                    if (!sesionRestaurada || paso == Paso.ARRANQUE) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        return@Surface
                    }

                    // Notificaciones (HU22): mientras el cliente está dentro de la app y esta se ve, el «push» simulado de la
                    // bandeja del servidor se muestra como notificación del sistema.
                    val dentroDeLaApp = paso.seccion != null
                    LaunchedEffect(dentroDeLaApp) {
                        val cliente = SesionActual.clienteId
                        if (dentroDeLaApp && cliente != null) {
                            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                                sondeador.registrar(cliente, IdiomaDelTelefono.actual())
                                while (true) {
                                    sondeador.sondear()
                                    delay(INTERVALO_SONDEO_MS)
                                }
                            }
                        }
                    }
                    // Android 13 o superior: el permiso se pide al llegar a la firma, que es cuando tiene sentido avisar.
                    var permisoPedido by rememberSaveable { mutableStateOf(false) }
                    val pedirPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
                    LaunchedEffect(paso) {
                        if (paso == Paso.FIRMA && !permisoPedido && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !avisos.puedeMostrar()) {
                            permisoPedido = true
                            pedirPermiso.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    // Tocar la notificación abre el certificado de esa póliza.
                    LaunchedEffect(suscripcionDeNotificacion, paso) {
                        val id = suscripcionDeNotificacion ?: return@LaunchedEffect
                        suscripcionDeNotificacion = null
                        if (SesionActual.activa) {
                            suscripcionAFirmar = id
                            navegar(Paso.FIRMA)
                        }
                    }

                    // Atrás dentro de una sección vuelve a su raíz; desde seguros sale de la app.
                    BackHandler(enabled = paso.atras != null) { paso.atras?.let(::navegar) }

                    Scaffold(
                        bottomBar = {
                            paso.seccion?.let { seccion ->
                                BarraPrincipal(seleccion = seccion) { elegida ->
                                    val destino = if (elegida == Seccion.SEGUROS) Paso.INICIO else Paso.PERFIL
                                    if (destino != paso) navegar(destino)
                                }
                            }
                        },
                    ) { relleno ->
                        Box(Modifier.padding(relleno)) {
                            when (paso) {
                                Paso.ARRANQUE -> Unit

                                Paso.REGISTRO -> {
                                    val viewModel: RegistroViewModel = viewModel(key = "registro-$visita")
                                    RegistroScreen(
                                        viewModel = viewModel,
                                        onRegistroExitoso = {
                                            SesionActual.credenciales()?.let { alcance.launch { almacen.guardar(it) } }
                                            pasoTrasConsentimiento = Paso.VERIFICACION
                                            navegar(Paso.CONSENTIMIENTO)
                                        },
                                    )
                                }

                                Paso.CONSENTIMIENTO -> {
                                    // Cada visita empieza con un ViewModel nuevo: si no, `continuar` seguiría en true y se saltaría el consentimiento.
                                    val viewModel: ConsentimientoViewModel = viewModel(key = "consentimiento-$visita")
                                    ConsentimientoScreen(
                                        viewModel = viewModel,
                                        onContinuar = { navegar(pasoTrasConsentimiento) },
                                    )
                                }

                                Paso.VERIFICACION -> {
                                    val viewModel: VerificacionViewModel = viewModel(key = "verificacion-$visita")
                                    VerificacionRoute(
                                        viewModel = viewModel,
                                        onFinalizar = { navegar(Paso.RESULTADO) },
                                    )
                                }

                                Paso.RESULTADO -> {
                                    val viewModel: ResultadoViewModel = viewModel(key = "resultado-$visita") {
                                        ResultadoViewModel(alPerderSesion = {
                                            SesionActual.cerrar()
                                            almacen.borrar()
                                        })
                                    }
                                    ResultadoRoute(
                                        viewModel = viewModel,
                                        onDestino = { destino ->
                                            when (destino) {
                                                Destino.REGISTRO -> navegar(Paso.REGISTRO)
                                                Destino.CONSENTIMIENTO_ANTES_DE_VERIFICAR -> {
                                                    pasoTrasConsentimiento = Paso.VERIFICACION
                                                    navegar(Paso.CONSENTIMIENTO)
                                                }
                                                Destino.CONSENTIMIENTO_TRAS_APROBAR -> {
                                                    pasoTrasConsentimiento = Paso.INICIO
                                                    navegar(Paso.CONSENTIMIENTO)
                                                }
                                                Destino.VERIFICACION -> navegar(Paso.VERIFICACION)
                                                Destino.INICIO -> navegar(Paso.INICIO)
                                            }
                                        },
                                        // En revisión o sin intentos: el usuario sale y al volver ve el estado vigente.
                                        onSalir = { finish() },
                                    )
                                }

                                Paso.INICIO -> {
                                    val viewModel: CotizacionViewModel = viewModel(key = "cotizacion-$visitaSeguros")
                                    CotizacionRoute(
                                        viewModel = viewModel,
                                        // Compartir los datos lleva al consentimiento y vuelve a cotizar con el perfil del cliente.
                                        onCompartirDatos = {
                                            pasoTrasConsentimiento = Paso.INICIO
                                            navegar(Paso.CONSENTIMIENTO)
                                        },
                                        onSesionVencida = {
                                            SesionActual.cerrar()
                                            alcance.launch { almacen.borrar() }
                                            navegar(Paso.REGISTRO)
                                        },
                                        onAceptarOferta = {
                                            ofertaAComprar = it
                                            navegar(Paso.COMPRA)
                                        },
                                    )
                                }

                                Paso.COMPRA -> {
                                    val oferta = ofertaAComprar
                                    if (oferta == null) {
                                        LaunchedEffect(Unit) { navegar(Paso.INICIO) }
                                    } else {
                                        val viewModel: CompraViewModel = viewModel(key = "compra-${oferta.id}")
                                        LaunchedEffect(oferta.id) { viewModel.iniciar(oferta) }
                                        CompraRoute(
                                            viewModel = viewModel,
                                            // Oferta vencida o rechazada: cotiza de nuevo; terminar también deja un formulario limpio.
                                            onCotizarDeNuevo = { visitaSeguros++; navegar(Paso.INICIO) },
                                            onTerminar = { visitaSeguros++; navegar(Paso.INICIO) },
                                            // Atrás deja el resultado de la cotización tal como estaba.
                                            onVolver = { navegar(Paso.INICIO) },
                                            // Cobro confirmado: lo que falta es firmar la póliza (HU21).
                                            onFirmar = { id ->
                                                suscripcionAFirmar = id
                                                navegar(Paso.FIRMA)
                                            },
                                            onSesionVencida = {
                                                SesionActual.cerrar()
                                                alcance.launch { almacen.borrar() }
                                                navegar(Paso.REGISTRO)
                                            },
                                        )
                                    }
                                }

                                Paso.FIRMA -> {
                                    val id = suscripcionAFirmar
                                    if (id == null) {
                                        LaunchedEffect(Unit) { navegar(Paso.INICIO) }
                                    } else {
                                        val viewModel: FirmaViewModel = viewModel(key = "firma-$id") {
                                            FirmaViewModel(dispositivo = {
                                                "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}"
                                            })
                                        }
                                        LaunchedEffect(id) { viewModel.iniciar(id) }
                                        FirmaRoute(
                                            viewModel = viewModel,
                                            // Firmada o devuelta: se vuelve a un formulario de cotización limpio.
                                            onTerminar = { visitaSeguros++; navegar(Paso.INICIO) },
                                            onCotizarDeNuevo = { visitaSeguros++; navegar(Paso.INICIO) },
                                            onVolver = { navegar(Paso.INICIO) },
                                            onSesionVencida = {
                                                SesionActual.cerrar()
                                                alcance.launch { almacen.borrar() }
                                                navegar(Paso.REGISTRO)
                                            },
                                        )
                                    }
                                }

                                Paso.PERFIL -> {
                                    val viewModel: PrivacidadViewModel = viewModel(key = "privacidad-$visita")
                                    PrivacidadRoute(
                                        viewModel = viewModel,
                                        // Volver a compartir reutiliza el consentimiento de HU06 y regresa aquí con el estado al día.
                                        onCompartirDatos = {
                                            pasoTrasConsentimiento = Paso.PERFIL
                                            navegar(Paso.CONSENTIMIENTO)
                                        },
                                        onSesionVencida = {
                                            SesionActual.cerrar()
                                            alcance.launch { almacen.borrar() }
                                            navegar(Paso.REGISTRO)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        /** Identificador de la suscripción cuyo certificado abre la notificación (HU22). */
        const val EXTRA_SUSCRIPCION = "suscripcion_id"

        /** Cada cuánto se consulta la bandeja mientras la app está visible. */
        private const val INTERVALO_SONDEO_MS = 10_000L
    }
}
