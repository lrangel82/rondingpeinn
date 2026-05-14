package com.larangel.rondy

import DataRawRondin
import IncidenciaAdapter
import MySettings
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
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

        mySettings = MySettings(applicationContext)
        dataRaw = DataRawRondin(applicationContext,CoroutineScope(Dispatchers.IO))

        val tipo = intent.getStringExtra("TIPO") ?: "Desconocido"
        val FechaIncidencias = LocalDate.parse( intent.getStringExtra("FECHA")) ?: LocalDate.now()
        findViewById<TextView>(R.id.txtTipoTitulo).text = "Incidencias $tipo (${FechaIncidencias})"
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }

        val incidencias = dataRaw?.getIncidenciasEventosTipo(tipo, FechaIncidencias as LocalDate) ?:mutableListOf<List<Any>>()

        val recycler = findViewById<RecyclerView>(R.id.recyclerIncidencias)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = IncidenciaAdapter(incidencias){ record ->
            mostrarDialogoPrevisualizacion(record as List<String>)
        }
    }

    private fun mostrarDialogoPrevisualizacion(IncidenciaRow: List<String>) {
        val calle = IncidenciaRow[0]
        val numero = IncidenciaRow[1]
        val fechaHora= IncidenciaRow[3]
        val tipo = IncidenciaRow[4]
        val localPhotoPath = IncidenciaRow.getOrNull(5).toString()
        val descripcion = IncidenciaRow.getOrNull(6).toString()
        val textData = "$tipo $calle:$numero a las $fechaHora descripcion: $descripcion "

        val builder = AlertDialog.Builder(this)
        val inflater = layoutInflater
        val dialogLayout = inflater.inflate(R.layout.dialogo_preview_photo, null)

        val imgPreview = dialogLayout.findViewById<ImageView>(R.id.imgPreviewLarge)
        val etMensaje = dialogLayout.findViewById<EditText>(R.id.etMensajeWhatsapp)

        // Cargar la imagen y el texto
        val photoUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", File(localPhotoPath))
        photoUri?.let { imgPreview.setImageURI(it) }
        etMensaje.setText(textData)

        builder.setView(dialogLayout)
            .setPositiveButton("Enviar WhatsApp") { _, _ ->
                val mensaje = etMensaje.text.toString()
                enviarImagenIndividualWhatsapp(photoUri, mensaje)
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }

        builder.create().show()
    }
    private fun enviarImagenIndividualWhatsapp(uri: Uri?, mensaje: String) {
        if (uri == null) return

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, mensaje)
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "WhatsApp no instalado", Toast.LENGTH_SHORT).show()
        }
    }

//    private fun shareViaWhatsApp(IncidenciaRow: List<String>) {
//        val calle = IncidenciaRow[0]
//        val numero = IncidenciaRow[1]
//        val fechaHora= IncidenciaRow[3]
//        val tipo = IncidenciaRow[4]
//        val localPhotoPath = IncidenciaRow.getOrNull(5).toString()
//        val descripcion = IncidenciaRow.getOrNull(6).toString()
//        val text = "$tipo $calle:$numero a las $fechaHora descripcion: $descripcion "
//
//        val intent = Intent(Intent.ACTION_SEND)
//        intent.type = "text/plain"
//        intent.putExtra(Intent.EXTRA_TEXT, text)
//        intent.setPackage("com.whatsapp")
//
//        if (localPhotoPath.isNotEmpty()) {
//            val file = File(localPhotoPath)
//            if (file.exists()) {
//                val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
//                intent.type = "image/jpeg"
//                intent.putExtra(Intent.EXTRA_STREAM, uri)
//                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
//            }
//        }
//
//        try {
//            startActivity(intent)
//        } catch (e: Exception) {
//            Toast.makeText(this, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
//        }
//    }

}