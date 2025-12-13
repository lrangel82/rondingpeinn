package com.larangel.rondingpeinn

import MySettings
import DataRawRondin
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.larangel.rondingpeinn.R
import com.larangel.rondingpeinn.VehicleSearchActivity
import com.larangel.rondingpeinn.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var counterAdmin: Int = 0
    private var mySettings: MySettings? = null
    private var dataRaw: DataRawRondin? = null
    private lateinit var sheetsService: Sheets
    private var loadingOverlay: View? = null
    private var progressBar: ProgressBar? = null
    private lateinit var resultText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        resultText = findViewById(R.id.txtMainResult)
        mySettings = MySettings(this)
        dataRaw = DataRawRondin(this,CoroutineScope(Dispatchers.IO))

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val btn: Button = findViewById(R.id.btn_StartRondin)
        btn.setOnClickListener{
            val intent: Intent = Intent(this, StartRondinActivity::class.java )
            startActivity(intent)
        }
        val btnPermisos: Button = findViewById(R.id.btn_permisos)
        btnPermisos.setOnClickListener{
            val intent: Intent = Intent(this, PermisosActivity::class.java )
            startActivity(intent)
        }
        val btn_vehiculos: Button = findViewById(R.id.btn_vehiculos)
        btn_vehiculos.setOnClickListener{
            val intent: Intent = Intent(this, VehicleSearchActivity::class.java )
            startActivity(intent)
        }

        val copyRAdmin: TextView = findViewById<TextView>(R.id.textCopyright)
        copyRAdmin.setOnClickListener{
            val clickRequired: Int = 9
            if (counterAdmin++ >= clickRequired ) {
                // Modo Admin
                val intent: Intent = Intent(this, ProgramarTags::class.java )
                startActivity(intent)
            }
            else if ( counterAdmin >= clickRequired - 2 ) {
                Toast.makeText(this, "Admin left clicks: ${ clickRequired - counterAdmin }", Toast.LENGTH_SHORT)
                    .show()
            }

        }

        LoadingSheetDATA()


//        val navView: BottomNavigationView = binding.navView
//
//        val navController = findNavController(R.id.nav_host_fragment_activity_main)
//        // Passing each menu ID as a set of Ids because each
//        // menu should be considered as top level destinations.
//        val appBarConfiguration = AppBarConfiguration(
//            setOf(
//                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications
//            )
//        )
//        setupActionBarWithNavController(navController, appBarConfiguration)
//        navView.setupWithNavController(navController)
    }

    private fun LoadingSheetDATA(){
        waitingOn()
        val autosEventos = dataRaw?.getAutosEventos()
        val vehiculosData = dataRaw?.getCachedVehiclesData()
        val domiciliosUbicacion = dataRaw?.getDomiciliosUbicacion()
        val porRevisar = dataRaw?.getPorRevisar()
        val parkingSlots = dataRaw?.getParkingSlots()
        val multas = dataRaw?.getMultas()
        val domiciliosWarnings = dataRaw?.getDomicilioWarnings()
        //Loading all the sheets
        if (isNetworkAvailable()) {
            // Initialize Google services (requires Google Sign-In setup)
            initializeGoogleServices()
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    withContext(Dispatchers.Main) {
                        println("LARANGEL total autos: ${autosEventos?.size}")
                        waitingOff()
                    }
                }catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        waitingOff()
                        println("LARANGEL exception Loading Sheet DATA error:${e}")
                        e.printStackTrace()
                        Toast.makeText(
                            this@MainActivity,
                            "Error al cargar INFORMACION, no hay conexion a INTERNET: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            waitingOff()
        }else{
            lifecycleScope.launch(Dispatchers.IO) {
                withContext(Dispatchers.Main) {
                    waitingOff()
                    Toast.makeText(
                        this@MainActivity,
                        "No hay conexion a INTERNET, usando CACHE",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                waitingOff()
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Datos cargados")
                    .setMessage("Multas: ${multas?.count()}\n" +
                            "Advertencias: ${domiciliosWarnings?.count()}\n" +
                            "Cajones Visita: ${parkingSlots?.count()}\n" +
                            "Por Revisar: ${porRevisar?.count()}\n" +
                            "Domicilios: ${domiciliosUbicacion?.count()}\n" +
                            "Vehiculos: ${vehiculosData?.count()}")
                    .setPositiveButton("OK") { _, _ ->
                        Toast.makeText(
                            this@MainActivity,
                            "Iniciando...",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    .show()
            }
        }
    }

    // Utilidad simple para detectar red
    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = cm.activeNetworkInfo
        return network?.isConnected == true
    }
    private fun initializeGoogleServices() {
        val serviceAccountStream =
            applicationContext.resources.openRawResource(R.raw.json_google_service_account)
        val credential = GoogleCredential.fromStream(serviceAccountStream)
            .createScoped(
                listOf(
                    "https://www.googleapis.com/auth/drive",
                    "https://www.googleapis.com/auth/spreadsheets"
                )
            )
        sheetsService =
            Sheets.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
                .setApplicationName("My First Project")
                .build()
        println("LARANGEL sheetsService:${sheetsService}")
    }
    private fun waitingOn() {
        if (loadingOverlay == null) {
            val rootView = findViewById<ViewGroup>(android.R.id.content)
            loadingOverlay = View(this).apply {
                setBackgroundColor(Color.parseColor("#80000000")) // Semi-transparent black
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                isClickable = true
                isFocusable = true
            }

            progressBar = ProgressBar(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.CENTER
                }
            }

            rootView.addView(loadingOverlay)
            rootView.addView(progressBar)
        }
    }
    private fun waitingOff() {
        loadingOverlay?.let { overlay ->
            val rootView = findViewById<ViewGroup>(android.R.id.content)
            rootView.removeView(overlay)
            rootView.removeView(progressBar)
            loadingOverlay = null
            progressBar = null
        }
    }

}