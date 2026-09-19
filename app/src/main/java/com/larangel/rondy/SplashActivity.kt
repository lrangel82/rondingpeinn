package com.larangel.rondy

import CrashHandler
import MySettings
import android.Manifest
import androidx.appcompat.app.AppCompatActivity
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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

    //PERMISOS
    private val REQUEST_CAMERA_PERMISSION = 100
    private val REQUEST_LOCATION_PERMISSION = 101
    private val REQUEST_IMAGE_CAPTURE = 102
    private val REQUEST_IMAGE_PICK = 103
    private val REQUEST_STORAGE_PERMISSION = 104
    private val REQUEST_ALARM_PERMISSION = 105

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permiso concedido: las alarmas funcionarán
            Toast.makeText(this, "PERMISO CONCEDIDO", Toast.LENGTH_SHORT).show()
        } else {
            // Permiso denegado: explica al usuario que no recibirá alertas
            Toast.makeText(this, "PERMISO Denegado las alarmas no se mostraran", Toast.LENGTH_SHORT).show()
        }
    }


    //RECORDAR ANIMACIONES LOTTIE JSON
    // PARA LA GUIA DE AYUDA

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))

        setContentView(R.layout.activity_splash)

        val imgLogo: ImageView = findViewById(R.id.imgLogoSplash)

        verificarPermisosRequeridos()

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

    //Verificar los permisos de la aplicacion
    private fun verificarPermisosRequeridos(){
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager

        if ((ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED)) {

            AlertDialog.Builder(this@SplashActivity)
                .setMessage("No se ha dado permiso para la camara y lectura de imagenes del dispositivo, debe ser activado para el correcto funcionamiento.")
                .setPositiveButton("Activar permiso") { _, _ ->
                    ActivityCompat.requestPermissions(this, arrayOf(
                        android.Manifest.permission.CAMERA,
                        android.Manifest.permission.READ_MEDIA_IMAGES
                    ),REQUEST_CAMERA_PERMISSION)
                }
                .setCancelable(false)
                .show()

        }
        else if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()){
                AlertDialog.Builder(this@SplashActivity)
                    .setMessage("No se ha dado permiso para las ALARMAS del dispositivo, debe ser activado para el correcto funcionamiento.")
                    .setPositiveButton("Activar permiso") { _, _ ->
//                    ActivityCompat.requestPermissions(this, arrayOf(
//                        android.Manifest.permission.SCHEDULE_EXACT_ALARM,
//                        android.Manifest.permission.WAKE_LOCK,
//                        android.Manifest.permission.USE_FULL_SCREEN_INTENT
//                    ),REQUEST_ALARM_PERMISSION)
                        // No tenemos permiso: Abrir la configuración del sistema para que el usuario lo otorgue
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.fromParts("package", applicationContext.packageName, null)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        applicationContext.startActivity(intent)
                    }
                    .setCancelable(false)
                    .show()
            }
        }
        if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            if (!notificationManager.canUseFullScreenIntent()) {
                AlertDialog.Builder(this@SplashActivity)
                    .setMessage("No se ha dado permiso para ejecutar FULL SCREEN, debe ser activado para el correcto funcionamiento.")
                    .setPositiveButton("Activar permiso") { _, _ ->
                        val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                            data = Uri.fromParts("package", applicationContext.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        applicationContext.startActivity(intent)
                    }
                    .setCancelable(false)
                    .show()

            }
        }
        if ( ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            AlertDialog.Builder(this@SplashActivity)
                .setMessage("No se ha dado permiso para PostNotificaciones, debe ser activado para el correcto funcionamiento.")
                .setPositiveButton("Activar permiso") { _, _ ->
                    requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
//                    ActivityCompat.requestPermissions(this, arrayOf(
//                        android.Manifest.permission.POST_NOTIFICATIONS
//                    ),REQUEST_ALARM_PERMISSION)
                }
                .setCancelable(false)
                .show()

        }
//        else if( isActive && (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
//            || ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)) {
//
//            AlertDialog.Builder(this@MainActivity)
//                .setMessage("No se ha dado permiso para guardar imagenes, debe ser activado para el correcto funcionamiento.")
//                .setPositiveButton("Activar permiso") { _, _ ->
//                    ActivityCompat.requestPermissions(this, arrayOf(
//                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
//                        android.Manifest.permission.READ_EXTERNAL_STORAGE
//                    ),REQUEST_STORAGE_PERMISSION)
//                }
//                .setCancelable(false)
//                .show()
//        }
    }
    @RequiresPermission(allOf = [android.Manifest.permission.CAMERA, Manifest.permission.READ_MEDIA_IMAGES])
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CAMERA_PERMISSION -> {
                if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED)
                    Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
            }
            REQUEST_LOCATION_PERMISSION -> {
                if (grantResults.isEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
                }
            }
            REQUEST_ALARM_PERMISSION -> {
                if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED)
                    Toast.makeText(this, "Alarma permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
}