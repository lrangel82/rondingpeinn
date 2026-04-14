package com.larangel.rondy

import CheckPoint
import MySettings
import DataRawRondin
import EventAdapter
import EventModal
import PorRevisarRecord
import kotlinx.coroutines.*
import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentFilter.MalformedMimeTypeException
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest
import com.google.api.services.sheets.v4.model.DeleteDimensionRequest
import com.google.api.services.sheets.v4.model.DimensionRange
import com.google.api.services.sheets.v4.model.Request
import com.google.api.services.sheets.v4.model.ValueRange
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.ByteArrayOutputStream
import java.io.File as JavaFile
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import androidx.core.graphics.scale
import java.io.File
import java.io.FileOutputStream
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.ProgressBar
import android.widget.FrameLayout
import android.widget.Switch
import android.widget.TableRow
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetSequence
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.switchmaterial.SwitchMaterial
import com.larangel.rondy.ProgramarTags
import com.larangel.rondy.StartRondinActivity
import java.time.temporal.ChronoUnit
import kotlin.String
import kotlin.collections.List
import kotlin.collections.mutableListOf

class  VehicleSearchActivity : AppCompatActivity() {
    private var mySettings: MySettings? = null
    private var dataRaw: DataRawRondin? = null
    private lateinit var plateInput: EditText
    private lateinit var searchButton: Button
    private lateinit var cleanButton: ImageButton
    private lateinit var cameraButton: ImageButton
    private lateinit var resultText: TextView
    private lateinit var eventsRecyclerView: RecyclerView
    private lateinit var saveEventButton: Button
    private lateinit var porRevisarButton: Button
    private lateinit var photoThumbnail: ImageView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    //private lateinit var sheetsService: Sheets
    private val events = ArrayList<EventModal>()
    private val REQUEST_CAMERA_PERMISSION = 100
    private val REQUEST_LOCATION_PERMISSION = 101
    private val REQUEST_IMAGE_CAPTURE = 102
    private val REQUEST_IMAGE_PICK = 103
    private val REQUEST_STORAGE_PERMISSION = 104
    private var photoUri: Uri? = null
    private var vehicleStreet: String? = null
    private var vehicleNumber: String? = null
    private var vehicleSource: String? = null
    private var keySlotSeleccionado: String? = null
    private val porRevisarRecords = mutableListOf<PorRevisarRecord>()
    private val porRevisarNotificado= mutableListOf<String>()
    //private var porRevisarSheetId: Int = 0
    //private var domicilioWarningsSheetId: Int = 0
    private val handler = Handler(Looper.getMainLooper())
    //private val checkRunnable = Runnable { checkPorRevisarLocations() }
    private var loadingOverlay: View? = null
    private var progressBar: ProgressBar? = null
    private var googleMap: GoogleMap? = null
    private var isManualMode = false // Controla si mandamos nosotros o el GPS
    private var lastGpsLocation: LatLng? = null
    private var locationListener: LocationListener? = null

    private var stopSearchVehicle: Boolean = false
    private var isActive: Boolean = false

    //RONDIN NFC
    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntentNFC: PendingIntent? = null
    private var intentNFCFiltersArray: Array<IntentFilter>? = null
    private var techNFCListsArray: Array<Array<String>>? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vehicle_search)

        // Initialize UI components
        plateInput = findViewById(R.id.plateInput)
        searchButton = findViewById(R.id.searchButton)
        cleanButton = findViewById(R.id.cleanButton)
        cameraButton = findViewById(R.id.cameraButton)
        resultText = findViewById(R.id.resultText)
        eventsRecyclerView = findViewById(R.id.eventsRecyclerView)
        saveEventButton = findViewById(R.id.saveEventButton)
        photoThumbnail = findViewById(R.id.photoThumbnail)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mySettings = MySettings(applicationContext)
        dataRaw = DataRawRondin(applicationContext,CoroutineScope(Dispatchers.IO))

        isActive= mySettings?.getInt( "APP_ACTIVADA",0) == 1

        if (!isActive){
            //Ocultar lo que no es de rondin
            eventsRecyclerView.visibility = View.GONE
            searchButton.visibility = View.GONE
            plateInput.visibility = View.GONE
            cleanButton.visibility = View.GONE
            cameraButton.visibility = View.GONE
            saveEventButton.visibility = View.GONE
            findViewById<TableRow>(R.id.tableRowVisistas).visibility = View.GONE
        }

        // Setup RecyclerView
        eventsRecyclerView.layoutManager = LinearLayoutManager(this)
        eventsRecyclerView.adapter = EventAdapter(events)

        // Search button click listener
        searchButton.setOnClickListener {
            val plate = plateInput.text.toString().trim()
            if (plate.isNotEmpty()) {
                searchVehicle(plate)
            } else {
                Toast.makeText(this, "Enter a license plate", Toast.LENGTH_SHORT).show()
            }
        }
        plateInput.doOnTextChanged { text, start, before, count ->
            if (text.toString().length >= 3) {
                searchVehicle(text.toString())
            }else{
                resultText.text = "" //Clean
            }
        }

        // Clean button
        cleanButton.setOnClickListener {
            cleanFrom()
        }
        // Camera button click listener
        cameraButton.setOnClickListener {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                captureImage()
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
            }
        }

        // Save event button click listener
        saveEventButton.setOnClickListener {
            saveNewEvent()
        }

        // ##### NFC #####  Inicializa Rondin SWTICH
        pendingIntentNFC = PendingIntent.getActivity(
            //Start pending intent ESCUCHANDO..
            this, 0, Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE
        )
        val ndef = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
            // Setup an intent filter for all MIME based dispatches
            try {
                addDataType("*/*")
            } catch (e: MalformedMimeTypeException) {
                throw RuntimeException("fail", e)
            }
        }
        intentNFCFiltersArray = arrayOf(ndef, IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED), IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED))
        techNFCListsArray = arrayOf(
            arrayOf(Ndef::class.java.name),
            arrayOf(android.nfc.tech.NfcA::class.java.name),
            arrayOf(android.nfc.tech.NfcB::class.java.name),
            arrayOf(android.nfc.tech.IsoDep::class.java.name),
            arrayOf(android.nfc.tech.MifareClassic::class.java.name),
            arrayOf(android.nfc.tech.MifareUltralight::class.java.name)
        )
                //arrayOf(arrayOf<String>(Ndef::class.java.name)) // Setup a tech list for all Ndef tags
        inicializaRondinSwitch()
        // ##### END NFC INIT #####


        // Add Por Revisar button
        if(isActive) {
            porRevisarButton = Button(this)
            porRevisarButton.setOnClickListener {
                val intent = Intent(this, PorRevisarListActivity::class.java)
                startActivity(intent)
            }
            val parent = saveEventButton.parent as? LinearLayout
            parent?.addView(porRevisarButton)
        }

        // Botón para volver al GPS
        val btnRecuperarGPS: ImageButton = findViewById(R.id.btn_recuperar_gps_vehiculo)
        btnRecuperarGPS.setOnClickListener {
            isManualMode = false
            lastGpsLocation?.let { loc ->
                googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(loc, 20f))
            }
        }

        // Carga el fragmento solo si aún no existe
        if (supportFragmentManager.findFragmentById(R.id.map_fragment_vehicle) == null) {
            val mapFragment = SupportMapFragment.newInstance()
            supportFragmentManager.beginTransaction()
                .replace(R.id.map_fragment_vehicle, mapFragment)
                .commit()
        }

        // Run cleanup on app start
        cleanOldThumbnails()

        loadPorRevisar()
        refreshContadorEventos()
        setupMap()
        updatePorRevisarButton()

        val yaVioAyuda = mySettings?.getBoolean("ayuda_mapa_activity", false)
        val btnAyuda: ImageButton = findViewById(R.id.btnAyudaMapa)
        if (yaVioAyuda == false) {
            mostrarAyuda()
            mySettings?.saveBoolean("ayuda_mapa_activity", true) // Lo marcamos como visto
        }
        btnAyuda.setOnClickListener {
            mostrarAyuda()
        }


    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.bottom_nav_menu, menu)
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.navigation_home -> {
                startActivity(Intent(this, MainActivity::class.java))
                this.finish()
                true
            }
            R.id.navigation_mapa -> {
                true
            }
            R.id.navigation_incidencias -> {
                if (isActive) {
                    startActivity(Intent(this, ListadoIncidenciasActivity::class.java))
                    this.finish()
                }else
                    startActivity(Intent(this, AyudaActivity::class.java))

                true
            }
            R.id.navigation_permisos -> {
                if (isActive) {
                    startActivity(Intent(this, PermisosActivity::class.java))
                    this.finish()
                }else
                    startActivity(Intent(this, AyudaActivity::class.java))
                true
            }
            R.id.navigation_notifications -> {
                if (isActive) {
                    startActivity(Intent(this, PorRevisarListActivity::class.java))
                    this.finish()
                }else
                    startActivity(Intent(this, AyudaActivity::class.java))
                true
            }
            R.id.navigation_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                this.finish()
                true
            }
            R.id.navigation_acercade -> {
                startActivity(Intent(this, AcercadeActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }


    private fun waitingOn() {
        if (loadingOverlay == null) {
            //val rootView = findViewById<ViewGroup>(android.R.id.content)

            val rootView = resultText.parent as? LinearLayout
            loadingOverlay = View(this).apply {
                setBackgroundColor(Color.parseColor("#80000000")) // Semi-transparent black
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    gravity = android.view.Gravity.CENTER
                }
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

            rootView?.addView(loadingOverlay)
            rootView?.addView(progressBar)
        }
    }
    private fun waitingOff() {
        loadingOverlay?.let { overlay ->
            //val rootView = findViewById<ViewGroup>(android.R.id.content)
            val rootView = resultText.parent as? LinearLayout
            rootView?.removeView(overlay)
            rootView?.removeView(progressBar)
            loadingOverlay = null
            progressBar = null
        }
    }

    private fun setupMap() {
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_fragment_vehicle) as SupportMapFragment
        mapFragment.getMapAsync { gMap ->
            googleMap = gMap

            // Este callback se ejecuta cuando el mapa terminó de cargar visualmente
            googleMap?.setOnMapLoadedCallback {
                updateMapMarkers()
                moveCameraToShowAllTAGS()
                isManualMode = true
            }
//            // Centrar en la ubicación actual si tienes permiso:
//            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
//                gMap.isMyLocationEnabled = true
//                fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
//                    if (loc != null) {
//                        val userLatLng = LatLng(loc.latitude, loc.longitude)
//                        gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 21f))
//                    }
//                }
//            }
            // Listener cuando el mapa se mueve
            googleMap?.setOnCameraMoveStartedListener { reason ->
                // Si el usuario mueve el mapa con el dedo, activamos modo manual
                if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                    isManualMode = true
                }
            }
        }
    }

    private fun updateMapMarkers(conMarkersVehiculos: Boolean = true) {
        googleMap?.let { gMap ->
            gMap.clear()
            //**************** PARKING SLOTS ***************************
            // 1. Obtén la lista de espacios (your implementation here)
            val parkSlots = dataRaw?.getParkingSlots()
            // 2. Filtra eventos de las últimas horas
            val eventosRecientes = dataRaw?.getAutosEventos_6horas()

            // 3. Por cada espacio
            parkSlots?.forEach { slot ->
                val lat = slot[0].toString().toDoubleOrNull() ?: return@forEach
                val lon = slot[1].toString().toDoubleOrNull() ?: return@forEach
                val key = slot[2].toString()
                val ocupado = eventosRecientes?.find { it[4].toString() == key }
                val color = if (!ocupado.isNullOrEmpty()) Color.GREEN else Color.RED
                gMap.addCircle(
                    CircleOptions()
                        .center(LatLng(lat, lon))
                        .radius(1.5) // metros visuales
                        .fillColor(color)
                        .strokeColor(Color.BLACK)
                        .strokeWidth(2f)
                )
                // Pon una etiqueta encima
                if(conMarkersVehiculos)
                    gMap.addMarker(
                        MarkerOptions()
                            .position(LatLng(lat, lon))
                            .title(key)
                            .icon(BitmapDescriptorFactory.defaultMarker(if (!ocupado.isNullOrEmpty()) 120f else 0f)) // Cambia el color si quieres
                            .snippet(if(!ocupado.isNullOrEmpty()) ocupado[0].toString() else "" )

                    )
            }
            //**************** FIN PARKING SLOTS **********************

            //**************** PERMISOS *******************************
            val permisos = dataRaw?.getPermisosCache_DeHoy()
            val direciones = dataRaw?.getDomiciliosUbicacion()
            permisos?.forEach { permiso ->
                if (permiso.size < 12) return@forEach
                //Verificar si tengo latLon
                val calle = permiso[1].toString()
                val numero = permiso[2].toString()
                val direcLatLon = direciones?.filter { it[0].toString().uppercase() == calle.uppercase() && it[1].toString().uppercase() == numero.uppercase() }
                //Dibujar marker
                direcLatLon?.forEach { ubicacion ->
                    val lat = ubicacion[2].toString().toDoubleOrNull() ?: return@forEach
                    val lon = ubicacion[3].toString().toDoubleOrNull() ?: return@forEach
                    val stringTrue = arrayOf("1", "Si", "si", "SI", "x", "X")
                    val tipo =permiso[6].toString()
                    val titleText = if( stringTrue.contains(permiso[11]) ) "Permiso ${calle}:${numero}" else "Permiso DENEGADO"
                    val snipetText = if( stringTrue.contains(permiso[11]) ) "${tipo} - ${calle}:${numero} valido hasta: ${permiso[8]}  ${permiso[9]}  personas:${permiso[10]}" else "DENEGADO!!! debe pedir que se retire cualquier trabajador!!"
                    var rscImg: BitmapDescriptor
                    when (tipo.uppercase()){
                        "Trabajador(es)".uppercase() -> rscImg = getResizedBitmapDescriptor(this,R.drawable.permiso_trabajadores,100,100)
                        "Mudanza".uppercase() -> rscImg = getResizedBitmapDescriptor(this,R.drawable.permiso_mudanza,100,100)
                        "Muebles".uppercase() -> rscImg = getResizedBitmapDescriptor(this,R.drawable.permiso_muebles,100,100)
                        "Construccion".uppercase() -> rscImg = getResizedBitmapDescriptor(this,R.drawable.permiso_construccion,100,100)
                        "Servicios".uppercase() ->  rscImg = getResizedBitmapDescriptor(this,R.drawable.permiso_servicios,100,100)
                        else -> rscImg = getResizedBitmapDescriptor(this,R.drawable.permiso_otro,100,100)
                    }
                    gMap.addMarker(
                        MarkerOptions()
                            .position(LatLng(lat, lon))
                            .title(titleText)
                            .icon(rscImg)
                            .snippet(snipetText)

                    )
                }
            }
            //**************** FIN PERMISOS ***************************

            //**************** POR REVISAR ***************************
            val rscImgRevisar: BitmapDescriptor = getResizedBitmapDescriptor(this,R.drawable.exclamacion,100,100)
            porRevisarRecords.forEach { pRevisar ->
                val titleText = "${pRevisar.street}:${pRevisar.number} carro en:${pRevisar.parkingSlotKey}"
                val snipetText = "${pRevisar.time} - ${pRevisar.parkingSlotKey}"
                gMap.addMarker(
                    MarkerOptions()
                        .position(LatLng(pRevisar.latitude, pRevisar.longitude))
                        .title(titleText)
                        .icon(rscImgRevisar)
                        .snippet(snipetText)

                )
            }
            //**************** FIN POR REVISAR ***********************

            //**************** TAGS Rondinero *************************
            val checkPoints = mySettings?.getListCheckPoint("LIST_CHECKPOINT")!!.toMutableList()
            checkPoints.forEachIndexed { index,pointTag ->
                val strTag = (index + 1).toString()
                val bitmap = createMarkerTAGRondinero(this, strTag)
                gMap.addMarker(
                    MarkerOptions()
                        .flat(true)
                        .title(pointTag.identificador)
                        .icon(BitmapDescriptorFactory.fromBitmap(bitmap))
                        .position(LatLng(pointTag.latitud,pointTag.longitud) )
                )
            }
            //*********************************************************

            // LISTENER por cad click en los markers
            gMap.setOnMarkerClickListener { marker ->
                if (marker.snippet.isNullOrEmpty() ) {
                    //Solo si no tiene info puesta en el marker
                    val tagLugar = marker.title ?: ""
                    keySlotSeleccionado = tagLugar
                    preguntarYProcesarLugar(tagLugar)
                    true
                }
                false
            }
        }
    }
    private fun getResizedBitmapDescriptor(context: Context, resId: Int, width: Int, height: Int): BitmapDescriptor {
        // 1. Decodificar el recurso a un objeto Bitmap
        val imageBitmap = BitmapFactory.decodeResource(context.resources, resId)

        // 2. Redimensionar el bitmap (ancho y alto en píxeles)
        val resizedBitmap = Bitmap.createScaledBitmap(imageBitmap, width, height, false)

        // 3. Crear el BitmapDescriptor desde el bitmap redimensionado
        return BitmapDescriptorFactory.fromBitmap(resizedBitmap)
    }

    private fun preguntarYProcesarLugar(key: String) {
        if (photoUri == null) {
            Toast.makeText(this, "Debe tomar una FOTO", Toast.LENGTH_SHORT).show()
            captureImage()
        }else{
            //SI HAY FOTO, continuar con el proceso de pregunta
            val plate = plateInput.text.toString().uppercase().trim()
            val eventosRecientes = dataRaw?.getAutosEventos_6horas()
            val eventoPrevio = eventosRecientes?.find { it[4].toString() == key }
            if (eventoPrevio != null) {
                AlertDialog.Builder(this)
                    .setTitle("Ya existe un registro")
                    .setMessage("¿Desea eliminar el evento anterior y registrar uno nuevo en el lugar $key?")
                    .setPositiveButton("Sí") { _, _ -> procesarEventLugar(key, plate, true,eventoPrevio) }
                    .setNegativeButton("No", null)
                    .show()
            } else if (plate.isEmpty()) {
                AlertDialog.Builder(this)
                    .setTitle("Lugar Vacío")
                    .setMessage("¿Está seguro que el lugar $key está vacío?")
                    .setPositiveButton("Sí") { _, _ -> procesarEventLugar(key, "VACIO", false) }
                    .setNegativeButton("No") { _, _ ->
                        //Preguntar por las placas
                        val input = EditText(this)
                        input.hint = "Ingrese Placa"
                        input.layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        AlertDialog.Builder(this)
                            .setTitle("Escriba las placas")
                            .setView(input)
                            .setPositiveButton("Ok") { _, _ ->
                                var plate = input.text.toString()
                                var re = Regex("[^A-Za-z0-9 ]")
                                plate = re.replace(plate, "") //Eliminar carcteres no deseados
                                procesarEventLugar(key, plate, false)
                            }
                            .setNegativeButton("Cancelar") { _, _ ->
                                //Limpiar url y placas
                                cleanFrom()
                            }
                            .show()
                    }
                    .show()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Registrar evento")
                    .setMessage("¿Deseas agregar el evento a $key con placa $plate?")
                    .setPositiveButton("Sí") { _, _ -> procesarEventLugar(key, plate, false) }
                    .setNegativeButton("No", null)
                    .show()
            }
        }
    }

    private fun procesarEventLugar(key: String, plate: String, eliminarAnterior: Boolean, autoEventoEliminar: List<Any> = listOf<Any>() ) {
        // Si eliminarAnterior, elimina el evento previo...
        // ... aquí tu código para borrar evento anterior en Google Sheets ...
        if (eliminarAnterior && autoEventoEliminar.isNotEmpty()){
            dataRaw?.eliminarAutosEventoCache(autoEventoEliminar as List<String>)
        }

        //Como este preoceso entra al hacer click en mapa, debemos asegurarnos de actualizar la calle y numero de esta placa
        searchVehicle(plate)

        // Luego, toma/usa foto, y llama tu lógica normal de registro
        if (photoUri == null) {
            Toast.makeText(this, "Debe tomar una FOTO", Toast.LENGTH_SHORT).show()
            captureImage()
        } else {
            // Modifica saveNewEvent o crea saveNewEventConSlot para aceptar el key
            continueSaveNewEvent(plate, key)
        }
    }


    private fun updatePorRevisarButton() {
        if (!isActive) return
        porRevisarButton.text = "Por Revisar (${porRevisarRecords.size})"
        porRevisarButton.setBackgroundColor(if (porRevisarRecords.size > 0) Color.RED else Color.GRAY)
        porRevisarButton.tooltipText = porRevisarRecords.size.toString()
    }

    private fun cleanOldThumbnails() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val storageDir = getExternalFilesDir("photos")
                if (storageDir == null || !storageDir.exists()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@VehicleSearchActivity, "Photos directory not found", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000 // 30 days in milliseconds
                val deletedFiles = mutableListOf<String>()
                storageDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.lastModified() < thirtyDaysAgo) {
                        if (file.delete()) {
                            deletedFiles.add(file.absolutePath)
                            println("VehicleSearchActivity Deleted old thumbnail: ${file.absolutePath}")
                        } else {
                            println("VehicleSearchActivity Failed to delete thumbnail: ${file.absolutePath}")
                        }
                    }
                }

                // Optionally update AutosEventos sheet to clear localPhotoPath for deleted files
                if (deletedFiles.isNotEmpty()) {
//                    val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
////                    val response = sheetsService.spreadsheets().values()
////                        .get(yourEventsSpreadSheetID, "AutosEventos!A:E")
////                        .execute()
////                    val rows = response.getValues() ?: emptyList()
//                    val rows = dataRaw?.getAutosEventos()
//                    val updates = mutableListOf<ValueRange>()
//                    rows?.forEachIndexed { index, row ->
//                        if (row.size >= 4 && row[3] in deletedFiles) {
//                            val rowIndex = index + 1
//                            val update = ValueRange().setValues(listOf(listOf("", "", "", "", row[4])))
//                            updates.add(update.setRange("AutosEventos!A$rowIndex:E$rowIndex"))
//                        }
//                    }
//                    if (updates.isNotEmpty()) {
//                        updates.forEach { update ->
//                            sheetsService.spreadsheets().values()
//                                .update(yourEventsSpreadSheetID, update.range, update)
//                                .setValueInputOption("RAW")
//                                .execute()
//                        }
//                        println("VehicleSearchActivity Updated ${updates.size} rows in AutosEventos with cleared localPhotoPath")
//                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@VehicleSearchActivity,
                            "Cleaned ${deletedFiles.size} old thumbnails",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@VehicleSearchActivity,
                            "No thumbnails older than 30 days found",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    println("VehicleSearchActivity Error cleaning old thumbnails ${e.message}")
                    Toast.makeText(
                        this@VehicleSearchActivity,
                        "Error cleaning thumbnails: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun captureImage() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val photoFile: JavaFile? = createImageFile()
        if (photoFile != null) {
            photoUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", photoFile)
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
            if (intent.resolveActivity(packageManager) != null) {
                startActivityForResult(intent, REQUEST_IMAGE_CAPTURE)
            } else {
                AlertDialog.Builder(this)
                    .setTitle("No Camera Available")
                    .setMessage("Quieres seleccionar una foto de la galeria?")
                    .setPositiveButton("Yes") { _, _ ->
                        pickFromGallery()
                    }
                    .setNegativeButton("No") { _, _ -> }
                    .show()
            }
        } else {
            Toast.makeText(this, "Failed to create image file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun pickFromGallery() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            startGalleryIntent()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(permission), REQUEST_STORAGE_PERMISSION)
        }
    }
    private fun startGalleryIntent() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        if (intent.resolveActivity(packageManager) != null) {
            startActivityForResult(intent, REQUEST_IMAGE_PICK)
        } else {
            // Fallback to ACTION_PICK if ACTION_GET_CONTENT fails
            val fallbackIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            if (fallbackIntent.resolveActivity(packageManager) != null) {
                startActivityForResult(fallbackIntent, REQUEST_IMAGE_PICK)
            } else {
                Toast.makeText(this, "No gallery or file picker app available. Please install one.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun createImageFile(): JavaFile? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
            val storageDir = getExternalFilesDir("photos")
            JavaFile.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
        } catch (e: Exception) {
            println("LARANGEL Error createImageFile: ${e}")
            e.printStackTrace()
            Toast.makeText(this@VehicleSearchActivity, "Error createImageFile: ${e.message}", Toast.LENGTH_SHORT).show()
            null
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                REQUEST_IMAGE_CAPTURE -> {
                    photoUri?.let { uri ->
                        try {
                            val image = InputImage.fromFilePath(this, uri)
                            processImage(image)
                            photoThumbnail.setImageURI(uri)
                            photoThumbnail.visibility = android.view.View.VISIBLE
                        } catch (e: Exception) {
                            println("LARANGEL Error onActivityResult REQUEST_IMAGE_CAPTURE: ${e}")
                            e.printStackTrace()
                            Toast.makeText(this, "Failed to process image: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    } ?: run {
                        Toast.makeText(this, "Photo URI is null", Toast.LENGTH_SHORT).show()
                    }
                }
                REQUEST_IMAGE_PICK -> {
                    photoUri = data?.data
                    photoUri?.let { uri ->
                        try {
                            val image = InputImage.fromFilePath(this, uri)
                            processImage(image)
                            photoThumbnail.setImageURI(uri)
                            photoThumbnail.visibility = android.view.View.VISIBLE
                        } catch (e: Exception) {
                            println("LARANGEL Error onActivityResult REQUEST_IMAGE_PICK: ${e}")
                            e.printStackTrace()
                            Toast.makeText(this, "Failed to process image: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    } ?: run {
                        Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }else {
            Toast.makeText(this, "Image selection cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processImage(image: InputImage) {
        //val plate = plateInput.text.toString().trim()
        //if (plate.isEmpty()) {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    var plate = visionText.textBlocks.joinToString(" ") { it.text }.trim()
                    var re = Regex("[^A-Za-z0-9 ]")
                    plate = re.replace(plate, "") //Eliminar carcteres no deseados
                    re = Regex("([A-Z]{3}[0-9]{3,4}[A-Z]?|[0-9]{2}[A-Z][0-9]{3}|[0-9]{3}[A-Z]{3}|[A-Z]{2}[0-9]{4,5}[A-Z]?|[A-Z][0-9]{4}|[A-Z][0-9]{2}[A-Z]{2,3}|[A-Z]{3}[0-9][A-Z]|[A-Z]{5}[0-9]{2})")
                    val matchRegult = re.find(plate) //Match Placa
                    plate = if (matchRegult != null) {
                        matchRegult.value
                    } else {
                        ""
                    }

                    if (plate.length > 3) {
                        plateInput.setText(plate)
                        //searchVehicle(plate)
                    }else{
                        Toast.makeText(this, "No PLACA en la imagen", Toast.LENGTH_SHORT).show()
                    }
                    if (! keySlotSeleccionado.isNullOrEmpty()){
                        //Se esta procesando un key seleccionado, volver a preguntar para procesar
                        preguntarYProcesarLugar(keySlotSeleccionado.toString())
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "OCR failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        //}
    }

    fun oneCharDifference(a: String, b: String): Boolean {
        // Devuelve true si solo difiere en una letra/número
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) {
            if (a[i] != b[i]) diff++
            if (diff > 1) return false
        }
        return diff == 1
    }

    fun cleanFrom(){
        photoThumbnail.visibility = View.GONE // Reset thumbnail
        photoUri = null // Reset photoUri
        resultText.text = ""
        plateInput.setText("")
        keySlotSeleccionado = null
    }

    private fun searchVehicle(_plate: String) {
        waitingOn()
        val plate=_plate.filter { it.isLetterOrDigit() }.uppercase()
        vehicleStreet = null
        vehicleNumber = null
        vehicleSource = null
        resultText.text = "" //Clean the text

        stopSearchVehicle = true

        //lifecycleScope.launch(Dispatchers.IO) {
            try {
                //val vehicles = getCachedVehiclesData()
                var match: List<Any>? = null
                var similarMatches: MutableList<List<Any>> = mutableListOf()

                //###### PUEDE SER TAG #########
                if (plate.toIntOrNull() != null){
                    val tags = dataRaw?.getTagsCache()
                    stopSearchVehicle = false
                    tags?.forEach { tag->
                        if (stopSearchVehicle) return
                        if (tag.size >= 3 && tag[0].toString().equals(plate, ignoreCase = true)) {
                            match = tag
                            return@forEach
                        }
                        if (tag.size >= 3 && tag[0].toString().startsWith(plate, ignoreCase = true)) {
                            similarMatches.add(tag)
                        }
                        if (tag.size >= 8 && plate.startsWith(tag[0].toString())){
                            similarMatches.add(tag)
                        }
                        if (similarMatches.size >= 20)
                            return@forEach
                    }
                }
                //########## ES PLACA ############
                else {
                    // 1. Buscar coincidencia exacta
                    val vehicles = dataRaw?.getCachedVehiclesData()
                    if (vehicles?.isNotEmpty() == true) {
                        stopSearchVehicle = false
                        for (row in vehicles) {
                            if (stopSearchVehicle) return
                            if (row.isEmpty() || row[0].toString().isEmpty())
                                continue

                            val rplate = row[0].toString().filter { it.isLetterOrDigit() }.uppercase()
                            if (rplate.isNotEmpty()) {
                                if (row.size >= 3 && rplate == plate) {
                                    match = row
                                    break
                                }
                                if (row.size >= 3 && oneCharDifference(rplate, plate)) {
                                    similarMatches.add(row)
                                }
                                if (row.size >= 3 && rplate.startsWith(plate)) {
                                    similarMatches.add(row)
                                }
                                if (similarMatches.size >= 20)
                                    break
                            }
                        }
                    }
                }

                //withContext(Dispatchers.Main) {
                    waitingOff()
                    if (match != null) {
                        vehicleStreet = match!![1].toString()
                        vehicleNumber = match!![2].toString()
                        resultText.append("\nPlaca: $plate\nCalle: $vehicleStreet : $vehicleNumber")
                    } else if (similarMatches.isNotEmpty()) {
                        resultText.append("\nPlaca no encontrada, pero similar a:\n")
                        similarMatches.forEach { sm ->
                            resultText.append("Placa: ${sm[0]} Calle: ${sm[1]} : ${sm[2]}\n")
                        }
                    } else {
                        resultText.append("\nNo se encontro la placa en ningun registro: $plate")
                    }
                    showEventsPlate(plate)
                //}
            } catch (e: Exception) {
               // withContext(Dispatchers.Main) {
                    waitingOff()
                    resultText.append("\nError searching plate: ${e.message}")
                    showEventsPlate(plate)
               // }
            }
        //}
    }

    private fun refreshContadorEventos(){
        waitingOn()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    //Solo ULTIMAS 6 HORAS
                    val plateEvents = dataRaw?.getAutosEventos_6horas()
                    val parkSlots = dataRaw?.getParkingSlots()
                    val checkPoints = mySettings?.getListCheckPoint("LIST_CHECKPOINT")!!.toMutableList()
                    val totalCheckPoints = mySettings?.getInt("rondin_num_tags",0)!!


                    //*** Parking Slots Progress
                    val pBarParkingSlots: ProgressBar = findViewById<ProgressBar>(R.id.progressBar_ParkingSlots)
                    val tPBarParkingSlots: TextView = findViewById<TextView>(R.id.txtPBar_ParkingSlots)
                    pBarParkingSlots.max = parkSlots?.size ?: 0
                    pBarParkingSlots.progress = plateEvents?.size ?: 0
                    tPBarParkingSlots.text = "Visitas: ${(plateEvents?.size ?: 0)}/${(parkSlots?.size ?: 0)}"

                    //*** CheckPoints de rondinero
                    val pBarCheckPoints: ProgressBar = findViewById<ProgressBar>(R.id.progressBar_CheckPoints)
                    val tPBarCheckPoints: TextView = findViewById<TextView>(R.id.txtPBar_CheckPoints)
                    pBarCheckPoints.max = totalCheckPoints
                    pBarCheckPoints.progress = checkPoints?.size ?: 0
                    tPBarCheckPoints.text = "TAGS ${(checkPoints?.size ?: 0)}/${totalCheckPoints}"


//                    val tFaltantes: TextView = findViewById<TextView>(R.id.vehicleText_PSFaltantes)
//                    val tCompletados: TextView = findViewById<TextView>(R.id.vehicleText_PSCompletos)
//
//                    tFaltantes.text = (parkSlots?.size?.minus(plateEvents?.size ?: 0)).toString()
//                    tCompletados.text= plateEvents?.size.toString()

                    waitingOff()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    waitingOff()
                    println("LARANGEL exception refreshContadorEventos error:${e}")
                    e.printStackTrace()
                    Toast.makeText(
                        this@VehicleSearchActivity,
                        "Error refreshContadorEventos: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun showEventsPlate(plate: String) {
        waitingOn()
        events.clear()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val rows = dataRaw?.getAutosEventos()
                withContext(Dispatchers.Main) {
                    val plateEvents =
                        rows?.filter { it.size >= 5 && it[0].toString().equals(plate, ignoreCase = true) }
                            ?.reversed()
                    plateEvents?.forEach { row ->
                        val date = LocalDate.parse(row[1].toString())
                        val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        val time = LocalDateTime.parse(row[2].toString(), timeFormatter)
                        val localPhotoPath = row[3].toString()
                        val parkingSlotKey = row[4].toString()
                        events.add(
                            EventModal(
                                plate = row[0].toString(),
                                date = date,
                                time = time,
                                localPhotoPath = localPhotoPath,
                                parkingSlotKey = parkingSlotKey
                            )
                        )
                    }
                    waitingOff()
                    eventsRecyclerView.adapter?.notifyDataSetChanged()
                    resultText.append("\nTotal Events: ${events.size}")
                    if (plateEvents != null)
                        checkForFine(plateEvents)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    waitingOff()
                    println("LARANGEL exception showEventsPlate plate:${plate} error:${e}")
                    e.printStackTrace()
                    Toast.makeText(this@VehicleSearchActivity, "Error loading events: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun checkForFine(events: List<List<Any>>) {
        if (events.isEmpty()) return
        waitingOn()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd")
        val eventDates = events.mapNotNull { row ->
            try {
                dateFormat.parse(row[1].toString())
            } catch (e: Exception) {
                null
            }
        }.sorted()
        var consecutiveDays = 1
        for (i in 1 until eventDates.size) {
            val diff = (eventDates[i].time - eventDates[i - 1].time) / (1000 * 60 * 60 * 24)
            if (diff == 1L) {
                consecutiveDays++
                if (consecutiveDays >= 10) {
                    waitingOff()
                    AlertDialog.Builder(this)
                        .setTitle("Fine Alert")
                        .setMessage("Automovil acredor de multa, por exceder mas de 10 dias consecutivos en visita")
                        .setPositiveButton("OK") { _, _ ->
                            // Send to WhatsApp and save to MultasGeneradas
                            handleFineConfirmation(events, consecutiveDays)
                        }
                        .show()
                    break
                }
            } else {
                waitingOff()
                consecutiveDays = 1
            }
        }
        waitingOff()
    }

    private fun handleFineConfirmation(events: List<List<Any>>, consecutiveDays: Int) {
        waitingOn()
        //val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
        val plate = events[0][0].toString()
        val now = LocalDateTime.now()
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val formattedDate = now.format(dateFormatter)
        val formattedTime = now.format(timeFormatter)

        // Collect the last 10 consecutive events' details
        val streakEvents = events.takeLast(10) // Assuming the last 10 are the consecutive ones
        val concatenatedSlots = streakEvents.joinToString(", ") { row ->
            val eventDate = row[1].toString()
            val slotKey = row[4].toString()
            "$slotKey ($eventDate)"
        }

        // Save to MultasGeneradas
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val values = listOf(
                        formattedTime,
                        vehicleStreet,
                        vehicleNumber,
                        plate,
                        concatenatedSlots
                    )
                //Agregar MULTA
                if (dataRaw?.addMulta(values as List<String>)== true){
                    withContext(Dispatchers.Main) {
                        waitingOff()
                        Toast.makeText(this@VehicleSearchActivity, "Nueva Multas guardada", Toast.LENGTH_SHORT).show()
                    }
                }else{
                    withContext(Dispatchers.Main) {
                        waitingOff()
                        Toast.makeText(
                            this@VehicleSearchActivity,
                            "NO Internet: ERROR salvando MULTA\nAuto reintento en 5 minutos",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
//                sheetsService.spreadsheets().values()
//                    .append(yourEventsSpreadSheetID, "MultasGeneradas!A:E", body)
//                    .setValueInputOption("RAW")
//                    .execute()
//                withContext(Dispatchers.Main) {
//                    waitingOff()
//                    Toast.makeText(this@VehicleSearchActivity, "Registro guardado en MultasGeneradas", Toast.LENGTH_SHORT).show()
//                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    waitingOff()
                    Toast.makeText(this@VehicleSearchActivity, "Error saving to MultasGeneradas: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Prepare WhatsApp share
        val text = "Automovil con placas $plate en calle $vehicleStreet numero $vehicleNumber cumple 10 dias consecutivos en visita y amerita multa. Detalles de eventos: $concatenatedSlots"

        val intent = Intent(Intent.ACTION_SEND_MULTIPLE)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TEXT, text)
        intent.setPackage("com.whatsapp")

        val imageUris = ArrayList<Uri>()
        streakEvents.forEach { row ->
            val localPhotoPath = row[3].toString()
            if (localPhotoPath.isNotEmpty()) {
                val file = JavaFile(localPhotoPath)
                if (file.exists()) {
                    val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
                    imageUris.add(uri)
                }
            }
        }

        if (imageUris.isNotEmpty()) {
            intent.type = "*/*"
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, imageUris)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveNewEvent() {
        var plate = plateInput.text.toString().uppercase().trim()
        if (plate.isEmpty()) {
            showEmptyPlateDialog { finalPlate ->
                if (finalPlate.isNullOrEmpty()) {
                    Toast.makeText(this, "Ingrese la Placa primero", Toast.LENGTH_SHORT).show()
                    return@showEmptyPlateDialog
                }
                continueSaveNewEvent(finalPlate)
            }
        } else {
            continueSaveNewEvent(plate)
        }
    }
    private fun showEmptyPlateDialog(callback: (String?) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("No hay placa")
            .setMessage("El lugar está VACÍO sin carro?, ¿es correcto?")
            .setPositiveButton("Sí") { dialog, which ->
                callback("VACIO")
            }
            .setNegativeButton("No") { dialog, which ->
                Toast.makeText(this, "Ingrese la PLACA por favor", Toast.LENGTH_SHORT).show()
                callback(null)
            }
            .setOnCancelListener {
                callback(null)
            }
            .show()
    }

    // Esta función contiene el salvar con el valor de placa
    private fun continueSaveNewEvent(plate: String, KeySlot: String = "") {
        if (photoUri == null) {
            Toast.makeText(this, "Debe tomar una FOTO", Toast.LENGTH_SHORT).show()
            captureImage()
        }

        if (KeySlot != "")
            saveEventPlateSlot(plate,KeySlot)
        else {
            //Usar la ultima ubicacion para buscar
            waitingOn()
            fetchClosestParkingSlots(lastGpsLocation!!.latitude,lastGpsLocation!!.longitude) { closestSlots ->
                if (closestSlots.isEmpty()) {
                    Toast.makeText(this,"No se encuentra lugares de estacionamiento cercanos",Toast.LENGTH_SHORT).show()
                    waitingOff()
                    return@fetchClosestParkingSlots
                }
                val slotDescriptions = closestSlots.map {
                    "${it.key} (${
                        String.format(
                            "%.2f",
                            it.distance
                        )
                    } m)"
                }.toTypedArray()
                waitingOff()
                AlertDialog.Builder(this)
                    .setTitle("Selecciona LUGAR de Estacionamiento")
                    .setItems(slotDescriptions) { _, which ->
                        val selectedKey = closestSlots[which].key
                        keySlotSeleccionado = selectedKey
                        //Salvar Evento en cajon de estacionamiento
                        if (!saveEventPlateSlot(plate, selectedKey))
                            return@setItems
                    }
                    .setNegativeButton("Cancel") { _, _ -> }
                    .show()
            }



//            if (ActivityCompat.checkSelfPermission(
//                    this,
//                    Manifest.permission.ACCESS_FINE_LOCATION
//                ) == PackageManager.PERMISSION_GRANTED
//            ) {
//                fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
//                    if (location != null) {
//                        waitingOn()
//
//                    } else {
//                        Toast.makeText(this, "Unable to get location", Toast.LENGTH_SHORT).show()
//                    }
//                }
//            } else {
//                ActivityCompat.requestPermissions(
//                    this,
//                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
//                    REQUEST_LOCATION_PERMISSION
//                )
//            }
        }
    }

    private fun saveEventPlateSlot(plate: String,keySlot: String): Boolean{
        val localPhotoPath = savePhotoLocally(photoUri)
        if (localPhotoPath == null) {
            Toast.makeText(this, "Failed to save photo locally", Toast.LENGTH_SHORT).show()
            return false
        }
        val date = LocalDate.now()
        val time = LocalDateTime.now()
        val event = EventModal(plate, date, time, localPhotoPath, keySlot)
        saveEventToSheet(event)
        updateMapMarkers()
        showEventsPlate(plate)
        if (keySlot in listOf("LugarProhibido", "BloqueoPeatonal", "ObstruyeCochera")) {
            handleSpecialKey(keySlot, plate, localPhotoPath)
        }
        return true
    }

    private fun handleSpecialKey(selectedKey: String, plate: String, localPhotoPath: String) {
        val now = LocalDateTime.now()
        val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val formattedTime = now.format(timeFormatter)
        val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
        if (selectedKey == "ObstruyeCochera") {
            AlertDialog.Builder(this)
                .setTitle("Multa Directa")
                .setMessage("Es multa directa por Obstruir Cochera")
                .setPositiveButton("OK") { _, _ ->
                    saveToMultasGeneradas(plate, "$selectedKey ($formattedTime)")
                    shareViaWhatsApp(plate, selectedKey, localPhotoPath)
                }
                .show()
        } else if (selectedKey in listOf("LugarProhibido", "BloqueoPeatonal")) {
            waitingOn()
            lifecycleScope.launch(Dispatchers.IO) {
                try {

                    //var rows = dataRaw?.getDomicilioWarnings()
                    //val newCount = currentCount + 1
                    val values = listOf(
                            vehicleStreet,
                            vehicleNumber,
                            0,
                            selectedKey
                        )

                    val countWarnings = dataRaw?.updateDomicilioWarning(values as List<String>)

                    withContext(Dispatchers.Main) {
                        waitingOff()
                        if (countWarnings!! >= 3) {
                            AlertDialog.Builder(this@VehicleSearchActivity)
                                .setTitle("Acredor a Multa")
                                .setMessage("El domicilio es acredor a una multa (3er aviso)")
                                .setPositiveButton("OK") { _, _ ->
                                    saveToMultasGeneradas( plate, "$selectedKey ($formattedTime)")
                                    shareViaWhatsApp(plate, selectedKey, localPhotoPath)
                                }
                                .show()
                        } else {
                            Toast.makeText(this@VehicleSearchActivity, "Avisos acumulados a $vehicleStreet:$vehicleNumber, count: $countWarnings", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        waitingOff()
                        Toast.makeText(this@VehicleSearchActivity, "Error handling warning: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun saveToMultasGeneradas(plate: String, concatenatedSlots: String) {
        //val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
        val now = LocalDateTime.now()
        val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val formattedTime = now.format(timeFormatter)
        waitingOn()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val values = listOf(
                        formattedTime,
                        vehicleStreet,
                        vehicleNumber,
                        plate,
                        concatenatedSlots
                    )

                //val body = ValueRange().setValues(values)
//                sheetsService.spreadsheets().values()
//                    .append(yourEventsSpreadSheetID, "MultasGeneradas!A:E", body)
//                    .setValueInputOption("RAW")
//                    .execute()
                //########## GUARDAR NUEVA MULTA ######################
                if (dataRaw?.updateMulta(values  as List<String>) == false)
                    withContext(Dispatchers.Main) {
                        waitingOff()
                        Toast.makeText(
                            this@VehicleSearchActivity,
                            "NO Internet: ERROR salvando MULTA\nAuto reintento en 5 minutos",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                else
                    withContext(Dispatchers.Main) {
                        waitingOff()
                        Toast.makeText(this@VehicleSearchActivity, "Nueva Multas guardada", Toast.LENGTH_SHORT).show()
                    }
                //#####################################################

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    waitingOff()
                    Toast.makeText(this@VehicleSearchActivity, "Error saving to MultasGeneradas: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun shareViaWhatsApp(plate: String, selectedKey: String, localPhotoPath: String) {
        val now = LocalDateTime.now()
        val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val formattedTime = now.format(timeFormatter)
        val text = "Automovil con placas $plate en calle $vehicleStreet numero $vehicleNumber amerita multa por $selectedKey. Detalle del evento: $formattedTime"

        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TEXT, text)
        intent.setPackage("com.whatsapp")

        if (localPhotoPath.isNotEmpty()) {
            val file = JavaFile(localPhotoPath)
            if (file.exists()) {
                val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
                intent.type = "image/jpeg"
                intent.putExtra(Intent.EXTRA_STREAM, uri)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchClosestParkingSlots(latitude: Double, longitude: Double, callback: (List<ParkingSlot>) -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {

                //val parkSlots = getParkingSlots()
                val parkSlots = dataRaw?.getParkingSlots()
                val plateEvents6Horas = dataRaw?.getAutosEventos_6horas()
                val slots = mutableListOf<ParkingSlot>()
                if (parkSlots != null) {
                    for (row in parkSlots) {
                        val lat = row[0].toString().toDoubleOrNull() ?: continue
                        val lon = row[1].toString().toDoubleOrNull() ?: continue
                        val key = row[2].toString()
                        val dist = calculateDistance(latitude, longitude, lat, lon)
                        slots.add(ParkingSlot(lat, lon, key, dist))
                    }
                }

                //LOS 8 MAS CERCANOS filtrando los ya capturados
                val closest = slots.sortedBy { it.distance }.take(8).filter { slot ->
                    plateEvents6Horas?.none { row ->
                        row[4].toString() == slot.key
                    } ?: true
                }
                val additionalSlots = listOf(
                    ParkingSlot(latitude, longitude, "LugarProhibido", 0.0),
                    ParkingSlot(latitude, longitude, "ObstruyeCochera", 0.0),
                    ParkingSlot(latitude, longitude, "BloqueoPeatonal", 0.0)
                )
                val finalSlots = closest + additionalSlots
                withContext(Dispatchers.Main) {
                    callback(finalSlots)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@VehicleSearchActivity, "Error fetching parking slots: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371e3 // Earth's radius in meters
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val deltaLat = Math.toRadians(lat2 - lat1)
        val deltaLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2)
        val c = 2 * Math.atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    private fun savePhotoLocally(uri: Uri?): String? {
        if (uri == null) return null

        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            //val aspectRatio = bitmap.height.toFloat() / bitmap.width.toFloat()
            val targetWidth = 600
            val factorscale = targetWidth.toFloat()/bitmap.width.toFloat()
            val targetHeight = (bitmap.height.toFloat() * factorscale).toInt()
            val reducedBitmap = bitmap.scale(targetWidth,targetHeight) // Reduced size
            val outputStream = ByteArrayOutputStream()
            reducedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            val byteArray = outputStream.toByteArray()

            // Save to local storage
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
            val storageDir = getExternalFilesDir("photos")
            val localFile = File(storageDir, "thumbnail_${timeStamp}.jpg")
            FileOutputStream(localFile).use { fos ->
                fos.write(byteArray)
                fos.flush()
            }
            return localFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return  null
        }
    }

    private fun saveEventToSheet(event: EventModal) {
        //val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
        if (event.plate == "VACIO"){
            //No ha domicilio asociado a lugar vacio
            vehicleStreet=null
            vehicleNumber=null
        }

        // Format LocalDate and LocalDateTime to strings
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val formattedDate = event.date.format(dateFormatter)
        val formattedTime = event.time.format(timeFormatter)

        // Create the values list with formatted strings for AutosEventos
        val valuesEventos = listOf(
                event.plate,
                formattedDate,
                formattedTime,
                event.localPhotoPath,
                event.parkingSlotKey
        )
        //val bodyEventos = ValueRange().setValues(listOf(valuesEventos))

        waitingOn()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                //########## GUARDAR NUEVO EVENTO CACHE ###############
                if (dataRaw?._addAutosEventCache(valuesEventos) == false)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@VehicleSearchActivity,
                            "NO Internet: ERROR salvando CAJON DE VISITAS\nAuto reintento en 5 minutos",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                else
                    withContext(Dispatchers.Main) {
                        waitingOff()
                        events.add(0,event)
                        eventsRecyclerView.adapter?.notifyDataSetChanged()
                        cleanFrom()
                        updatePorRevisarButton()
                        refreshContadorEventos()
                        Toast.makeText(this@VehicleSearchActivity, "Event saved", Toast.LENGTH_SHORT).show()
                    }
                refreshContadorEventos()
                //######################################################

                // If vehicle street and number available, save or update in PorRevisar
                if (vehicleStreet != null && vehicleNumber != null) {
                    // Fetch lat and lon from DomicilioUbicacion
                    val domicilioRows =  dataRaw?.getDomiciliosSimilares(vehicleStreet!!,
                        vehicleNumber!!
                    )  //domicilioResponse.getValues() ?: emptyList()
                    var lat: Double? = null
                    var lon: Double? = null
                    if (domicilioRows?.size == 1) {
                        lat = domicilioRows[0][2].toString().toDoubleOrNull()
                        lon = domicilioRows[0][3].toString().toDoubleOrNull()
                    }
                    if (lat == null || lon == null) {
                        // Skip if not found
                        withContext(Dispatchers.Main) {
                            waitingOff()
                            Toast.makeText(this@VehicleSearchActivity, "Ubicación no encontrada para ${vehicleStreet} ${vehicleNumber}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    else {
                        //Guardar PorRevisar registro
                        val valuesPorRevisar = listOf(
                                vehicleStreet,
                                vehicleNumber,
                                formattedTime,
                                event.parkingSlotKey,
                                "",
                                lat.toString(),
                                lon.toString()
                            )
                        //########## GUARDAR POR REVISAR CACHE ###############
                        if (dataRaw?._addPorRevisarCache(valuesPorRevisar as List<String>) == false)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    this@VehicleSearchActivity,
                                    "NO Internet: ERROR salvando POR REVISAR\nAuto reintento en 5 minutos",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        //######################################################

                        // Add to memory
                        val newRecord = PorRevisarRecord(
                            vehicleStreet!!,
                            vehicleNumber!!,
                            LocalDateTime.parse(formattedTime, timeFormatter),
                            event.parkingSlotKey,
                            "",
                            lat,
                            lon
                        )
                        porRevisarRecords.add(newRecord)

                    }
                }


            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    waitingOff()
                    Toast.makeText(this@VehicleSearchActivity, "Failed to save event: ${e.message}", Toast.LENGTH_SHORT).show()
                    println("LARANGEL exception saveEventToSheet: ${e}")
                    e.printStackTrace()
                }
            }
        }
    }

    private fun loadPorRevisar(forceLoad: Boolean = false) {
        if (!isActive) return
        //val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val rows = dataRaw?.getPorRevisar_20horas(forceLoad)//response.getValues() ?: emptyList()
                withContext(Dispatchers.Main) {
                    porRevisarRecords.clear()
                    val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    rows?.forEach { row ->
                        if (row.size >= 7) {
                            val street = row[0].toString()
                            val number = row[1].toString()
                            val timeStr = row[2].toString()
                            val slot = row[3].toString()
                            val validation = row[4].toString()
                            val lat = row[5].toString().toDoubleOrNull() ?: return@forEach
                            val lon = row[6].toString().toDoubleOrNull() ?: return@forEach
                            val time = LocalDateTime.parse(timeStr, timeFormatter)
                            porRevisarRecords.add(PorRevisarRecord(street, number, time, slot, validation, lat, lon))
                        }
                    }
                    updatePorRevisarButton()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@VehicleSearchActivity, "Error loading PorRevisar: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onResume() {
        super.onResume()
        inicializaRondinSwitch()
        //Recalcular y redibujar mapa
        updateMapMarkers()
        refreshContadorEventos()
        loadPorRevisar()
        set_start_gps()
        //handler.postDelayed(checkRunnable, 2000)
    }

    override fun onPause() {
        super.onPause()
        stopNFC()
        set_pause_gps()
        //handler.removeCallbacks(checkRunnable)
    }

    private fun checkPorRevisarLocations() {

        porRevisarRecords.forEach { row ->
            val distance = calculateDistance(lastGpsLocation!!.latitude, lastGpsLocation!!.longitude, row.latitude, row.longitude)
            if (distance < 10.0) {
                //Ya se notifico? saltarlo
                if (porRevisarNotificado.contains("${row.street}:${row.number}")) return@forEach

                val intent = Intent(this, PorRevisarListActivity::class.java).apply {
                    putExtra("street", row.street)
                    putExtra("number", row.number)
                    putExtra("notification", "Domicilio por verificar en ${row.street} ${row.number}")
                }
                set_pause_gps()
                porRevisarNotificado.add("${row.street}:${row.number}")
                startActivity(intent)
                return@forEach
            }
        }

    }

    fun set_pause_gps(){
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        if (locationManager != null)
            locationManager.removeUpdates(locationListener as LocationListener)
    }
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun set_start_gps(){
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            //GPS
            val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
            locationListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    // Actualizar la ubicación del usuario
                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    lastGpsLocation = currentLatLng

                    // SOLO actualizamos si NO estamos en modo manual (moviendo el mapa)
                    if (!isManualMode) {
                        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 20f))
                    }

                    //Por REVISAR
                    checkPorRevisarLocations()
                }

                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
                    // ...
                    Toast.makeText(this@VehicleSearchActivity, "GPS status:${status}", Toast.LENGTH_LONG).show()
                }

                override fun onProviderEnabled(provider: String) {
                    //..
                    Toast.makeText(this@VehicleSearchActivity, "Buscando GPS ${provider}", Toast.LENGTH_LONG).show()
                }

                override fun onProviderDisabled(provider: String) {
                    Toast.makeText(this@VehicleSearchActivity, "GPS Deshabilitado ${provider}", Toast.LENGTH_LONG).show()
                }
            }
            // Request updates for GPS provider
            if (locationManager.allProviders.contains(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000L, 1f, locationListener as LocationListener)
            }
            else {
                // El dispositivo no tiene GPS físico, intenta con el de red o avisa al usuario
                if (locationManager.allProviders.contains(LocationManager.NETWORK_PROVIDER)) {
                    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 1f, locationListener as LocationListener)
                } else {

                    val builder = AlertDialog.Builder(this@VehicleSearchActivity)
                    builder.setMessage("Este dispositivo no tiene GPS.")
                    builder.setPositiveButton("Enterado") { _, _ ->
                    }
                    val myDialog = builder.create()
                    myDialog.setCanceledOnTouchOutside(false)
                    myDialog.show()
                }
            }

        }

    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CAMERA_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    captureImage()
                } else {
                    Toast.makeText(this, "Camara permiso denegado", Toast.LENGTH_SHORT).show()
                }
            }
            REQUEST_LOCATION_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    saveNewEvent()
                } else {
                    Toast.makeText(this, "GPS permiso denegado", Toast.LENGTH_SHORT).show()
                }
            }
            REQUEST_STORAGE_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    pickFromGallery()
                } else {
                    println("Storage permission denied")
                    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Manifest.permission.READ_MEDIA_IMAGES
                    } else {
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    }
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                        println("Storage permission permanently denied")
                        AlertDialog.Builder(this)
                            .setTitle("Permission Required")
                            .setMessage("Storage permission is needed to select photos from the gallery. Please enable it in Settings.")
                            .setPositiveButton("Go to Settings") { _, _ ->
                                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                intent.data = Uri.parse("package:$packageName")
                                startActivity(intent)
                            }
                            .setNegativeButton("Cancel") { _, _ -> }
                            .show()
                    } else {
                        Toast.makeText(this, "Storage permission denied. Cannot access gallery.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    //##################### RONDINERO ##################
    private fun inicializaRondinSwitch(){
        val rswitch = findViewById<Switch>(R.id.swRonding)
        val checkPoints = mySettings?.getListCheckPoint("LIST_CHECKPOINT")!!.toMutableList()
        try { //Limpiar cualquier listener existente
            rswitch.setOnCheckedChangeListener(null)
            //Hay algun rondin iniciado?
            rswitch.isChecked = checkPoints.size > 0

            setListenerRondinSwitch(rswitch)
            //Ocultar/Mostrar elementos
            val pBarCheckPoints: ProgressBar = findViewById<ProgressBar>(R.id.progressBar_CheckPoints)
            val tPBarCheckPoints: TextView = findViewById<TextView>(R.id.txtPBar_CheckPoints)
            if (rswitch.isChecked){
                pBarCheckPoints.visibility = View.VISIBLE
                tPBarCheckPoints.visibility = View.VISIBLE
                InitNFC()
            }else{
                pBarCheckPoints.visibility = View.GONE
                tPBarCheckPoints.visibility = View.GONE
            }

        }catch (e: Exception){
            Toast.makeText(this, "Error al incializar el Rondin: ${e.message}", Toast.LENGTH_SHORT).show()
            val pBarCheckPoints: ProgressBar = findViewById<ProgressBar>(R.id.progressBar_CheckPoints)
            val tPBarCheckPoints: TextView = findViewById<TextView>(R.id.txtPBar_CheckPoints)
            pBarCheckPoints.visibility = View.GONE
            tPBarCheckPoints.visibility = View.GONE
        }

    }
    private fun setListenerRondinSwitch(rswitch: Switch) {
        rswitch.setOnCheckedChangeListener { buttonView, isChecked ->
            // 1. Obtener valor: isChecked ya nos da el nuevo estado
            if (!isChecked) {
                // 2. Validación: Si el usuario intenta apagarlo preguntar si desea realmete finalizar
                msgDeseaFinalizarelRondin(buttonView)
            } else {
                // Lógica cuando se enciende normalmente
                msgDeseaIniciarRondin(buttonView)
            }
        }
    }
    private fun msgDeseaFinalizarelRondin(rswitch: CompoundButton){
        AlertDialog.Builder(this)
            .setTitle("¿Finalizar rondin?")
            .setMessage("¿Estás seguro de que deseas FINALIZAR el RONDIN?")
            .setPositiveButton("-->Sí, fin rondin") { _, _ ->
                // El switch ya cambió a 'false' (apagado), no hacemos nada más
                //Toast.makeText(this, "Servicio Desactivado", Toast.LENGTH_SHORT).show()
                //Aqui logica para enviar los datos
                finalizarRondin()
            }
            .setNegativeButton("No") { dialog, _ ->
                // 3. Cancelar acción: Revertimos el estado a 'true'
                // Quitamos el listener antes de cambiar el estado para no disparar el diálogo otra vez
                rswitch.setOnCheckedChangeListener(null)
                rswitch.isChecked = true
                // Reasignamos el listener después del cambio programático
                setListenerRondinSwitch(rswitch as Switch)
                dialog.dismiss()
            }
            .setCancelable(false) // Evita que cierren el mensaje tocando fuera
            .show()
    }
    private fun msgDeseaIniciarRondin(rswitch: CompoundButton){
        AlertDialog.Builder(this)
            .setTitle("¿Iniciar rondin?")
            .setMessage("¿Deseas INCIAR EL RODIN?")
            .setPositiveButton("Sí, iniciar") { _, _ ->
                Toast.makeText(this, "Iniciando Rondin...", Toast.LENGTH_SHORT).show()
                //Aqui logica para activar el NFC
                iniciarRondin()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                // 3. Cancelar acción: Revertimos el estado a 'false'
                // Quitamos el listener antes de cambiar el estado para no disparar el diálogo otra vez
                rswitch.setOnCheckedChangeListener(null)
                rswitch.isChecked = false
                // Reasignamos el listener después del cambio programático
                setListenerRondinSwitch(rswitch as Switch)
                dialog.dismiss()
            }
            .setCancelable(false) // Evita que cierren el mensaje tocando fuera
            .show()
    }
    private fun iniciarRondin(){
        //Mostrar barProgress
        val pBarCheckPoints: ProgressBar = findViewById<ProgressBar>(R.id.progressBar_CheckPoints)
        val tPBarCheckPoints: TextView = findViewById<TextView>(R.id.txtPBar_CheckPoints)
        pBarCheckPoints.visibility = View.VISIBLE
        tPBarCheckPoints.visibility = View.VISIBLE
        //Start NFC
        InitNFC()
    }
    private fun finalizarRondin(){
        try {
            //Enviar mensaje e imagen por whatsapp
            enviarFinRondintoWhatsapp()

        }catch (e: Exception){
            Toast.makeText(this, "Error al Finalizar el Rondin: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    private fun InitNFC(){
        nfcAdapter =  NfcAdapter.getDefaultAdapter(this)
        val rswitch = findViewById<Switch>(R.id.swRonding)
        // Check the NFC adapter
        if (nfcAdapter == null && !isRunningOnEmulator()) {
            nfcAdapter = null
            val builder = AlertDialog.Builder(this@VehicleSearchActivity)
            builder.setMessage("Este dispositivo no tiene NFC. Imposible hacer uso del Rondin")
            //Return to MAIN
            builder.setPositiveButton("Enterado") { dialog, _ ->
                //Apagar ek switch
                rswitch.setOnCheckedChangeListener(null)
                rswitch.isChecked = false
                setListenerRondinSwitch(rswitch as Switch)
                dialog.dismiss()
            }
            val myDialog = builder.create()
            myDialog.setCanceledOnTouchOutside(false)
            myDialog.show()
            return
        }
        else if (nfcAdapter != null && nfcAdapter?.isEnabled ==false) {
            val builder = AlertDialog.Builder(this@VehicleSearchActivity)//, R.style.MyAlertDialogStyle)
            builder.setTitle("NFC Deshabilitado")
            builder.setMessage("Por favor habilita el NFC")

            builder.setPositiveButton("Settings") { _, _ -> startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }
            builder.setNegativeButton("Cancel", null)
            val myDialog = builder.create()
            myDialog.setCanceledOnTouchOutside(false)
            myDialog.show()
            return
        }

        //ENABLE LISTENING IN THIS ACTIVITY
        if (nfcAdapter != null && nfcAdapter!!.isEnabled)
            nfcAdapter?.enableForegroundDispatch(this, pendingIntentNFC, intentNFCFiltersArray, techNFCListsArray)
    }
    private fun stopNFC(){
        if (nfcAdapter != null) nfcAdapter!!.disableForegroundDispatch(this)
    }
    private fun isRunningOnEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.contains("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.PRODUCT.contains("sdk_google")
                || Build.PRODUCT.contains("google_sdk")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("sdk_x86")
                || Build.PRODUCT.contains("vbox86p")
                || Build.PRODUCT.contains("emulator")
                || Build.PRODUCT.contains("simulator"))
    }
    //LECTOR DE NFC se ejecuta el intent y valida si es del NFC correcto
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val txtLog: TextView = findViewById<EditText>(R.id.resultText)
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action) {
            try {
                val tagFromIntent: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
                val nfc = Ndef.get(tagFromIntent)


                val ndefMessage = nfc?.cachedNdefMessage
                ndefMessage?.records?.forEach { record ->
                    if (record.tnf == NdefRecord.TNF_MIME_MEDIA) {
                        // This is a MIME type record
                        val data = String(record.payload, Charsets.UTF_8)
                        // Process the mimeType and data
                        val checkP = convertTagStringToCheckPoint(data)
                        checkP.strTime =  LocalDateTime.now().toString()
                        if (addCheckPoint(checkP)) {
                            txtLog.text = "-----TAG-----\nAT:" + checkP.strTime + "\n ${data}"
                            updateMapMarkers()
                            refreshContadorEventos()
                            //Centrar MAPA a este punto leido
                            val userLatLng = LatLng(checkP.latitud, checkP.longitud)
                            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 21f))
                        }else{
                            txtLog.text = "-----TAG REPETIDO (ya se habia leido)-----\n"
                        }
                    }
                }
            }catch (e: Exception) {
                txtLog.append("Lectura ERRONEA:" + e.message + "\n")
            }
        }else{
            txtLog.append("NO VALID TAG\n")
        }

    }
    fun convertTagStringToCheckPoint(tagString:String):CheckPoint{
        val index2 = tagString.indexOf(' ')
        val index1 = tagString.indexOf(':')
        val identificador=tagString.substring(index1+1, index2)
        val location = tagString.substring(index2+2,tagString.length - 1).split(',')
        return CheckPoint(identificador,location[0].toDouble(),location[1].toDouble(),true)
    }
    fun addCheckPoint(checkP: CheckPoint):Boolean{
        val checkPoints = mySettings?.getListCheckPoint("LIST_CHECKPOINT")!!.toMutableList()
        val listIterator = checkPoints.listIterator()
        while (listIterator.hasNext()) {
            val element: CheckPoint = listIterator.next()
            if (element.identificador == checkP.identificador) {
                //exists
                return false
            }
        }
        //Add new one
        checkPoints.add(checkP)
        mySettings?.saveListCheckPoint("LIST_CHECKPOINT",checkPoints.toList())
        return true
    }
    fun createMarkerTAGRondinero(context: Context, text: String): Bitmap {
        val markerView = LayoutInflater.from(context).inflate(R.layout.custom_marker_layout, null)

        val markerText = markerView.findViewById<TextView>(R.id.marker_text)
        markerText.text = text

        markerView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        markerView.layout(0, 0, markerView.measuredWidth, markerView.measuredHeight)
        markerView.isDrawingCacheEnabled = true
        markerView.buildDrawingCache()
        val bitmap = createBitmap(markerView.measuredWidth, markerView.measuredHeight)
        val canvas = Canvas(bitmap)
        markerView.draw(canvas)

        return bitmap
    }
    fun moveCameraToShowAllTAGS(){
        val checkPoints = mySettings?.getListCheckPoint("LIST_CHECKPOINT")!!.toMutableList()
        val pSlots = dataRaw?.getParkingSlots()
        if (checkPoints.isEmpty() && pSlots!!.isEmpty()){
            return
        }

        val builder = LatLngBounds.Builder()
        checkPoints.forEach { builder.include(LatLng(it.latitud,it.longitud)) }
        dataRaw?.getParkingSlots()?.forEach {
            try{
                val lat = it[0].toString().toDoubleOrNull() ?: 0.0
                val lon = it[1].toString().toDoubleOrNull() ?: 0.0
                builder.include(LatLng(lat,lon))
            }catch (e: Exception) {
                null
            }
        }
        val bounds = builder.build()
        val padding = 100 // offset from edges of the map in pixels
        val cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, padding)
        googleMap?.moveCamera(cameraUpdate)


    }
    suspend fun obtenerMapaRondinAllTAGS(minutosTotales: Long = 0): Uri? {
        //Repintamos sin tags en parkingSlots
        updateMapMarkers(false)

        delay(1000)
        // Movemos la cámara primero
        moveCameraToShowAllTAGS()

        // Esperamos a que el mapa esté en reposo (Idle)
        // Usamos esta pequeña función auxiliar para esperar el evento Idle
        esperarCamaraIdle()

        // AGREGAMOS EL DELAY: Damos 500ms extras para que los iconos de los tags se pinten
        delay(1000)

        // Ahora sí, disparamos el snapshot y esperamos el resultado
        return capturarSnapshotConTexto(minutosTotales)
    }
    private suspend fun esperarCamaraIdle() = suspendCancellableCoroutine<Unit> { cont ->
        googleMap?.setOnCameraIdleListener {
            googleMap?.setOnCameraIdleListener(null)
            if (cont.isActive) cont.resume(Unit,null)
        }
    }
    suspend fun capturarSnapshotConTexto(minutosTotales: Long = 0): Uri? = suspendCancellableCoroutine { continuation ->
        googleMap?.snapshot { bitmap ->
            if (bitmap == null) {
                continuation.resume(null,null)
                return@snapshot
            }

            try {
                val tPBarParkingSlots: TextView = findViewById<TextView>(R.id.txtPBar_ParkingSlots)
                val msgMapa = "GpeINN Rondin: ${
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))
                }"
                val msg2Mapa = "Tiempo: ${minutosTotales} min"
                val msg3Mapa = tPBarParkingSlots.text.toString()
                // 1. Copy of bitmap
                var bitmaptext = bitmap!!.copy(Bitmap.Config.ARGB_8888, true)
                // 2. Create a Canvas to draw on the Bitmap
                val canvas = Canvas(bitmaptext)

                // 3. Define the Paint object for the text
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                paint.color = Color.GREEN // Set text color
                paint.textSize = 50f // Set text size in pixels
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.setShadowLayer(2f, 1f, 1f, Color.BLACK) // Add a slight shadow

                // 4. Calculate the text position
                val textBounds = Rect()
                paint.getTextBounds(msgMapa, 0, msgMapa.length, textBounds)
                val textBounds3 = Rect()
                paint.getTextBounds(msg3Mapa, 0, msg3Mapa.length, textBounds3)

                // Position the text at the bottom center of the image
                val x = (bitmaptext.width - textBounds.width()) / 2f
                val y =
                    (bitmaptext.height + textBounds.height()) - 100f // 20f for padding from bottom

                // 5. Draw the text onto the canvas
                canvas.drawText(msgMapa, x, y, paint)
                canvas.drawText(msg2Mapa, 5F, 55F, paint)
                canvas.drawText(msg3Mapa, bitmaptext.width - textBounds3.width() - 5f, 55f, paint)

                //########### Save the photo
                val imageFile =
                    File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "map_snapshot.png")
                FileOutputStream(imageFile).use { out ->
                    bitmaptext.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                //############ Send de message
                // Creating intent with action send
                val imageUri: Uri? = try {
                    FileProvider.getUriForFile(
                        applicationContext,
                        "${packageName}.fileprovider", // Replace with your actual provider authority
                        imageFile
                    )
                } catch (e: IllegalArgumentException) {
                    // Handle the case where the file is not found or the FileProvider is not configured correctly
                    null
                }
                continuation.resume(imageUri, null)
            }catch (e: Exception) {
                continuation.resume(null,null)
            }

        } // end google map snapshot
    }
    fun enviarFinRondintoWhatsapp(){
        val checkPoints = mySettings?.getListCheckPoint("LIST_CHECKPOINT")!!.toMutableList()
        val tPBarParkingSlots: TextView = findViewById<TextView>(R.id.txtPBar_ParkingSlots)
        var message: String = ""
        checkPoints.forEachIndexed { index, point ->
            message += "-----------\nP(${index + 1}) AT:" + point.strTime + "\n"
        }
        var minutosTotales: Long
        try {
            val fechaInicio = LocalDateTime.parse(checkPoints[0].strTime)
            minutosTotales = ChronoUnit.MINUTES.between(fechaInicio, LocalDateTime.now())
        }catch (e: Exception){
            minutosTotales = 0
        }
        message+="END TIME:"+LocalDateTime.now().toString()+"\n"

        lifecycleScope.launch {
            val imageUri = obtenerMapaRondinAllTAGS(minutosTotales)

            //ENVIAR Mensaje por whatsapp
            val sendIntent: Intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_TEXT, message)
                putExtra(Intent.EXTRA_STREAM, imageUri)
                putExtra("android.intent.extra.TEXT", message)
                //type = "text/plain"
                //type = "*/*"
                //setPackage("com.whatsapp")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val shareIntent = Intent.createChooser(sendIntent, "Enviar rondin con...")
            startActivity(shareIntent)

            //######## Limpiar Memoria #####################################
            mySettings?.saveListCheckPoint("LIST_CHECKPOINT",mutableListOf())
            stopNFC()
            //Ocultar barProgress
            val pBarCheckPoints: ProgressBar = findViewById<ProgressBar>(R.id.progressBar_CheckPoints)
            val tPBarCheckPoints: TextView = findViewById<TextView>(R.id.txtPBar_CheckPoints)
            val txtLog: TextView = findViewById<EditText>(R.id.resultText)
            pBarCheckPoints.visibility = View.GONE
            tPBarCheckPoints.visibility = View.GONE
            txtLog.setText("")
            //######## FIN Limpiar Memoria ##################################
        }
//        googleMap?.snapshot { bitmap ->
//            val msgMapa = "GpeINN Rondin: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))}"
//            val msg2Mapa = "Tiempo: ${minutosTotales} min"
//            val msg3Mapa = tPBarParkingSlots.text.toString()
//            // 1. Copy of bitmap
//            var bitmaptext = bitmap!!.copy(Bitmap.Config.ARGB_8888, true)
//            // 2. Create a Canvas to draw on the Bitmap
//            val canvas = Canvas(bitmaptext)
//
//            // 3. Define the Paint object for the text
//            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
//            paint.color = Color.GREEN // Set text color
//            paint.textSize = 50f // Set text size in pixels
//            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
//            paint.setShadowLayer(2f, 1f, 1f, Color.BLACK) // Add a slight shadow
//
//            // 4. Calculate the text position
//            val textBounds = Rect()
//            paint.getTextBounds(msgMapa, 0, msgMapa.length, textBounds)
//            val textBounds3 = Rect()
//            paint.getTextBounds(msg3Mapa,0,msg3Mapa.length, textBounds3)
//
//            // Position the text at the bottom center of the image
//            val x = (bitmaptext.width - textBounds.width()) / 2f
//            val y = (bitmaptext.height + textBounds.height()) - 100f // 20f for padding from bottom
//
//            // 5. Draw the text onto the canvas
//            canvas.drawText(msgMapa, x, y, paint)
//            canvas.drawText(msg2Mapa, 5F, 55F, paint)
//            canvas.drawText(msg3Mapa, bitmaptext.width - textBounds3.width() - 5f, 55f,paint)
//
//            //########### Save the photo
//            val imageFile = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "map_snapshot.png")
//            FileOutputStream(imageFile).use { out ->
//                bitmaptext.compress(Bitmap.CompressFormat.PNG, 100, out)
//            }
//            //############ Send de message
//            // Creating intent with action send
//            val imageUri: Uri? = try {
//                FileProvider.getUriForFile(
//                    applicationContext,
//                    "${packageName}.fileprovider", // Replace with your actual provider authority
//                    imageFile
//                )
//            } catch (e: IllegalArgumentException) {
//                // Handle the case where the file is not found or the FileProvider is not configured correctly
//                null
//            }
//
//            val sendIntent: Intent = Intent(Intent.ACTION_SEND).apply {
//                type = "image/*"
//                putExtra(Intent.EXTRA_TEXT, message)
//                putExtra(Intent.EXTRA_STREAM, imageUri)
//                putExtra("android.intent.extra.TEXT", message)
//                //type = "text/plain"
//                //type = "*/*"
//                //setPackage("com.whatsapp")
//                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
//            }
//            val shareIntent = Intent.createChooser(sendIntent, "Enviar rondin con...")
//            startActivity(shareIntent)
//
//            //######## Limpiar Memoria #####################################
//            mySettings?.saveListCheckPoint("LIST_CHECKPOINT",mutableListOf())
//            stopNFC()
//            //Ocultar barProgress
//            val pBarCheckPoints: ProgressBar = findViewById<ProgressBar>(R.id.progressBar_CheckPoints)
//            val tPBarCheckPoints: TextView = findViewById<TextView>(R.id.txtPBar_CheckPoints)
//            val txtLog: TextView = findViewById<EditText>(R.id.resultText)
//            pBarCheckPoints.visibility = View.GONE
//            tPBarCheckPoints.visibility = View.GONE
//            txtLog.setText("")
//            //######## FIN Limpiar Memoria ##################################
//
//        } // end google map snapshot

    }// end SendMessage
    //##################### FIN RONDINERO ##############


    //############ AYUDA ###############
    fun mostrarAyuda() {
        val txtVehiculos = findViewById<TextView>(R.id.txtPBar_ParkingSlots)
        val swRondin = findViewById<Switch>(R.id.swRonding)
        val txtPlate = findViewById<EditText>(R.id.plateInput)
        val bMapa = findViewById<ImageView>(R.id.imgRondineroCentrado)

        TapTargetSequence(this)
            .targets(
                TapTarget.forView(txtVehiculos, "¡Lugares de Visita!", "Aqui veras el numero de cajones de visita que se han escaneado, esto se resetea cada 6 hr y se debe volver hacer hacer rondin de placas en cajones de estacionamiento")
                    // Personalización con los colores de Rondy
                    .outerCircleColor(R.color.teal_700)      // El color de fondo del círculo grande
                    .targetCircleColor(R.color.white)         // El color que rodea al botón
                    .titleTextSize(24)                        // Tamaño del título
                    .descriptionTextSize(16)                  // Tamaño de la descripción
                    .textColor(R.color.white)                 // Color del texto
                    .drawShadow(true)                         // Sombra para profundidad
                    .cancelable(false)                        // No se cierra si tocan fuera
                    .tintTarget(true),                        // Mantiene el color del botón original
                    //.transparentTarget(false),                // El botón se ve sólido dentro del círculo
                TapTarget.forView(swRondin, "¡RONDIN!", "Aqui inicias y finalizas el rondin, una ves finalizado los tags deberan ser enviados por whatsapp al grupo de administracion")
                    // Personalización con los colores de Rondy
                    .outerCircleColor(R.color.teal_700)      // El color de fondo del círculo grande
                    .targetCircleColor(R.color.white)         // El color que rodea al botón
                    .titleTextSize(24)                        // Tamaño del título
                    .descriptionTextSize(16)                  // Tamaño de la descripción
                    .textColor(R.color.white)                 // Color del texto
                    .drawShadow(true)                         // Sombra para profundidad
                    .cancelable(false)                        // No se cierra si tocan fuera
                    .tintTarget(true)                         // Mantiene el color del botón original
                    .transparentTarget(false),                // El botón se ve sólido dentro del círculo
                TapTarget.forView(txtPlate, "PLACAS", "Ingresa las placas para saber el domicilio al que pertence, puedes tomar foto y se tratara de leer la placa desde la foto")
                    // Personalización con los colores de Rondy
                    .outerCircleColor(R.color.teal_700)      // El color de fondo del círculo grande
                    .targetCircleColor(R.color.white)         // El color que rodea al botón
                    .titleTextSize(24)                        // Tamaño del título
                    .descriptionTextSize(16)                  // Tamaño de la descripción
                    .textColor(R.color.white)                 // Color del texto
                    .drawShadow(true)                         // Sombra para profundidad
                    .cancelable(false)                        // No se cierra si tocan fuera
                    .tintTarget(true)                         // Mantiene el color del botón original
                    .transparentTarget(false),                // El botón se ve sólido dentro del círculo
                TapTarget.forView(bMapa, "MAPA", "Aqui podras ver los lugares de visitas que no han sido validados, podras ver los permisos aprobados, y tambien los domicilios a los cuales se debe validar si sus cocheras estan vacias o ocupadas")
                    // Personalización con los colores de Rondy
                    .outerCircleColor(R.color.teal_700)      // El color de fondo del círculo grande
                    .targetCircleColor(R.color.white)         // El color que rodea al botón
                    .titleTextSize(24)                        // Tamaño del título
                    .descriptionTextSize(16)                  // Tamaño de la descripción
                    .textColor(R.color.white)                 // Color del texto
                    .drawShadow(true)                         // Sombra para profundidad
                    .cancelable(false)                        // No se cierra si tocan fuera
                    .tintTarget(true)                         // Mantiene el color del botón original
            )
            .listener(object : TapTargetSequence.Listener {
                override fun onSequenceFinish() {
                    // Se ejecuta cuando el usuario termina todo el tour
                }
                override fun onSequenceStep(lastTarget: TapTarget?, targetClicked: Boolean) {
                    // Se ejecuta cada que avanza un paso
                }
                override fun onSequenceCanceled(lastTarget: TapTarget?) {
                    // Se ejecuta si el usuario cancela (si es cancelable)
                }
            })
            .start()

//        TapTargetView.showFor(this,
//            TapTarget.forView(txtCodigoAct, "¡Activa tu aplicacion!", "Ingresa cualquier valor para no regresar a esta pantalla, si tienes un codigo de activacion proporcionado por el programador usalo aqui y podras usar todas las funciones.")
//                // Personalización con los colores de Rondy
//                .outerCircleColor(R.color.teal_700)      // El color de fondo del círculo grande
//                .targetCircleColor(R.color.white)         // El color que rodea al botón
//                .titleTextSize(24)                        // Tamaño del título
//                .descriptionTextSize(16)                  // Tamaño de la descripción
//                .textColor(R.color.white)                 // Color del texto
//                .drawShadow(true)                         // Sombra para profundidad
//                .cancelable(false)                        // No se cierra si tocan fuera
//                .tintTarget(true)                         // Mantiene el color del botón original
//                .transparentTarget(false),                // El botón se ve sólido dentro del círculo
//            object : TapTargetView.Listener() {
//                override fun onTargetClick(view: TapTargetView?) {
//                    super.onTargetClick(view)
//                    // Aquí puedes ejecutar la acción del botón o cerrar la guía
//                }
//            }
//        )
    }


    data class ParkingSlot(val latitude: Double, val longitude: Double, val key: String, val distance: Double)

}
