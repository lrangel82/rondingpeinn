package com.larangel.rondy

import CrashHandler
import MySettings
import androidx.appcompat.app.AppCompatActivity
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.larangel.rondy.databinding.ActivitySplashBinding
import coil.load
import com.larangel.rondy.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
class SplashActivity : AppCompatActivity() {
    private var mySettings: MySettings? = null


    //RECORDAR ANIMACIONES LOTTIE JSON
    // PARA LA GUIA DE AYUDA

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))

        setContentView(R.layout.activity_splash)

        val imgLogo: ImageView = findViewById(R.id.imgLogoSplash)

        // 1. Obtener la URL de tu MySettings
        mySettings = MySettings(applicationContext)


        val urlImagenPersonalizada = mySettings?.getString("IMAGEN_LOGO_PNG", "")

        // 2. Cargar con Coil: Si la URL falla o está vacía, usa el logo de Rondy
        imgLogo.load(urlImagenPersonalizada) {
            crossfade(true)
            placeholder(R.drawable.logo) // Tu imagen actual
            error(R.drawable.logo)       // Si falla el internet
        }


        val codigoActiviacion = mySettings?.getString("CODIGO_ACTIVACION", "")!!
        val num_tags = mySettings?.getInt("rondin_num_tags", 0)!!
        if (codigoActiviacion.isEmpty()){
            val intent: Intent = Intent(this, SettingsActivity::class.java )
            startActivity(intent)
        }
        else if(num_tags <= 0){
            val intent: Intent = Intent(this, ProgramarTags::class.java )
            startActivity(intent)
        }

        // Si hay un error guardado, lo enviamos
        val ultimoCrash = mySettings?.getString("ultimoCrash","")
        if (!ultimoCrash.isNullOrEmpty()) {
            enviarAWhatsApp(ultimoCrash)

            // IMPORTANTE: Limpiar el registro para que no lo vuelva a enviar
            mySettings!!.saveString("ultimoCrash", "")
        }else {
            validaLicencia()
        }

//        // 3. Esperar 500 miliseconds y brincar al MainActivity
//        Handler(Looper.getMainLooper()).postDelayed({
//            startActivity(Intent(this, MainActivity::class.java))
//            finish() // Cerramos el Splash para que no puedan volver atrás
//        }, 500)
    }


    private fun enviarAWhatsApp(mensaje: String) {
        AlertDialog.Builder(this)
            .setTitle("Ocurrió un error anteriormente")
            .setMessage("La aplicación se cerró de forma inesperada. ¿Deseas enviar el reporte de error por WhatsApp al programador?")
            .setPositiveButton("Enviar") { _, _ ->
                // Si el usuario acepta, se ejecuta tu código original de WhatsApp
                try {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Reporte de error:\n\n$mensaje")
                        setPackage("com.whatsapp")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "WhatsApp no está instalado", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null) // Si cancela, simplemente se cierra el mensaje
            .setCancelable(false) // Evita que se cierre si tocan fuera del cuadro
            .setOnDismissListener {
                // Esta función se ejecuta SIEMPRE que el diálogo desaparece de la pantalla
                abrirMainActivity()
            }
            .show()
    }

    // Utilidad simple para detectar red
    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = cm.activeNetworkInfo
        return network?.isConnected == true
    }


    private fun abrirMainActivity(){
        startActivity(Intent(this, MainActivity::class.java))
        this.finish() // Cerramos el Splash para que no puedan volver atrás
    }

    private fun validaLicencia(){
        if (isNetworkAvailable()){
            //DESCARGAR CONFIGURACION Y VALIDAR
            Toast.makeText(this@SplashActivity,"VALIDANDO LICENCIA....",Toast.LENGTH_SHORT).show()
            val bucketName = mySettings?.getString("BUCKET_NAME", "").toString()
            val regionStr  = mySettings?.getString("REGION_STR", "").toString()
            val codigoActiv= mySettings?.getString("CODIGO_ACTIVACION", "").toString()
            lifecycleScope.launch(Dispatchers.Main) {
                try {
                    //Buscar y descargar nueva configuracion
                    mySettings?.fetchAndProcessS3Config(bucketName, regionStr, codigoActiv)
                    //Inizializa el ENUM con los valores correctos del nombre de sheets
                    SheetTable.initializeAll(mySettings)
                } catch (e: Exception) {
                    //withContext(Dispatchers.Main) {
                    Toast.makeText(this@SplashActivity,"Error al validar la LICENCIA, error: ${e.message}",Toast.LENGTH_LONG).show()
                    //}
                } finally {
                    abrirMainActivity()
                }
            }
        }else{
            abrirMainActivity()
        }
    }

}