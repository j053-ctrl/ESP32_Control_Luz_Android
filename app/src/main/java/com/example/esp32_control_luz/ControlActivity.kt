
package com.example.esp32_control_luz

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.amplifyframework.auth.cognito.AWSCognitoAuthSession
import com.amplifyframework.auth.result.AuthSessionResult
import com.amplifyframework.core.Amplify
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ControlActivity : AppCompatActivity() {

    // =====================================
    // 1. CONEXION CON AWS API GATEWAY
    // =====================================

    private val apiUrl =
        "https://t176n8haoj.execute-api.us-east-1.amazonaws.com/luz"

    // =====================================
    // 2. BOTONES Y TEXTOS
    // =====================================

    private lateinit var btnEncender: Button
    private lateinit var btnApagar: Button
    private lateinit var btnHistorial: Button
    private lateinit var btnSalir: Button

    private lateinit var txtEstado: TextView
    private lateinit var txtHistorial: TextView

    // =====================================
    // 3. COLORES DE LA LUZ
    // =====================================

    private val colorVerde = Color.rgb(22, 163, 74)
    private val colorRojo = Color.rgb(220, 38, 38)

    // =====================================
    // 4. ACTUALIZACION AUTOMATICA
    // =====================================

    private val handler = Handler(Looper.getMainLooper())

    // Consultar el estado cada 2 segundos
    private val intervaloConsulta = 2000L

    private var monitoreoActivo = false
    private var consultandoEstado = false

    private val tareaEstado = object : Runnable {

        override fun run() {

            if (!monitoreoActivo) return

            consultarEstado()

            handler.postDelayed(
                this,
                intervaloConsulta
            )
        }
    }

    // =====================================
    // 5. INICIAR PANTALLA
    // =====================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_control)

        // Conectar botones
        btnEncender = findViewById(R.id.btnEncender)
        btnApagar = findViewById(R.id.btnApagar)
        btnHistorial = findViewById(R.id.btnHistorial)
        btnSalir = findViewById(R.id.btnSalir)

        // Conectar textos
        txtEstado = findViewById(R.id.txtEstado)
        txtHistorial = findViewById(R.id.txtHistorial)

        // No mostrar ESTADO DESCONOCIDO
        txtEstado.text = ""

        // ENCENDER LUZ
        btnEncender.setOnClickListener {
            enviarComando("ENCENDER")
        }

        // APAGAR LUZ
        btnApagar.setOnClickListener {
            enviarComando("APAGAR")
        }

        // VER HISTORIAL
        btnHistorial.setOnClickListener {
            enviarComando("HISTORIAL")
        }

        // CERRAR SESION
        btnSalir.setOnClickListener {
            cerrarSesion()
        }
    }

    // =====================================
    // 6. ACTIVAR MONITOREO
    // =====================================

    override fun onResume() {
        super.onResume()

        monitoreoActivo = true

        handler.removeCallbacks(tareaEstado)
        handler.post(tareaEstado)
    }

    // =====================================
    // 7. DETENER MONITOREO
    // =====================================

    override fun onPause() {

        monitoreoActivo = false

        handler.removeCallbacks(tareaEstado)

        super.onPause()
    }

    // =====================================
    // 8. OBTENER TOKEN DE COGNITO
    // =====================================

    private fun obtenerToken(
        resultado: (String?) -> Unit
    ) {

        Amplify.Auth.fetchAuthSession(
            { sesion ->

                val cognito =
                    sesion as? AWSCognitoAuthSession

                val tokens =
                    cognito?.userPoolTokensResult

                val token = if (
                    cognito?.isSignedIn == true &&
                    tokens?.type == AuthSessionResult.Type.SUCCESS
                ) {

                    tokens.value?.accessToken

                } else {

                    null
                }

                resultado(token)
            },
            {
                resultado(null)
            }
        )
    }

    // =====================================
    // 9. RESPUESTA DE LA API
    // =====================================

    private data class RespuestaApi(
        val codigo: Int,
        val contenido: String
    )

    // =====================================
    // 10. REALIZAR PETICION HTTP
    // =====================================

    private fun solicitarApi(
        accion: String,
        token: String
    ): RespuestaApi {

        val conexion =
            URL(apiUrl).openConnection() as HttpURLConnection

        try {

            conexion.requestMethod = "POST"

            conexion.connectTimeout = 15000
            conexion.readTimeout = 15000

            conexion.doOutput = true

            // Autenticacion Cognito
            conexion.setRequestProperty(
                "Authorization",
                "Bearer $token"
            )

            conexion.setRequestProperty(
                "Content-Type",
                "application/json"
            )

            conexion.setRequestProperty(
                "Accept",
                "application/json"
            )

            // Crear mensaje JSON
            val datos = JSONObject()

            datos.put("accion", accion)

            // Enviar datos a Lambda
            conexion.outputStream.use { salida ->

                salida.write(
                    datos.toString().toByteArray(
                        Charsets.UTF_8
                    )
                )
            }

            // Recibir respuesta
            val codigo = conexion.responseCode

            val flujo = if (codigo in 200..299) {

                conexion.inputStream

            } else {

                conexion.errorStream
            }

            val texto =
                flujo?.bufferedReader(Charsets.UTF_8)?.use {
                    it.readText()
                } ?: ""

            return RespuestaApi(
                codigo,
                texto
            )

        } finally {

            conexion.disconnect()
        }
    }

    // =====================================
    // 11. LEER RESPUESTA JSON
    // =====================================

    private fun leerDatos(
        respuesta: String
    ): JSONObject {

        val original = JSONObject(respuesta)

        val body = original.opt("body")

        return when (body) {

            is JSONObject -> body

            is String -> {

                if (body.isNotBlank()) {

                    JSONObject(body)

                } else {

                    original
                }
            }

            else -> original
        }
    }

    // =====================================
    // 12. CONSULTAR ESTADO AUTOMATICAMENTE
    // =====================================

    private fun consultarEstado() {

        // Evitar consultas simultaneas
        if (consultandoEstado) return

        consultandoEstado = true

        obtenerToken { token ->

            if (token.isNullOrBlank()) {

                runOnUiThread {
                    consultandoEstado = false
                }

                return@obtenerToken
            }

            Thread {

                try {

                    val respuesta = solicitarApi(
                        "ESTADO",
                        token
                    )

                    if (respuesta.codigo in 200..299) {

                        val original =
                            JSONObject(respuesta.contenido)

                        val codigoLambda =
                            original.optInt(
                                "statusCode",
                                respuesta.codigo
                            )

                        if (codigoLambda in 200..299) {

                            val datos = leerDatos(
                                respuesta.contenido
                            )

                            val estado =
                                datos.optString(
                                    "estado",
                                    ""
                                )

                            runOnUiThread {

                                if (monitoreoActivo) {

                                    mostrarEstado(estado)
                                }

                                consultandoEstado = false
                            }

                        } else {

                            runOnUiThread {
                                consultandoEstado = false
                            }
                        }

                    } else {

                        runOnUiThread {
                            consultandoEstado = false
                        }
                    }

                } catch (error: Exception) {

                    runOnUiThread {
                        consultandoEstado = false
                    }
                }

            }.start()
        }
    }

    // =====================================
    // 13. MOSTRAR ESTADO EN ANDROID
    // =====================================

    private fun mostrarEstado(estado: String) {

        when (estado.uppercase(Locale.ROOT)) {

            "ENCENDIDA", "ENCENDER" -> {

                txtEstado.text = "LUZ ENCENDIDA"

                txtEstado.setTextColor(colorVerde)
            }

            "APAGADA", "APAGAR" -> {

                txtEstado.text = "LUZ APAGADA"

                txtEstado.setTextColor(colorRojo)
            }
        }
    }

    // =====================================
    // 14. BLOQUEAR BOTONES
    // =====================================

    private fun bloquearBotones(
        bloquear: Boolean
    ) {

        runOnUiThread {

            btnEncender.isEnabled = !bloquear

            btnApagar.isEnabled = !bloquear

            btnHistorial.isEnabled = !bloquear
        }
    }

    // =====================================
    // 15. ENVIAR COMANDOS
    // =====================================

    private fun enviarComando(accion: String) {

        bloquearBotones(true)

        if (accion == "HISTORIAL") {

            txtHistorial.text =
                "Consultando historial..."
        }

        obtenerToken { token ->

            if (token.isNullOrBlank()) {

                mostrarError(
                    "Debes iniciar sesion nuevamente."
                )

                return@obtenerToken
            }

            Thread {

                try {

                    val respuesta = solicitarApi(
                        accion,
                        token
                    )

                    val original =
                        JSONObject(respuesta.contenido)

                    val codigoLambda =
                        original.optInt(
                            "statusCode",
                            respuesta.codigo
                        )

                    runOnUiThread {

                        bloquearBotones(false)

                        if (
                            respuesta.codigo in 200..299 &&
                            codigoLambda in 200..299
                        ) {

                            when (accion) {

                                "ENCENDER" -> {

                                    mostrarEstado("ENCENDIDA")

                                    mostrarMensaje(
                                        "Comando ENCENDER enviado"
                                    )
                                }

                                "APAGAR" -> {

                                    mostrarEstado("APAGADA")

                                    mostrarMensaje(
                                        "Comando APAGAR enviado"
                                    )
                                }

                                "HISTORIAL" -> {

                                    mostrarHistorial(
                                        respuesta.contenido
                                    )
                                }
                            }

                        } else {

                            mostrarMensaje(
                                "Error de AWS: ${respuesta.codigo}"
                            )
                        }
                    }

                } catch (error: Exception) {

                    mostrarError(
                        "Error de conexion: ${error.message}"
                    )
                }

            }.start()
        }
    }

    // =====================================
    // 16. MOSTRAR HISTORIAL DE DYNAMODB
    // =====================================

    private fun mostrarHistorial(
        respuesta: String
    ) {

        try {

            // Obtener registros de Lambda
            val datos = leerDatos(respuesta)

            val registros =
                datos.optJSONArray("historial")

            if (registros == null) {

                txtHistorial.text =
                    "No se pudo leer el historial"

                mostrarMensaje(
                    "AWS no devolvio el historial"
                )

                return
            }

            // HISTORIAL VACIO
            if (registros.length() == 0) {

                txtHistorial.text =
                    "No hay eventos registrados"

                AlertDialog.Builder(this)
                    .setTitle("HISTORIAL DE LA LUZ")
                    .setMessage(
                        "No hay registros disponibles."
                    )
                    .setPositiveButton("CERRAR", null)
                    .show()

                return
            }

            // Crear historial con colores
            val historial =
                SpannableStringBuilder()

            // Formato de fecha de Chile
            val formatoFecha = SimpleDateFormat(
                "dd/MM/yyyy HH:mm:ss",
                Locale.forLanguageTag("es-CL")
            )

            formatoFecha.timeZone =
                TimeZone.getTimeZone("America/Santiago")

            // Recorrer todos los registros
            for (i in 0 until registros.length()) {

                val item =
                    registros.getJSONObject(i)

                val evento =
                    item.optString("evento", "")

                val modo =
                    item.optString("modo", "")

                val origen =
                    item.optString("origen", "")

                val timestamp =
                    item.optLong("timestamp", 0L)

                // Convertir fecha
                val fecha = if (timestamp > 0) {

                    formatoFecha.format(
                        Date(timestamp)
                    )

                } else {

                    "Fecha desconocida"
                }

                // Identificar estado
                val eventoMayuscula =
                    evento.uppercase(Locale.ROOT)

                val encendida =
                    eventoMayuscula == "ENCENDIDA" ||
                            eventoMayuscula == "ENCENDER"

                val apagada =
                    eventoMayuscula == "APAGADA" ||
                            eventoMayuscula == "APAGAR"

                // =================================
                // CIRCULO VERDE O ROJO
                // =================================

                val inicio = historial.length

                // Circulo compatible con Android
                historial.append("●")

                val color = when {

                    encendida -> colorVerde

                    apagada -> colorRojo

                    else -> Color.GRAY
                }

                // Aplicar color al circulo
                historial.setSpan(
                    ForegroundColorSpan(color),
                    inicio,
                    inicio + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                // =================================
                // TEXTO DEL ESTADO
                // =================================

                val textoEstado = when {

                    encendida -> "LUZ ENCENDIDA"

                    apagada -> "LUZ APAGADA"

                    else -> evento
                }

                // =================================
                // AGREGAR DATOS DEL REGISTRO
                // =================================

                historial.append(" $textoEstado\n")

                historial.append(
                    "Fecha: $fecha\n"
                )

                historial.append(
                    "Modo: $modo\n"
                )

                historial.append(
                    "Origen: $origen\n"
                )

                historial.append(
                    "\n--------------------------\n\n"
                )
            }

            // Mostrar cantidad de registros
            txtHistorial.text =
                "${registros.length()} registros encontrados"

            // =================================
            // VENTANA DEL HISTORIAL
            // =================================

            AlertDialog.Builder(this)
                .setTitle("HISTORIAL DE LA LUZ")
                .setMessage(historial)
                .setPositiveButton(
                    "CERRAR",
                    null
                )
                .show()

        } catch (error: Exception) {

            txtHistorial.text =
                "Error al cargar historial"

            mostrarMensaje(
                "Error de historial: ${error.message}"
            )
        }
    }

    // =====================================
    // 17. MOSTRAR ERRORES
    // =====================================

    private fun mostrarError(
        mensaje: String
    ) {

        runOnUiThread {

            bloquearBotones(false)

            mostrarMensaje(mensaje)
        }
    }

    // =====================================
    // 18. MOSTRAR MENSAJES
    // =====================================

    private fun mostrarMensaje(
        mensaje: String
    ) {

        Toast.makeText(
            this,
            mensaje,
            Toast.LENGTH_LONG
        ).show()
    }

    // =====================================
    // 19. CERRAR SESION COGNITO
    // =====================================

    private fun cerrarSesion() {

        // Detener consultas automaticas
        monitoreoActivo = false

        handler.removeCallbacks(tareaEstado)

        Amplify.Auth.signOut {

            Amplify.Auth.fetchAuthSession(
                { sesion ->

                    runOnUiThread {

                        if (!sesion.isSignedIn) {

                            val intent = Intent(
                                this,
                                MainActivity::class.java
                            )

                            intent.flags =
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_CLEAR_TASK

                            startActivity(intent)

                            finish()

                        } else {

                            mostrarMensaje(
                                "No se pudo cerrar sesion"
                            )

                            monitoreoActivo = true

                            handler.post(tareaEstado)
                        }
                    }
                },
                { error ->

                    runOnUiThread {

                        mostrarMensaje(
                            "Error al cerrar sesion: ${error.message}"
                        )

                        monitoreoActivo = true

                        handler.post(tareaEstado)
                    }
                }
            )
        }
    }
}
