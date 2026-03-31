package com.larangel.rondingpeinn

import DataRawRondin
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
    private var dataRaw: DataRawRondin? = null
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
        sheetRecyclerView = findViewById(R.id.sheetRecyclerView)
        progressBar = findViewById(R.id.progressBar)

        // Initialize the new adapter
        permisosRVAdapter = PermisosRVAdapter(permisosModalArrayList)
        sheetRecyclerView.layoutManager = LinearLayoutManager(this)

        // Set the new adapter
        sheetRecyclerView.adapter = permisosRVAdapter

        mySettings=MySettings(this)

        dataRaw = DataRawRondin(this, CoroutineScope(Dispatchers.IO))

        btnCerrar.setOnClickListener{
//            val intent: Intent = Intent(this, MainActivity::class.java )
//            startActivity(intent)
            finish()
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
        progressBar.visibility = View.VISIBLE
        val permisosData = dataRaw?.getPermisosCache_DeHoy()

        val stringTrue = arrayOf("1", "Si", "si", "SI", "x", "X")
        permisosData?.forEach { permiso ->
            try{
                val userModal = PermisosModal(
                    fechaCreado = parseLenientDateTime(permiso[0].toString()),
                    calle = permiso[1].toString(),
                    numero = permiso[2].toString(),
                    solicitante = permiso[3].toString(),
                    correo = permiso[4].toString(),
                    tipoAcceso = permiso[5].toString(),
                    tipo = permiso[6].toString(),
                    fechaInicio = parseLenientDate(permiso[7].toString()),
                    fechaFin = parseLenientDate(permiso[8].toString()),
                    descripcion = permiso[9].toString(),
                    nombrePersonas = permiso[10].toString(),
                    aprobado = stringTrue.contains(permiso[11]),
                    motivo_denegado = permiso[12].toString(),
                    procesado = stringTrue.contains(permiso[13])
                )

                permisosModalArrayList.add(userModal)

            }catch (e: Exception) {
                println("Error: ${e.message}")
            }
        }
        permisosRVAdapter.notifyDataSetChanged()
        progressBar.visibility = View.GONE

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