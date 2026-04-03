package com.larangel.rondingpeinn

import MySettings
import DataRawRondin
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
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
import coil.load
import coil.transform.CircleCropTransformation

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

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayoutMain)
        swipeRefreshLayout.setOnRefreshListener {
            LoadingSheetDATA(true)
        }

        mySettings = MySettings(this)

        val codigoActiviacion = mySettings?.getString("CODIGO_ACTIVACION", "")!!
        if (codigoActiviacion.isEmpty()){
            val intent: Intent = Intent(this, ProgramarTags::class.java )
            startActivity(intent)
        }else {
            dataRaw = DataRawRondin(this, CoroutineScope(Dispatchers.IO))
            validaLicencia()
            LoadingSheetDATA()
        }



    }
    override fun onResume() {
        super.onResume()
        updateTextoBotones()
    }

    private fun validaLicencia(){
        val copyRAdmin: TextView = findViewById<TextView>(R.id.textCopyright)
        swipeRefreshLayout.isRefreshing = true
        Toast.makeText(this@MainActivity,"VALIDANDO LICENCIA....",Toast.LENGTH_SHORT).show()
        if (isNetworkAvailable()){
            //DESCARGAR CONFIGURACION Y VALIDAR
            val bucketName = mySettings?.getString("BUCKET_NAME", "").toString()
            val regionStr  = mySettings?.getString("REGION_STR", "").toString()
            val codigoActiv= mySettings?.getString("CODIGO_ACTIVACION", "").toString()
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    //Buscar y descargar nueva configuracion
                    mySettings?.fetchAndProcessS3Config(bucketName, regionStr, codigoActiv)
                    //Inizializa el ENUM con los valores correctos del nombre de sheets
                    SheetTable.initializeAll(mySettings)
                    val appActivada = mySettings?.getInt("APP_ACTIVADA", 0)
                    withContext(Dispatchers.Main) {
                        if (appActivada == 1) {
                            copyRAdmin.text = "v1.0 develop by Luis Rangel"
                        } else {
                            copyRAdmin.text =
                                "#### APP DESACTIVADA #### contactar luisrangel@gmail.com"
                            abrirAlertDesactivada()
                        }
                        swipeRefreshLayout.isRefreshing = false
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        swipeRefreshLayout.isRefreshing = false
                        e.printStackTrace()
                        Toast.makeText(this@MainActivity,"Error al validar la LICENCIA, error: ${e.message}",Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        else{
            val appActivada = mySettings?.getInt("APP_ACTIVADA",0)
            if (appActivada == 1) {
                copyRAdmin.text = "(SIN INTERNET)       v1.0 develop by Luis Rangel"
                //Mostrar cache
                lifecycleScope.launch(Dispatchers.IO) {
                    val vehiculosData = dataRaw?.getCachedVehiclesData()
                    val tagsData = dataRaw?.getTagsCache()
                    val domiciliosUbicacion = dataRaw?.getDomiciliosUbicacion()
                    val porRevisar = dataRaw?.getPorRevisar_20horas()
                    val parkingSlots = dataRaw?.getParkingSlots()
                    val multas = dataRaw?.getMultas()
                    val domiciliosWarnings = dataRaw?.getDomicilioWarnings()
                    val permisosData = dataRaw?.getPermisosCache_DeHoy()
                    val incidenciasData = dataRaw?.getIncidenciasEventos()
                    withContext(Dispatchers.Main) {
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
                }

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
        swipeRefreshLayout.isRefreshing = false
    }

    @SuppressLint("SuspiciousIndentation")
    private fun LoadingSheetDATA(forceLoad: Boolean = false){

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
                swipeRefreshLayout.isRefreshing = false
                //Init textos
                updateTextoBotones()
                //LOGO
                //  Prompt: "High-quality 512x512 app icon, PNG with transparency. Features a friendly security guard for condominiums wearing a green and orange safety vest and a blue cap. A large green map location pin with a white checkmark is positioned above him. In the background, stylized blue condominium buildings and an orange road leading towards them. The text 'Rondy' is integrated into the bottom of the icon in a bold, modern sans-serif font. Color palette: Green, Blue, Orange, and Black. Flat vector style, clean lines, professional and friendly aesthetic, optimized for mobile UI."
                val logoimg = findViewById<ImageView>(R.id.imageLogoMain)
                val urlLogo = mySettings?.getString("IMAGEN_LOGO_PNG","")
                cargarImagenConfigurada(logoimg,urlLogo,R.drawable.logo)

                val totalCargados = autosEventos!!.count() + vehiculosData!!.count() + tagsData!!.count()
                                + domiciliosUbicacion!!.count() + porRevisar!!.count() + parkingSlots!!.count()
                                + multas!!.count() + domiciliosWarnings!!.count() + permisosData!!.count()
                                + incidenciasData!!.count()
                Toast.makeText(
                    this@MainActivity,
                    "Iniciando...${totalCargados} registrosDB",
                    Toast.LENGTH_LONG
                ).show()
            }

        } // fun coroutine

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

    private fun cargarImagenConfigurada(imageView: ImageView, urlDesdeRed: String?, recursoDefault: Int) {
        // Si urlDesdeRed es null o vacío, Coil usará el 'error' o 'placeholder'
        imageView.load(urlDesdeRed) {
            // Imagen que se muestra mientras descarga
            placeholder(recursoDefault)

            // Imagen que se muestra si la URL falla o es inválida
            error(recursoDefault)

            // Opcional: puedes añadir transformaciones
            crossfade(true)
            transformations(CircleCropTransformation())
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


}