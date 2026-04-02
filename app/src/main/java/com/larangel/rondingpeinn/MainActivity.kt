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
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.larangel.rondingpeinn.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var counterAdmin: Int = 0
    private var mySettings: MySettings? = null
    private var dataRaw: DataRawRondin? = null
    //private lateinit var sheetsService: Sheets
    private var loadingOverlay: View? = null
    private var progressBar: ProgressBar? = null
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

//        binding = ActivityMainBinding.inflate(layoutInflater)
//        setContentView(binding.root)

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
        val btn_incidencias: Button = findViewById(R.id.btnIncidenciasMain)
        btn_incidencias.setOnClickListener {
            val intent: Intent = Intent(this, IncidenciasMenu::class.java )
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

        val btnCnfTags: ImageButton = findViewById(R.id.btnConfigTag)
        btnCnfTags.setOnClickListener {
            val intent: Intent = Intent(this, ProgramarTags::class.java )
            startActivity(intent)
        }

        mySettings = MySettings(this)

        val codigoActiviacion = mySettings?.getString("CODIGO_ACTIVACION", "")!!
        if (codigoActiviacion.isEmpty()){
            val intent: Intent = Intent(this, ProgramarTags::class.java )
            startActivity(intent)
        }else {
            dataRaw = DataRawRondin(this, CoroutineScope(Dispatchers.IO))
            SheetTable.initializeAll(mySettings) //Inizializa el ENUM
            //dataRaw?.checarPendientePorSalvarEnCACHE()
            LoadingSheetDATA()
        }

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayoutMain)
        swipeRefreshLayout.setOnRefreshListener {
            LoadingSheetDATA(true)
        }

    }
    override fun onResume() {
        super.onResume()
        updateTextoBotones()
    }

    private fun LoadingSheetDATA(forceLoad: Boolean = false){
        //waitingOn()
        val copyRAdmin: TextView = findViewById<TextView>(R.id.textCopyright)

        //Load all the data in thread
        lifecycleScope.launch(Dispatchers.IO) {
            if (::swipeRefreshLayout.isInitialized)
                swipeRefreshLayout.isRefreshing = true

            val autosEventos = dataRaw?.getAutosEventos(forceLoad)
            val vehiculosData = dataRaw?.getCachedVehiclesData(forceLoad)
            val tagsData = dataRaw?.getTagsCache(forceLoad)
            val domiciliosUbicacion = dataRaw?.getDomiciliosUbicacion(forceLoad)
            val porRevisar = dataRaw?.getPorRevisar_20horas(forceLoad)
            val parkingSlots = dataRaw?.getParkingSlots(forceLoad)
            val multas = dataRaw?.getMultas(forceLoad)
            val domiciliosWarnings = dataRaw?.getDomicilioWarnings(forceLoad)
            val permisosData = dataRaw?.getPermisosCache_DeHoy(forceLoad)
            val incidenciasData = dataRaw?.getIncidenciasEventos(forceLoad)

            withContext(Dispatchers.Main) {
                //Una ves finalizado enviar la info a la UI
                if (isNetworkAvailable()){
                    val bucketName = mySettings?.getString("BUCKET_NAME", "").toString()
                    val regionStr  = mySettings?.getString("REGION_STR", "").toString()
                    val codigoActiv= mySettings?.getString("CODIGO_ACTIVACION", "").toString()
                    //Ejecutar la lectura del CONFIG
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            mySettings?.fetchAndProcessS3Config(bucketName,regionStr,codigoActiv)
                            val appActivada = mySettings?.getInt("APP_ACTIVADA",0)
                            withContext(Dispatchers.Main) {
                                if (appActivada == 1) {
                                    copyRAdmin.text = "v1.0 develop by Luis Rangel"
                                } else {
                                    copyRAdmin.text = "#### APP DESACTIVADA #### contactar luisrangel@gmail.com"
                                    abrirAlertDesactivada()
                                }
                                println("LARANGEL total autos: ${autosEventos?.size}")
                                //waitingOff()
                                if (::swipeRefreshLayout.isInitialized)
                                    swipeRefreshLayout.isRefreshing = false
                            }
                        }catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                //waitingOff()
                                if (::swipeRefreshLayout.isInitialized)
                                    swipeRefreshLayout.isRefreshing = false
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
                }
                else{
                //Indicar que no hay INTERNET
                    //waitingOff()
                    if (::swipeRefreshLayout.isInitialized)
                        swipeRefreshLayout.isRefreshing = false
                    val appActivada = mySettings?.getInt("APP_ACTIVADA",0)
                    if (appActivada == 1) {
                        copyRAdmin.text = "(SIN INTERNET)       v1.0 develop by Luis Rangel"
                        //Mostrar cache
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Datos en cache!!")
                            .setMessage(
                                "Multas: ${multas?.count()}\n" +
                                        "Advertencias: ${domiciliosWarnings?.count()}\n" +
                                        "Cajones Visita: ${parkingSlots?.count()}\n" +
                                        "Por Revisar: ${porRevisar?.count()}\n" +
                                        "Domicilios: ${domiciliosUbicacion?.count()}\n" +
                                        "Permisos: ${permisosData?.count()}\n" +
                                        "Vehiculos: ${vehiculosData?.count()}\n" +
                                        "Tags: ${tagsData?.count()}\n" +
                                        "Incidencias: ${incidenciasData?.count()}"
                            )
                            .setPositiveButton("OK", null)
                            .show()
                    }
                    else {
                        copyRAdmin.text = "#### APP DESACTIVADA #### contactar luisrangel@gmail.com"
                        abrirAlertDesactivada()
                    }
                    Toast.makeText(
                        this@MainActivity,
                        "No hay conexion a INTERNET, usando CACHE",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                //Init textos
                updateTextoBotones()

                Toast.makeText(
                    this@MainActivity,
                    "Iniciando...",
                    Toast.LENGTH_LONG
                ).show()
            }

        }


    }

    private fun updateTextoBotones(){
        val vehiculosData = dataRaw?.getCachedVehiclesData()
        val permisosData = dataRaw?.getPermisosCache_DeHoy()
        val incidenciasData = dataRaw?.getIncidenciasEventosDesde(LocalDate.now().minusDays(2)) //Desde antier (3 dias)

        //Nombre coto
        val nombreCoto: TextView = findViewById<TextView>(R.id.txtCotoName)
        val str_coto = mySettings?.getString("COTO","Version Gratuita")
        nombreCoto.text = mySettings?.getString("COTO","Version Gratuita")

        //Vehiculos
        val btn_vehiculos: Button = findViewById(R.id.btn_vehiculos)
        btn_vehiculos.text = "MAPA v:${vehiculosData?.count()}"

        //Permisos
        val btn_permisos: Button = findViewById(R.id.btn_permisos)
        btn_permisos.text = "Permisos ${permisosData?.count()}"

        //Incidencias
        val btn_incidencias: Button = findViewById(R.id.btnIncidenciasMain)
        btn_incidencias.text = "Incidencias ${incidenciasData?.count()}"

        //ES ADMIN mostrar el boton de config
        val esAdmin = mySettings?.getInt("ESADMIN",0)
        val btnCnfTags: ImageButton = findViewById(R.id.btnConfigTag)
        if (esAdmin == 1) {
            btnCnfTags.visibility = View.VISIBLE
            nombreCoto.text = "Administrador ${str_coto}"
        }
        else {
            btnCnfTags.visibility = View.GONE
            nombreCoto.text = str_coto
        }
    }

    private fun abrirAlertDesactivada(){
        AlertDialog.Builder(this@MainActivity)
            .setTitle("Utilizando Cache, sin conexion a DB")
            .setMessage(
                "##### LA APLICACION NO ESTA ACTIVA #####\n" +
                        "##                                    \n"+
                        "##    Funcionalidad Limitada          \n" +
                        "##                                    \n\n"+
                        "     contactar a luisrangel@gmail.com"
            )
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("OK", null)
            .show()
    }

    // Utilidad simple para detectar red
    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = cm.activeNetworkInfo
        return network?.isConnected == true
    }
//    private fun initializeGoogleServices() {
//        val serviceAccountStream =
//            applicationContext.resources.openRawResource(R.raw.json_google_service_account)
//        val credential = GoogleCredential.fromStream(serviceAccountStream)
//            .createScoped(
//                listOf(
//                    "https://www.googleapis.com/auth/drive",
//                    "https://www.googleapis.com/auth/spreadsheets"
//                )
//            )
//        sheetsService =
//            Sheets.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
//                .setApplicationName("My First Project")
//                .build()
//        println("LARANGEL sheetsService:${sheetsService}")
//    }
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