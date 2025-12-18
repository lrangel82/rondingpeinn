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
import android.widget.LinearLayout
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
import com.google.android.material.button.MaterialButton
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
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

class IncidenciasMenu : AppCompatActivity() {
    private var mySettings: MySettings? = null
    private var dataRaw: DataRawRondin? = null

    private lateinit var FechaIncidencias : LocalDate
    private lateinit var radioAntier: RadioButton
    private lateinit var radioAyer: RadioButton
    private lateinit var radioHoy: RadioButton

    private var currentPhotoPath: Uri? = null
    private var currentTipo: String? = null
    private var currentDescripcion: String? = null
    private val REQUEST_CAMERA_PERMISSION = 100
    private val REQUEST_LOCATION_PERMISSION = 101
    private val REQUEST_IMAGE_CAPTURE = 102
    private val REQUEST_IMAGE_PICK = 103
    private val REQUEST_STORAGE_PERMISSION = 104

    private val arrayBotonesIncidencias: ArrayList<Button>? = ArrayList<Button>()
    private var indexBtnClicked: Int = -1
    private val configuraIncidencias = listOf(
        listOf(
            "AlteraOrden",
            "Alteracion del Orden",
            0,
            "V.- Sanciones ARTICULO 12.- B., SE GENERA MULTA DIRECTA EN EL CASO DE: 2.-ESCANDALO POR FIESTAS REUNIONES Ó CUALQUIER TIPO"
        ),
        listOf(
            "FachadaDescuidada",
            "Jardin/Fachada descuidada",
            1,
            "V.- Sanciones ARTICULO 12.- B., SE GENERA MULTA DIRECTA EN EL CASO DE: 1.- JARDIN DESCUIDADO (PASTO CRECIDO) Y/O FACHADA EN MAL ESTADO"
        ),
        listOf(
            "RuidoAlto",
            "Musica/Ruido alto volumen",
            3,
            "V.- RESTRICCIONES ARTICULO 11.- A LOS CONDÓMINOS, FAMILIARES,\n" +
                    "INVITADOS Y EMPLEADOS DOMESTICOS, ADEMÁS DE LAS\n" +
                    "PROHIBICIONES QUE ESTABLECE EL REGLAMENTO DEL\n" +
                    "CONDOMINIO Y ADMINISTRACION, MENCIONADO EN EL\n" +
                    "ARTÍCULO 1 DE ESTE INSTRUMENTO, LES ESTA PROHIBIDO:\n" +
                    "12. PONER MUSICA YA SEA EN SU AUTO O CASA A VOLUMEN\n" +
                    "ALTO QUE PUEDA MOLESTAR A LOS DEMÁS CONDÓMINOS"
        ),
        listOf(
            "AgrecionGuardia",
            "Agrecion fisica/verbal guardias",
            0,
            "V.- Sanciones ARTICULO 12.- B., SE GENERA MULTA DIRECTA EN EL CASO DE: 6.- AGRESION DE CUALQUIER TIPO, FISICA O VERBAL A LOS ELEMENTOS DE SEGURIDA"
        ),
        listOf(
            "MascotaDefeca",
            "Mascota Defecando",
            0,
            "V.- Sanciones ARTICULO 12.- B., SE GENERA MULTA DIRECTA EN EL CASO DE: 5.- MASCOTAS DEFECANDO EN AREAS COMUNES"
        ),
        listOf(
            "MascotaSinCorrea",
            "Mascota sin correa/ladridos/ruidos",
            3,
            "V.- RESTRICCIONES ARTICULO 11.- A LOS CONDÓMINOS, FAMILIARES,\n" +
                    "INVITADOS Y EMPLEADOS DOMESTICOS, ADEMÁS DE LAS\n" +
                    "PROHIBICIONES QUE ESTABLECE EL REGLAMENTO DEL\n" +
                    "CONDOMINIO Y ADMINISTRACION, MENCIONADO EN EL\n" +
                    "ARTÍCULO 1 DE ESTE INSTRUMENTO, LES ESTA PROHIBIDO:\n" +
                    "19. TRANSITAR CON ANIMALES DOMÉSTICOS, SIN CORREA, POR\n" +
                    "LAS ÁREAS DE USO COMÚN Y AJENAS, DARLES DE COMER\n" +
                    "FUERA DE LA UNIDAD PRIVATIVA DEL CONDÓMINO\n" +
                    "PROPIETARIO DE LA MASCOTA; PERMITIR QUE REALICE SUS\n" +
                    "NECESIDADES EN LAS ÁREAS COMUNES DEL CONDOMINIO O\n" +
                    "QUE EL ANIMAL HAGA RUIDOS SIN MOTIVO APARENTE\n" +
                    "(LADRIDOS O RUIDOS OCASIONADOS CON OBJETOS)."
        ),
        listOf(
            "BasuraTirada",
            "Tirar basura",
            3,
            "V.- RESTRICCIONES ARTICULO 11.- A LOS CONDÓMINOS, FAMILIARES,\n" +
                    "INVITADOS Y EMPLEADOS DOMESTICOS, ADEMÁS DE LAS\n" +
                    "PROHIBICIONES QUE ESTABLECE EL REGLAMENTO DEL\n" +
                    "CONDOMINIO Y ADMINISTRACION, MENCIONADO EN EL\n" +
                    "ARTÍCULO 1 DE ESTE INSTRUMENTO, LES ESTA PROHIBIDO:\n" +
                    "3. TIRAR BASURA O ESCOMBRO EN LOTES BALDIOS U OTRAS\n" +
                    "PROPIEDADES, INCLUSO EN ÁREAS COMUNES"
        ),
        listOf(
            "TrabajadoresFueraHorario",
            "Tirar basura",
            3,
            "VIII.- DE LAS FINCAS EN CONSTRUCCION ARTÍCULO 20.- EN LO REFERENTE A CONTRUCCIONES, SE\n" +
                    "BASARÁN EN LAS SIGUIENTES DISPOSICIONES:,\n" +
                    "VII. EL HORARIO DE TRABAJO DEBERÁ SER\n" +
                    "RIGUROSAMENTE RESPETADO, SIENDO ÉSTE DE LUNES A\n" +
                    "VIERNES DE 8:00 A 18:00 HORAS Y SABADOS DE 8:00 A\n" +
                    "14:00 HORAS. DOMINGOS Y DIAS FESTIVOS NO SE\n" +
                    "LABORARA NI SE PERMITIRA EL INGRESO DE\n" +
                    "MATERIALES. "

        ),
        listOf(
            "TrabajosSinPermiso",
            "Trabajos sin Permiso",
            3,
            "Pendiente de agregar en reglamento"
        ),
        listOf(
            "ObraSucia",
            "Obras sucias/no limpias",
            0,
            "V.- Sanciones ARTICULO 12.- B., SE GENERA MULTA DIRECTA EN EL CASO DE: 5.- MASCOTAS DEFECANDO EN AREAS COMUNES"
        ),
        listOf(
            "BocinaClaxon",
            "Bocina o Claxon ",
            3,
            "V.- RESTRICCIONES ARTICULO 11.- A LOS CONDÓMINOS, FAMILIARES,\n" +
                    "INVITADOS Y EMPLEADOS DOMESTICOS, ADEMÁS DE LAS\n" +
                    "PROHIBICIONES QUE ESTABLECE EL REGLAMENTO DEL\n" +
                    "CONDOMINIO Y ADMINISTRACION, MENCIONADO EN EL\n" +
                    "ARTÍCULO 1 DE ESTE INSTRUMENTO, LES ESTA PROHIBIDO:\n" +
                    "9. UTILIZAR LA BOCINA DEL VEHÍCULO DENTRO DEL CONDOMINIO."

        ),
        listOf(
            "Velocidad20km",
            "Mas de 20km/h vehiculos",
            3,
            "V.- RESTRICCIONES ARTICULO 11.- A LOS CONDÓMINOS, FAMILIARES,\n" +
                    "INVITADOS Y EMPLEADOS DOMESTICOS, ADEMÁS DE LAS\n" +
                    "PROHIBICIONES QUE ESTABLECE EL REGLAMENTO DEL\n" +
                    "CONDOMINIO Y ADMINISTRACION, MENCIONADO EN EL\n" +
                    "ARTÍCULO 1 DE ESTE INSTRUMENTO, LES ESTA PROHIBIDO:\n" +
                    "11. TRANSITAR SU VEHÍCULO A MÁS DE 20 KM/HR. DENTRO DEL\n" +
                    "CONDOMINIO Y/O EN SENTIDO CONTRARIO."
        )
    )

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

        //Agregar Botones
        val contenedor = findViewById<LinearLayout>(R.id.layoutBotonesIncidencias)
        configuraIncidencias.forEachIndexed { index, rowIncidencia ->
            val nuevoBoton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonStyle)
            nuevoBoton.text = rowIncidencia[2].toString()
            nuevoBoton.id = index
            // Configura los parámetros de diseño (LayoutParams)
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, // Ancho
                LinearLayout.LayoutParams.WRAP_CONTENT // Alto
            )
            layoutParams.setMargins(16, 8, 16, 8) // Márgenes (izquierda, arriba, derecha, abajo)
            nuevoBoton.layoutParams = layoutParams
            nuevoBoton.setOnClickListener {
                indexBtnClicked = index
                showOpcionesDialog(rowIncidencia[0].toString())
            }
            // Agrega el botón al contenedor
            contenedor.addView(nuevoBoton)
            arrayBotonesIncidencias!!.add(nuevoBoton)
        }

        findViewById<Button>(R.id.btnBackIncidenciasMenu).setOnClickListener { finish() }

        loadIncidencias()
    }

    private fun loadIncidencias() {
        //val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                configuraIncidencias.forEachIndexed { index, rowIncidencia ->
                    val tipo = rowIncidencia[0].toString()
                    val textoBoton = rowIncidencia[1].toString()
                    val eventos = dataRaw?.getIncidenciasEventosTipo(tipo,FechaIncidencias)
                    arrayBotonesIncidencias?.get(index)?.text = "$textoBoton (${eventos?.size})"
                }

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
            val targetWidth = 600
            val factorscale = targetWidth.toFloat()/bitmap.width.toFloat()
            val targetHeight = (bitmap.height.toFloat() * factorscale).toInt()
            val reducedBitmap = bitmap.scale(targetHeight,targetWidth) // Reduced size
            val outputStream = ByteArrayOutputStream()
            reducedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
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
                    if (result == true){
                        guardarWarningIncidencias(newrow)
                    }

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
    fun guardarWarningIncidencias(incidenciaRow: List<String>){
        //listOf(calle, numero, date, datetime, tipo, localPhotoPath, descripcion)
        try {
            val calle = incidenciaRow[0]
            val number = incidenciaRow[1]
            val tipo = incidenciaRow[4]
            val maxWarnings = configuraIncidencias.get(indexBtnClicked)[2] as Int
            val descripcionIncidencia = configuraIncidencias.get(indexBtnClicked)[3].toString()
            val values = listOf(
                calle,
                number,
                0,
                tipo
            )
            val countWarnings = dataRaw?.updateDomicilioWarning(values as List<String>)

            if (countWarnings!! >= maxWarnings) {
                AlertDialog.Builder(this@IncidenciasMenu)
                    .setTitle("Acredor a Multa")
                    .setMessage("El domicilio es acredor a una multa (aviso #$maxWarnings)\n $descripcionIncidencia")
                    .setPositiveButton("OK") { _, _ ->
                        saveToMultasGeneradas( tipo,calle,number, descripcionIncidencia)
                        shareViaWhatsApp(tipo,calle,number, maxWarnings)
                    }
                    .show()
            } else {
                Toast.makeText(this@IncidenciasMenu, "Avisos acumulados a $calle:$number, count: $countWarnings", Toast.LENGTH_SHORT).show()
            }
        }catch (e: Exception) {
            AlertDialog.Builder(this)
                .setTitle("Error al guardar warning")
                .setMessage("Error al guardar el warning. $e")
                .setPositiveButton("OK") {_, _ ->
                    loadIncidencias()
                }
                .show()
        }
    }
    private fun saveToMultasGeneradas(tipo: String, calle:String, numero: String, concatenatedDescription: String) {
        val now = LocalDateTime.now()
        val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val formattedTime = now.format(timeFormatter)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val values = listOf(
                    formattedTime,
                    calle,
                    numero,
                    tipo,
                    concatenatedDescription
                )

                //########## GUARDAR NUEVA MULTA ######################
                if (dataRaw?.updateMulta(values  as List<String>) == false)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@IncidenciasMenu,
                            "NO Internet: ERROR salvando MULTA\nAuto reintento en 5 minutos",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                else
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@IncidenciasMenu, "Nueva Multas guardada", Toast.LENGTH_SHORT).show()
                    }
                //#####################################################

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@IncidenciasMenu, "Error saving to MultasGeneradas: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    private fun shareViaWhatsApp(tipo: String, calle: String, numero: String, numWarnings: Int) {
        val now = LocalDateTime.now()
        val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val formattedTime = now.format(timeFormatter)

        val incidencias = dataRaw?.getIncidenciasEventos()
        val incidenciasFilter = incidencias?.filter { it[4].toString().uppercase()==tipo.uppercase() && it[0].toString() == calle && it[1].toString() == numero }?.reversed()
        var arryUrl : ArrayList<Uri> = ArrayList<Uri>()
        incidenciasFilter?.forEachIndexed { index, inciden ->
            val file = File(inciden[5].toString())
            if (file.exists()) {
                arryUrl.add( FileProvider.getUriForFile(this, "${packageName}.fileprovider", file) )
            }
            if (index >= numWarnings) return@forEachIndexed
        }
        val descripcionIncidencia = configuraIncidencias.get(indexBtnClicked)[3].toString()
        val shareText = "Nueva multa $calle:$numero por $tipo legal: $formattedTime $descripcionIncidencia"

        val intent = Intent().apply {
            action = Intent.ACTION_SEND_MULTIPLE // Use ACTION_SEND_MULTIPLE
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arryUrl) // Add image URIs
            putExtra(Intent.EXTRA_TEXT, shareText) // Add the text
            type = "*/*" // Use a wildcard MIME type for mixed content

            // Add flags to grant read permissions to the receiving app
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
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