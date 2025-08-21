package com.larangel.rondingpeinn

import MySettings
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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlin.math.sqrt

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
                            AlertDialog.Builder(this@PorRevisarListActivity)
                                .setTitle("Acredor")
                                .setMessage(message)
                                .setPositiveButton("OK") { _, _ -> }
                                .show()
                            deletePorRevisarRecord(record)
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@PorRevisarListActivity, "Error actualizando warnings: ${e.message}", Toast.LENGTH_SHORT).show()
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

