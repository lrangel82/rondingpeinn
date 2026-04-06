package com.larangel.rondy

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

        validaLicencia()

//        // 3. Esperar 500 miliseconds y brincar al MainActivity
//        Handler(Looper.getMainLooper()).postDelayed({
//            startActivity(Intent(this, MainActivity::class.java))
//            finish() // Cerramos el Splash para que no puedan volver atrás
//        }, 500)
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
        Toast.makeText(this@SplashActivity,"VALIDANDO LICENCIA....",Toast.LENGTH_SHORT).show()
        if (isNetworkAvailable()){
            //DESCARGAR CONFIGURACION Y VALIDAR
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


//    private lateinit var binding: ActivitySplashBinding
//    private lateinit var fullscreenContent: TextView
//    private lateinit var fullscreenContentControls: LinearLayout
//    private val hideHandler = Handler(Looper.myLooper()!!)
//
//    @SuppressLint("InlinedApi")
//    private val hidePart2Runnable = Runnable {
//        // Delayed removal of status and navigation bar
//        if (Build.VERSION.SDK_INT >= 30) {
//            fullscreenContent.windowInsetsController?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
//        } else {
//            // Note that some of these constants are new as of API 16 (Jelly Bean)
//            // and API 19 (KitKat). It is safe to use them, as they are inlined
//            // at compile-time and do nothing on earlier devices.
//            fullscreenContent.systemUiVisibility =
//                View.SYSTEM_UI_FLAG_LOW_PROFILE or
//                        View.SYSTEM_UI_FLAG_FULLSCREEN or
//                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
//                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
//                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
//                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
//        }
//    }
//    private val showPart2Runnable = Runnable {
//        // Delayed display of UI elements
//        supportActionBar?.show()
//        fullscreenContentControls.visibility = View.VISIBLE
//    }
//    private var isFullscreen: Boolean = false
//
//    private val hideRunnable = Runnable { hide() }
//
//    /**
//     * Touch listener to use for in-layout UI controls to delay hiding the
//     * system UI. This is to prevent the jarring behavior of controls going away
//     * while interacting with activity UI.
//     */
//    private val delayHideTouchListener = View.OnTouchListener { view, motionEvent ->
//        when (motionEvent.action) {
//            MotionEvent.ACTION_DOWN -> if (AUTO_HIDE) {
//                delayedHide(AUTO_HIDE_DELAY_MILLIS)
//            }
//
//            MotionEvent.ACTION_UP -> view.performClick()
//            else -> {
//            }
//        }
//        false
//    }
//
//    @SuppressLint("ClickableViewAccessibility")
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//
//        binding = ActivitySplashBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//
//        supportActionBar?.setDisplayHomeAsUpEnabled(true)
//
//        isFullscreen = true
//
//        // Set up the user interaction to manually show or hide the system UI.
//        fullscreenContent = binding.fullscreenContent
//        fullscreenContent.setOnClickListener { toggle() }
//
//        //fullscreenContentControls = binding.fullscreenContentControls
//
//        // Upon interacting with UI controls, delay any scheduled hide()
//        // operations to prevent the jarring behavior of controls going away
//        // while interacting with the UI.
//       // binding.dummyButton.setOnTouchListener(delayHideTouchListener)
//    }
//
//    override fun onPostCreate(savedInstanceState: Bundle?) {
//        super.onPostCreate(savedInstanceState)
//
//        // Trigger the initial hide() shortly after the activity has been
//        // created, to briefly hint to the user that UI controls
//        // are available.
//        delayedHide(100)
//    }
//
//    private fun toggle() {
//        if (isFullscreen) {
//            hide()
//        } else {
//            show()
//        }
//    }
//
//    private fun hide() {
//        // Hide UI first
//        supportActionBar?.hide()
//        fullscreenContentControls.visibility = View.GONE
//        isFullscreen = false
//
//        // Schedule a runnable to remove the status and navigation bar after a delay
//        hideHandler.removeCallbacks(showPart2Runnable)
//        hideHandler.postDelayed(hidePart2Runnable, UI_ANIMATION_DELAY.toLong())
//    }
//
//    private fun show() {
//        // Show the system bar
//        if (Build.VERSION.SDK_INT >= 30) {
//            fullscreenContent.windowInsetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
//        } else {
//            fullscreenContent.systemUiVisibility =
//                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
//                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
//        }
//        isFullscreen = true
//
//        // Schedule a runnable to display UI elements after a delay
//        hideHandler.removeCallbacks(hidePart2Runnable)
//        hideHandler.postDelayed(showPart2Runnable, UI_ANIMATION_DELAY.toLong())
//    }
//
//    /**
//     * Schedules a call to hide() in [delayMillis], canceling any
//     * previously scheduled calls.
//     */
//    private fun delayedHide(delayMillis: Int) {
//        hideHandler.removeCallbacks(hideRunnable)
//        hideHandler.postDelayed(hideRunnable, delayMillis.toLong())
//    }
//
//    companion object {
//        /**
//         * Whether or not the system UI should be auto-hidden after
//         * [AUTO_HIDE_DELAY_MILLIS] milliseconds.
//         */
//        private const val AUTO_HIDE = true
//
//        /**
//         * If [AUTO_HIDE] is set, the number of milliseconds to wait after
//         * user interaction before hiding the system UI.
//         */
//        private const val AUTO_HIDE_DELAY_MILLIS = 3000
//
//        /**
//         * Some older devices needs a small delay between UI widget updates
//         * and a change of the status and navigation bar.
//         */
//        private const val UI_ANIMATION_DELAY = 300
//    }
}