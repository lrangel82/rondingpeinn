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
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
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
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.larangel.rondingpeinn.R
import java.nio.charset.Charset
import java.util.Timer
import java.util.TimerTask
import kotlin.concurrent.schedule

class ProgramarTags : AppCompatActivity() {
    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var intentFiltersArray: Array<IntentFilter>? = null

    private lateinit var recyclerView: RecyclerView
    private lateinit var dataList: MutableList<CheckPoint>
    private lateinit var myAdapter: CheckPointAdapter

    private var wichCheckpointToSave: CheckPoint? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationListener: LocationListener? = null


    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_programar_tags)

        nfcAdapter =  NfcAdapter.getDefaultAdapter(this)
        // Check the NFC adapter
        if (nfcAdapter == null && false) {
            val builder = AlertDialog.Builder(this@ProgramarTags)
            builder.setMessage("Este dispositivo no tiene NFC.")
            //Return to MAIN
            builder.setPositiveButton("Enterado") { _, _ ->
                startActivity(Intent(this, MainActivity::class.java))
            }
            val myDialog = builder.create()
            myDialog.setCanceledOnTouchOutside(false)
            myDialog.show()
        }
        else if (nfcAdapter != null && nfcAdapter?.isEnabled ==false ) {
            val builder = AlertDialog.Builder(this@ProgramarTags)
            builder.setTitle("NFC Disabled")
            builder.setMessage("Porfavor habilitar NFC en la configuracion")

            builder.setPositiveButton("Settings") { _, _ -> startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }
            builder.setNegativeButton("Cancel", null)
            val myDialog = builder.create()
            myDialog.setCanceledOnTouchOutside(false)
            myDialog.show()
        }
        else {
            //Setting Pending intent
            pendingIntent = PendingIntent.getActivity(
                this, 0, Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_MUTABLE
            )
        }

        val btnProgramarTag: Button = findViewById(R.id.btn_ProgramarTag)
        btnProgramarTag.setOnClickListener{
            val myText = "Scanning...."
            btnProgramarTag.text = myText

            // Setup an intent filter for all MIME based dispatches
            val ndef = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
                try {
                    addDataType("*/*")
                } catch (e: MalformedMimeTypeException) {
                    throw RuntimeException("fail", e)
                }
            }
            intentFiltersArray = arrayOf(ndef, IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED), IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED))
            // Setup a tech list for all Ndef tags
            val techListsArray = arrayOf(arrayOf<String>(Ndef::class.java.name))
            nfcAdapter?.enableForegroundDispatch(this, pendingIntent, intentFiltersArray, techListsArray)

        }

        val btncerrar: Button = findViewById<Button>(R.id.btn_cerrar)
        btncerrar.setOnClickListener{
            val intent: Intent = Intent(this, MainActivity::class.java )
            startActivity(intent)
        }


        //LISTADO de checkpoints
        recyclerView = findViewById(R.id.recyclerView)

        dataList = mutableListOf(
            CheckPoint("DR006", 20.660325, -103.446918, false),
            CheckPoint("EC073", 20.660463, -103.447213,  false),
            CheckPoint("EC013", 20.660703, -103.447302, false),
            CheckPoint("EC025", 20.661110, -103.447471, false),
            CheckPoint("EC029", 20.661559, -103.447677, false),
            CheckPoint("EC053", 20.661931, -103.447829, false),
            CheckPoint("MZ057", 20.662311, -103.448088, false),
            CheckPoint("MZ029", 20.662338, -103.447473, false),
            CheckPoint("MZ014", 20.662233, -103.4468432, false),
            CheckPoint("MN062", 20.662344, -103.446481, false),
            CheckPoint("MN038", 20.661853, -103.446457, false),
            CheckPoint("OL030", 20.661962, -103.447336, false),
            CheckPoint("RB014", 20.661485, -103.447153, false),
            CheckPoint("MZ018", 20.661428, -103.446513, false),
            CheckPoint("MZ004", 20.661151, -103.446567, false),
            CheckPoint("GP001", 20.661136, -103.446218, false),
            CheckPoint("GP049", 20.662208, -103.446128, false),
            CheckPoint("GP077", 20.660525, -103.446278, false),
            CheckPoint("GP099", 20.659650, -103.446292, false),
            CheckPoint("NR009", 20.661157, -103.447119, false),
            CheckPoint("CR006", 20.660699, -103.446889, false),
            CheckPoint("MZ057", 20.659835, -103.446684, false),
        )

        myAdapter = CheckPointAdapter(dataList) { item ->
            val btnProgramarTag: Button = findViewById(R.id.btn_ProgramarTag)
            btnProgramarTag.setEnabled(true)
            btnProgramarTag.text="Programar TAG " + item.identificador
            wichCheckpointToSave=item
        }
        recyclerView.adapter = myAdapter

        val layoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = layoutManager

        //Request GPS Permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            //Timer().schedule(0L,5000L) {
                get_gps_location_lister()
            //}
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
        //GPS
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                // Actualizar la ubicación del usuario
                val txtLat: TextView = findViewById<TextView>(R.id.txtLat)
                val txtLon: TextView = findViewById<TextView>(R.id.txtLon)
                val txtDesc: EditText = findViewById<EditText>(R.id.txtDescripcion)
                txtLat.text = location.latitude.toString()
                txtLon.text = location.longitude.toString()
                if ( txtDesc.text.length > 3 ) {
                        val btnProgramarTag: Button = findViewById(R.id.btn_ProgramarTag)
                        wichCheckpointToSave=CheckPoint(txtDesc.text.toString(), location.latitude, location.longitude, false )
                        btnProgramarTag.setEnabled(true)
                        btnProgramarTag.text="Programar TAG " + wichCheckpointToSave?.identificador

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
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 1f,
            locationListener as LocationListener
        )

        // You can also request updates for NETWORK_PROVIDER (using Wi-Fi or cellular)
        //locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0f, locationListener)


//        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

//        if (ActivityCompat.checkSelfPermission(
//                this,
//                Manifest.permission.ACCESS_FINE_LOCATION
//            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
//                this,
//                Manifest.permission.ACCESS_COARSE_LOCATION
//            ) != PackageManager.PERMISSION_GRANTED
//        ) {
//            ActivityCompat.requestPermissions(
//                this,
//                arrayOf(
//                    Manifest.permission.ACCESS_FINE_LOCATION,
//                    Manifest.permission.ACCESS_COARSE_LOCATION
//                ),
//                100
//            )
//            //return
//        }
//        fusedLocationClient.lastLocation
//            .addOnSuccessListener { location : Location? ->
//                val txtLat: TextView = findViewById<TextView>(R.id.txtLat)
//                val txtLon: TextView = findViewById<TextView>(R.id.txtLon)
//                val txtDesc: EditText = findViewById<EditText>(R.id.txtDescripcion)
//                location?.let {
//                    // Got last known location. In some rare situations this can be null.
//
//                    txtLat.text = it.latitude.toString()
//                    txtLon.text = it.longitude.toString()
//
//                    if ( txtDesc.text.length > 3 ) {
//                        val btnProgramarTag: Button = findViewById(R.id.btn_ProgramarTag)
//                        wichCheckpointToSave=CheckPoint(txtDesc.text.toString(), it.latitude, it.longitude, false )
//                        btnProgramarTag.setEnabled(true)
//                        btnProgramarTag.text="Programar TAG " + wichCheckpointToSave?.identificador
//
//                    }
//
//                } ?: run {
//                    txtLat.text = "error get lat"
//                    txtLon.text = "error get long"
//                }
//            }.addOnFailureListener { e ->
//                Toast.makeText(this, "Failed to get location: ${e.message}", Toast.LENGTH_SHORT)
//                    .show()
//            }
    }

    public override fun onPause() {
        super.onPause()
        set_pause_gps()
        nfcAdapter!!.disableForegroundDispatch(this)
    }
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    @SuppressLint("SetTextI18n")
    public override fun onResume() {
        super.onResume()
        val btnProgramarTag: Button = findViewById(R.id.btn_ProgramarTag)
        get_gps_location_lister()
        btnProgramarTag.text="Programar TAG nuevo"
        wichCheckpointToSave=null
        btnProgramarTag.setEnabled(false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val txtLog: TextView = findViewById<EditText>(R.id.txtlog2)
        txtLog.text=""

        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action || NfcAdapter.ACTION_TECH_DISCOVERED == intent.action) {
            val tagFromIntent: Tag? = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            val nfc = Ndef.get(tagFromIntent)
            txtLog.append("NFC TAG:"+tagFromIntent?.id+"\n")
            val txtdata = "Checkpoint:"+ wichCheckpointToSave?.identificador +
                    " [" +
                        wichCheckpointToSave?.latitud.toString() + "," +
                        wichCheckpointToSave?.longitud.toString() +
                      "]"
            val outRecord = NdefRecord.createMime(
                "application/rondingpeinn",
                txtdata.toByteArray(Charset.forName("US-ASCII")))

            val ndefMessage = NdefMessage(arrayOf(outRecord))

            //Write
            nfc.connect()
            val isConnected= nfc.isConnected()

            if(isConnected)
            {
                nfc?.writeNdefMessage(ndefMessage)
                nfc?.close()
                //val receivedData:ByteArray= nfc.transceive(byteArrayOf(0b00000001))
                //code to handle the received data
                // Received data would be in the form of a byte array that can be converted to string
                //NFC_READ_COMMAND would be the custom command you would have to send to your NFC Tag in order to read it
                txtLog.append(txtdata+"\n" )
                txtLog.append("Exitoso!!!\n" )


            }else{
                val myText = "Error: Not connected"
                txtLog.append(myText+"\n")
            }
            val btnProgramarTag: Button = findViewById(R.id.btn_ProgramarTag)
            btnProgramarTag.text="Programar TAG nuevo"
        }

    }
}