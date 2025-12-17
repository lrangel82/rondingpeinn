package com.larangel.rondingpeinn

import DataRawRondin
import MySettings
import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.scale
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.common.InputImage
import com.larangel.rondingpeinn.VehicleSearchActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Date
import java.util.Locale

class IncidenciasMenu : AppCompatActivity() {
    private var mySettings: MySettings? = null
    private var dataRaw: DataRawRondin? = null

    private lateinit var FechaIncidencias : LocalDate
    private lateinit var radioAntier: RadioButton
    private lateinit var radioAyer: RadioButton
    private lateinit var radioHoy: RadioButton
    private lateinit var btnRegresar: Button
    private lateinit var btnAlteraOrden: Button
    private lateinit var btnFachadaDescuidada: Button
    private lateinit var btnRuidoAlto: Button
    private lateinit var btnAgrecionGuardia: Button
    private lateinit var btnMascotaFalta: Button
    private lateinit var btnBasuraTirada: Button
    private lateinit var btnTrabajadoresFueraHorario: Button
    private lateinit var btnTrabajosSinPermiso: Button
    private lateinit var btnObraSucia: Button

    private var currentPhotoPath: Uri? = null
    private var currentTipo: String? = null
    private var currentDescripcion: String? = null
    private val REQUEST_CAMERA_PERMISSION = 100
    private val REQUEST_LOCATION_PERMISSION = 101
    private val REQUEST_IMAGE_CAPTURE = 102
    private val REQUEST_IMAGE_PICK = 103
    private val REQUEST_STORAGE_PERMISSION = 104

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_incidencias_menu)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        mySettings = MySettings(this)
        dataRaw = DataRawRondin(this,CoroutineScope(Dispatchers.IO))

        // Initialize UI components
        radioAntier                 = findViewById(R.id.radioButtonDayAntier)
        radioAyer                   = findViewById(R.id.radioButtonDayAyer)
        radioHoy                    = findViewById(R.id.radioButtonDayHoy)
        btnAlteraOrden              = findViewById(R.id.btn_AlteraOrden)
        btnFachadaDescuidada        = findViewById(R.id.btn_FachadaDescuidada)
        btnRuidoAlto                = findViewById(R.id.btn_RuidoAlto)
        btnAgrecionGuardia          = findViewById(R.id.btn_AgrecionGuardia)
        btnMascotaFalta             = findViewById(R.id.btn_MascotaFalta)
        btnBasuraTirada             = findViewById(R.id.btn_BasuraTirada)
        btnTrabajadoresFueraHorario = findViewById(R.id.btn_TrabajadoresFueraHorario)
        btnTrabajosSinPermiso       = findViewById(R.id.btn_TrabajosSinPermiso)
        btnObraSucia                = findViewById(R.id.btn_ObraSucia)

        FechaIncidencias = LocalDate.now()
        radioHoy.isChecked = true
        radioAntier.setOnClickListener {
            FechaIncidencias = LocalDate.now().minusDays(2)
            loadIncidencias()
        }
        radioAyer.setOnClickListener {
            FechaIncidencias = LocalDate.now().minusDays(1)
            loadIncidencias()
        }
        radioHoy.setOnClickListener {
            FechaIncidencias = LocalDate.now()
            loadIncidencias()
        }

        btnAlteraOrden.setOnClickListener { showOpcionesDialog("AlteraOrden") }
        btnFachadaDescuidada.setOnClickListener { showOpcionesDialog("FachadaDescuidada") }
        btnRuidoAlto.setOnClickListener { showOpcionesDialog("RuidoAlto") }
        btnAgrecionGuardia.setOnClickListener { showOpcionesDialog("AgrecionGuardia") }
        btnMascotaFalta.setOnClickListener { showOpcionesDialog("MascotaFalta") }
        btnBasuraTirada.setOnClickListener { showOpcionesDialog("BasuraTirada") }
        btnTrabajadoresFueraHorario.setOnClickListener { showOpcionesDialog("TrabajadoresFueraHorario") }
        btnTrabajosSinPermiso.setOnClickListener { showOpcionesDialog("TrabajosSinPermiso") }
        btnObraSucia.setOnClickListener { showOpcionesDialog("ObraSucia") }

        findViewById<Button>(R.id.btnBackIncidenciasMenu).setOnClickListener { finish() }

        loadIncidencias()
    }

    private fun loadIncidencias() {
        //val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val events_AlteraOrden              = dataRaw?.getIncidenciasEventosTipo("AlteraOrden",FechaIncidencias)
                val events_FachadaDescuidada        = dataRaw?.getIncidenciasEventosTipo("FachadaDescuidada",FechaIncidencias)
                val events_RuidoAlto                = dataRaw?.getIncidenciasEventosTipo("RuidoAlto",FechaIncidencias)
                val events_AgrecionGuardia          = dataRaw?.getIncidenciasEventosTipo("AgrecionGuardia",FechaIncidencias)
                val events_MascotaFalta             = dataRaw?.getIncidenciasEventosTipo("MascotaFalta",FechaIncidencias)
                val events_BasuraTirada             = dataRaw?.getIncidenciasEventosTipo("BasuraTirada",FechaIncidencias)
                val events_TrabajadoresFueraHorario = dataRaw?.getIncidenciasEventosTipo("TrabajadoresFueraHorario",FechaIncidencias)
                val events_TrabajosSinPermiso       = dataRaw?.getIncidenciasEventosTipo("TrabajosSinPermiso",FechaIncidencias)
                val events_ObraSucia                = dataRaw?.getIncidenciasEventosTipo("ObraSucia",FechaIncidencias)

                btnAlteraOrden.text                 = "Alteracion del Orden o Vandalismo (${events_AlteraOrden?.size})"
                btnFachadaDescuidada.text           = "Jardin/Fachada descuidada (${events_FachadaDescuidada?.size})"
                btnRuidoAlto.text                   = "Musica/Ruido alto volumen (${events_RuidoAlto?.size})"
                btnAgrecionGuardia.text             = "Agrecion fisica/verbal guardias (${events_AgrecionGuardia?.size})"
                btnMascotaFalta.text                = "Mascota Defecando o sin Correa (${events_MascotaFalta?.size})"
                btnBasuraTirada.text                = "Tirar basura (${events_BasuraTirada?.size})"
                btnTrabajadoresFueraHorario.text    = "Trabajadores fuera de Horario (${events_TrabajadoresFueraHorario?.size})"
                btnTrabajosSinPermiso.text          = "Trabajos sin Permiso (${events_TrabajosSinPermiso?.size})"
                btnObraSucia.text                   = "Obras sucias/no limpias (${events_ObraSucia?.size})"

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@IncidenciasMenu,
                        "Error loading Incidencias: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun showOpcionesDialog(tipo: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Acción requerida")
        builder.setMessage("¿Desea crear un nuevo incidente de $tipo, o mostrar el listado de los existentes?")
        builder.setPositiveButton("Nuevo") { _, _ -> solicitarDescripcion(tipo) }
        builder.setNeutralButton("Listado") { _, _ -> mostrarListadoIncidencias(tipo) }
        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }
    fun solicitarDescripcion(tipo: String) {
        val input = EditText(this)
        input.hint = "Descripción corta"
        AlertDialog.Builder(this)
            .setTitle("Nueva Incidencia")
            .setMessage("Escriba una descripción corta")
            .setView(input)
            .setPositiveButton("Siguiente: Tomar foto") { _, _ ->
                val descripcion = input.text.toString()
                tomarFoto(tipo, descripcion)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    fun tomarFoto(tipo: String, descripcion: String) {
        currentTipo = tipo
        currentDescripcion = descripcion

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val photoFile: File? = createImageFile()
        if (photoFile != null) {
            currentPhotoPath = FileProvider.getUriForFile(this, "${packageName}.fileprovider", photoFile)
            intent.putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoPath)
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
    private fun createImageFile(): File? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
            val storageDir = getExternalFilesDir("photos")
            File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
        } catch (e: Exception) {
            println("LARANGEL Error createImageFile: ${e}")
            e.printStackTrace()
            Toast.makeText(this@IncidenciasMenu, "Error createImageFile: ${e.message}", Toast.LENGTH_SHORT).show()
            null
        }
    }
    private fun pickFromGallery() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_IMAGES
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
    fun solicitarCalleYNumero() {
        val domicilios = dataRaw?.getDomiciliosUbicacion() ?: return
        val calles = domicilios.map { it[0].toString() }.distinct()
        var numeroSeleccionado: String? = null
        var calleSeleccionada: String? = null

        // Seleccionar calle
        AlertDialog.Builder(this)
            .setTitle("Seleccione la Calle")
            .setItems(calles.toTypedArray()) { _, which ->
                calleSeleccionada = calles[which]
                // Filtrar números de la calle seleccionada
                val numeros = domicilios.filter { it[0] == calleSeleccionada }.map { it[1].toString() }
                // Seleccionar número
                AlertDialog.Builder(this)
                    .setTitle("Seleccione el Número")
                    .setItems(numeros.toTypedArray()) { _, numWhich ->
                        numeroSeleccionado = numeros[numWhich]
                        guardarIncidencia(calleSeleccionada!!, numeroSeleccionado!!)
                    }
                    .show()
            }
            .show()
    }
    private fun savePhotoLocally(uri: Uri?, tipo: String): String? {
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
            val localFile = File(storageDir, "incidente_${tipo}_${timeStamp}.jpg")
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
    fun guardarIncidencia(calle: String, numero: String) {
        val localPhotoPath = savePhotoLocally(currentPhotoPath, currentTipo.toString())
        if (localPhotoPath == null) {
            Toast.makeText(this, "Failed to save photo locally", Toast.LENGTH_SHORT).show()
            return
        }
        val tipo = currentTipo ?: return
        val descripcion = currentDescripcion ?: ""
        //val localPhotoPath = currentPhotoPath ?: ""
        val date = LocalDate.now().toString()
        val datetime = LocalDateTime.now().toString()
        val newrow = listOf(calle, numero, date, datetime, tipo, localPhotoPath, descripcion)
        try{
            val result = dataRaw?.addIncidenciaEvento(newrow)
            AlertDialog.Builder(this)
                .setTitle("Confirmación")
                .setMessage(if (result == true) "Registro guardado correctamente." else "Error al guardar el registro.")
                .setPositiveButton("OK") {_, _ ->
                    loadIncidencias()
                    }
                .show()
        }catch (e: Exception) {
            AlertDialog.Builder(this)
                .setTitle("Error al guardar incidencia")
                .setMessage("Error al guardar el registro. $e")
                .setPositiveButton("OK") {_, _ ->
                    loadIncidencias()
                }
                .show()
        }
    }

    fun mostrarListadoIncidencias(tipo: String) {
        val intent = Intent(this, ListadoIncidenciasActivity::class.java)
        intent.putExtra("TIPO", tipo)
        intent.putExtra("FECHA", FechaIncidencias.toString())
        startActivity(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                REQUEST_IMAGE_CAPTURE -> {
                    solicitarCalleYNumero()
                }
                REQUEST_IMAGE_PICK -> {
                    currentPhotoPath = data?.data
                    solicitarCalleYNumero()
                }
            }
        }else {
            Toast.makeText(this, "Image selection cancelled", Toast.LENGTH_SHORT).show()
        }
//        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
//            solicitarCalleYNumero()
//        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CAMERA_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    tomarFoto(currentTipo.toString(), currentDescripcion.toString())
                } else {
                    Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
                }
            }
//            REQUEST_LOCATION_PERMISSION -> {
//                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                    saveNewEvent()
//                } else {
//                    Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
//                }
//            }
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




}