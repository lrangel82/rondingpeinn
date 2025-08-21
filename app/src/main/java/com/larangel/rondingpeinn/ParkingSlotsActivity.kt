package com.larangel.rondingpeinn

import MySettings
import ParkingSlotAdapter
import ParkingSlot
import ParkingSlotWithRow
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
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
import com.google.api.services.sheets.v4.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope

class ParkingSlotsActivity : AppCompatActivity() {
    private var mySettings: MySettings? = null
    private lateinit var sheetsService: Sheets
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var parkingSlotsRecyclerView: RecyclerView
    private lateinit var addButton: Button
    private lateinit var backButton: Button
    private val parkingSlots = mutableListOf<ParkingSlotWithRow>()
    private var parkingSlotsSheetId: Int = 0
    private val REQUEST_LOCATION_PERMISSION = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_parking_slots) // Assume layout exists with RecyclerView id=parkingSlotsRecyclerView, Button id=addButton, Button id=backButton

        parkingSlotsRecyclerView = findViewById(R.id.parkingSlotsRecyclerView)
        addButton = findViewById(R.id.addButton)
        backButton = findViewById(R.id.backButton)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mySettings = MySettings(this)

        initializeGoogleServices()

        parkingSlotsRecyclerView.layoutManager = LinearLayoutManager(this)

        loadParkingSlots()

        addButton.setOnClickListener {
            addNewParkingSlot()
        }

        backButton.setOnClickListener {
            finish()
        }
    }

    private fun initializeGoogleServices() {
        val serviceAccountStream = applicationContext.resources.openRawResource(R.raw.json_google_service_account)
        val credential = GoogleCredential.fromStream(serviceAccountStream)
            .createScoped(listOf("https://www.googleapis.com/auth/drive", "https://www.googleapis.com/auth/spreadsheets"))
        sheetsService = Sheets.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("My First Project")
            .build()

        // Get sheet ID for ParkingSlots
        val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val spreadsheet = sheetsService.spreadsheets().get(yourEventsSpreadSheetID).execute()
                for (sheet in spreadsheet.sheets) {
                    if (sheet.properties.title == "ParkingSlots") {
                        parkingSlotsSheetId = sheet.properties.sheetId
                        break
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ParkingSlotsActivity, "Error initializing sheet ID: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadParkingSlots() {
        val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = sheetsService.spreadsheets().values()
                    .get(yourEventsSpreadSheetID, "ParkingSlots!A:C")
                    .execute()
                val rows = response.getValues() ?: emptyList()
                parkingSlots.clear()
                rows.forEachIndexed { index, row ->
                    if (row.size >= 3) {
                        val lat = row[0].toString().toDoubleOrNull() ?: return@forEachIndexed
                        val lon = row[1].toString().toDoubleOrNull() ?: return@forEachIndexed
                        val key = row[2].toString()
                        parkingSlots.add(ParkingSlotWithRow(ParkingSlot(lat, lon, key), index + 1))
                    }
                }
                withContext(Dispatchers.Main) {
                    parkingSlotsRecyclerView.adapter =
                        ParkingSlotAdapter(parkingSlots, ::editParkingSlot, ::deleteParkingSlot)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ParkingSlotsActivity, "Error loading parking slots: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun addNewParkingSlot() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    val lat = location.latitude
                    val lon = location.longitude
                    val input = EditText(this)
                    input.hint = "Enter Parking Slot Key"
                    AlertDialog.Builder(this)
                        .setTitle("Add New Parking Slot")
                        .setMessage("Latitude: $lat\nLongitude: $lon")
                        .setView(input)
                        .setPositiveButton("Add") { _, _ ->
                            val key = input.text.toString().trim()
                            if (key.isNotEmpty()) {
                                saveNewParkingSlot(lat, lon, key)
                            } else {
                                Toast.makeText(this, "Key cannot be empty", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .setNegativeButton("Cancel") { _, _ -> }
                        .show()
                } else {
                    Toast.makeText(this, "Unable to get location", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION_PERMISSION)
        }
    }

    private fun saveNewParkingSlot(lat: Double, lon: Double, key: String) {
        val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
        val values = listOf(listOf(lat.toString(), lon.toString(), key))
        val body = ValueRange().setValues(values)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                sheetsService.spreadsheets().values()
                    .append(yourEventsSpreadSheetID, "ParkingSlots!A:C", body)
                    .setValueInputOption("RAW")
                    .execute()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ParkingSlotsActivity, "Parking slot added", Toast.LENGTH_SHORT).show()
                    loadParkingSlots()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ParkingSlotsActivity, "Error adding parking slot: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun editParkingSlot(slotWithRow: ParkingSlotWithRow) {
        val slot = slotWithRow.slot
        val latInput = EditText(this)
        latInput.setText(slot.latitude.toString())
        val lonInput = EditText(this)
        lonInput.setText(slot.longitude.toString())
        val keyInput = EditText(this)
        keyInput.setText(slot.key)

        val layout = android.widget.LinearLayout(this)
        layout.orientation = android.widget.LinearLayout.VERTICAL
        layout.addView(latInput)
        layout.addView(lonInput)
        layout.addView(keyInput)

        AlertDialog.Builder(this)
            .setTitle("Edit Parking Slot")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val newLat = latInput.text.toString().toDoubleOrNull()
                val newLon = lonInput.text.toString().toDoubleOrNull()
                val newKey = keyInput.text.toString().trim()
                if (newLat != null && newLon != null && newKey.isNotEmpty()) {
                    updateParkingSlot(slotWithRow.row, newLat, newLon, newKey)
                } else {
                    Toast.makeText(this, "Invalid input", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel") { _, _ -> }
            .show()
    }

    private fun updateParkingSlot(row: Int, lat: Double, lon: Double, key: String) {
        val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
        val values = listOf(listOf(lat.toString(), lon.toString(), key))
        val body = ValueRange().setValues(values)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                sheetsService.spreadsheets().values()
                    .update(yourEventsSpreadSheetID, "ParkingSlots!A$row:C$row", body)
                    .setValueInputOption("RAW")
                    .execute()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ParkingSlotsActivity, "Parking slot updated", Toast.LENGTH_SHORT).show()
                    loadParkingSlots()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ParkingSlotsActivity, "Error updating parking slot: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun deleteParkingSlot(slotWithRow: ParkingSlotWithRow) {
        AlertDialog.Builder(this)
            .setTitle("Delete Parking Slot")
            .setMessage("Are you sure you want to delete this parking slot?")
            .setPositiveButton("Delete") { _, _ ->
                performDelete(slotWithRow.row)
            }
            .setNegativeButton("Cancel") { _, _ -> }
            .show()
    }

    private fun performDelete(row: Int) {
        val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val dimensionRange = DimensionRange()
                    .setSheetId(parkingSlotsSheetId)
                    .setDimension("ROWS")
                    .setStartIndex(row - 1)
                    .setEndIndex(row)
                val deleteRequest = DeleteDimensionRequest().setRange(dimensionRange)
                val request = Request().setDeleteDimension(deleteRequest)
                val batchRequest = BatchUpdateSpreadsheetRequest().setRequests(listOf(request))
                sheetsService.spreadsheets().batchUpdate(yourEventsSpreadSheetID, batchRequest).execute()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ParkingSlotsActivity, "Parking slot deleted", Toast.LENGTH_SHORT).show()
                    loadParkingSlots()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ParkingSlotsActivity, "Error deleting parking slot: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                addNewParkingSlot()
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
