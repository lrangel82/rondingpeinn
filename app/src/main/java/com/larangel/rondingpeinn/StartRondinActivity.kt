package com.larangel.rondingpeinn

import CheckPoint
import MySettings
//import coil.load
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentFilter.MalformedMimeTypeException
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import androidx.core.graphics.createBitmap
import com.google.android.gms.maps.model.BitmapDescriptorFactory

class StartRondinActivity : AppCompatActivity() {
    private var mySettings: MySettings? = null
    private var CUANTOS_POR_ESCANEAR: Int = 0
    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var intentFiltersArray: Array<IntentFilter>? = null

    private var dataList =  mutableListOf<CheckPoint>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_start_rondin)

        mySettings=MySettings(this)

        CUANTOS_POR_ESCANEAR = mySettings?.getInt("rondin_num_tags",0)!!

        nfcAdapter =  NfcAdapter.getDefaultAdapter(this)
        // Check the NFC adapter
        if (nfcAdapter == null && !isRunningOnEmulator()) {
            sendAlertOK("Este dispositivo no tiene NFC.")
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)

        }
        else if (nfcAdapter != null && nfcAdapter?.isEnabled ==false) {
            val builder = AlertDialog.Builder(this@StartRondinActivity)//, R.style.MyAlertDialogStyle)
            builder.setTitle("NFC Disabled")
            builder.setMessage("Plesae Enable NFC")

            builder.setPositiveButton("Settings") { _, _ -> startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }
            builder.setNegativeButton("Cancel", null)
            val myDialog = builder.create()
            myDialog.setCanceledOnTouchOutside(false)
            myDialog.show()
        }
        else {

            //Start pending intent ESCUCHANDO...
            // Create a generic PendingIntent that will be deliver to this activity. The NFC stack
            // will fill in the intent with the details of the discovered tag before delivering to
            // this activity.
            pendingIntent = PendingIntent.getActivity(
                this, 0, Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_MUTABLE
            )
        }


        val btncancelar: Button = findViewById<Button>(R.id.btn_cancelar)
        btncancelar.text="Cancelar"
        btncancelar.setOnClickListener{
            //Reset
            resetData()
            val intent = Intent(this, MainActivity::class.java )
            startActivity(intent)
        }

        val btnFinalizar: Button = findViewById<Button>(R.id.btn_Finalizar)
        val txtLog: TextView = findViewById<TextView>(R.id.txtlog)
        btnFinalizar.setOnClickListener {
            txtLog.append("END TIME:"+LocalDateTime.now().toString()+"\n")
            sendMessage(txtLog.text.toString())

            btncancelar.text="Limpiar y apagar Scaner"

        }



        resetData()



        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
    public override fun onPause() {
        super.onPause()
        nfcAdapter!!.disableForegroundDispatch(this)
    }
    public override fun onResume() {
        super.onResume()
        val txtLog: TextView = findViewById<TextView>(R.id.txtlog)
        if (txtLog.text == "")
            txtLog.append("START TIME:"+LocalDateTime.now().toString()+"\n")

        // Setup an intent filter for all MIME based dispatches
        val ndef = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
            try {
                addDataType("application/rondingpeinn")
            } catch (e: MalformedMimeTypeException) {
                throw RuntimeException("fail", e)
            }
        }
        intentFiltersArray = arrayOf(ndef, IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED), IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED))
        // Setup a tech list for all Ndef tags
        val techListsArray = arrayOf(arrayOf<String>(Ndef::class.java.name))
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, intentFiltersArray, techListsArray)

    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val txtLog: TextView = findViewById<EditText>(R.id.txtlog)
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action) {
            try {
                val tagFromIntent: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
                val nfc = Ndef.get(tagFromIntent)


                val ndefMessage = nfc?.cachedNdefMessage
                ndefMessage?.records?.forEach { record ->
                    if (record.tnf == NdefRecord.TNF_MIME_MEDIA) {
                        // This is a MIME type record
                        //val mimeType = String(record.type, Charsets.UTF_8)
                        val data = String(record.payload, Charsets.UTF_8)
                        // Process the mimeType and data
                        //txtLog.append("a mimeType:" + mimeType + "\n")
                        val checkP = convertTagStringToCheckPoint(data)
                        if (setCheckPoint(checkP)) {
                            txtLog.append(
                                "-----------\nAT:" + LocalDateTime.now().toString() + "\n"
                            )
                            txtLog.append(data +"\n")
                            addPointToMap(checkP)
                        }
                        fillResume()
                    }
                }
            }catch (e: Exception) {
                txtLog.append("ERROR on read:" + e.message + "\n")
            }
        }else{
            txtLog.append("NO VALID TAG\n")
        }

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

    fun clearAllPointFromMap(){
        val mapFragment = supportFragmentManager.findFragmentById(
            R.id.map_fragment
        ) as? SupportMapFragment
        mapFragment?.getMapAsync { googleMap ->
            googleMap.clear()
        }
    }
    fun addPointToMap(point: CheckPoint){
        val mapFragment = supportFragmentManager.findFragmentById(
            R.id.map_fragment
        ) as? SupportMapFragment
        mapFragment?.getMapAsync { googleMap ->
            val bitmap = createCustomMarker(this, dataList.size.toString())
            val marker = googleMap.addMarker(
                MarkerOptions()
                    .flat(true)
                    .title(point.identificador)
                    .icon(BitmapDescriptorFactory.fromBitmap(bitmap))
                    .position(LatLng(point.latitud,point.longitud) )
            )
            marker?.showInfoWindow()
        }
    }
    fun createCustomMarker(context: Context, text: String): Bitmap {
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
    fun moveCameraToPoints(){
        val mapFragment = supportFragmentManager.findFragmentById(
            R.id.map_fragment
        ) as? SupportMapFragment
        mapFragment?.getMapAsync { googleMap ->
            val builder = LatLngBounds.Builder()
            dataList.forEach { builder.include(LatLng(it.latitud,it.longitud)) }
            val bounds = builder.build()
            val padding = 100 // offset from edges of the map in pixels
            val cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, padding)
            googleMap.moveCamera(cameraUpdate)

        }
    }
    fun convertTagStringToCheckPoint(tagString:String):CheckPoint{
        val index2 = tagString.indexOf(' ')
        val index1 = tagString.indexOf(':')
        val identificador=tagString.substring(index1+1, index2)
        val location = tagString.substring(index2+2,tagString.length - 1).split(',')
        return CheckPoint(identificador,location[0].toDouble(),location[1].toDouble(),true)
    }

    fun setCheckPoint(checkP: CheckPoint):Boolean{
        val listIterator = dataList.listIterator()
        while (listIterator.hasNext()) {
            val element: CheckPoint = listIterator.next()
            if (element.identificador == checkP.identificador) {
                //exists
                return false
            }
        }
        //Add new one
        dataList.add(checkP)
        moveCameraToPoints()
        return true
    }

    fun fillResume(){
        //val listIterator = dataList.listIterator()
        val cuantoOk: Int = dataList.size
//        while (listIterator.hasNext()) {
//            val element: CheckPoint = listIterator.next()
//            if (element.escanedo)
//                cuantoOk++
//        }
        val tFaltantes: TextView = findViewById<TextView>(R.id.tagsFaltantes)
        val tCompletados: TextView = findViewById<TextView>(R.id.tagsCompletados)
        tFaltantes.text = (CUANTOS_POR_ESCANEAR - cuantoOk).toString()
        tCompletados.text= cuantoOk.toString()
    }

    fun resetData(){
        val txtLog: TextView = findViewById<TextView>(R.id.txtlog)
        txtLog.text=""
        dataList= mutableListOf()
        clearAllPointFromMap()
        fillResume()
    }

    fun sendAlertOK(message: String){
        val builder = AlertDialog.Builder(this@StartRondinActivity)//, R.style.MyAlertDialogStyle)
        builder.setMessage(message)
        builder.setPositiveButton("OK", null)
        val myDialog = builder.create()
        myDialog.setCanceledOnTouchOutside(false)
        myDialog.show()
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
    }

    fun createPolyline(googleMap: GoogleMap, points: List<LatLng>){
        val polylineOptions = PolylineOptions().apply {
            addAll(points) // Add all points to the polyline
            width(10f) // Set the width of the polyline (optional)
            color(android.graphics.Color.BLUE) // Set the color of the polyline (optional)
            // You can add more customization here, like:
            // startCap(ButtCap())
            // endCap(RoundCap())
            // jointType(JointType.ROUND)
        }
        googleMap.addPolyline(polylineOptions)
    }

    fun sendMessage(message:String){

        //This is only to send when the snapshot photo is ready in
        // another thread, here I will wait
        val mapFragment = supportFragmentManager.findFragmentById(
            R.id.map_fragment
        ) as? SupportMapFragment
        mapFragment?.getMapAsync { googleMap ->
            googleMap.snapshot { bitmap ->
                //########### Save the photo
                val imageFile = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "map_snapshot.png")
                FileOutputStream(imageFile).use { out ->
                    bitmap?.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                //############ Send de message
                // Creating intent with action send
                val imageUri: Uri? = try {
                    FileProvider.getUriForFile(
                        applicationContext,
                        "com.larangel.rondingpeinn", // Replace with your actual provider authority
                        imageFile
                    )
                } catch (e: IllegalArgumentException) {
                    // Handle the case where the file is not found or the FileProvider is not configured correctly
                    null
                }

                val sendIntent: Intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, message)
                    putExtra(Intent.EXTRA_STREAM, imageUri)
                    //type = "text/plain"
                    //type = "*/*"
                    type = "image/*"
                    setPackage("com.whatsapp")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val shareIntent = Intent.createChooser(sendIntent, null)
                startActivity(shareIntent)

            } // end google map snapshot

        } // end get map

    } // end SendMessage
}