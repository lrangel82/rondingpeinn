package com.larangel.rondingpeinn

import DataRawRondin
import IncidenciaAdapter
import MySettings
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


class ListadoIncidenciasActivity : AppCompatActivity() {
    private var mySettings: MySettings? = null
    private var dataRaw: DataRawRondin? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_listado_incidencias)

        mySettings = MySettings(this)
        dataRaw = DataRawRondin(this,CoroutineScope(Dispatchers.IO))

        val tipo = intent.getStringExtra("TIPO") ?: "Desconocido"
        val FechaIncidencias = LocalDate.parse( intent.getStringExtra("FECHA")) ?: LocalDate.now()
        findViewById<TextView>(R.id.txtTipoTitulo).text = "Incidencias $tipo (${FechaIncidencias})"
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }

        val incidencias = dataRaw?.getIncidenciasEventosTipo(tipo, FechaIncidencias as LocalDate) ?:mutableListOf<List<Any>>()

        val recycler = findViewById<RecyclerView>(R.id.recyclerIncidencias)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = IncidenciaAdapter(incidencias){ record ->
            shareViaWhatsApp(record as List<String>)
        }
    }
    private fun shareViaWhatsApp(IncidenciaRow: List<String>) {
        val calle = IncidenciaRow[0]
        val numero = IncidenciaRow[1]
        val fechaHora= IncidenciaRow[3]
        val tipo = IncidenciaRow[4]
        val localPhotoPath = IncidenciaRow[5]
        val descripcion = IncidenciaRow[6]
        val text = "$tipo $calle:$numero a las $fechaHora descripcion: $descripcion "

        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TEXT, text)
        intent.setPackage("com.whatsapp")

        if (localPhotoPath.isNotEmpty()) {
            val file = File(localPhotoPath)
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

}