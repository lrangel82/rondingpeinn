package com.larangel.rondingpeinn

import MySettings
import PermisosModal
import PermisosRVAdapter
import SheetRow
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class PermisosActivity : AppCompatActivity() {
    private var mySettings: MySettings? = null
    private lateinit var fetchButton: Button
    private lateinit var btnCerrar: Button
    private lateinit var sheetRecyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar

    // Use the new adapter
    private lateinit var permisosRVAdapter: PermisosRVAdapter

    // List to hold permisos data
    private val permisosModalArrayList = ArrayList<PermisosModal>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_permisos)

        btnCerrar =findViewById(R.id.btnCerrarPermisos)
        fetchButton = findViewById(R.id.fetchButton)
        sheetRecyclerView = findViewById(R.id.sheetRecyclerView)
        progressBar = findViewById(R.id.progressBar)

        // Initialize the new adapter
        permisosRVAdapter = PermisosRVAdapter(permisosModalArrayList)
        sheetRecyclerView.layoutManager = LinearLayoutManager(this)

        // Set the new adapter
        sheetRecyclerView.adapter = permisosRVAdapter

        mySettings=MySettings(this)

        fetchButton.setOnClickListener {
            fetchSheetData()
        }

        btnCerrar.setOnClickListener{
            val intent: Intent = Intent(this, MainActivity::class.java )
            startActivity(intent)
        }

        //First INIT LOADING DATA....
        fetchSheetData()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun parseLenientDateTime(dateTimeString: String): LocalDateTime {
        val formats = listOf(
            "d/MM/yyyy HH:mm:ss",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy/MM/dd'T'HH:mm:ss",
            "dd-MM-yyyy HH:mm:ss",
            "dd/MM/yyyy HH:mm:ss",
            "MM-dd-yyyy HH:mm:ss",
            "MM/dd/yyyy HH:mm:ss",
            "yyyyMMdd HHmmss",
            "yyyyMMdd'T'HHmmss"
        ).map { DateTimeFormatter.ofPattern(it) }

        for (format in formats) {
            try {
                return LocalDateTime.parse(dateTimeString, format)
            } catch (e: Exception) {
                // Try the next format if parsing fails
            }
        }
        return LocalDateTime.MIN // Return null if no format matches
    }
    private fun parseLenientDate(dateTimeString: String): LocalDate {
        val formats = listOf(
            "d/MM/yyyy",
            "yyyy/MM/dd",
            "yyyy-MM-dd",
            "dd-MM-yyyy",
            "dd/MM/yyyy",
            "MM-dd-yyyy",
            "MM/dd/yyyy",
            "yyyyMMdd"
        ).map { DateTimeFormatter.ofPattern(it) }

        for (format in formats) {
            try {
                return LocalDate.parse(dateTimeString, format)
            } catch (e: Exception) {
                // Try the next format if parsing fails
            }
        }
        return LocalDate.MIN // Return null if no format matches
    }

    private fun fetchSheetData() {
        //Coto1: https://docs.google.com/spreadsheets/d/e/2PACX-1vTk443om2jiXzF62FFliGAhjqHZikVR-1ziu3lg8-wk3TmWrd31fawCu_z7S0Kp41zTxaJnSZXLexRz/pub?output=csv
        //Coto2: https://docs.google.com/spreadsheets/d/e/2PACX-1vTXQSO3BMLsPdRW3nF-b6wWJlbBPvf_0cF9v6dPkyuVN0ihKOMbhVGDPx5PqHq4Ai6u_aalCIMbK_Zt/pub?output=csv
        val url = mySettings?.getString("url_googlesheet_permisos","")!!
        if (url.isNotEmpty()) {
            progressBar.visibility = View.VISIBLE
            CoroutineScope(Dispatchers.IO).launch {
                try {

                    // Convert Google Sheets URL to CSV export URL
                    val csvUrl = url.replace("/edit#gid=", "/export?format=csv&gid=")
                    val response = URL(csvUrl).readText()
                    val rows = parseCSV(response)

                    // Clear the existing data
                    permisosModalArrayList.clear()

                    var flag = 0

                    // Convert CSV rows to UserModal objects
                    for (row in rows) {
                        if (flag == 0) {
                            flag = 1;
                            continue;
                        }
                        if (row.cells.size >= 4) {

                            val stringTrue = arrayOf("1", "Si", "si", "SI", "x", "X")
                            // Ensure the row has enough columns
                            val userModal = PermisosModal(
                                fechaCreado = parseLenientDateTime(row.cells[0]),
                                calle = row.cells[1],
                                numero = row.cells[2],
                                solicitante = row.cells[3],
                                correo = row.cells[4],
                                tipoAcceso = row.cells[5],
                                tipo = row.cells[6],
                                fechaInicio = parseLenientDate(row.cells[7]),
                                fechaFin = parseLenientDate(row.cells[8]),
                                descripcion = row.cells[9],
                                nombrePersonas = row.cells[10],
                                aprobado = stringTrue.contains(row.cells[11]),
                                motivo_denegado = row.cells[12],
                                procesado = stringTrue.contains(row.cells[13])
                            )
                            if (filterPermisos(userModal)) {
                                permisosModalArrayList.add(userModal)
                            }
                        }
                    }

                    // Update the adapter on the main thread
                    withContext(Dispatchers.Main) {
                        permisosRVAdapter.notifyDataSetChanged()
                    }
                } catch (e: Exception) {

                    withContext(Dispatchers.Main) {
                        //Toast.makeText(currentCoroutineContext(), e.message, Toast.LENGTH_LONG).show()
                        // Handle errors (e.g., show a toast or log the error)
                        println("Error: ${e.message}")
                    }
                }
                progressBar.visibility = View.GONE
            } //End Coroutine
        }
    }

    private fun parseCSV(csvData: String): List<SheetRow> {
        return csvData.split("\n")
            .filter { it.isNotBlank() }
            .map { row ->

                // Split by comma, but respect quoted values
                val cells = row.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex())
                    .map { cell ->
                        cell.trim()
                            .removeSurrounding("\"")
                            .replace("\"\"", "\"")
                    }
                SheetRow(cells)
            }
    }

    private fun filterPermisos(itemPermiso: PermisosModal ) : Boolean {
        val currentDate = LocalDate.now()
        when {
            currentDate.isBefore(itemPermiso.fechaInicio) -> return false
            currentDate.isAfter(itemPermiso.fechaFin) -> return false
        }
        return itemPermiso.procesado
    }
}