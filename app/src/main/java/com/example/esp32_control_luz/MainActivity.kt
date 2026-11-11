package com.example.esp32_control_luz

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

import com.amplifyframework.auth.cognito.AWSCognitoAuthPlugin
import com.amplifyframework.core.Amplify
import com.amplifyframework.core.configuration.AmplifyOutputs

class MainActivity : AppCompatActivity() {

    private lateinit var btnIngresar: Button
    private lateinit var txtMensaje: TextView
    private lateinit var etUsuario: EditText
    private lateinit var etContrasena: EditText

    private var cognitoListo = false

    companion object {
        private var amplifyInicializado = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        // Elementos de la pantalla
        etUsuario = findViewById(R.id.etUsuario)
        etContrasena = findViewById(R.id.etContrasena)
        btnIngresar = findViewById(R.id.btnIngresar)
        txtMensaje = findViewById(R.id.txtMensaje)

        // Conectar con AWS Cognito
        configurarCognito()

        // Botón para iniciar sesión
        btnIngresar.setOnClickListener {

            val nombre = etUsuario.text.toString().trim()
            val clave = etContrasena.text.toString()

            when {
                nombre.isEmpty() -> {
                    etUsuario.error = "Ingrese su usuario"
                    etUsuario.requestFocus()
                }

                clave.isEmpty() -> {
                    etContrasena.error = "Ingrese su contraseña"
                    etContrasena.requestFocus()
                }

                !cognitoListo -> {
                    mostrarError(
                        "AWS Cognito no está configurado"
                    )
                }

                else -> iniciarSesion(nombre, clave)
            }
        }
    }

    // ===================================
    // CONFIGURAR AMAZON COGNITO
    // ===================================

    private fun configurarCognito() {

        btnIngresar.isEnabled = false
        txtMensaje.text = "Conectando con AWS..."

        try {
            if (!amplifyInicializado) {
                Amplify.addPlugin(AWSCognitoAuthPlugin())

                Amplify.configure(
                    AmplifyOutputs(R.raw.amplify_outputs),
                    applicationContext
                )

                amplifyInicializado = true
            }

            cognitoListo = true

            Log.i("AWS", "Cognito configurado")

            comprobarSesion()

        } catch (error: Exception) {

            Log.e("AWS", "Error de configuración", error)

            btnIngresar.isEnabled = true

            mostrarError(
                "Error al conectar con Amazon Cognito"
            )
        }
    }

    // ===================================
    // COMPROBAR SI YA EXISTE UNA SESIÓN
    // ===================================

    private fun comprobarSesion() {

        Amplify.Auth.fetchAuthSession(
            { sesion ->

                runOnUiThread {
                    if (sesion.isSignedIn) {

                        Log.i("AWS", "Sesión activa")

                        abrirPantallaControl()

                    } else {

                        btnIngresar.isEnabled = true

                        txtMensaje.text =
                            "Ingrese sus credenciales"
                    }
                }
            },
            { error ->

                Log.e(
                    "AWS",
                    "Error al comprobar sesión",
                    error
                )

                runOnUiThread {

                    btnIngresar.isEnabled = true

                    mostrarError(
                        "No se pudo comprobar la sesión"
                    )
                }
            }
        )
    }

    // ===================================
    // INICIAR SESIÓN CON COGNITO
    // ===================================

    private fun iniciarSesion(
        nombre: String,
        clave: String
    ) {

        btnIngresar.isEnabled = false
        txtMensaje.text = "Verificando con AWS..."

        Amplify.Auth.signIn(
            nombre,
            clave,
            { resultado ->

                runOnUiThread {

                    when {

                        resultado.isSignedIn -> {

                            Log.i(
                                "AWS",
                                "Inicio de sesión correcto"
                            )

                            txtMensaje.setTextColor(
                                Color.GREEN
                            )

                            txtMensaje.text =
                                "¡Inicio de sesión correcto!"

                            abrirPantallaControl()
                        }

                        resultado.nextStep.signInStep.name ==
                                "CONFIRM_SIGN_IN_WITH_NEW_PASSWORD" -> {

                            txtMensaje.text =
                                "Debe cambiar su contraseña"

                            solicitarNuevaContrasena()
                        }

                        else -> {

                            btnIngresar.isEnabled = true

                            txtMensaje.text =
                                "AWS solicita otro paso: " +
                                        resultado.nextStep.signInStep.name
                        }
                    }
                }
            },
            { error ->

                Log.e(
                    "AWS",
                    "Error al iniciar sesión",
                    error
                )

                runOnUiThread {

                    btnIngresar.isEnabled = true

                    mostrarError(
                        "No se pudo iniciar sesión. " +
                                "Revise sus credenciales."
                    )
                }
            }
        )
    }

    // ===================================
    // CAMBIAR CONTRASEÑA TEMPORAL
    // ===================================

    private fun solicitarNuevaContrasena() {

        val campo = EditText(this).apply {

            hint = "Nueva contraseña"

            inputType =
                InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        AlertDialog.Builder(this)
            .setTitle("Cambiar contraseña")
            .setMessage(
                "Amazon Cognito solicita una " +
                        "contraseña definitiva."
            )
            .setView(campo)
            .setNegativeButton("Cancelar") { _, _ ->

                btnIngresar.isEnabled = true
            }
            .setPositiveButton("Guardar") { _, _ ->

                val nuevaClave = campo.text.toString()

                if (nuevaClave.isBlank()) {

                    btnIngresar.isEnabled = true

                    mostrarError(
                        "Ingrese una contraseña válida"
                    )

                } else {

                    txtMensaje.text =
                        "Actualizando contraseña..."

                    Amplify.Auth.confirmSignIn(
                        nuevaClave,
                        { resultado ->

                            runOnUiThread {

                                if (resultado.isSignedIn) {

                                    abrirPantallaControl()

                                } else {

                                    btnIngresar.isEnabled = true

                                    txtMensaje.text =
                                        "Falta completar: " +
                                                resultado.nextStep.signInStep.name
                                }
                            }
                        },
                        { error ->

                            Log.e(
                                "AWS",
                                "Error al cambiar contraseña",
                                error
                            )

                            runOnUiThread {

                                btnIngresar.isEnabled = true

                                mostrarError(
                                    "No se pudo cambiar " +
                                            "la contraseña"
                                )
                            }
                        }
                    )
                }
            }
            .show()
    }

    // ===================================
    // MOSTRAR ERRORES
    // ===================================

    private fun mostrarError(mensaje: String) {

        txtMensaje.setTextColor(Color.RED)
        txtMensaje.text = mensaje
    }

    // ===================================
    // ABRIR PANTALLA DE CONTROL
    // ===================================

    private fun abrirPantallaControl() {

        val intent = Intent(
            this@MainActivity,
            ControlActivity::class.java
        )

        startActivity(intent)

        // Cerrar la pantalla de inicio de sesión
        finish()
    }
}
