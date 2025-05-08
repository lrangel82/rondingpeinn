package com.larangel.rondingpeinn

import PermisosModal
import PermisosRVAdapter
import SheetRow
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class PermisosActivity : AppCompatActivity() {
    private lateinit var fetchButton: Button
    private lateinit var btnCerrar: Button
    private lateinit var sheetRecyclerView: RecyclerView

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

        // Initialize the new adapter
        permisosRVAdapter = PermisosRVAdapter(permisosModalArrayList)
        sheetRecyclerView.layoutManager = LinearLayoutManager(this)

        // Set the new adapter
        sheetRecyclerView.adapter = permisosRVAdapter

        fetchButton.setOnClickListener {
            //val url = "https://docs.google.com/spreadsheets/d/e/2PACX-1vQlLsJUKrUv22ulAXvBXLwT2rcMhSOKOS4BiIAPc-WZQTssJ5S0LeIWDwtgYW90fI-IZaE7sEOW1hVP/pub?output=csv"
            val url = "https://docs.google.com/spreadsheets/d/e/2PACX-1vQDS4yz6MLos5Se56p0CUIJrA1jO6nLr7JC_eZiTT3s8xg8hEI2gSTeTrYSqVHyrTVvw6Z15KsmhSUO/pub?output=csv"
            if (url.isNotEmpty()) {
                fetchSheetData(url)
            }
        }

        btnCerrar.setOnClickListener{
            val intent: Intent = Intent(this, MainActivity::class.java )
            startActivity(intent)
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun fetchSheetData(url: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {

                // Convert Google Sheets URL to CSV export URL
                val csvUrl = url.replace("/edit#gid=", "/export?format=csv&gid=")
                val response = URL(csvUrl).readText()
                val rows = parseCSV(response)

                // Clear the existing data
                permisosModalArrayList.clear()

                var flag=0

                // Convert CSV rows to UserModal objects
                for (row in rows) {
                    if(flag==0) {
                        flag=1;
                        continue;
                    }
                    if (row.cells.size >= 4) {

                        val formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")
                        // Ensure the row has enough columns
                        val userModal = PermisosModal(
                            domicilio = row.cells[0],
                            tipo = row.cells[1],
                            fechaInicio = LocalDate.parse(row.cells[2],formatter),
                            fechaFin = LocalDate.parse(row.cells[3],formatter),
                            descripcion =  row.cells[4],
                            aprobado = row.cells[5] != "1",
                            procesado = row.cells[6] != "1"
                        )
                        permisosModalArrayList.add(userModal)
                    }
                }

                // Update the adapter on the main thread
                withContext(Dispatchers.Main) {
                    permisosRVAdapter.notifyDataSetChanged()
                }
            } catch (e: Exception) {

                withContext(Dispatchers.Main) {
                    //Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    // Handle errors (e.g., show a toast or log the error)
                    println("Error: ${e.message}")
                }
            }
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
}