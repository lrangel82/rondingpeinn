package com.larangel.rondy

import MySettings
import DataRawRondin
import SplashSlidesDialog
import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.icu.util.Calendar
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.larangel.rondy.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import coil.load
import coil.transform.CircleCropTransformation
import com.larangel.rondy.IncidenciasMenu
import com.larangel.rondy.utils.programarAlarma

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var counterAdmin: Int = 0
    private var mySettings: MySettings? = null
    private var dataRaw: DataRawRondin? = null
    //private lateinit var sheetsService: Sheets
    private var loadingOverlay: View? = null
    private var progressBar: ProgressBar? = null
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private var isActive: Boolean = false
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

//        binding = ActivityMainBinding.inflate(layoutInflater)
//        setContentView(binding.root)

//        val btn: Button = findViewById(R.id.btn_StartRondin)
//        btn.setOnClickListener{
//            val intent: Intent = Intent(this, StartRondinActivity::class.java )
//            startActivity(intent)
//        }
        val btnPermisos: Button = findViewById(R.id.btn_permisos)
        btnPermisos.setOnClickListener{
            if (isActive) {
                val intent: Intent = Intent(this, PermisosActivity::class.java)
                startActivity(intent)
            }else{
                //startActivity( Intent(this, AyudaActivity::class.java) )
                mostrarSplashPermisos()
            }
        }

        //MAPA
        val btn_vehiculos: Button = findViewById(R.id.btn_vehiculos)
        btn_vehiculos.setOnClickListener{
            val intent: Intent = Intent(this, VehicleSearchActivity::class.java )
            startActivity(intent)
        }

        //INCIDENCIAS
        val btn_incidencias: Button = findViewById(R.id.btnIncidenciasMain)
        btn_incidencias.setOnClickListener {
            if (isActive) {
                val intent: Intent = Intent(this, IncidenciasMenu::class.java )
                startActivity(intent)
            }else{
                //startActivity( Intent(this, AyudaActivity::class.java) )
                mostrarSplashIncidencias()
            }
        }

//        val copyRAdmin: TextView = findViewById<TextView>(R.id.textCopyright)
//        copyRAdmin.setOnClickListener{
//            val clickRequired: Int = 9
//            if (counterAdmin++ >= clickRequired ) {
//                // Modo Admin
//                val intent: Intent = Intent(this, ProgramarTags::class.java )
//                startActivity(intent)
//            }
//            else if ( counterAdmin >= clickRequired - 2 ) {
//                Toast.makeText(this, "Admin left clicks: ${ clickRequired - counterAdmin }", Toast.LENGTH_SHORT)
//                    .show()
//            }
//
//        }

        //CONFIG --> Settings
        val btnCnfTags: ImageButton = findViewById(R.id.btnConfigTag)
        btnCnfTags.setOnClickListener {
            val intent: Intent = Intent(this, SettingsActivity::class.java )
            startActivity(intent)
        }

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayoutMain)
        swipeRefreshLayout.setOnRefreshListener {
            lifecycleScope.launch {
                swipeRefreshLayout.isRefreshing = true
                validaLicencia(true)
                loadingSheetDATA(true)
                swipeRefreshLayout.isRefreshing = false
            }
        }

        mySettings = MySettings(applicationContext)

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
        else {
            dataRaw = DataRawRondin(applicationContext, CoroutineScope(Dispatchers.IO))
            lifecycleScope.launch {
                swipeRefreshLayout.isRefreshing = true
                validaLicencia()
                loadingSheetDATA()
                swipeRefreshLayout.isRefreshing = false
            }
        }
        isActive= mySettings?.getInt( "APP_ACTIVADA",0) == 1

        verificarPermisosRequeridos()

        val yaVioAyuda = mySettings?.getBoolean("ayuda_main_activity", false)
        val btnAyuda: ImageButton = findViewById(R.id.btnAyuda)
        if (yaVioAyuda == false) {
            mostrarSplashInformativo()
            mySettings?.saveBoolean("ayuda_main_activity", true) // Lo marcamos como visto
        }
        btnAyuda.setOnClickListener {
            mostrarSplashInformativo()
        }


    }
    override fun onResume() {
        super.onResume()
        updateTextoBotones()
    }

    //##### MENU ####
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.bottom_nav_menu, menu)
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.navigation_home -> {
                true
            }
            R.id.navigation_mapa -> {
                startActivity(Intent(this, VehicleSearchActivity::class.java))
                true
            }
            R.id.navigation_incidencias -> {
                if (isActive)
                    startActivity(Intent(this, ListadoIncidenciasActivity::class.java))
                else
                    startActivity(Intent(this, AyudaActivity::class.java))
                true
            }
            R.id.navigation_permisos -> {
                if (isActive)
                    startActivity(Intent(this, PermisosActivity::class.java))
                else
                    startActivity(Intent(this, AyudaActivity::class.java))
                true
            }
            R.id.navigation_notifications -> {
                if (isActive)
                    startActivity(Intent(this, PorRevisarListActivity::class.java))
                else
                    startActivity(Intent(this, AyudaActivity::class.java))
                true
            }
            R.id.navigation_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.navigation_acercade -> {
                startActivity(Intent(this, AcercadeActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private suspend fun validaLicencia(force: Boolean = false){
        val copyRAdmin: TextView = findViewById<TextView>(R.id.textCopyright)
        Toast.makeText(this@MainActivity,"VALIDANDO LICENCIA....",Toast.LENGTH_SHORT).show()

        //VERSION
        val versionName = getVersionApp()

        if (isNetworkAvailable()){
            //DESCARGAR CONFIGURACION Y VALIDAR
            val bucketName = mySettings?.getString("BUCKET_NAME", "").toString()
            val regionStr  = mySettings?.getString("REGION_STR", "").toString()
            val codigoActiv= mySettings?.getString("CODIGO_ACTIVACION", "").toString()
            //lifecycleScope.launch(Dispatchers.IO) {
                try {
                    //Buscar y descargar nueva configuracion
                    mySettings?.fetchAndProcessS3Config(bucketName, regionStr, codigoActiv, force = force)
                    withContext(Dispatchers.Main) {
                        //Inizializa el ENUM con los valores correctos del nombre de sheets
                        SheetTable.initializeAll(mySettings)
                        isActive= mySettings?.getInt( "APP_ACTIVADA",0) == 1
                    }

                    if (isActive) {
                        copyRAdmin.text = "${versionName} develop by Luis Rangel"
                    } else {
                        copyRAdmin.text =
                            "#### APP DESACTIVADA #### contactar luisrangel@gmail.com ${versionName}"
                        mostrarSplashDemo()
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this@MainActivity,"Error al validar la LICENCIA, error: ${e.message}",Toast.LENGTH_LONG).show()
                }
            //}
        }
        else{
            val appActivada = mySettings?.getInt("APP_ACTIVADA",0)
            if (appActivada == 1) {
                copyRAdmin.text = "(SIN INTERNET)       ${versionName} develop by Luis Rangel"
                //Mostrar cache
                //lifecycleScope.launch(Dispatchers.IO) {
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
                //}

            }
            else {
                copyRAdmin.text = "#### APP DESACTIVADA #### contactar luisrangel@gmail.com ${versionName}"
                mostrarSplashDemo()
            }
            Toast.makeText(
                this@MainActivity,
                "No hay conexion a INTERNET, usando CACHE",
                Toast.LENGTH_SHORT
            ).show()
        }
        swipeRefreshLayout.isRefreshing = false
    }
    fun getVersionApp(): String {
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Para Android 13+
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                // Para versiones anteriores
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }

            val versionName = packageInfo.versionName
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }

            return "$versionName(v.$versionCode)"

        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            return "sin Version"
        }
    }
    fun mostrarSplashDemo(){
        val misImagenes = listOf(
            R.drawable.slide_demo_01
        )

        val dialog = SplashSlidesDialog(misImagenes,"")
        dialog.show(supportFragmentManager, "SplashSlides")
    }
    fun mostrarSplashInformativo() {
        val misImagenes = listOf(
            R.drawable.slide_bienvenida_01,
            R.drawable.slide_incidencias_01,
            R.drawable.slide_incidencias_02,
            R.drawable.slide_permisos_01,
            R.drawable.slide_permisos_02,
            R.drawable.slide_vehiculos_01,
            R.drawable.slide_vehiculos_02
        )

        val dialog = SplashSlidesDialog(misImagenes,"Info Rondy")
        dialog.show(supportFragmentManager, "SplashSlides")
    }
    fun mostrarSplashPermisos() {
        val misImagenes = listOf(
            R.drawable.slide_permisos_01,
            R.drawable.slide_permisos_02
        )

        val dialog = SplashSlidesDialog(misImagenes,"Info Permisos")
        dialog.show(supportFragmentManager, "SplashSlides")
    }
    fun mostrarSplashIncidencias(){
        val misImagenes = listOf(
            R.drawable.slide_incidencias_01,
            R.drawable.slide_incidencias_02
        )

        val dialog = SplashSlidesDialog(misImagenes,"Info Incidencias")
        dialog.show(supportFragmentManager, "SplashSlides")
    }



    @SuppressLint("SuspiciousIndentation")
    private suspend fun loadingSheetDATA(forceLoad: Boolean = false){

        //Load all the data in thread
        //lifecycleScope.launch(Dispatchers.IO) {
            //withContext(Dispatchers.Main) {
        val dataResult = withContext(Dispatchers.IO) {
            val alarmas = dataRaw?.getAlarmas(forceLoad)
            val residentes = dataRaw?.getResidentes(forceLoad)
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
            val incidenciasConfig = dataRaw?.getIncidenciasConfig(forceLoad)

            // Empaquetamos los datos para usarlos afuera en el hilo Main
            DataWrapper(
                alarmas, residentes, autosEventos, vehiculosData, tagsData,
                domiciliosUbicacion, porRevisar, parkingSlots, multas,
                domiciliosWarnings, permisosData, incidenciasData, incidenciasConfig
            )
        }
            //}

            //withContext(Dispatchers.Main) {
                //swipeRefreshLayout.isRefreshing = false
                //Init textos
                updateTextoBotones()

                //SetUp Alarmas
        dataResult.alarmas?.forEach { row->
                    programarAlarma(applicationContext, row[0].toString(),row[1].toString())
                }

                //LOGO
                //  Prompt: "High-quality 512x512 app icon, PNG with transparency. Features a friendly security guard for condominiums wearing a green and orange safety vest and a blue cap. A large green map location pin with a white checkmark is positioned above him. In the background, stylized blue condominium buildings and an orange road leading towards them. The text 'Rondy' is integrated into the bottom of the icon in a bold, modern sans-serif font. Color palette: Green, Blue, Orange, and Black. Flat vector style, clean lines, professional and friendly aesthetic, optimized for mobile UI."
                val logoimg = findViewById<ImageView>(R.id.imageLogoMain)
                val urlLogo = mySettings?.getString("IMAGEN_LOGO_PNG","")
                cargarImagenConfigurada(logoimg,urlLogo,R.drawable.logo)
                try {
                    val totalCargados = (dataResult.residentes?.count() ?: 0) +
                            (dataResult.autosEventos?.count() ?: 0) +
                            (dataResult.vehiculosData?.count() ?: 0) +
                            (dataResult.tagsData?.count() ?: 0) +
                            (dataResult.domiciliosUbicacion?.count() ?: 0) +
                            (dataResult.porRevisar?.count() ?: 0) +
                            (dataResult.parkingSlots?.count() ?: 0) +
                            (dataResult.multas?.count() ?: 0) +
                            (dataResult.domiciliosWarnings?.count() ?: 0) +
                            (dataResult.permisosData?.count() ?: 0) +
                            (dataResult.incidenciasData?.count() ?: 0) +
                            (dataResult.incidenciasConfig?.count() ?: 0)
                    Toast.makeText(
                        this@MainActivity,
                        "Iniciando...${totalCargados} registrosDB",
                        Toast.LENGTH_LONG
                    ).show()
                }catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this@MainActivity,"Error cargar, error: ${e.message}",Toast.LENGTH_LONG).show()
                }
            //}

        //} // fun coroutine

    }
    private fun updateTextoBotones(){
        val vehiculosData = dataRaw?.getCachedVehiclesData()
        val permisosData = dataRaw?.getPermisosCache_DeHoy()
        val incidenciasData = dataRaw?.getIncidenciasEventosDesde(LocalDate.now().minusDays(2)) //Desde antier (3 dias)

        //Nombre coto
        val nombreCoto: TextView = findViewById<TextView>(R.id.txtCotoName)
        val str_coto = mySettings?.getString("APP_NAME","Version Gratuita")
        nombreCoto.text = mySettings?.getString("APP_NAME","Version Gratuita")

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


    //Verificar los permisos de la aplicacion
    private fun verificarPermisosRequeridos(){
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager

        if (isActive && (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED)) {

            AlertDialog.Builder(this@MainActivity)
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
        else if (isActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()){
                AlertDialog.Builder(this@MainActivity)
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
        if (isActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            if (!notificationManager.canUseFullScreenIntent()) {
                AlertDialog.Builder(this@MainActivity)
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
        if (isActive && ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            AlertDialog.Builder(this@MainActivity)
                .setMessage("No se ha dado permiso para PostNotificaciones, debe ser activado para el correcto funcionamiento.")
                .setPositiveButton("Activar permiso") { _, _ ->
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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


// Clase auxiliar de ayuda (puedes ponerla al final de tu archivo) para pasar los datos limpios de IO a Main
private class DataWrapper(
    val alarmas: List<List<Any>>?, // Cambia el tipo List<List<Any>> por el tipo real que retornen tus funciones
    val residentes: List<Any>?,
    val autosEventos: List<Any>?,
    val vehiculosData: List<Any>?,
    val tagsData: List<Any>?,
    val domiciliosUbicacion: List<Any>?,
    val porRevisar: List<Any>?,
    val parkingSlots: List<Any>?,
    val multas: List<Any>?,
    val domiciliosWarnings: List<Any>?,
    val permisosData: List<Any>?,
    val incidenciasData: List<Any>?,
    val incidenciasConfig: List<Any>?
)