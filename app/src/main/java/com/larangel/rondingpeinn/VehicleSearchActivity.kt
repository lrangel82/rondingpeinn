package com.larangel.rondingpeinn

import MySettings
import DataRawRondin
import EventAdapter
import EventModal
import PorRevisarRecord
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.location.Location
import android.os.Build
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
import com.google.api.client.http.InputStreamContent
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
import java.io.ByteArrayInputStream
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
import android.widget.ProgressBar
import android.widget.FrameLayout

class VehicleSearchActivity : AppCompatActivity() {
    private var mySettings: MySettings? = null
    private var dataRaw: DataRawRondin? = null
    private lateinit var plateInput: EditText
    private lateinit var searchButton: Button
    private lateinit var cameraButton: ImageButton
    private lateinit var resultText: TextView
    private lateinit var eventsRecyclerView: RecyclerView
    private lateinit var saveEventButton: Button
    private lateinit var porRevisarButton: Button
    private lateinit var photoThumbnail: ImageView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var sheetsService: Sheets
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
    private val porRevisarRecords = mutableListOf<PorRevisarRecord>()
    private var porRevisarSheetId: Int = 0
    private var domicilioWarningsSheetId: Int = 0
    private val handler = Handler(Looper.getMainLooper())
    private val checkRunnable = Runnable { checkPorRevisarLocations() }
    private var loadingOverlay: View? = null
    private var progressBar: ProgressBar? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vehicle_search)

        // Initialize UI components
        plateInput = findViewById(R.id.plateInput)
        searchButton = findViewById(R.id.searchButton)
        cameraButton = findViewById(R.id.cameraButton)
        resultText = findViewById(R.id.resultText)
        eventsRecyclerView = findViewById(R.id.eventsRecyclerView)
        saveEventButton = findViewById(R.id.saveEventButton)
        photoThumbnail = findViewById(R.id.photoThumbnail)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mySettings = MySettings(this)
        dataRaw = DataRawRondin(this)

        // Initialize Google services (requires Google Sign-In setup)
        initializeGoogleServices()

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

        // Add Por Revisar button
        porRevisarButton = Button(this)
        porRevisarButton.setOnClickListener {
            val intent = Intent(this, PorRevisarListActivity::class.java)
            startActivity(intent)
        }
        val parent = saveEventButton.parent as? LinearLayout
        parent?.addView(porRevisarButton)

        // Run cleanup on app start
        cleanOldThumbnails()

        loadPorRevisar()
        refreshContadorEventos()
        updatePorRevisarButton()
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

    private fun updatePorRevisarButton() {
        porRevisarButton.text = "Por Revisar (${porRevisarRecords.size})"
        porRevisarButton.setBackgroundColor(if (porRevisarRecords.size > 0) Color.RED else Color.GRAY)
        porRevisarButton.tooltipText = porRevisarRecords.size.toString()
    }

    private fun initializeGoogleServices() {
        val serviceAccountStream = applicationContext.resources.openRawResource(R.raw.json_google_service_account)
        val credential = GoogleCredential.fromStream(serviceAccountStream)
            .createScoped(listOf("https://www.googleapis.com/auth/drive", "https://www.googleapis.com/auth/spreadsheets"))
        sheetsService = Sheets.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("My First Project")
            .build()
        println("LARANGEL sheetsService:${sheetsService}")

        // Get sheet ID for PorRevisar
        val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val spreadsheet = sheetsService.spreadsheets().get(yourEventsSpreadSheetID).execute()
                for (sheet in spreadsheet.sheets) {
                    when (sheet.properties.title) {
                        "PorRevisar" -> porRevisarSheetId = sheet.properties.sheetId
                        "DomicilioWarnings" -> domicilioWarningsSheetId = sheet.properties.sheetId
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@VehicleSearchActivity, "Error initializing sheet ID: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
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
                    val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
                    val response = sheetsService.spreadsheets().values()
                        .get(yourEventsSpreadSheetID, "AutosEventos!A:E")
                        .execute()
                    val rows = response.getValues() ?: emptyList()
                    val updates = mutableListOf<ValueRange>()
                    rows.forEachIndexed { index, row ->
                        if (row.size >= 4 && row[3] in deletedFiles) {
                            val rowIndex = index + 1
                            val update = ValueRange().setValues(listOf(listOf("", "", "", "", row[4])))
                            updates.add(update.setRange("AutosEventos!A$rowIndex:E$rowIndex"))
                        }
                    }
                    if (updates.isNotEmpty()) {
                        updates.forEach { update ->
                            sheetsService.spreadsheets().values()
                                .update(yourEventsSpreadSheetID, update.range, update)
                                .setValueInputOption("RAW")
                                .execute()
                        }
                        println("VehicleSearchActivity Updated ${updates.size} rows in AutosEventos with cleared localPhotoPath")
                    }

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
                    .setMessage("Do you want to select a photo from the gallery?")
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
        val plate = plateInput.text.toString().trim()
        if (plate.isEmpty()) {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    var plate = visionText.textBlocks.joinToString(" ") { it.text }.trim()
                    var re = Regex("[^A-Za-z0-9 ]")
                    plate = re.replace(plate, "") //Eliminar carcteres no deseados
                    re = Regex("[A-Z]{3}[0-9]{3,4}[A-Z]?")
                    val matchRegult = re.find(plate) //Match Placa
                    plate = if (matchRegult != null) {
                        matchRegult.value
                    } else {
                        ""
                    }
                    plateInput.setText(plate)
                    searchVehicle(plate)
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "OCR failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // Utilidad simple para detectar red
    fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = cm.activeNetworkInfo
        return network?.isConnected == true
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

    private fun searchVehicle(plate: String) {
        waitingOn()
        vehicleStreet = null
        vehicleNumber = null
        vehicleSource = null
        resultText.text = "" //Clean the text

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                //val vehicles = getCachedVehiclesData()
                val vehicles = dataRaw?.getCachedVehiclesData(sheetsService,isNetworkAvailable())
                var match: List<Any>? = null

                // 1. Buscar coincidencia exacta
                if (vehicles != null) {
                    for (row in vehicles) {
                        if (row.size >= 3 && row[0].toString().equals(plate, ignoreCase = true)) {
                            match = row
                            break
                        }
                    }
                }

                // 2. Si no hay coincidencia exacta, buscar similar
                var similarMatches: MutableList<List<Any>> = mutableListOf()
                if (match == null && vehicles != null) {
                    // Búsqueda tolerante
                    for (row in vehicles) {
                        if (row.size >= 3 && oneCharDifference(row[0].toString().toUpperCase(), plate.toUpperCase())) {
                            similarMatches.add(row)
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    waitingOff()
                    if (match != null) {
                        vehicleStreet = match[1].toString()
                        vehicleNumber = match[2].toString()
                        resultText.append("\nPlaca: $plate\nCalle: $vehicleStreet\nNumero: $vehicleNumber")
                    } else if (similarMatches.isNotEmpty()) {
                        resultText.append("\nPlaca no encontrada, pero similar a:\n")
                        similarMatches.forEach { sm ->
                            resultText.append("Placa: ${sm[0]} Calle: ${sm[1]}, Número: ${sm[2]}\n")
                        }
                    } else {
                        resultText.append("\nNo se encontro la placa en ningun registro: $plate")
                    }
                    showEventsPlate(plate)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    waitingOff()
                    resultText.append("\nError searching plate: ${e.message}")
                    showEventsPlate(plate)
                }
            }
        }
    }


    //AutoEventos
//    private fun getAutosEventos_6horas(): List<List<Any>>{
//        val rows = dataRaw?.getAutosEventos(sheetsService,isNetworkAvailable())
//        val date6HoursAgo = LocalDateTime.now().minusHours(6)
//        val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
//        val plateEvents = rows?.filter { LocalDateTime.parse(it[2].toString(),timeFormatter) >= date6HoursAgo }
//            ?.reversed()
//        return plateEvents ?: emptyList()
//    }

    private fun refreshContadorEventos(){
        waitingOn()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    //Solo ULTIMAS 6 HORAS
                    val plateEvents = dataRaw?.getAutosEventos_6horas(sheetsService,isNetworkAvailable())
                    val parkSlots = dataRaw?.getParkingSlots(sheetsService)

                    val tFaltantes: TextView = findViewById<TextView>(R.id.vehicleText_PSFaltantes)
                    val tCompletados: TextView = findViewById<TextView>(R.id.vehicleText_PSCompletos)

                    tFaltantes.text = (parkSlots?.size?.minus(plateEvents?.size ?: 0)).toString()
                    tCompletados.text= plateEvents?.size.toString()

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
                val rows = dataRaw?.getAutosEventos(sheetsService,isNetworkAvailable())
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
        val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
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
                    listOf(
                        formattedTime,
                        vehicleStreet,
                        vehicleNumber,
                        plate,
                        concatenatedSlots
                    )
                )
                val body = ValueRange().setValues(values)
                sheetsService.spreadsheets().values()
                    .append(yourEventsSpreadSheetID, "MultasGeneradas!A:E", body)
                    .setValueInputOption("RAW")
                    .execute()
                withContext(Dispatchers.Main) {
                    waitingOff()
                    Toast.makeText(this@VehicleSearchActivity, "Registro guardado en MultasGeneradas", Toast.LENGTH_SHORT).show()
                }
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
        var plate = plateInput.text.toString().trim()
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
    private fun continueSaveNewEvent(plate: String) {
        if (photoUri == null) {
            Toast.makeText(this, "Debe tomar una FOTO", Toast.LENGTH_SHORT).show()
            captureImage()
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    waitingOn()
                    fetchClosestParkingSlots(location.latitude, location.longitude) { closestSlots ->
                        if (closestSlots.isEmpty()) {
                            Toast.makeText(this, "No se encuentra lugares de estacionamiento cercanos", Toast.LENGTH_SHORT).show()
                            waitingOff()
                            return@fetchClosestParkingSlots
                        }
                        val slotDescriptions = closestSlots.map { "${it.key} (${String.format("%.2f", it.distance)} m)" }.toTypedArray()
                        waitingOff()
                        AlertDialog.Builder(this)
                            .setTitle("Selecciona LUGAR de Estacionamiento")
                            .setItems(slotDescriptions) { _, which ->
                                val selectedKey = closestSlots[which].key
                                val localPhotoPath = savePhotoLocally(photoUri)
                                if (localPhotoPath == null) {
                                    Toast.makeText(this, "Failed to save photo locally", Toast.LENGTH_SHORT).show()
                                    return@setItems
                                }
                                val date = LocalDate.now()
                                val time = LocalDateTime.now()
                                val event = EventModal(plate, date, time, localPhotoPath, selectedKey)
                                saveEventToSheet(event)
                                showEventsPlate(plate)
                                if (selectedKey in listOf("LugarProhibido", "BloqueoPeatonal", "ObstruyeCochera")) {
                                    handleSpecialKey(selectedKey, plate, localPhotoPath)
                                }
                            }
                            .setNegativeButton("Cancel") { _, _ -> }
                            .show()
                    }
                } else {
                    Toast.makeText(this, "Unable to get location", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION_PERMISSION)
        }
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
                    val response = sheetsService.spreadsheets().values()
                        .get(yourEventsSpreadSheetID, "DomicilioWarnings!A:C")
                        .execute()
                    val rows = response.getValues() ?: emptyList()
                    var existingRowIndex: Int? = null
                    var currentCount = 0
                    rows.forEachIndexed { index, row ->
                        if (row.size >= 2 && row[0].toString() == vehicleStreet && row[1].toString() == vehicleNumber) {
                            existingRowIndex = index + 1
                            currentCount = row[2].toString().toIntOrNull() ?: 0
                            return@forEachIndexed
                        }
                    }
                    val newCount = currentCount + 1
                    val values = listOf(
                        listOf(
                            vehicleStreet,
                            vehicleNumber,
                            newCount.toString()
                        )
                    )
                    val body = ValueRange().setValues(values)
                    if (existingRowIndex != null) {
                        sheetsService.spreadsheets().values()
                            .update(yourEventsSpreadSheetID, "DomicilioWarnings!A$existingRowIndex:C$existingRowIndex", body)
                            .setValueInputOption("RAW")
                            .execute()
                    } else {
                        sheetsService.spreadsheets().values()
                            .append(yourEventsSpreadSheetID, "DomicilioWarnings!A:C", body)
                            .setValueInputOption("RAW")
                            .execute()
                    }
                    withContext(Dispatchers.Main) {
                        waitingOff()
                        if (newCount >= 3) {
                            AlertDialog.Builder(this@VehicleSearchActivity)
                                .setTitle("Acredor a Multa")
                                .setMessage("El domicilio es acredor a una multa (3er aviso)")
                                .setPositiveButton("OK") { _, _ ->
                                    saveToMultasGeneradas( plate, "$selectedKey ($formattedTime)")
                                    shareViaWhatsApp(plate, selectedKey, localPhotoPath)
                                }
                                .show()
                        } else {
                            Toast.makeText(this@VehicleSearchActivity, "Warning recorded, count: $newCount", Toast.LENGTH_SHORT).show()
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
        val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
        val now = LocalDateTime.now()
        val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val formattedTime = now.format(timeFormatter)
        waitingOn()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val values = listOf(
                    listOf(
                        formattedTime,
                        vehicleStreet,
                        vehicleNumber,
                        plate,
                        concatenatedSlots
                    )
                )
                val body = ValueRange().setValues(values)
                sheetsService.spreadsheets().values()
                    .append(yourEventsSpreadSheetID, "MultasGeneradas!A:E", body)
                    .setValueInputOption("RAW")
                    .execute()
                withContext(Dispatchers.Main) {
                    waitingOff()
                    Toast.makeText(this@VehicleSearchActivity, "Fine recorded in MultasGeneradas", Toast.LENGTH_SHORT).show()
                }
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

//    private fun getParkingSlots(): List<List<Any>>{
//        if (parkingSlotsCache == null) { //Si no hay cache buscar en los settings
//            // 1. Si RAM no, revisa persisted cache (MySettings)
//            val cacheList = mySettings?.getList("PARKINGSLOTS_CACHE")!!.toMutableList()
//            if (cacheList?.isEmpty() == false)
//                parkingSlotsCache = cacheList
//
//            // 2. Sin mySettings no, descargar de red
//            if (parkingSlotsCache == null) {
//                val yourEventsSpreadSheetID =  mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
//                val response = sheetsService.spreadsheets().values()
//                    .get(
//                        yourEventsSpreadSheetID,
//                        "ParkingSlots!A:C"
//                    ) // Latitude, Longitude, Key
//                    .execute()
//                val allRows = response.getValues() ?: emptyList()
//                mySettings?.saveList("PARKINGSLOTS_CACHE", allRows as List<List<String>>)
//                parkingSlotsCache = allRows
//            }
//        }
//        return parkingSlotsCache ?: emptyList()
//    }
    private fun fetchClosestParkingSlots(latitude: Double, longitude: Double, callback: (List<ParkingSlot>) -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {

                //val parkSlots = getParkingSlots()
                val parkSlots = dataRaw?.getParkingSlots(sheetsService)
                val plateEvents6Horas = dataRaw?.getAutosEventos_6horas(sheetsService,isNetworkAvailable())
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
            val aspectRatio = bitmap.height.toFloat() / bitmap.width.toFloat()
            val targetWidth = 400
            val targetHeight = (targetWidth * aspectRatio).toInt()
            val reducedBitmap = bitmap.scale(targetHeight,targetWidth) // Reduced size
            val outputStream = ByteArrayOutputStream()
            reducedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
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
        val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!

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
        val bodyEventos = ValueRange().setValues(listOf(valuesEventos))
        dataRaw?._addEventCache(valuesEventos)
        refreshContadorEventos()
        waitingOn()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Save to AutosEventos
                sheetsService.spreadsheets().values()
                    .append(yourEventsSpreadSheetID, "AutosEventos!A:E", bodyEventos)
                    .setValueInputOption("RAW")
                    .execute()

                // If vehicle street and number available, save or update in PorRevisar
                if (vehicleStreet != null && vehicleNumber != null) {
                    // Fetch lat and lon from DomicilioUbicacion
                    val domicilioResponse = sheetsService.spreadsheets().values()
                        .get(yourEventsSpreadSheetID, "DomicilioUbicacion!A:D") // calle, numero, latitud, longitud
                        .execute()
                    val domicilioRows = domicilioResponse.getValues() ?: emptyList()
                    var lat: Double? = null
                    var lon: Double? = null
                    for (row in domicilioRows) {
                        if (row.size >= 4 && row[0].toString() == vehicleStreet && row[1].toString() == vehicleNumber) {
                            lat = row[2].toString().toDoubleOrNull()
                            lon = row[3].toString().toDoubleOrNull()
                            break
                        }
                    }
                    if (lat == null || lon == null) {
                        // Skip if not found
                        withContext(Dispatchers.Main) {
                            waitingOff()
                            Toast.makeText(this@VehicleSearchActivity, "Ubicación no encontrada para ${vehicleStreet} ${vehicleNumber}", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // Check if record exists in PorRevisar
                        val response = sheetsService.spreadsheets().values()
                            .get(yourEventsSpreadSheetID, "PorRevisar!A:G") // street, number, time, parkingSlotKey, validation, lat, lon
                            .execute()
                        val rows = response.getValues() ?: emptyList()
                        var existingRowIndex: Int? = null
                        rows.forEachIndexed { index, row ->
                            if (row.size >= 2 && row[0].toString() == vehicleStreet && row[1].toString() == vehicleNumber) {
                                existingRowIndex = index + 1 // 1-based index for Sheets
                                return@forEachIndexed
                            }
                        }

                        if (existingRowIndex != null) {
                            // Update existing record
                            val valuesPorRevisar = listOf(
                                listOf(
                                    vehicleStreet,
                                    vehicleNumber,
                                    formattedTime,
                                    event.parkingSlotKey,
                                    "",
                                    lat.toString(),
                                    lon.toString()
                                )
                            )
                            val bodyPorRevisar = ValueRange().setValues(valuesPorRevisar)
                            sheetsService.spreadsheets().values()
                                .update(yourEventsSpreadSheetID, "PorRevisar!A$existingRowIndex:G$existingRowIndex", bodyPorRevisar)
                                .setValueInputOption("RAW")
                                .execute()
                            // Update in memory
                            porRevisarRecords.find { it.street == vehicleStreet && it.number == vehicleNumber }?.let {
                                it.time = LocalDateTime.parse(formattedTime, timeFormatter)
                                it.parkingSlotKey = event.parkingSlotKey
                                it.latitude = lat
                                it.longitude = lon
                            }
                        } else {
                            // Append new record
                            val valuesPorRevisar = listOf(
                                listOf(
                                    vehicleStreet,
                                    vehicleNumber,
                                    formattedTime,
                                    event.parkingSlotKey,
                                    "",
                                    lat.toString(),
                                    lon.toString()
                                )
                            )
                            val bodyPorRevisar = ValueRange().setValues(valuesPorRevisar)
                            sheetsService.spreadsheets().values()
                                .append(yourEventsSpreadSheetID, "PorRevisar!A:G", bodyPorRevisar)
                                .setValueInputOption("RAW")
                                .execute()
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
                }

                withContext(Dispatchers.Main) {
                    waitingOff()
                    events.add(0,event)
                    eventsRecyclerView.adapter?.notifyDataSetChanged()
                    photoThumbnail.visibility = View.GONE // Reset thumbnail after saving
                    photoUri = null // Reset photoUri
                    updatePorRevisarButton()
                    refreshContadorEventos()
                    Toast.makeText(this@VehicleSearchActivity, "Event saved", Toast.LENGTH_SHORT).show()
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

    private fun loadPorRevisar() {
        val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = sheetsService.spreadsheets().values()
                    .get(yourEventsSpreadSheetID, "PorRevisar!A:G")
                    .execute()
                val rows = response.getValues() ?: emptyList()
                withContext(Dispatchers.Main) {
                    porRevisarRecords.clear()
                    val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    rows.forEach { row ->
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

    override fun onResume() {
        super.onResume()
        loadPorRevisar()
        handler.postDelayed(checkRunnable, 2000)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(checkRunnable)
    }

    private fun checkPorRevisarLocations() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            handler.postDelayed(checkRunnable, 2000)
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                val currentLat = location.latitude
                val currentLon = location.longitude
                val now = LocalDateTime.now()
                val toDelete = mutableListOf<PorRevisarRecord>()
                var nearbyRecord: PorRevisarRecord? = null
                porRevisarRecords.forEach { record ->
                    val ageHours = Duration.between(record.time, now).toHours()
                    if (ageHours > 20) {
                        toDelete.add(record)
                    } else {
                        val distance = calculateDistance(currentLat, currentLon, record.latitude, record.longitude)
                        if (distance < 10.0) {
                            nearbyRecord = record
                        }
                    }
                }
                toDelete.forEach { deletePorRevisarRecord(it) }
                nearbyRecord?.let { record ->
                    // Show verification alert for nearby record
                    //showVerificationAlert(record)
                    // Start PorRevisarListActivity with notification
                    val intent = Intent(this, PorRevisarListActivity::class.java).apply {
                        putExtra("street", record.street)
                        putExtra("number", record.number)
                        putExtra("notification", "Domicilio por verificar en ${record.street} ${record.number}")
                    }
                    startActivity(intent)
                }
                handler.postDelayed(checkRunnable, 2000)
            }

        }.addOnFailureListener {

            handler.postDelayed(checkRunnable, 2000)
        }
    }

    private fun showVerificationAlert(record: PorRevisarRecord) {
        AlertDialog.Builder(this)
            .setTitle("Verificar Cochera")
            .setMessage("¿La cochera está vacía en ${record.street} ${record.number}?")
            .setPositiveButton("Sí") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
                        // Get or create warning record
                        val response = sheetsService.spreadsheets().values()
                            .get(yourEventsSpreadSheetID, "DomicilioWarnings!A:C")
                            .execute()
                        val rows = response.getValues() ?: emptyList()
                        var existingRowIndex: Int? = null
                        var currentCount = 0
                        rows.forEachIndexed { index, row ->
                            if (row.size >= 2 && row[0].toString() == record.street && row[1].toString() == record.number) {
                                existingRowIndex = index + 1
                                currentCount = row[2].toString().toIntOrNull() ?: 0
                                return@forEachIndexed
                            }
                        }
                        val newCount = currentCount + 1
                        val values = listOf(
                            listOf(
                                record.street,
                                record.number,
                                newCount.toString()
                            )
                        )
                        val body = ValueRange().setValues(values)
                        if (existingRowIndex != null) {
                            sheetsService.spreadsheets().values()
                                .update(yourEventsSpreadSheetID, "DomicilioWarnings!A$existingRowIndex:C$existingRowIndex", body)
                                .setValueInputOption("RAW")
                                .execute()
                        } else {
                            sheetsService.spreadsheets().values()
                                .append(yourEventsSpreadSheetID, "DomicilioWarnings!A:C", body)
                                .setValueInputOption("RAW")
                                .execute()
                        }
                        withContext(Dispatchers.Main) {
                            val message = if (newCount < 3) "El domicilio es acredor a un aviso por tener vehículo en visita con cochera vacía."
                            else "El domicilio es acredor a una multa por tener vehículo en visita con cochera vacía (3er aviso)."
                            AlertDialog.Builder(this@VehicleSearchActivity)
                                .setTitle("Acredor")
                                .setMessage(message)
                                .setPositiveButton("OK") { _, _ -> }
                                .show()
                            deletePorRevisarRecord(record)
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@VehicleSearchActivity, "Error actualizando warnings: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("No") { _, _ ->
                deletePorRevisarRecord(record)
            }
            .show()
    }

    private fun deletePorRevisarRecord(record: PorRevisarRecord) {
        val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = sheetsService.spreadsheets().values()
                    .get(yourEventsSpreadSheetID, "PorRevisar!A:B") // Only need street and number to find row
                    .execute()
                val rows = response.getValues() ?: emptyList()
                var rowIndex: Int? = null
                rows.forEachIndexed { index, row ->
                    if (row.size >= 2 && row[0].toString() == record.street && row[1].toString() == record.number) {
                        rowIndex = index + 1
                        return@forEachIndexed
                    }
                }
                if (rowIndex != null) {
                    val dimensionRange = DimensionRange()
                        .setSheetId(porRevisarSheetId)
                        .setDimension("ROWS")
                        .setStartIndex(rowIndex!! - 1)
                        .setEndIndex(rowIndex!!)
                    val deleteRequest = DeleteDimensionRequest().setRange(dimensionRange)
                    val request = Request().setDeleteDimension(deleteRequest)
                    val batchRequest = BatchUpdateSpreadsheetRequest().setRequests(listOf(request))
                    sheetsService.spreadsheets().batchUpdate(yourEventsSpreadSheetID, batchRequest).execute()
                    withContext(Dispatchers.Main) {
                        porRevisarRecords.remove(record)
                        updatePorRevisarButton()
                        Toast.makeText(this@VehicleSearchActivity, "Registro eliminado", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@VehicleSearchActivity, "Error eliminando registro: ${e.message}", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
                }
            }
            REQUEST_LOCATION_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    saveNewEvent()
                } else {
                    Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
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

    data class Vehicle(val plate: String, val street: String, val number: String, val registrationDate: String)
    data class ParkingSlot(val latitude: Double, val longitude: Double, val key: String, val distance: Double)
//    data class PorRevisarRecord(val street: String, val number: String, var time: LocalDateTime, var parkingSlotKey: String, val validation: String, var latitude: Double, var longitude: Double) : Parcelable {
//        constructor(parcel: Parcel) : this(
//            parcel.readString()!!,
//            parcel.readString()!!,
//            LocalDateTime.parse(parcel.readString()),
//            parcel.readString()!!,
//            parcel.readString()!!,
//            parcel.readDouble(),
//            parcel.readDouble()
//        ) {}
//
//        override fun writeToParcel(parcel: Parcel, flags: Int) {
//            parcel.writeString(street)
//            parcel.writeString(number)
//            parcel.writeString(time.toString())
//            parcel.writeString(parkingSlotKey)
//            parcel.writeString(validation)
//            parcel.writeDouble(latitude)
//            parcel.writeDouble(longitude)
//        }
//
//        override fun describeContents(): Int = 0
//
//        companion object CREATOR : Parcelable.Creator<PorRevisarRecord> {
//            override fun createFromParcel(parcel: Parcel): PorRevisarRecord {
//                return PorRevisarRecord(parcel)
//            }
//
//            override fun newArray(size: Int): Array<PorRevisarRecord?> {
//                return arrayOfNulls(size)
//            }
//        }
//    }
}