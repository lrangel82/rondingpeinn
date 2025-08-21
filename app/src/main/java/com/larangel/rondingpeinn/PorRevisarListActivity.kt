package com.larangel.rondingpeinn

import MySettings
import EventModal
import PorRevisarRecord
import PorRevisarAdapter
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.Duration
import java.io.File as JavaFile
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlin.math.sqrt
import android.content.Intent
import android.provider.MediaStore
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.io.FileOutputStream
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import androidx.core.graphics.scale
import java.io.ByteArrayOutputStream
import java.time.LocalDate

class PorRevisarListActivity : AppCompatActivity() {
    private var mySettings: MySettings? = null
    private lateinit var sheetsService: Sheets
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var currentLocationText: TextView
    private lateinit var porRevisarRecyclerView: RecyclerView
    private lateinit var backButton: Button
    private var currentLat: Double = 0.0
    private var currentLon: Double = 0.0
    private val porRevisarRecords = mutableListOf<PorRevisarRecord>()
    private var porRevisarSheetId: Int = 0
    private var domicilioWarningsSheetId: Int = 0
    private val REQUEST_LOCATION_PERMISSION = 101
    private val REQUEST_IMAGE_CAPTURE = 102

    private var verificationPhotoUri: Uri? = null
    private var verificationRecord: PorRevisarRecord? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_por_revisar_list)

        currentLocationText = findViewById(R.id.currentLocationText)
        porRevisarRecyclerView = findViewById(R.id.porRevisarRecyclerView)
        backButton = findViewById(R.id.backButton)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mySettings = MySettings(this)

        initializeGoogleServices()

        // Back button click listener
        backButton.setOnClickListener {
            finish() // Returns to the previous activity
        }

        loadPorRevisar()
        getCurrentLocation()
    }

    private fun initializeGoogleServices() {
        val serviceAccountStream = applicationContext.resources.openRawResource(R.raw.json_google_service_account)
        val credential = GoogleCredential.fromStream(serviceAccountStream)
            .createScoped(listOf("https://www.googleapis.com/auth/drive", "https://www.googleapis.com/auth/spreadsheets"))
        sheetsService = Sheets.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("My First Project")
            .build()

        // Get sheet ID for PorRevisar and DomicilioWarnings
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
                    Toast.makeText(this@PorRevisarListActivity, "Error initializing sheet ID: ${e.message}", Toast.LENGTH_SHORT).show()
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
                    updateList()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PorRevisarListActivity, "Error loading PorRevisar: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    currentLat = location.latitude
                    currentLon = location.longitude
                    currentLocationText.text = "Current Location: Lat ${String.format("%.6f", currentLat)}, Lon ${String.format("%.6f", currentLon)}"
                    updateList()
                }
            }
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION_PERMISSION)
        }
    }

    private fun updateList() {
        val now = LocalDateTime.now()
        val filtered = porRevisarRecords.filter { Duration.between(it.time, now).toHours() <= 20 }
        val sorted = filtered.sortedBy { calculateDistance(currentLat, currentLon, it.latitude, it.longitude) }
        porRevisarRecords.clear()
        porRevisarRecords.addAll(filtered)
        porRevisarRecyclerView.layoutManager = LinearLayoutManager(this)
        porRevisarRecyclerView.adapter = PorRevisarAdapter(sorted) { record ->
            showVerificationAlert(record)
        }
    }

    private fun showVerificationAlert(record: PorRevisarRecord) {
        AlertDialog.Builder(this)
            .setTitle("Verificar Cochera")
            .setMessage("¿La cochera está vacía en ${record.street} ${record.number}?")
            .setPositiveButton("Sí") { _, _ ->
                captureVerificationPhoto(record)
            }
            .setNegativeButton("No") { _, _ ->
                deletePorRevisarRecord(record)
            }
            .show()
    }

    private fun captureVerificationPhoto(record: PorRevisarRecord) {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val photoFile = createVerificationImageFile()
        if (photoFile != null) {
            verificationPhotoUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", photoFile)
            intent.putExtra(MediaStore.EXTRA_OUTPUT, verificationPhotoUri)
            verificationRecord = record
            if (intent.resolveActivity(packageManager) != null) {
                startActivityForResult(intent, REQUEST_IMAGE_CAPTURE)
            } else {
                Toast.makeText(this, "No camera app available", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Failed to create image file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createVerificationImageFile(): File? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
            val storageDir = getExternalFilesDir("photos")
            File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
        } catch (e: Exception) {
            Toast.makeText(this, "Error creating image file: ${e.message}", Toast.LENGTH_SHORT).show()
            null
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            verificationPhotoUri?.let { uri ->
                val localPhotoPath = saveVerificationPhotoLocally(uri)
                if (localPhotoPath != null) {
                    val verificationRecordCopy = verificationRecord
                    if (verificationRecordCopy != null) {
                        saveCocheraVaciaEvent(verificationRecordCopy, localPhotoPath)
                    } else {
                        Toast.makeText(this, "Error: Verification record is null", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Failed to save verification photo locally", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveVerificationPhotoLocally(uri: Uri): String? {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val reducedBitmap = bitmap.scale(300, 200)
            val outputStream = ByteArrayOutputStream()
            reducedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val byteArray = outputStream.toByteArray()

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
            val storageDir = getExternalFilesDir("photos")
            val localFile = File(storageDir, "thumbnail_${timeStamp}.jpg")
            FileOutputStream(localFile).use { fos ->
                fos.write(byteArray)
                fos.flush()
            }
            return localFile.absolutePath
        } catch (e: Exception) {
            Toast.makeText(this, "Error saving verification photo: ${e.message}", Toast.LENGTH_SHORT).show()
            return null
        }
    }

    private fun saveCocheraVaciaEvent(record: PorRevisarRecord, localPhotoPath: String) {
        val plate = "${record.street}${record.number}"
        val date = LocalDate.now()
        val time = LocalDateTime.now()
        val event = EventModal(plate, date, time, localPhotoPath, "CocheraVacia")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                val formattedDate = date.format(dateFormatter)
                val formattedTime = time.format(timeFormatter)

                val valuesEventos = listOf(
                    listOf(
                        event.plate,
                        formattedDate,
                        formattedTime,
                        event.localPhotoPath,
                        event.parkingSlotKey
                    )
                )
                val bodyEventos = ValueRange().setValues(valuesEventos)
                val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
                sheetsService.spreadsheets().values()
                    .append(yourEventsSpreadSheetID, "AutosEventos!A:E", bodyEventos)
                    .setValueInputOption("RAW")
                    .execute()

                incrementDomicilioWarnings(record, formattedDate, formattedTime, event)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PorRevisarListActivity, "Error saving CocheraVacia event: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun incrementDomicilioWarnings(record: PorRevisarRecord, formattedDate: String, formattedTime: String, newEvent: EventModal) {
        val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
        lifecycleScope.launch(Dispatchers.IO) {
            try {
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
                    if (newCount > 3) {
                        saveToMultasGeneradas(record, newEvent, formattedDate)
                        shareViaWhatsApp(record, newEvent, formattedDate, formattedTime,newCount)
                    } else {
                        shareViaWhatsApp(record, newEvent, formattedDate, formattedTime,newCount)
                        Toast.makeText(this@PorRevisarListActivity, "Warning recorded, count: $newCount", Toast.LENGTH_SHORT).show()
                    }
                    deletePorRevisarRecord(record)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PorRevisarListActivity, "Error incrementing warning: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveToMultasGeneradas(record: PorRevisarRecord, newEvent: EventModal, formattedDate: String) {
        val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Fetch the last event with matching parkingSlotKey
                val response = sheetsService.spreadsheets().values()
                    .get(yourEventsSpreadSheetID, "AutosEventos!A:E")
                    .execute()
                val rows = response.getValues() ?: emptyList()
                val matchingEvents = rows.filter { row ->
                    row.size >= 5 && row[4].toString() == record.parkingSlotKey
                }.sortedByDescending { row ->
                    LocalDateTime.parse(row[2].toString(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                }
                val lastEvent = if (matchingEvents.isNotEmpty()) matchingEvents.first() else null

                val concatenatedSlots = if (lastEvent != null) {
                    val lastDate = lastEvent[1].toString()
                    val lastSlotKey = lastEvent[4].toString()
                    "$lastSlotKey ($lastDate), CocheraVacia ($formattedDate)"
                } else {
                    "CocheraVacia ($formattedDate)"
                }
                val plateLastEvent = if (lastEvent != null) {
                    lastEvent[0].toString()
                }else {
                    "N/A"
                }

                val values = listOf(
                    listOf(
                        formattedDate,
                        record.street,
                        record.number,
                        plateLastEvent,
                        concatenatedSlots
                    )
                )
                val body = ValueRange().setValues(values)
                sheetsService.spreadsheets().values()
                    .append(yourEventsSpreadSheetID, "MultasGeneradas!A:E", body)
                    .setValueInputOption("RAW")
                    .execute()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PorRevisarListActivity, "Fine recorded in MultasGeneradas", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PorRevisarListActivity, "Error saving to MultasGeneradas: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun shareViaWhatsApp(record: PorRevisarRecord, newEvent: EventModal, formattedDate: String, formattedTime: String, conteoWarning: Int) {
        val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = sheetsService.spreadsheets().values()
                    .get(yourEventsSpreadSheetID, "AutosEventos!A:E")
                    .execute()
                val rows = response.getValues() ?: emptyList()
                val matchingEvents = rows.filter { row ->
                    row.size >= 5 && row[4].toString() == record.parkingSlotKey
                }.sortedByDescending { row ->
                    LocalDateTime.parse(row[2].toString(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                }
                val lastEvent = if (matchingEvents.isNotEmpty()) matchingEvents.first() else null

                val text = if (conteoWarning < 3) {
                    "Aviso #${conteoWarning} al Domicilio en calle ${record.street} numero ${record.number}. Detalle del evento: CocheraVacia ($formattedDate $formattedTime)"
                }else{
                    "*Multa* tercer aviso a Domicilio en calle ${record.street} numero ${record.number} amerita multa por cochera vacia. Detalle del evento: CocheraVacia ($formattedDate $formattedTime)"
                }

                val intent = Intent(Intent.ACTION_SEND_MULTIPLE)
                intent.type = "text/plain"
                intent.putExtra(Intent.EXTRA_TEXT, text)
                intent.setPackage("com.whatsapp")

                val imageUris = ArrayList<Uri>()
                // Add new event photo
                if (newEvent.localPhotoPath.isNotEmpty()) {
                    val file = JavaFile(newEvent.localPhotoPath)
                    if (file.exists()) {
                        val uri = FileProvider.getUriForFile(this@PorRevisarListActivity, "${packageName}.fileprovider", file)
                        imageUris.add(uri)
                    }
                }
                // Add last event photo if exists
                if (lastEvent != null) {
                    val lastPhotoPath = lastEvent[3].toString()
                    if (lastPhotoPath.isNotEmpty()) {
                        val file = JavaFile(lastPhotoPath)
                        if (file.exists()) {
                            val uri = FileProvider.getUriForFile(this@PorRevisarListActivity, "${packageName}.fileprovider", file)
                            imageUris.add(uri)
                        }
                    }
                }

                if (imageUris.isNotEmpty()) {
                    intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, imageUris)
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                withContext(Dispatchers.Main) {
                    try {
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this@PorRevisarListActivity, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PorRevisarListActivity, "Error sharing via WhatsApp: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
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
                        updateList()
                        Toast.makeText(this@PorRevisarListActivity, "Registro eliminado", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PorRevisarListActivity, "Error eliminando registro: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation()
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
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


}