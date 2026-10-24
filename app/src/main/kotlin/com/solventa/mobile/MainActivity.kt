package com.solventa.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.solventa.mobile.configuracion.ConfiguracionScreen
import com.solventa.mobile.consentimiento.ConsentimientoScreen
import com.solventa.mobile.consentimiento.ConsentimientoViewModel
import com.solventa.mobile.cotizacion.CotizacionRoute
import com.solventa.mobile.cotizacion.CotizacionViewModel
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
import com.solventa.mobile.verificacion.VerificacionRoute
import com.solventa.mobile.verificacion.VerificacionViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val almacen = AlmacenSesion.crear(this)
        setContent {
            MaterialTheme {
                Surface {
                    // Registro (HU01) → consentimiento (HU06) → verificación (HU02) → resultado (HU03) → secciones: seguros (HU37) y
                    // configuración con privacidad (HU07), unidas por la barra inferior.
                    var paso by rememberSaveable { mutableStateOf(Paso.ARRANQUE) }
                    var pasoTrasConsentimiento by rememberSaveable { mutableStateOf(Paso.VERIFICACION) }
                    // Cada visita a verificación o resultado empieza con un ViewModel nuevo.
                    var visita by rememberSaveable { mutableIntStateOf(0) }
                    // La cotización en curso sobrevive a cambiar de pestaña: solo se reinicia al llegar desde fuera de las secciones.
                    var visitaSeguros by rememberSaveable { mutableIntStateOf(0) }
                    // Oferta que el cliente va a comprar; no sobrevive a que el sistema mate el proceso (se vuelve a cotizar).
                    var ofertaAComprar by remember { mutableStateOf<CotizacionResponse?>(null) }
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
                            almacen.leer()?.let { SesionActual.iniciar(it.clienteId, it.accessToken) }
                        }
                        sesionRestaurada = true
                        if (paso == Paso.ARRANQUE) navegar(if (SesionActual.activa) Paso.RESULTADO else Paso.REGISTRO)
                    }

                    if (!sesionRestaurada || paso == Paso.ARRANQUE) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        return@Surface
                    }

                    // Atrás dentro de una sección vuelve a su raíz; desde seguros sale de la app.
                    BackHandler(enabled = paso.atras != null) { paso.atras?.let(::navegar) }

                    Scaffold(
                        bottomBar = {
                            paso.seccion?.let { seccion ->
                                BarraPrincipal(seleccion = seccion) { elegida ->
                                    val destino = if (elegida == Seccion.SEGUROS) Paso.INICIO else Paso.CONFIGURACION
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
                                            onSesionVencida = {
                                                SesionActual.cerrar()
                                                alcance.launch { almacen.borrar() }
                                                navegar(Paso.REGISTRO)
                                            },
                                        )
                                    }
                                }

                                Paso.CONFIGURACION -> ConfiguracionScreen(alAbrirPrivacidad = { navegar(Paso.PRIVACIDAD) })

                                Paso.PRIVACIDAD -> {
                                    val viewModel: PrivacidadViewModel = viewModel(key = "privacidad-$visita")
                                    PrivacidadRoute(
                                        viewModel = viewModel,
                                        // Volver a compartir reutiliza el consentimiento de HU06 y regresa aquí con el estado al día.
                                        onCompartirDatos = {
                                            pasoTrasConsentimiento = Paso.PRIVACIDAD
                                            navegar(Paso.CONSENTIMIENTO)
                                        },
                                        onVolver = { navegar(Paso.CONFIGURACION) },
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
}
