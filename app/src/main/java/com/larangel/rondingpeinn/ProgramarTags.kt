package com.larangel.rondingpeinn

import CheckPoint
import CheckPointAdapter
import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentFilter.MalformedMimeTypeException
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import java.nio.charset.Charset

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng

class ProgramarTags : AppCompatActivity() {
    private var googleMap: GoogleMap? = null
    private var isManualMode = false // Controla si mandamos nosotros o el GPS
    private var lastGpsLocation: LatLng? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var nfcAdapter: NfcAdapter? = null
    private var pendingNFCIntent: PendingIntent? = null
    private var intentNFCFiltersArray: Array<IntentFilter>? = null
    private var techNFCListsArray: Array<Array<String>>? = null//        }


    private var wichCheckpointToSave: CheckPoint? = null
    private var locationListener: LocationListener? = null

    private var isScanning: Boolean? = false


    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_programar_tags)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        //######### Setup all for READING tags NFC and write
        nfcAdapter =  NfcAdapter.getDefaultAdapter(this)
        //Setting Pending intent
        pendingNFCIntent = PendingIntent.getActivity(
            this, 0, Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE
        )
        // Setup an intent filter for all MIME based dispatches
        val ndef = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
            try {
                addDataType("*/*")
            } catch (e: MalformedMimeTypeException) {
                throw RuntimeException("fail", e)
            }
        }
        intentNFCFiltersArray = arrayOf(ndef, IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED), IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED))
        // Setup a tech list for all Ndef tags
        techNFCListsArray = arrayOf(
            arrayOf(Ndef::class.java.name),
            arrayOf(android.nfc.tech.NfcA::class.java.name),
            arrayOf(android.nfc.tech.NfcB::class.java.name),
            arrayOf(android.nfc.tech.IsoDep::class.java.name),
            arrayOf(android.nfc.tech.MifareClassic::class.java.name),
            arrayOf(android.nfc.tech.MifareUltralight::class.java.name)
        )
        InitNFC()
        //######### FIN Setup NFC #########################

        // Inicializar el Mapa
        setupMap()

        // Botón para volver al GPS
        val btnRecuperarGPS: ImageButton = findViewById(R.id.btn_recuperar_gps)
        btnRecuperarGPS.setOnClickListener {
            isManualMode = false
            lastGpsLocation?.let { loc ->
                googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(loc, 17f))
                actualizarUI(loc.latitude, loc.longitude)
            }
        }

        val btnProgramarTag: Button = findViewById(R.id.btn_ProgramarTag)
        btnProgramarTag.setOnClickListener{
            clickProgramarTag()
        }

        val btncerrar: Button = findViewById<Button>(R.id.btn_cerrar)
        btncerrar.setOnClickListener{
            val intent: Intent = Intent(this, MainActivity::class.java )
            startActivity(intent)
        }

        val btnSettings: ImageButton = findViewById<ImageButton>(R.id.btnSettings)
        btnSettings.setOnClickListener{
            startActivity(Intent(this, SettingsActivity::class.java ))
        }


        //Request GPS Permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {

                get_gps_location_lister()

        } else {
            // El permiso no ha sido concedido, solicitarlo
            ActivityCompat.requestPermissions(this, arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION), 123)
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
    private fun setupMap() {
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_fragment_programarTags) as SupportMapFragment
        mapFragment.getMapAsync { gMap ->
            googleMap = gMap
            // Centrar en la ubicación actual si tienes permiso:
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                gMap.isMyLocationEnabled = true
                fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) {
                        val userLatLng = LatLng(loc.latitude, loc.longitude)
                        gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 20f))
                    }
                }
            }
            // Listener cuando el mapa se mueve
            googleMap?.setOnCameraMoveStartedListener { reason ->
                // Si el usuario mueve el mapa con el dedo, activamos modo manual
                if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                    isManualMode = true
                }
            }

            googleMap?.setOnCameraIdleListener {
                if (isManualMode) {
                    val centro = googleMap?.cameraPosition?.target
                    centro?.let {
                        actualizarUI(it.latitude, it.longitude)
                    }
                }
            }
        }
    }

    //########## MAPA
    private fun actualizarUI(lat: Double, lon: Double) {
        val txtLat: TextView = findViewById(R.id.txtLat)
        val txtLon: TextView = findViewById(R.id.txtLon)
        txtLat.text = String.format("%.8f", lat)
        txtLon.text = String.format("%.8f", lon)
        findViewById<Button>(R.id.btn_ProgramarTag).isEnabled = true
    }
    //##########END MAPA


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


    //Una ves solicitado el permiso manejar la respuesta
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 123) { //GPS permission
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //Timer().schedule(0L,5000L) {
                    get_gps_location_lister()
                //}
            } else {
                // El usuario rechazó el permiso, mostrar un mensaje al usuario
                Toast.makeText(this, "Necesitas el permiso para acceder a la ubicación", Toast.LENGTH_SHORT).show()
            }
        }
    }


    fun set_pause_gps(){
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        locationManager.removeUpdates(locationListener as LocationListener)
    }
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun get_gps_location_lister(){
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
                        actualizarUI(location.latitude, location.longitude)
                        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 20f))
                    }
                }

                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
                    // ...
                }

                override fun onProviderEnabled(provider: String) {
                    //..
                    val txtLat: TextView = findViewById<TextView>(R.id.txtLat)
                    val txtLon: TextView = findViewById<TextView>(R.id.txtLon)
                    txtLat.text = "search GPS"
                    txtLon.text = "search GPS"
                }

                override fun onProviderDisabled(provider: String) {
                    val txtLat: TextView = findViewById<TextView>(R.id.txtLat)
                    val txtLon: TextView = findViewById<TextView>(R.id.txtLon)
                    txtLat.text = "error get lat"
                    txtLon.text = "error get long"
                }
            }
            // Request updates for GPS provider
            if (locationManager.allProviders.contains(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000L, 1f, locationListener as LocationListener)
            } else {
                // El dispositivo no tiene GPS físico, intenta con el de red o avisa al usuario
                if (locationManager.allProviders.contains(LocationManager.NETWORK_PROVIDER)) {
                    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 1f, locationListener as LocationListener)
                } else {
                    val btnProgramarTag: Button = findViewById(R.id.btn_ProgramarTag)
                    btnProgramarTag.text =  "No hay proveedores de ubicación disponibles (SIN GPS)"
                    btnProgramarTag.setEnabled(false)

                    val builder = AlertDialog.Builder(this@ProgramarTags)
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

    fun hideKeyboard() {
        val view = currentFocus
        if (view != null) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    public override fun onPause() {
        super.onPause()
        set_pause_gps()
        stopNFC()
    }
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    @SuppressLint("SetTextI18n")
    public override fun onResume() {
        super.onResume()
//        val btnProgramarTag: Button = findViewById(R.id.btn_ProgramarTag)
        get_gps_location_lister()
        InitNFC()
//        btnProgramarTag.text="Programar TAG nuevo"
//        wichCheckpointToSave=null
//        btnProgramarTag.setEnabled(false)
    }

    private fun InitNFC(){
        nfcAdapter =  NfcAdapter.getDefaultAdapter(this)
        // Check the NFC adapter
        if (nfcAdapter == null && !isRunningOnEmulator()) {
            nfcAdapter = null
            val builder = AlertDialog.Builder(this@ProgramarTags)
            builder.setMessage("Este dispositivo no tiene NFC. Imposible programar TAGS")
            //Return to MAIN
            builder.setPositiveButton("Enterado") { dialog, _ ->
                //Desaparecer boton
                val btnProgramarTag: Button = findViewById(R.id.btn_ProgramarTag)
                val txtDesc: EditText = findViewById<EditText>(R.id.txtDescripcion)
                btnProgramarTag.visibility = View.GONE
                txtDesc.visibility = View.GONE
                dialog.dismiss()
            }
            val myDialog = builder.create()
            myDialog.setCanceledOnTouchOutside(false)
            myDialog.show()
            return
        }
        else if (nfcAdapter != null && nfcAdapter?.isEnabled ==false) {
            val builder = AlertDialog.Builder(this@ProgramarTags)//, R.style.MyAlertDialogStyle)
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
            nfcAdapter?.enableForegroundDispatch(this, pendingNFCIntent, intentNFCFiltersArray, techNFCListsArray)
    }
    private fun stopNFC(){
        if (nfcAdapter != null && !isRunningOnEmulator()) nfcAdapter!!.disableForegroundDispatch(this)
    }

    private fun clickProgramarTag(){
        val txtLog: TextView = findViewById<EditText>(R.id.txtlog2)
        val btnProgramarTag: Button = findViewById(R.id.btn_ProgramarTag)
        if (isScanning == true){
            btnProgramarTag.text = "Programar TAG nuevo"
            isScanning = false
            return
        }
        if (nfcAdapter != null && nfcAdapter?.isEnabled == true) {
            hideKeyboard()
            try {
                val txtLat: TextView = findViewById<TextView>(R.id.txtLat)
                val txtLon: TextView = findViewById<TextView>(R.id.txtLon)
                val txtDesc: EditText = findViewById<EditText>(R.id.txtDescripcion)
                if (txtDesc.text.length >= 3) {
                    wichCheckpointToSave = CheckPoint(
                        txtDesc.text.toString(),
                        txtLat.text.toString().toDouble(),
                        txtLon.text.toString().toDouble(),
                        false
                    )
                    btnProgramarTag.text =
                        "Scanning for ${wichCheckpointToSave?.identificador} ..."
                    isScanning = true

                }else{
                    txtLog.append("Error: El texto debe ser por lo menos de 3 caracteres")
                }
            } catch (e: Exception) {
                txtLog.append("Error al iniciar los datos para el NFC: ${e.message}\n")
            }
        }else{
            txtLog.append("No hay NFC activo para realizar esta accion, active primero el NFC")
        }

    }

    override fun onNewIntent(intent: Intent) {
        val txtLog: TextView = findViewById<EditText>(R.id.txtlog2)
        if (isScanning == false){
            txtLog.append("<<precione 'programar TAG' antes de acercar el TAG>>>")
            return
        }
        super.onNewIntent(intent)
        val txtDesc: EditText = findViewById<EditText>(R.id.txtDescripcion)
        txtLog.text=""

        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action || NfcAdapter.ACTION_TECH_DISCOVERED == intent.action) {
            val txtdata = "Checkpoint:" + wichCheckpointToSave?.identificador +
                    " [" +
                    String.format("%.6f", wichCheckpointToSave?.latitud) + "," +
                    String.format("%.6f", wichCheckpointToSave?.longitud) +
                    "]"
            try {
                val tagFromIntent: Tag? = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
                val nfc = Ndef.get(tagFromIntent)
                txtLog.append("NFC TAG:" + tagFromIntent?.id + "\n")

                val outRecord = NdefRecord.createMime(
                    "application/rondingpeinn",
                    txtdata.toByteArray(Charset.forName("US-ASCII"))
                )

                val ndefMessage = NdefMessage(arrayOf(outRecord))

                //Write
                nfc.connect()
                val isConnected = nfc.isConnected()

                if (isConnected) {
                    nfc?.writeNdefMessage(ndefMessage)
                    nfc?.close()
                    txtLog.text = "EXITOSO!!!: ${txtdata}\n"
                    txtDesc.setText("")
                    val btnProgramarTag: Button = findViewById(R.id.btn_ProgramarTag)
                    btnProgramarTag.text = "Programar TAG nuevo"
                    isScanning = false
                } else {
                    val myText = "Error: Not connected"
                    txtLog.append(myText + "\n")
                }
            }catch(e: Exception){
                txtLog.append("Error al guardar!!! error: ${e.message}\n --> Intenta de nuevo \n")
            }
        }

    }
}